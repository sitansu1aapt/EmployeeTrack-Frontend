package com.yatri

import android.app.Application
import com.yatri.analytics.Analytics
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Color

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.context = applicationContext
        
        // Initialize Sleep Alert Manager
        SleepAlertManager.initialize(this)
        // Initialize Analytics
        Analytics.init(this)
        // Set global user properties
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) { "unknown" }
        Analytics.setUserProperty("app_version", versionName)
        Analytics.setUserProperty("device_model", Build.MODEL ?: "Android")
        Analytics.setUserProperty("os_version", Build.VERSION.RELEASE ?: "")
        // Ensure default notification channel exists to avoid FCM warnings
        ensureDefaultNotificationChannel()
    }

    private fun ensureDefaultNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channelId = getString(R.string.default_notification_channel_id)
        val nm = getSystemService(NotificationManager::class.java)
        // Always recreate with our desired sound so devices pick up changes
        try { nm.deleteNotificationChannel(channelId) } catch (_: Exception) {}
        val soundUri = try {
            val resId = resources.getIdentifier("task_notification", "raw", packageName)
            if (resId != 0) android.net.Uri.parse("android.resource://$packageName/$resId")
            else android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
        } catch (_: Exception) {
            android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
        }
        val ch = NotificationChannel(
                channelId,
                "General Notifications",
                NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableLights(true)
            lightColor = Color.BLUE
            enableVibration(true)
            description = "Default notifications"
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(soundUri, attrs)
        }
        nm.createNotificationChannel(ch)
        android.util.Log.d("FCM", "Default channel ensured: $channelId sound=$soundUri")
    }
}



