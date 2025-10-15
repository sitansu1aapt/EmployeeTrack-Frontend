package com.yatri.checkin

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.yatri.R
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

class CheckInLocationActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var map: GoogleMap
    private lateinit var tvStatus: TextView
    private lateinit var btnContinue: Button
    private var siteAssignment: SiteAssignment? = null
    private var userLatLng: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_in_location)
        tvStatus = findViewById(R.id.tvGeofenceStatus)
        btnContinue = findViewById(R.id.btnContinue)
        btnContinue.isEnabled = false
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fetchSiteAssignmentAndLocation()
    }

    private fun fetchSiteAssignmentAndLocation() {
        // Fetch site assignment (mock or real API call)
        // TODO: Replace mock with real API
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(com.yatri.AppConfig.API_BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val api = retrofit.create(SiteAssignmentApi::class.java)
        lifecycleScope.launch {
            try {
                val assignments = api.getSiteAssignmentsByUserId()
                if (assignments.isNotEmpty()) {
                    siteAssignment = assignments[0]
                } else {
                    tvStatus.text = "No site assignment found"
                    tvStatus.setTextColor(getColor(R.color.error))
                }
            } catch (e: Exception) {
                tvStatus.text = "Failed to fetch assignment"
                tvStatus.setTextColor(getColor(R.color.error))
            }
            // Fetch user location
            getUserLocation()
        }
    }

    private fun getUserLocation() {
        // TODO: Use FusedLocationProviderClient for real location
        // For demo, use a fixed location
        userLatLng = LatLng(12.9352, 77.6903)
        if (::map.isInitialized) {
            updateMapAndGeofence()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        updateMapAndGeofence()
    }

    private fun updateMapAndGeofence() {
        val assignment = siteAssignment ?: return
        val userLoc = userLatLng ?: return
        map.clear()
        map.addMarker(MarkerOptions().position(userLoc).title("Your Location"))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(userLoc, 16f))
        // Draw geofence polygon
        val polygon = assignment.geofenceShapeData.coordinates.map { LatLng(it.lat, it.lng) }
        map.addPolygon(com.google.android.gms.maps.model.PolygonOptions()
            .addAll(polygon)
            .strokeColor(getColor(R.color.blue_500))
            .fillColor(getColor(R.color.blue_500) and 0x55FFFFFF)
        )
        // Check if user is inside geofence (simple bounding box for now)
        val inside = isPointInPolygon(userLoc, polygon)
        btnContinue.isEnabled = inside
        tvStatus.text = if (inside) "You are within the site boundary" else "You must be within the site boundary to check in"
        tvStatus.setTextColor(getColor(if (inside) R.color.success else R.color.error))
    }

    private fun isPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
        // Ray-casting algorithm
        var intersectCount = 0
        for (j in polygon.indices) {
            val k = (j + 1) % polygon.size
            val a = polygon[j]
            val b = polygon[k]
            if (((a.latitude > point.latitude) != (b.latitude > point.latitude)) &&
                (point.longitude < (b.longitude - a.longitude) * (point.latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude)
            ) {
                intersectCount++
            }
        }
        return (intersectCount % 2 == 1)
    }
}