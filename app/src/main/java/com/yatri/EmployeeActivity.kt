package com.yatri

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.yatri.checkin.DiscrepancyReportRequest
import com.yatri.checkin.GeofenceCheckDetails
import com.yatri.checkin.GeofenceChecker
import com.yatri.checkin.SiteAssignmentApi

class EmployeeActivity : AppCompatActivity(), OnMapReadyCallback {
    
    private lateinit var geofenceWarningLayout: View
    private lateinit var btnRetry: Button
    private lateinit var btnReportGeofence: Button
    private lateinit var btnCheckInAnyway: Button
    private lateinit var tvCheckInAnywayHint: TextView
    private lateinit var mapWarningView: MapView
    private lateinit var pbGeofenceLoading: ProgressBar
    private lateinit var tvGeofenceSiteName: TextView
    private lateinit var tvGeofenceUserLocation: TextView
    private lateinit var tvGeofenceDistanceStatus: TextView
    private lateinit var cardGeofenceRcaReason: androidx.cardview.widget.CardView
    private lateinit var tvGeofenceRcaTitle: TextView
    private lateinit var tvGeofenceRcaDesc: TextView

    private lateinit var btnFocusUser: TextView
    private lateinit var btnFocusSite: TextView
    private lateinit var btnFocusBoth: TextView

    private var googleMap: GoogleMap? = null
    private var lastGeofenceDetails: GeofenceCheckDetails? = null
    private var overrodeOutOfBoundary = false

