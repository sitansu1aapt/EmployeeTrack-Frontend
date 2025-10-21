package com.yatri

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.os.Bundle
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import okhttp3.MediaType.Companion.toMediaType

class DashboardFragment : Fragment() {
    private val permReq = 2001
    private lateinit var tvDutyStatus: TextView
    private lateinit var btnCheckIn: Button
    private lateinit var btnCheckOut: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvDutyStatus = view.findViewById(R.id.tvDutyStatus)
        btnCheckIn = view.findViewById(R.id.btnCheckIn)
        btnCheckOut = view.findViewById(R.id.btnCheckOut)
        view.findViewById<Button>(R.id.btnCheckIn).setOnClickListener {
            startActivity(Intent(requireContext(), com.yatri.checkin.CheckInActivity::class.java).putExtra("mode", "checkin"))
        }
        view.findViewById<Button>(R.id.btnCheckOut).setOnClickListener {
            startActivity(Intent(requireContext(), com.yatri.checkin.CheckInActivity::class.java).putExtra("mode", "checkout"))
        }
        view.findViewById<View>(R.id.cardAttendance).setOnClickListener {
            startActivity(Intent(requireContext(), com.yatri.attendance.AttendanceHistoryActivity::class.java))
        }
        view.findViewById<Button>(R.id.btnPatrols).setOnClickListener {
            startActivity(Intent(requireContext(), PatrolDashboardActivity::class.java))
        }
        view.findViewById<Switch>(R.id.swBackground).setOnCheckedChangeListener { switchView, isChecked ->
            val ctx = requireContext()
            if (isChecked) {
                val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fine || !coarse) {
                    ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), permReq)
                    android.widget.Toast.makeText(ctx, "Grant location permission to start tracking", android.widget.Toast.LENGTH_SHORT).show()
                    switchView.isChecked = false
                    return@setOnCheckedChangeListener
                }
                android.util.Log.d("LocationService", "Starting foreground location service")
                ctx.startForegroundService(Intent(ctx, LocationService::class.java))
            } else {
                android.util.Log.d("LocationService", "Stopping location service")
                ctx.stopService(Intent(ctx, LocationService::class.java))
            }
        }

        ensureLocationPermission()
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
        // Fetch duty status initially
        fetchDutyStatusAndUpdateUI()
    }

    override fun onResume() {
        super.onResume()
        // Refresh duty status when returning to dashboard
        fetchDutyStatusAndUpdateUI()
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
                                .post(okhttp3.RequestBody.create("application/json".toMediaType(), body))
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
                val isOnDuty = body.contains("\"is_on_duty\":true")
                android.util.Log.d("DashboardDuty", "Parsed isOnDuty=$isOnDuty")
                view?.post { setDutyUI(isOnDuty) }
            } catch (e: Exception) {
                android.util.Log.e("DashboardDuty", "Failed to fetch duty status", e)
                // leave existing state
            }
        }
    }

    private fun setDutyUI(isOnDuty: Boolean) {
        tvDutyStatus.text = if (isOnDuty) "  On Duty" else "  Off Duty"
        // Red dot color already set in XML; only toggle buttons
        btnCheckIn.isEnabled = !isOnDuty
        btnCheckIn.alpha = if (isOnDuty) 0.5f else 1f
        btnCheckOut.isEnabled = isOnDuty
        btnCheckOut.alpha = if (isOnDuty) 1f else 0.5f
    }
}


