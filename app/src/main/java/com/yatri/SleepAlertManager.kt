package com.yatri

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object SleepAlertManager {
    private const val TAG = "SleepAlertManager"
    private const val CHANNEL_ID = "sleep_alerts"
    private const val NOTIFICATION_ID = 1001
    
    private var snoozeHandler: Handler? = null
    private var snoozeRunnable: Runnable? = null
    
    fun initialize(context: Context) {
        createNotificationChannel(context)
        Log.d(TAG, "SleepAlertManager initialized")
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Get custom sound URI
            val soundUri = getCustomSoundUri(context)
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical sleep alerts that wake up the device"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                setSound(soundUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build())
                setBypassDnd(true) // Bypass Do Not Disturb
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created with sound: $soundUri")
        }
    }
    
    private fun getCustomSoundUri(context: Context): Uri {
        return try {
            val resourceId = context.resources.getIdentifier("sleep_alert", "raw", context.packageName)
            if (resourceId != 0) {
                Uri.parse("android.resource://${context.packageName}/$resourceId")
            } else {
                android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get custom sound, using default alarm", e)
            android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
        }
    }
    
    fun showSleepAlert(context: Context, message: String, alertType: String = "SLEEP_ALERT") {
        Log.d(TAG, "Showing sleep alert: $message")
        
        try {
            // Create full-screen intent
            val fullScreenIntent = SleepAlertActivity.createIntent(context, message, alertType)
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Create notification with full-screen intent
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("🚨 SLEEP ALERT 🚨")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true) // This is the key!
                .setAutoCancel(false)
                .setOngoing(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
                .setLights(Color.RED, 500, 500)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setTimeoutAfter(0) // No timeout
                .build()
            
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, notification)
            
            // Also directly start the activity as a backup
            context.startActivity(fullScreenIntent)
            
            Log.d(TAG, "Sleep alert notification sent with full-screen intent")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show sleep alert", e)
            
            // Fallback: directly start the activity
            try {
                val fallbackIntent = SleepAlertActivity.createIntent(context, message, alertType)
                context.startActivity(fallbackIntent)
                Log.d(TAG, "Fallback: Started sleep alert activity directly")
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback also failed", e2)
            }
        }
    }
    
    fun dismissAlert(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(NOTIFICATION_ID)
            
            // Cancel any pending snooze
            cancelSnoozeAlert()
            
            Log.d(TAG, "Sleep alert dismissed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss alert", e)
        }
    }
    
    fun scheduleSnoozeAlert(context: Context, delayMs: Long) {
        try {
            cancelSnoozeAlert() // Cancel any existing snooze
            
            snoozeHandler = Handler(Looper.getMainLooper())
            snoozeRunnable = Runnable {
                showSleepAlert(context, "🚨 SNOOZE ALERT 🚨\nTime to wake up!", "SNOOZE_ALERT")
            }
            
            snoozeHandler?.postDelayed(snoozeRunnable!!, delayMs)
            
            Log.d(TAG, "Snooze alert scheduled for ${delayMs / 1000} seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule snooze alert", e)
        }
    }
    
    private fun cancelSnoozeAlert() {
        try {
            snoozeRunnable?.let { runnable ->
                snoozeHandler?.removeCallbacks(runnable)
            }
            snoozeHandler = null
            snoozeRunnable = null
            Log.d(TAG, "Snooze alert cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel snooze alert", e)
        }
    }
    
    // Test function for manual testing
    fun testSleepAlert(context: Context) {
        showSleepAlert(
            context,
            "🚨 TEST SLEEP ALERT 🚨\nThis is a test of the sleep alert system.\nTime: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}",
            "TEST_ALERT"
        )
    }
}