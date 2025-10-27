package com.yatri

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.yatri.localization.LocalizationManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {
    private val permReq = 2001
    private val PREF_TRACKING = "background_tracking"
    private val PREF_NAME = "dashboard_prefs"
    private lateinit var tvDutyStatus: TextView
    private lateinit var tvDutyTimer: TextView
    private lateinit var tvBackgroundStatus: TextView
    private lateinit var btnCheckIn: Button
    private lateinit var btnCheckOut: Button
    private lateinit var btnEmergency: Button
    private lateinit var btnReportIssue: Button
    private lateinit var btnSwitchRole: Button
    private lateinit var btnSwitchLanguage: Button
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    
    // Timer variables
    private var dutyStartTime: Long = 0
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var isOnDuty = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupClickListeners(view)
        setupSwipeRefresh()
        setupTimer()
        ensureLocationPermission()
        setupMapFragment(view)

        // Restore tracking switch state
        val prefs = requireContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        val trackingActive = prefs.getBoolean(PREF_TRACKING, false)
        view.findViewById<Switch>(R.id.swBackground).isChecked = trackingActive
        updateBackgroundStatus(trackingActive)

        // Fetch duty status initially
        fetchDutyStatusAndUpdateUI()
        loadUserProfile()
    }

    private fun initializeViews(view: View) {
        tvDutyStatus = view.findViewById(R.id.tvDutyStatus)
        tvDutyTimer = view.findViewById(R.id.tvDutyTimer)
        tvBackgroundStatus = view.findViewById(R.id.tvBackgroundStatus)
        btnCheckIn = view.findViewById(R.id.btnCheckIn)
        btnCheckOut = view.findViewById(R.id.btnCheckOut)
        btnEmergency = view.findViewById(R.id.btnEmergency)
        btnReportIssue = view.findViewById(R.id.btnReportIssue)
        btnSwitchRole = view.findViewById(R.id.btnSwitchRole)
        btnSwitchLanguage = view.findViewById(R.id.btnSwitchLanguage)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
    }

    private fun setupClickListeners(view: View) {
        btnCheckIn.setOnClickListener {
            startActivity(Intent(requireContext(), com.yatri.checkin.CheckInActivity::class.java).putExtra("mode", "checkin"))
        }
        
        btnCheckOut.setOnClickListener {
            startActivity(Intent(requireContext(), com.yatri.checkin.CheckInActivity::class.java).putExtra("mode", "checkout"))
        }
        
        btnEmergency.setOnClickListener {
            handleEmergency()
        }
        
        btnReportIssue.setOnClickListener {
            handleReportIssue()
        }
        
        btnSwitchRole.setOnClickListener {
            showRoleSwitchDialog()
        }
        
        btnSwitchLanguage.setOnClickListener {
            showLanguageSwitchDialog()
        }
        
        view.findViewById<View>(R.id.cardAttendance).setOnClickListener {
            startActivity(Intent(requireContext(), com.yatri.attendance.AttendanceHistoryActivity::class.java))
        }
        
        view.findViewById<Button>(R.id.btnPatrols).setOnClickListener {
            startActivity(Intent(requireContext(), PatrolDashboardActivity::class.java))
        }
        view.findViewById<Switch>(R.id.swBackground).setOnCheckedChangeListener { switchView, isChecked ->
            val ctx = requireContext()
            // Persist switch state
            val prefs = ctx.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_TRACKING, isChecked).apply()

            if (isChecked) {
                val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fine || !coarse) {
                    ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), permReq)
                    android.widget.Toast.makeText(ctx, "Grant location permission to start tracking", android.widget.Toast.LENGTH_SHORT).show()
                    switchView.isChecked = false
                    prefs.edit().putBoolean(PREF_TRACKING, false).apply()
                    return@setOnCheckedChangeListener
                }
                android.util.Log.d("LocationService", "Starting foreground location service")
                ctx.startForegroundService(Intent(ctx, LocationService::class.java))
                updateBackgroundStatus(true)
            } else {
                android.util.Log.d("LocationService", "Stopping location service")
                ctx.stopService(Intent(ctx, LocationService::class.java))
                updateBackgroundStatus(false)
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            refreshDashboard()
        }
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
    }

    private fun setupTimer() {
        timerHandler = Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                if (isOnDuty && dutyStartTime > 0) {
                    val currentTime = System.currentTimeMillis()
                    val elapsedTime = currentTime - dutyStartTime
                    updateTimerDisplay(elapsedTime)
                    timerHandler?.postDelayed(this, 1000)
                }
            }
        }
    }

    private fun setupMapFragment(view: View) {
        val center = view.findViewById<ImageButton>(R.id.btnCenter)
        val refresh = view.findViewById<ImageButton>(R.id.btnRefresh)
        val mapFrag = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        center?.setOnClickListener {
            if (!ensureLocationReady()) return@setOnClickListener
            centerToLatest(mapFrag, postToServer = false)
        }
        refresh?.setOnClickListener {
            if (!ensureLocationReady()) return@setOnClickListener
            centerToLatest(mapFrag, postToServer = true)
        }
        view.post { centerToLatest(mapFrag, postToServer = false) }
    }

    // Emergency and Issue Handling
    private fun handleEmergency() {
        AlertDialog.Builder(requireContext())
            .setTitle("Emergency Alert")
            .setMessage("Are you sure you want to send an emergency alert? This will notify your supervisor immediately.")
            .setPositiveButton("Send Alert") { _, _ ->
                sendEmergencyAlert()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleReportIssue() {
        AlertDialog.Builder(requireContext())
            .setTitle("Report Issue")
            .setMessage("Select the type of issue you want to report:")
            .setItems(arrayOf("Equipment Malfunction", "Safety Concern", "Other Issue")) { _, which ->
                val issueType = when (which) {
                    0 -> "Equipment Malfunction"
                    1 -> "Safety Concern"
                    else -> "Other Issue"
                }
                reportIssue(issueType)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Role Switching
    private fun showRoleSwitchDialog() {
        lifecycleScope.launch {
            try {
                val prefs = requireContext().dataStore.data.first()
                val currentRole = prefs[PrefKeys.ACTIVE_ROLE_NAME] ?: "Employee"
                
                // For now, show a simple dialog. In a real app, you'd fetch available roles from API
                val roles = arrayOf("Employee", "Security Guard", "Supervisor")
                
                AlertDialog.Builder(requireContext())
                    .setTitle("Switch Role")
                    .setSingleChoiceItems(roles, roles.indexOf(currentRole)) { dialog, which ->
                        val selectedRole = roles[which]
                        switchToRole(selectedRole)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Error showing role switch dialog", e)
            }
        }
    }

    // Language Switching
    private fun showLanguageSwitchDialog() {
    val currentLang = LocalizationManager.getCurrentLanguage()
        val languages = arrayOf("English", "Odia")
        val langCodes = arrayOf("en", "or")
        val currentIndex = langCodes.indexOf(currentLang)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Select Language")
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val selectedLangCode = langCodes[which]
                LocalizationManager.setLanguage(requireContext(), selectedLangCode)
                updateLanguageButton(languages[which])
                dialog.dismiss()
                // Restart activity to apply language changes
                requireActivity().recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Update UI Methods
    private fun updateBackgroundStatus(isActive: Boolean) {
        tvBackgroundStatus.text = if (isActive) "Active" else "Inactive"
        tvBackgroundStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isActive) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }

    private fun updateTimerDisplay(elapsedTimeMs: Long) {
        val hours = (elapsedTimeMs / (1000 * 60 * 60)) % 24
        val minutes = (elapsedTimeMs / (1000 * 60)) % 60
        val seconds = (elapsedTimeMs / 1000) % 60
        
        val timeString = String.format("  %02d:%02d:%02d", hours, minutes, seconds)
        tvDutyTimer.text = timeString
    }

    private fun updateLanguageButton(language: String) {
        btnSwitchLanguage.text = language
    }

    private fun refreshDashboard() {
        fetchDutyStatusAndUpdateUI()
        loadUserProfile()
        view?.let { centerToLatest(childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment, postToServer = false) }
        
        // Stop refresh animation after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            swipeRefreshLayout.isRefreshing = false
        }, 1000)
    }

    // API Calls
    private fun sendEmergencyAlert() {
        kotlin.concurrent.thread {
            try {
                val body = org.json.JSONObject().apply {
                    put("type", "emergency")
                    put("message", "Emergency alert sent from mobile app")
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                
                val req = okhttp3.Request.Builder()
                    .url("${AppConfig.API_BASE_URL}alerts/emergency")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer ${TokenStore.token}")
                    .build()
                    
                okhttp3.OkHttpClient().newCall(req).execute().use { response ->
                    requireActivity().runOnUiThread {
                        if (response.isSuccessful) {
                            android.widget.Toast.makeText(requireContext(), "Emergency alert sent successfully", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(requireContext(), "Failed to send emergency alert", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Error sending emergency alert", e)
                requireActivity().runOnUiThread {
                    android.widget.Toast.makeText(requireContext(), "Error sending emergency alert", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun reportIssue(issueType: String) {
        kotlin.concurrent.thread {
            try {
                val body = org.json.JSONObject().apply {
                    put("type", "issue_report")
                    put("issue_type", issueType)
                    put("message", "Issue reported from mobile app")
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                
                val req = okhttp3.Request.Builder()
                    .url("${AppConfig.API_BASE_URL}reports/issue")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer ${TokenStore.token}")
                    .build()
                    
                okhttp3.OkHttpClient().newCall(req).execute().use { response ->
                    requireActivity().runOnUiThread {
                        if (response.isSuccessful) {
                            android.widget.Toast.makeText(requireContext(), "Issue reported successfully", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(requireContext(), "Failed to report issue", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Error reporting issue", e)
                requireActivity().runOnUiThread {
                    android.widget.Toast.makeText(requireContext(), "Error reporting issue", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun switchToRole(roleName: String) {
        lifecycleScope.launch {
            try {
                requireContext().dataStore.edit { prefs ->
                    prefs[PrefKeys.ACTIVE_ROLE_NAME] = roleName
                }
                btnSwitchRole.text = roleName
                android.widget.Toast.makeText(requireContext(), "Switched to $roleName role", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Error switching role", e)
            }
        }
    }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            try {
                val prefs = requireContext().dataStore.data.first()
                val userName = prefs[PrefKeys.USER_NAME] ?: "Employee"
                val roleName = prefs[PrefKeys.ACTIVE_ROLE_NAME] ?: "Employee"
                
                view?.findViewById<TextView>(R.id.tvUserName)?.text = userName
                view?.findViewById<TextView>(R.id.tvRole)?.text = roleName
                btnSwitchRole.text = roleName
                
                // Update language button
                val currentLang = LocalizationManager.getCurrentLanguage()
                updateLanguageButton(if (currentLang == "or") "Odia" else "English")
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Error loading user profile", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Restore tracking switch state
        val prefs = requireContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        val trackingActive = prefs.getBoolean(PREF_TRACKING, false)
        view?.findViewById<Switch>(R.id.swBackground)?.isChecked = trackingActive
        updateBackgroundStatus(trackingActive)

        // Refresh duty status when returning to dashboard
        fetchDutyStatusAndUpdateUI()
        loadUserProfile()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop timer to prevent memory leaks
        timerHandler?.removeCallbacks(timerRunnable!!)
    }

    private fun ensureLocationPermission() {
        val ctx = requireContext()
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine || !coarse) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), permReq)
        }
    }

    private fun ensureLocationReady(): Boolean {
        val ctx = requireContext()
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine || !coarse) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), permReq)
            return false
        }
        val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!enabled) {
            // Prompt user to enable location services
            try { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (_: Exception) {}
            android.widget.Toast.makeText(ctx, "Please enable Location services", android.widget.Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun centerToLatest(mapFrag: SupportMapFragment?, postToServer: Boolean) {
        val ctx = requireContext()
        val fused = LocationServices.getFusedLocationProviderClient(ctx)
        fused.lastLocation.addOnSuccessListener { last ->
            val lat = last?.latitude
            val lng = last?.longitude
            val acc = last?.accuracy
            if (lat != null && lng != null) {
                val latLng = LatLng(lat, lng)
                mapFrag?.getMapAsync { gMap ->
                    gMap.uiSettings.isMyLocationButtonEnabled = false
                    try { gMap.isMyLocationEnabled = true } catch (_: SecurityException) {}
                    gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                }
                view?.findViewById<TextView>(R.id.tvLatLng)?.text = "Lat: ${String.format("%.4f", lat)}, Long: ${String.format("%.4f", lng)}"
                view?.findViewById<TextView>(R.id.tvAccuracy)?.text = "Accuracy: ${acc?.toInt() ?: 0} m"
                if (postToServer && acc != null) {
                    kotlin.concurrent.thread {
                        try {
                            val body = org.json.JSONObject().apply {
                                put("latitude", lat)
                                put("longitude", lng)
                                put("accuracy", acc)
                            }.toString()
                            val req = okhttp3.Request.Builder()
                                .url("${AppConfig.API_BASE_URL}locations/me/update")
                                .post(body.toRequestBody("application/json".toMediaType()))
                                .build()
                            okhttp3.OkHttpClient().newCall(req).execute().close()
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun fetchDutyStatusAndUpdateUI() {
        // Call /users/is-on-duty and update buttons
        kotlin.concurrent.thread {
            try {
                val url = com.yatri.AppConfig.API_BASE_URL + "users/is-on-duty"
                val token = com.yatri.TokenStore.token
                android.util.Log.d("DashboardDuty", "GET $url, tokenPresent=${!token.isNullOrBlank()} tokenPrefix=${token?.take(12)}")
                val reqBuilder = okhttp3.Request.Builder().url(url)
                if (!token.isNullOrBlank()) reqBuilder.header("Authorization", "Bearer $token")
                val req = reqBuilder.build()
                val client = okhttp3.OkHttpClient()
                val resp = client.newCall(req).execute()
                val status = resp.code
                val body = resp.body?.string().orEmpty()
                android.util.Log.d("DashboardDuty", "Response status=$status body=${body.take(300)}")
                
                val isOnDutyFromApi = body.contains("\"is_on_duty\":true")
                
                // Try to extract check_in_time from response
                var checkInTime: String? = null
                try {
                    val jsonResponse = org.json.JSONObject(body)
                    if (jsonResponse.has("data") && !jsonResponse.isNull("data")) {
                        val data = jsonResponse.getJSONObject("data")
                        if (data.has("check_in_time") && !data.isNull("check_in_time")) {
                            checkInTime = data.getString("check_in_time")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DashboardDuty", "Error parsing check_in_time", e)
                }
                
                android.util.Log.d("DashboardDuty", "Parsed isOnDuty=$isOnDutyFromApi, checkInTime=$checkInTime")
                view?.post { 
                    setDutyUI(isOnDutyFromApi, checkInTime)
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardDuty", "Failed to fetch duty status", e)
                // leave existing state
            }
        }
    }

    private fun setDutyUI(isOnDuty: Boolean, checkInTime: String? = null) {
        this.isOnDuty = isOnDuty
        tvDutyStatus.text = if (isOnDuty) "  On Duty" else "  Off Duty"
        
        // Handle timer
        if (isOnDuty && checkInTime != null) {
            try {
                // Parse check_in_time and start timer
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                val checkInDate = dateFormat.parse(checkInTime)
                dutyStartTime = checkInDate?.time ?: System.currentTimeMillis()
                
                tvDutyTimer.visibility = View.VISIBLE
                startTimer()
            } catch (e: Exception) {
                android.util.Log.e("DashboardDuty", "Error parsing check_in_time: $checkInTime", e)
                // Fallback to current time
                dutyStartTime = System.currentTimeMillis()
                tvDutyTimer.visibility = View.VISIBLE
                startTimer()
            }
        } else if (isOnDuty) {
            // On duty but no check-in time, use current time
            dutyStartTime = System.currentTimeMillis()
            tvDutyTimer.visibility = View.VISIBLE
            startTimer()
        } else {
            tvDutyTimer.visibility = View.GONE
            stopTimer()
        }
        
        // Red dot color already set in XML; only toggle buttons
        btnCheckIn.isEnabled = !isOnDuty
        btnCheckIn.alpha = if (isOnDuty) 0.5f else 1f
        btnCheckOut.isEnabled = isOnDuty
        btnCheckOut.alpha = if (isOnDuty) 1f else 0.5f
    }

    private fun startTimer() {
        timerHandler?.removeCallbacks(timerRunnable!!)
        timerHandler?.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerHandler?.removeCallbacks(timerRunnable!!)
    }
}


