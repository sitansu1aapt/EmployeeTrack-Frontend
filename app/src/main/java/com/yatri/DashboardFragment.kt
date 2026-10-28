package com.yatri

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
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

/** Asked at most once per app launch, so the dialog doesn't reappear on every dashboard refresh. */
private var askedBackgroundLocation = false

class DashboardFragment : Fragment() {
    private val permReq = 2001
    private val backgroundPermReq = 2002
    private val PREF_TRACKING = "background_tracking"
    private val PREF_NAME = "dashboard_prefs"
    private lateinit var tvDutyStatus: TextView
    private lateinit var tvDutyTimer: TextView
    private lateinit var tvBackgroundStatus: TextView
    private lateinit var btnCheckIn: Button
    private lateinit var btnCheckOut: Button
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    
    // Timer variables
    private var dutyStartTime: Long = 0
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var isOnDuty = false
    private var checkInTimeStr: String? = null
    private var gracePeriodCheckout: Int = 0
    
    private var supervisorName: String? = null
    private var supervisorRole: String? = null
    private var supervisorId: Int? = null
    
    // Location dialog reference
    private var locationServicesDialog: AlertDialog? = null
    private var locationReady = false

    // Tracking follows duty (see TrackingController). Guard so programmatic switch updates
    // don't re-enter the switch listener.
    private var settingTrackingSwitch = false
    private var trackingBlockedToastShown = false

    private fun setTrackingSwitch(checked: Boolean) {
        val sw = view?.findViewById<Switch>(R.id.swBackground) ?: return
        settingTrackingSwitch = true
        sw.isChecked = checked
        settingTrackingSwitch = false
        updateBackgroundStatus(checked)
    }

    /** Start/stop tracking to match duty and reflect the result on the switch. */
    private fun syncTrackingWithDuty(onDuty: Boolean) {
        val ctx = context ?: return
        val blocked = com.yatri.location.TrackingController.onDutyStatusKnown(ctx, onDuty)
        setTrackingSwitch(onDuty && blocked == null)
        if (onDuty && blocked == null) maybeAskBackgroundLocation()
        if (onDuty && blocked != null) {
            tvBackgroundStatus.text = "Inactive — $blocked"
            if (!trackingBlockedToastShown) {
                android.widget.Toast.makeText(ctx, blocked, android.widget.Toast.LENGTH_LONG).show()
                trackingBlockedToastShown = true
            }
        } else {
            trackingBlockedToastShown = false
        }
    }

