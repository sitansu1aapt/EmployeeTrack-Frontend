package com.yatri

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.google.android.material.bottomnavigation.BottomNavigationView

class EmployeeActivity : AppCompatActivity() {
    
    private lateinit var geofenceWarningLayout: android.view.View
    private lateinit var btnRetry: android.widget.Button

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
        btnRetry.setOnClickListener {
            checkPermissionsAndGeofence()
        }

        // Ensure token is loaded from DataStore before any API calls
        lifecycleScope.launch {
            val tok = applicationContext.dataStore.data.first()[com.yatri.PrefKeys.AUTH_TOKEN]
            if (!tok.isNullOrEmpty()) {
                com.yatri.TokenStore.token = tok
                android.util.Log.d("EmployeeActivity", "Token loaded in EmployeeActivity: $tok")
                // Check geofence after token is loaded
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

    override fun onResume() {
        super.onResume()
        // Improve UX: check on resume too, but maybe with less aggression if already checked?
        // User requested: "once app opens". Doing it on Resume is safer.
        if (!com.yatri.TokenStore.token.isNullOrEmpty()) {
            checkPermissionsAndGeofence()
        }
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
        lifecycleScope.launch {
            val result = com.yatri.checkin.GeofenceChecker.checkUserInGeofence(this@EmployeeActivity)
            if (result.isSuccess) {
                val inGeofence = result.getOrDefault(false)
                showGeofenceWarning(!inGeofence)
                if (inGeofence) {
                    android.widget.Toast.makeText(this@EmployeeActivity, "You are inside the site boundary", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                     android.widget.Toast.makeText(this@EmployeeActivity, "You are outside the site boundary", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                // If error (e.g. network), what to do? 
                // Currently showing warning if we can't verify.
                android.util.Log.e("EmployeeActivity", "Geofence check failed", result.exceptionOrNull())
                showGeofenceWarning(true)
                android.widget.Toast.makeText(this@EmployeeActivity, "Failed to verify geofence location", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showGeofenceWarning(show: Boolean) {
        geofenceWarningLayout.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<android.view.View>(R.id.container).visibility = if (show) android.view.View.GONE else android.view.View.VISIBLE
        findViewById<android.view.View>(R.id.bottomNav).visibility = if (show) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun switch(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }
}


