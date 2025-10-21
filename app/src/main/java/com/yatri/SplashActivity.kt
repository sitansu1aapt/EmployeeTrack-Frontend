package com.yatri

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.yatri.TokenStore
import com.yatri.PrefKeys
import com.yatri.localization.LocalizationManager

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize localization first
        LocalizationManager.initialize(this)
        
        // Load auth token from DataStore if present and set TokenStore.token
        lifecycleScope.launch {
            val tok = applicationContext.dataStore.data.first()[PrefKeys.AUTH_TOKEN]
            if (!tok.isNullOrEmpty()) {
                TokenStore.token = tok
                android.util.Log.d("SplashActivity", "Loaded existing token from storage")
            } else {
                android.util.Log.d("SplashActivity", "No existing token found")
            }
        }

        // Samsung UFC workaround - catch and ignore the ClassNotFoundException
        try {
            setContentView(R.layout.activity_splash)
        } catch (e: Exception) {
            android.util.Log.w("SplashActivity", "Samsung UFC error caught and ignored: ${e.message}")
            // Fallback: try to continue without the layout
            try {
                setContentView(R.layout.activity_splash)
            } catch (e2: Exception) {
                android.util.Log.e("SplashActivity", "Failed to set content view even on retry: ${e2.message}")
                // If all else fails, just finish and start login
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
        }

        ensureNotificationsEnabled()

        // Check for existing token and redirect accordingly
        lifecycleScope.launch {
            val tok = applicationContext.dataStore.data.first()[PrefKeys.AUTH_TOKEN]
            Handler(Looper.getMainLooper()).postDelayed({
                if (!tok.isNullOrEmpty()) {
                    // Token exists, go directly to main app
                    android.util.Log.d("SplashActivity", "Token found, redirecting to EmployeeActivity")
                    startActivity(Intent(this@SplashActivity, EmployeeActivity::class.java))
                } else {
                    // No token, go to login
                    android.util.Log.d("SplashActivity", "No token found, redirecting to LoginActivity")
                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                }
                finish()
            }, 1500)
        }
    }

    private fun ensureNotificationsEnabled() {
        // 1) Runtime permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(perm), 1001)
            }
        }

        // 2) If notifications disabled at app level, open settings page
        val appNm = NotificationManagerCompat.from(this)
        if (!appNm.areNotificationsEnabled()) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            try { startActivity(intent) } catch (_: Exception) {}
            return
        }

        // 3) If channel 'alerts' exists and is blocked, open channel settings (API 26+)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = nm.getNotificationChannel("alerts")
            if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) {
                val ci = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, "alerts")
                try { startActivity(ci) } catch (_: Exception) {}
            }
        }
    }
}