    /**
     * Tracking can only resume on its own after an app update or phone restart if location is
     * "Allow all the time". Explain and ask once per app launch while on duty.
     */
    private fun maybeAskBackgroundLocation() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q || askedBackgroundLocation) return
        val act = activity ?: return
        if (ContextCompat.checkSelfPermission(act, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) return
        askedBackgroundLocation = true
        AlertDialog.Builder(act)
            .setTitle("Allow location all the time")
            .setMessage(
                "So your duty tracking keeps working after a phone restart or app update, " +
                    "set Yatri's location permission to \"Allow all the time\"."
            )
            .setPositiveButton("Open settings") { _, _ ->
                // Android 11+ opens the app's location permission page for this request.
                @Suppress("DEPRECATION")
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), backgroundPermReq)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == permReq) {
            // If any permission is denied, close the app immediately
            if (grantResults.isEmpty() || grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                activity?.finishAffinity()
                return
            }
            ensureLocationPermission()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        locationReady = false
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupClickListeners(view)
        setupSwipeRefresh()
        setupTimer()
        ensureLocationPermission()
        blockDashboardIfLocationNotReady(view)
        setupMapFragment(view)

        // Restore tracking switch state
        val prefs = requireContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        val trackingActive = prefs.getBoolean(PREF_TRACKING, false)
        setTrackingSwitch(trackingActive)

        // Fetch duty status initially
        fetchDutyStatusAndUpdateUI()
        fetchSupervisor()
    }

    private fun initializeViews(view: View) {
        tvDutyStatus = view.findViewById(R.id.tvDutyStatus)
        tvDutyTimer = view.findViewById(R.id.tvDutyTimer)
        tvBackgroundStatus = view.findViewById(R.id.tvBackgroundStatus)
        btnCheckIn = view.findViewById(R.id.btnCheckIn)
        btnCheckOut = view.findViewById(R.id.btnCheckOut)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
    }

    private fun setupClickListeners(view: View) {
        btnCheckIn.setOnClickListener {
            startActivity(Intent(requireContext(), com.yatri.checkin.CheckInActivity::class.java).putExtra("mode", "checkin"))
        }
        
        btnCheckOut.setOnClickListener {
            val intent = Intent(requireContext(), com.yatri.checkin.CheckInActivity::class.java)
            intent.putExtra("mode", "checkout")
            intent.putExtra("check_in_time", checkInTimeStr)
            intent.putExtra("grace_period_checkout", gracePeriodCheckout)
            startActivity(intent)
        }

        view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnChat)?.setOnClickListener {
            kotlin.concurrent.thread {
                try {
                    // Must match GET /messages/... partner used by RN admin chat (same Firestore conversation id).
                    val url = com.yatri.AppConfig.API_BASE_URL + "messages/supervisor"
                    val token = com.yatri.TokenStore.token
                    val reqBuilder = okhttp3.Request.Builder().url(url)
                    if (!token.isNullOrBlank()) reqBuilder.header("Authorization", "Bearer $token")
                    val resp = okhttp3.OkHttpClient().newCall(reqBuilder.build()).execute()
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        activity?.runOnUiThread {
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Could not load supervisor for chat",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@thread
                    }
                    val jsonResponse = org.json.JSONObject(body)
                    val data = jsonResponse.optJSONObject("data") ?: return@thread
                    // messages/supervisor: data is the contact. my-supervisor: data.supervisor
                    val supervisor = when {
                        data.has("supervisor") && !data.isNull("supervisor") ->
                            data.getJSONObject("supervisor")
                        else -> data
                    }
                    val rawId = supervisor.opt("user_id")
                    val receiverId = when (rawId) {
                        null, org.json.JSONObject.NULL -> ""
                        else -> rawId.toString()
                    }
                    if (receiverId.isEmpty() || receiverId == "-1") {
                        activity?.runOnUiThread {
                            android.widget.Toast.makeText(
                                requireContext(),
                                "No supervisor assigned for messaging",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        return@thread
                    }
                    val name = supervisor.optString("full_name", supervisor.optString("name", supervisorName ?: "Administrator"))
                    val role = supervisor.optString("role_name", supervisor.optString("role", supervisorRole ?: "Admin"))
                    activity?.runOnUiThread {
                        startActivity(Intent(requireContext(), ChatActivity::class.java)
                            .putExtra(EXTRA_RECEIVER_USER_ID, receiverId)
                            .putExtra("adminName", name)
                            .putExtra("adminRole", role))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Dashboard", "Open chat failed", e)
                    activity?.runOnUiThread {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Unable to open messages",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        
        
        view.findViewById<View>(R.id.cardAttendance).setOnClickListener {
            startActivity(Intent(requireContext(), com.yatri.attendance.AttendanceHistoryActivity::class.java))
        }
        
        view.findViewById<Button>(R.id.btnPatrols).setOnClickListener {
            startActivity(Intent(requireContext(), PatrolDashboardActivity::class.java))
        }
        view.findViewById<Switch>(R.id.swBackground).setOnCheckedChangeListener { switchView, isChecked ->
            if (settingTrackingSwitch) return@setOnCheckedChangeListener
            val ctx = requireContext()
            // Tracking follows duty: it can't be switched off while on duty, or on while off duty.
            if (!isChecked && isOnDuty) {
                android.widget.Toast.makeText(ctx, "Tracking stays on while you're on duty. Check out to stop it.", android.widget.Toast.LENGTH_SHORT).show()
                setTrackingSwitch(true)
                return@setOnCheckedChangeListener
            }
            if (isChecked && !isOnDuty) {
                android.widget.Toast.makeText(ctx, "Tracking starts automatically when you check in.", android.widget.Toast.LENGTH_SHORT).show()
                setTrackingSwitch(false)
                return@setOnCheckedChangeListener
            }
            // Persist switch state
            val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_TRACKING, isChecked).apply()

            if (isChecked) {
                // Check location permissions first
                val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fine || !coarse) {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        permReq
                    )
                    android.widget.Toast.makeText(ctx, "Grant location permission to start tracking", android.widget.Toast.LENGTH_SHORT).show()
                    setTrackingSwitch(false)
                    prefs.edit().putBoolean(PREF_TRACKING, false).apply()
                    return@setOnCheckedChangeListener
                }

                // Check if location services are enabled
                val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

                if (!isGpsEnabled && !isNetworkEnabled) {
                    // Dismiss any existing dialog first
                    locationServicesDialog?.dismiss()

                    locationServicesDialog = AlertDialog.Builder(ctx)
                        .setTitle("Location Services Required")
                        .setMessage("Please enable GPS/Location services to start background tracking.")
                        .setPositiveButton("Enable Location") { _, _ ->
                            try {
                                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(ctx, "Please enable Location services in Settings", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                            locationServicesDialog = null
                        }
                        .setOnDismissListener {
                            locationServicesDialog = null
                        }
                        .show()

                    setTrackingSwitch(false)
                    prefs.edit().putBoolean(PREF_TRACKING, false).apply()
                    return@setOnCheckedChangeListener
                }

                // Check notification permission (Android 13+) and channel state
                if (!hasNotificationPermission(ctx)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ActivityCompat.requestPermissions(
                            requireActivity(),
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            permReq
                        )
                    }
                    android.widget.Toast.makeText(
                        ctx,
                        "Enable notification permission to start background tracking",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    setTrackingSwitch(false)
                    prefs.edit().putBoolean(PREF_TRACKING, false).apply()
                    return@setOnCheckedChangeListener
                }

                if (!isLocationChannelEnabled(ctx)) {
                    android.widget.Toast.makeText(
                        ctx,
                        "Please enable Yatri location notifications in system settings to start background tracking",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    setTrackingSwitch(false)
                    prefs.edit().putBoolean(PREF_TRACKING, false).apply()
                    return@setOnCheckedChangeListener
                }

                // Only reached on duty (e.g. retrying after granting a missing permission).
                android.util.Log.d("LocationService", "Starting foreground location service")
                val blocked = com.yatri.location.TrackingController.start(ctx)
                setTrackingSwitch(blocked == null)
                if (blocked != null) android.widget.Toast.makeText(ctx, blocked, android.widget.Toast.LENGTH_LONG).show()
            } else {
                // Only reached off duty, where tracking is already stopped.
                android.util.Log.d("LocationService", "Stopping location service")
                com.yatri.location.TrackingController.stop(ctx)
                setTrackingSwitch(false)
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

    private fun hasNotificationPermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun isLocationChannelEnabled(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel("loc_ch") ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
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
                        dialog.dismiss()
                // Restart activity to apply language changes
                requireActivity().recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Block all dashboard actions if location is not ready
    private fun blockDashboardIfLocationNotReady(view: View) {
        val btns = listOf(
            view.findViewById<Button>(R.id.btnCheckIn),
            view.findViewById<Button>(R.id.btnCheckOut),
            view.findViewById<Button>(R.id.btnPatrols)
        )
        btns.forEach { it?.isEnabled = false; it?.alpha = 0.5f }
        view.findViewById<Switch>(R.id.swBackground)?.isEnabled = false
        view.findViewById<View>(R.id.cardAttendance)?.isEnabled = false
        // Optionally show a message
        view.findViewById<TextView?>(R.id.tvDutyStatus)?.text = "Enable location to use app"
    }
    private fun unblockDashboard(view: View) {
        val btns = listOf(
            view.findViewById<Button>(R.id.btnCheckIn),
            view.findViewById<Button>(R.id.btnCheckOut),
            view.findViewById<Button>(R.id.btnPatrols)
        )
        btns.forEach { it?.isEnabled = true; it?.alpha = 1f }
        view.findViewById<Switch>(R.id.swBackground)?.isEnabled = true
        view.findViewById<View>(R.id.cardAttendance)?.isEnabled = true
        fetchDutyStatusAndUpdateUI()
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
        // Ensure elapsed time is not negative (same as React Native safety check)
        val safeElapsedTime = kotlin.math.max(0L, elapsedTimeMs)
        
        // Use same calculation as React Native formatDuration function
        val totalSeconds = (safeElapsedTime / 1000).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        // Format like React Native: "2h 15m 30s"
        val timeString = "${hours}h ${minutes}m ${seconds}s"
        tvDutyTimer.text = timeString
    }

    // Removed updateLanguageButton: no longer needed after removing language UI

    private fun refreshDashboard() {
        fetchDutyStatusAndUpdateUI()
        view?.let { centerToLatest(childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment, postToServer = false) }
        
        // Stop refresh animation after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            swipeRefreshLayout.isRefreshing = false
        }, 1000)
    }

    // API Calls

    private fun switchToRole(roleName: String) {
        lifecycleScope.launch {
            try {
                requireContext().dataStore.edit { prefs ->
                    prefs[PrefKeys.ACTIVE_ROLE_NAME] = roleName
                }
                android.widget.Toast.makeText(requireContext(), "Switched to $roleName role", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Error switching role", e)
            }
        }
    }
    override fun onResume() {
        locationReady = false
        super.onResume()
        // Check location services when returning to dashboard
        checkLocationServicesEnabled()
        
        // Restore tracking switch state
        val prefs = requireContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        val trackingActive = prefs.getBoolean(PREF_TRACKING, false)
        setTrackingSwitch(trackingActive)

        // Refresh duty status when returning to dashboard
        fetchDutyStatusAndUpdateUI()
        
        // Update map location if location services are now enabled
        updateMapLocationIfEnabled()

        // Double check geofence (User Request)
        (activity as? EmployeeActivity)?.checkPermissionsAndGeofence()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop timer to prevent memory leaks
        timerHandler?.removeCallbacks(timerRunnable!!)
        
        // Dismiss location dialog if showing to prevent memory leaks
        locationServicesDialog?.dismiss()
        locationServicesDialog = null
    }

    private fun ensureLocationPermission() {
        locationReady = false
        val ctx = requireContext()
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (!fine || !coarse) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), permReq)
            // keep locationReady false
            return
        }
        
        // Check if GPS/Location services are enabled
        checkLocationServicesEnabled()
    }
    
    private fun checkLocationServicesEnabled() {
        val activity = activity
        val ctx = requireContext()
        val locationManager = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        if (!isGpsEnabled && !isNetworkEnabled) {
            // Only show dialog if it's not already showing
            if (locationServicesDialog?.isShowing != true) {
                showLocationServicesDialog()
            }
            locationReady = false
            blockDashboardIfLocationNotReady(view ?: return)
        } else {
            // Location is enabled, dismiss dialog if it's showing
            locationServicesDialog?.dismiss()
            locationServicesDialog = null
            locationReady = true
            unblockDashboard(view ?: return)
            // Update map location now that location services are enabled
            updateMapLocationIfEnabled()
        }
    }
    
    private fun showLocationServicesDialog() {
        val activity = activity
        val frag = this
        locationServicesDialog = AlertDialog.Builder(requireContext())
            .setTitle("Location Services Required")
            .setMessage("Background location tracking is mandatory for this app. Please enable GPS/Location services to continue.")
            .setPositiveButton("Enable Location") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (e: Exception) {
                    android.widget.Toast.makeText(requireContext(), "Please enable Location services in Settings", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                locationServicesDialog = null
                android.widget.Toast.makeText(requireContext(), "Location services are required for background tracking", android.widget.Toast.LENGTH_LONG).show()
                activity?.finishAffinity() // Close app if user cancels
            }
            .setOnDismissListener {
                locationServicesDialog = null
                activity?.finishAffinity() // Close app if dialog dismissed
            }
            .setCancelable(false)
            .show()
    }
    
    private fun updateMapLocationIfEnabled() {
        val ctx = requireContext()
        
        // Check if we have location permissions
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (!fine || !coarse) {
            return // No permissions, can't update location
        }
        
        // Check if location services are enabled
        val locationManager = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        if (!isGpsEnabled && !isNetworkEnabled) {
            return // Location services not enabled
        }
        
        // Update map location
        val mapFrag = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        centerToLatest(mapFrag, postToServer = false)
        
        android.widget.Toast.makeText(ctx, "Location updated", android.widget.Toast.LENGTH_SHORT).show()
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
                
                android.util.Log.d("DashboardLocation", "Location updated: Lat=$lat, Lng=$lng, Accuracy=${acc?.toInt() ?: 0}m")
                
                if (postToServer && acc != null) {
                    kotlin.concurrent.thread {
                        try {
                            val body = org.json.JSONObject().apply {
                                put("latitude", lat)
                                put("longitude", lng)
                                put("accuracy", acc)
                            }.toString()
                            val token = TokenStore.token
                            val url = "${AppConfig.API_BASE_URL}locations/me/update"
                            android.util.Log.i("LOCATION_API", "🌐 [Dashboard Manual Update] Server: ${AppConfig.API_BASE_URL} | Type: locations/me/update | URL: $url | Lat=$lat, Lng=$lng, Acc=$acc")
                            val req = okhttp3.Request.Builder()
                                .url(url)
                                .apply {
                                    if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
                                }
                                .post(body.toRequestBody("application/json".toMediaType()))
                                .build()
                            okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                                android.util.Log.i("LOCATION_API", "📥 [Dashboard Manual Update] Response code=${resp.code} for $url")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("LOCATION_API", "❌ [Dashboard Manual Update] Failed to send location to server ${AppConfig.API_BASE_URL}", e)
                        }
                    }
                }
            } else {
                android.util.Log.w("DashboardLocation", "No location available from fused location provider")
                view?.findViewById<TextView>(R.id.tvLatLng)?.text = "Location: Not available"
                view?.findViewById<TextView>(R.id.tvAccuracy)?.text = "Accuracy: --"
            }
        }.addOnFailureListener { e ->
            android.util.Log.e("DashboardLocation", "Failed to get location", e)
            view?.findViewById<TextView>(R.id.tvLatLng)?.text = "Location: Error"
            view?.findViewById<TextView>(R.id.tvAccuracy)?.text = "Accuracy: --"
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

                if (status !in 200..299) {
                    // Server error / auth problem: don't treat it as "off duty" (that would stop
                    // tracking); keep tracking in line with the last known duty state instead.
                    view?.post {
                        val ctx = context ?: return@post
                        syncTrackingWithDuty(com.yatri.location.TrackingController.lastKnownOnDuty(ctx))
                    }
                    return@thread
                }

                val isOnDutyFromApi = body.contains("\"is_on_duty\":true")
                
                // Try to extract check_in_time and grace periods from response
                var checkInTime: String? = null
                var graceCheckout: Int = 0
                try {
                    val jsonResponse = org.json.JSONObject(body)
                    if (jsonResponse.has("data") && !jsonResponse.isNull("data")) {
                        val data = jsonResponse.getJSONObject("data")
                        checkInTime = data.optString("check_in_time", null)
                        val gOut = data.optInt("grace_period_minutes_checkout", 0)
                        val gIn = data.optInt("grace_period_minutes_checkin", 0)
                        // Client-side fallback: if checkout grace is 0, use check-in grace
                        graceCheckout = if (gOut > 0) gOut else gIn
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DashboardDuty", "Error parsing check_in_time or grace periods", e)
                }
                
                android.util.Log.d("DashboardDuty", "Parsed isOnDuty=$isOnDutyFromApi, checkInTime=$checkInTime, graceCheckout=$graceCheckout")
                view?.post { 
                    setDutyUI(isOnDutyFromApi, checkInTime, graceCheckout)
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardDuty", "Failed to fetch duty status", e)
                // Offline: keep tracking in line with the last known duty state so an on-duty
                // employee who opens the app without network is still tracked (points queue offline).
                view?.post {
                    val ctx = context ?: return@post
                    syncTrackingWithDuty(com.yatri.location.TrackingController.lastKnownOnDuty(ctx))
                }
            }
        }
    }

    private fun setDutyUI(isOnDuty: Boolean, checkInTime: String? = null, graceCheckout: Int = 0) {
        this.isOnDuty = isOnDuty
        this.checkInTimeStr = checkInTime
        this.gracePeriodCheckout = graceCheckout
        tvDutyStatus.text = if (isOnDuty) "  On Duty" else "  Off Duty"
        
        // Handle timer - using same approach as React Native app
        if (isOnDuty && checkInTime != null) {
            try {
                // Parse check_in_time using multiple formats (same as React Native)
                val checkInDate = parseCheckInTime(checkInTime)
                dutyStartTime = checkInDate?.time ?: System.currentTimeMillis()
                
                android.util.Log.d("DashboardDuty", "Timer started. CheckInTime: $checkInTime, DutyStartTime: $dutyStartTime")
                
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
            android.util.Log.d("DashboardDuty", "Timer started with current time: $dutyStartTime")
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

        // Tracking follows duty: on after check-in / login / app open while on duty, off after check-out.
        syncTrackingWithDuty(isOnDuty)
    }

    private fun startTimer() {
        timerHandler?.removeCallbacks(timerRunnable!!)
        timerHandler?.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerHandler?.removeCallbacks(timerRunnable!!)
    }
    
    private fun parseCheckInTime(checkInTime: String): java.util.Date? {
        // React Native's new Date(utcString) automatically parses UTC strings correctly
        // We need to match that behavior by parsing UTC timestamps as UTC
        
        val formats = listOf(
            // UTC formats - these have 'Z' or timezone offset, parse as UTC
            Triple(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US), java.util.TimeZone.getTimeZone("UTC"), true),
            Triple(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US), java.util.TimeZone.getTimeZone("UTC"), true),
            Triple(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US), null, true), // XXX includes timezone
            Triple(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US), null, true),
            // Local formats - parse as local time
            Triple(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US), null, false)
        )
        
        for ((format, timezone, isUtc) in formats) {
            try {
                if (timezone != null) {
                    format.timeZone = timezone
                } else if (isUtc && (checkInTime.contains("Z") || checkInTime.matches(Regex(".*[+-]\\d{2}:\\d{2}")))) {
                    // Has timezone indicator, parse as UTC
                    format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val parsed = format.parse(checkInTime)
                if (parsed != null) {
                    android.util.Log.d("DashboardDuty", "Parsed check-in time: $checkInTime -> $parsed (${if (isUtc) "UTC" else "local"})")
                    return parsed
                }
            } catch (e: Exception) {
                // Try next format
                continue
            }
        }
        
        android.util.Log.w("DashboardDuty", "Could not parse check-in time: $checkInTime")
        return null
    }

    private fun fetchSupervisor() {
        kotlin.concurrent.thread {
            try {
                val url = com.yatri.AppConfig.API_BASE_URL + "messages/supervisor"
                val token = com.yatri.TokenStore.token
                val reqBuilder = okhttp3.Request.Builder().url(url)
                if (!token.isNullOrBlank()) reqBuilder.header("Authorization", "Bearer $token")
                val req = reqBuilder.build()
                val client = okhttp3.OkHttpClient()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string().orEmpty()
                
                if (resp.isSuccessful) {
                    val jsonResponse = org.json.JSONObject(body)
                    if (jsonResponse.has("data") && !jsonResponse.isNull("data")) {
                        val data = jsonResponse.getJSONObject("data")
                        val supervisor = when {
                            data.has("supervisor") && !data.isNull("supervisor") ->
                                data.getJSONObject("supervisor")
                            else -> data
                        }
                        if (supervisor.has("user_id") && !supervisor.isNull("user_id")) {
                            supervisorName = supervisor.optString("full_name", supervisor.optString("name", "Administrator"))
                            supervisorRole = supervisor.optString("role_name", supervisor.optString("role", "Admin"))
                            val uid = supervisor.opt("user_id")
                            supervisorId = when (uid) {
                                is Number -> uid.toInt()
                                else -> supervisor.optInt("user_id", -1)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Failed to fetch supervisor", e)
            }
        }
    }
}