    private val locationPermissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                checkGeofence()
            }
            else -> {
                showGeofenceWarning(true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employee)

        geofenceWarningLayout = findViewById(R.id.geofenceWarningLayout)
        btnRetry = findViewById(R.id.btnRetryGeofence)
        btnReportGeofence = findViewById(R.id.btnReportGeofence)
        btnCheckInAnyway = findViewById(R.id.btnCheckInAnyway)
        tvCheckInAnywayHint = findViewById(R.id.tvCheckInAnywayHint)
        mapWarningView = findViewById(R.id.mapWarningView)
        pbGeofenceLoading = findViewById(R.id.pbGeofenceLoading)
        tvGeofenceSiteName = findViewById(R.id.tvGeofenceSiteName)
        tvGeofenceUserLocation = findViewById(R.id.tvGeofenceUserLocation)
        tvGeofenceDistanceStatus = findViewById(R.id.tvGeofenceDistanceStatus)
        cardGeofenceRcaReason = findViewById(R.id.cardGeofenceRcaReason)
        tvGeofenceRcaTitle = findViewById(R.id.tvGeofenceRcaTitle)
        tvGeofenceRcaDesc = findViewById(R.id.tvGeofenceRcaDesc)

        btnFocusUser = findViewById(R.id.btnFocusUser)
        btnFocusSite = findViewById(R.id.btnFocusSite)
        btnFocusBoth = findViewById(R.id.btnFocusBoth)

        mapWarningView.onCreate(savedInstanceState)
        mapWarningView.getMapAsync(this)

        btnRetry.setOnClickListener {
            checkPermissionsAndGeofence()
        }

        btnReportGeofence.setOnClickListener {
            showReportDialog()
        }

        btnCheckInAnyway.setOnClickListener {
            confirmOverrideCheckIn()
        }

        btnFocusUser.setOnClickListener {
            val userLoc = lastGeofenceDetails?.userLocation ?: return@setOnClickListener
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(userLoc.latitude, userLoc.longitude), 17f))
        }

        btnFocusSite.setOnClickListener {
            val poly = lastGeofenceDetails?.geofencePolygon ?: emptyList()
            if (poly.size >= 3) {
                val siteBuilder = LatLngBounds.Builder()
                for (pt in poly) {
                    siteBuilder.include(LatLng(pt.lat, pt.lng))
                }
                try {
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(siteBuilder.build(), 120))
                } catch (e: Exception) {
                    val centerLat = poly.map { it.lat }.average()
                    val centerLng = poly.map { it.lng }.average()
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLng), 16f))
                }
            } else {
                Toast.makeText(this, "No geofence boundary coordinates available for this site", Toast.LENGTH_SHORT).show()
            }
        }

        btnFocusBoth.setOnClickListener {
            fitMapToBounds(lastGeofenceDetails)
        }

        // Ensure token is loaded from DataStore before any API calls
        lifecycleScope.launch {
            val tok = applicationContext.dataStore.data.first()[com.yatri.PrefKeys.AUTH_TOKEN]
            if (!tok.isNullOrEmpty()) {
                com.yatri.TokenStore.token = tok
                android.util.Log.d("EmployeeActivity", "Token loaded in EmployeeActivity: $tok")
                checkPermissionsAndGeofence()
            } else {
                android.util.Log.d("EmployeeActivity", "No token found in DataStore")
            }
        }

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tab_dashboard -> switch(DashboardFragment())
                R.id.tab_tasks -> switch(com.yatri.tasks.TasksFragment())
                R.id.tab_messages -> switch(MessagesFragment())
                R.id.tab_profile -> switch(ProfileFragment())
            }
            true
        }
        if (savedInstanceState == null) {
            val open = intent?.getStringExtra("open_tab")
            nav.selectedItemId = when (open) {
                "tasks" -> R.id.tab_tasks
                "messages" -> R.id.tab_messages
                "profile" -> R.id.tab_profile
                else -> R.id.tab_dashboard
            }
        }
    }

    private fun showReportDialog() {
        AlertDialog.Builder(this)
            .setTitle("Report Location Discrepancy")
            .setMessage("Are you currently physically at your assigned work site? Submitting this report will send your GPS coordinates and RCA diagnostics to Admin.")
            .setPositiveButton("Submit Report") { _, _ ->
                reportDiscrepancyToAdmin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmOverrideCheckIn() {
        val details = lastGeofenceDetails
        if (details == null || details.assignments.isEmpty() || details.userLocation == null) {
            Toast.makeText(this, "Override not available without an assigned site and a GPS fix.", Toast.LENGTH_LONG).show()
            return
        }
        val dist = details.distanceToCenterMeters.toInt()
        AlertDialog.Builder(this)
            .setTitle("Check in outside boundary?")
            .setMessage(
                "Your GPS shows you about $dist m from the site boundary. If this is due to weak GPS " +
                    "and you ARE at your work site, you can check in anyway.\n\nThis override is recorded " +
                    "and sent to Admin with your GPS and accuracy."
            )
            .setPositiveButton("Check in anyway") { _, _ ->
                overrodeOutOfBoundary = true
                // Record the override for accountability (reuses the discrepancy report endpoint).
                reportDiscrepancyToAdmin(
                    remarks = "OUT-OF-BOUNDARY CHECK-IN OVERRIDE: employee confirmed physical presence (${dist}m from center, ±${details.accuracyMeters.toInt()}m accuracy)"
                )
                // Unlock the app the same way an in-boundary result does.
                showGeofenceWarning(false)
                Toast.makeText(
                    this,
                    "Checked in outside boundary — override recorded and sent to Admin.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reportDiscrepancyToAdmin(remarks: String = "User reported physical presence at assigned site") {
        pbGeofenceLoading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val details = lastGeofenceDetails ?: GeofenceChecker.getGeofenceDetails(this@EmployeeActivity)
            val siteId = details.matchedAssignment?.siteId ?: details.assignments.firstOrNull()?.siteId
            val loc = details.userLocation

            android.util.Log.d("EmployeeActivity", "Submitting discrepancy report: siteId=$siteId, loc=$loc, distance=${details.distanceToCenterMeters}")

            try {
                val json = Json { ignoreUnknownKeys = true }
                val authInterceptor = Interceptor { chain ->
                    val original = chain.request()
                    val requestBuilder = original.newBuilder()
                    val token = TokenStore.token
                    if (!token.isNullOrEmpty()) {
                        requestBuilder.header("Authorization", "Bearer $token")
                    }
                    chain.proceed(requestBuilder.build())
                }
                val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
                }
                val client = OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .addInterceptor(loggingInterceptor)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(AppConfig.API_BASE_URL)
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()

                val api = retrofit.create(SiteAssignmentApi::class.java)
                val req = DiscrepancyReportRequest(
                    siteId = siteId,
                    userLatitude = loc?.latitude,
                    userLongitude = loc?.longitude,
                    accuracyMeters = details.accuracyMeters,
                    distanceMeters = details.distanceToCenterMeters,
                    userRemarks = remarks
                )
                android.util.Log.d("EmployeeActivity", "Sending request payload to POST /site-assignments/report-discrepancy: $req")
                val res = api.reportDiscrepancy(req)
                android.util.Log.d("EmployeeActivity", "Discrepancy report HTTP response code=${res.code()}, message=${res.message()}, body=${res.body()}")

                withContext(Dispatchers.Main) {
                    pbGeofenceLoading.visibility = View.GONE
                    if (res.isSuccessful) {
                        Toast.makeText(
                            this@EmployeeActivity,
                            "Report submitted! Admin team notified with your location RCA diagnostics.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@EmployeeActivity,
                            "API Error (${res.code()}): ${res.message()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("EmployeeActivity", "Exception while reporting discrepancy to admin", e)
                withContext(Dispatchers.Main) {
                    pbGeofenceLoading.visibility = View.GONE
                    Toast.makeText(
                        this@EmployeeActivity,
                        "Network error submitting report: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        googleMap?.uiSettings?.isMyLocationButtonEnabled = true
        renderMapGeofence(lastGeofenceDetails)
    }

    override fun onResume() {
        super.onResume()
        mapWarningView.onResume()
        if (!com.yatri.TokenStore.token.isNullOrEmpty()) {
            checkPermissionsAndGeofence()
        }
    }

    override fun onPause() {
        super.onPause()
        mapWarningView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapWarningView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapWarningView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapWarningView.onSaveInstanceState(outState)
    }

    fun checkPermissionsAndGeofence() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            checkGeofence()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun checkGeofence() {
        pbGeofenceLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val details = GeofenceChecker.getGeofenceDetails(this@EmployeeActivity)
            pbGeofenceLoading.visibility = View.GONE
            lastGeofenceDetails = details

            updateDiagnosticText(details)
            renderMapGeofence(details)

            val showWarning = !details.isInside
            showGeofenceWarning(showWarning)

            if (details.isInside) {
                Toast.makeText(this@EmployeeActivity, "Inside site boundary", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@EmployeeActivity, details.statusMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateDiagnosticText(details: GeofenceCheckDetails) {
        val siteName = details.matchedAssignment?.siteName ?: details.assignments.firstOrNull()?.siteName ?: "No site assigned"
        tvGeofenceSiteName.text = "Assigned Site: $siteName"

        if (details.userLocation != null) {
            val lat = String.format("%.5f", details.userLocation.latitude)
            val lng = String.format("%.5f", details.userLocation.longitude)
            val acc = details.accuracyMeters.toInt()
            tvGeofenceUserLocation.text = "Your GPS: Lat $lat, Lng $lng (Accuracy: ±${acc}m)"
        } else {
            tvGeofenceUserLocation.text = "Your GPS: Unavailable"
        }

        val distMeters = details.distanceToCenterMeters
        val distFormatted = if (distMeters >= 1000f) {
            String.format("%.1f km", distMeters / 1000f)
        } else {
            "${distMeters.toInt()} meters"
        }

        if (details.isInside) {
            tvGeofenceDistanceStatus.text = "Status: Inside site boundary ($distFormatted from center)"
            tvGeofenceDistanceStatus.setTextColor(0xFF4CAF50.toInt())
            cardGeofenceRcaReason.visibility = View.GONE
            btnCheckInAnyway.visibility = View.GONE
            tvCheckInAnywayHint.visibility = View.GONE
        } else {
            // Offer the recorded override only when it plausibly is a GPS issue: the worker has an
            // assigned site and a GPS fix, and isn't in a completely different city (wrong-site case).
            val allowOverride = details.assignments.isNotEmpty() &&
                details.userLocation != null &&
                distMeters <= 10000f
            btnCheckInAnyway.visibility = if (allowOverride) View.VISIBLE else View.GONE
            tvCheckInAnywayHint.visibility = if (allowOverride) View.VISIBLE else View.GONE

            tvGeofenceDistanceStatus.text = "Status: Outside site boundary ($distFormatted from site center)"
            tvGeofenceDistanceStatus.setTextColor(0xFFD32F2F.toInt())
            cardGeofenceRcaReason.visibility = View.VISIBLE

            val accMeters = details.accuracyMeters
            if (distMeters > 10000f) {
                tvGeofenceRcaTitle.text = "🚨 Cause: Site Assignment Mismatch"
                tvGeofenceRcaDesc.text = "Your assigned site is '$siteName' ($distFormatted away), but your phone GPS is in a different city. Ask your Manager or Admin to assign your correct local site."
            } else if (accMeters > 80f) {
                tvGeofenceRcaTitle.text = "📡 Cause: Low GPS Signal Accuracy (±${accMeters.toInt()}m)"
                tvGeofenceRcaDesc.text = "Your device satellite signal is weak due to indoor walls or coverage. Turn ON Wi-Fi or step into an open space to boost accuracy."
            } else if (details.assignments.isEmpty()) {
                tvGeofenceRcaTitle.text = "🏢 Cause: No Site Assigned"
                tvGeofenceRcaDesc.text = "No active work site is assigned to your employee ID. Please contact Admin."
            } else {
                tvGeofenceRcaTitle.text = "⚠️ Cause: Outside Geofence Boundary Line"
                tvGeofenceRcaDesc.text = "You are currently ${distMeters.toInt()} meters away from the '$siteName' boundary line. Please step closer to the main entrance/gate."
            }
        }
    }

    private fun renderMapGeofence(details: GeofenceCheckDetails?) {
        val map = googleMap ?: return
        if (details == null) return

        map.clear()

        // 1. Draw Geofence Polygon
        val poly = details.geofencePolygon
        if (poly.size >= 3) {
            val polygonOptions = PolygonOptions()
            val strokeColor = if (details.isInside) 0xFF4CAF50.toInt() else 0xFFD32F2F.toInt()
            val fillColor = if (details.isInside) 0x334CAF50.toInt() else 0x33F44336.toInt()

            polygonOptions.strokeColor(strokeColor)
                .fillColor(fillColor)
                .strokeWidth(6f)

            for (pt in poly) {
                polygonOptions.add(LatLng(pt.lat, pt.lng))
            }
            map.addPolygon(polygonOptions)

            // Add site center marker
            val centerLat = poly.map { it.lat }.average()
            val centerLng = poly.map { it.lng }.average()
            val siteName = details.matchedAssignment?.siteName ?: details.assignments.firstOrNull()?.siteName ?: "Site Geofence"
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(centerLat, centerLng))
                    .title(siteName)
                    .snippet("Geofence Center")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }

        // 2. Draw User Location Marker
        val userLoc = details.userLocation
        if (userLoc != null) {
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(userLoc.latitude, userLoc.longitude))
                    .title("Your Current Location")
                    .snippet("GPS Accuracy: ±${userLoc.accuracy.toInt()}m")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        }

        // 3. Move camera to fit bounds
        fitMapToBounds(details)
    }

    private fun fitMapToBounds(details: GeofenceCheckDetails?) {
        val map = googleMap ?: return
        if (details == null) return

        val builder = LatLngBounds.Builder()
        var hasPoints = false

        val poly = details.geofencePolygon
        for (pt in poly) {
            builder.include(LatLng(pt.lat, pt.lng))
            hasPoints = true
        }

        val userLoc = details.userLocation
        if (userLoc != null) {
            builder.include(LatLng(userLoc.latitude, userLoc.longitude))
            hasPoints = true
        }

        if (hasPoints) {
            try {
                val bounds = builder.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            } catch (e: Exception) {
                if (userLoc != null) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(userLoc.latitude, userLoc.longitude), 15f))
                }
            }
        }
    }

    private fun showGeofenceWarning(show: Boolean) {
        geofenceWarningLayout.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<View>(R.id.container).visibility = if (show) View.GONE else View.VISIBLE
        findViewById<View>(R.id.bottomNav).visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun switch(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }
}
