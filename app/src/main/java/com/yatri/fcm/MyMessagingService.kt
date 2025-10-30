package com.yatri.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yatri.EmployeeActivity
import com.yatri.sleep.QuestionActivity
import com.yatri.R
import com.yatri.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.create

class MyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        android.util.Log.d("FCM", "onNewToken: $token")
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val api = Network.retrofit.create<UsersApi>()
                api.updateFcmToken(FcmTokenBody(token))
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Route sleep_alert directly, others show generic
        val type = message.data["type"]
        if (type == "sleep_alert") {
            showSleepAlert(message)
        } else {
            showNotification(message)
        }
    }

    private fun showNotification(message: RemoteMessage) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = ensureAlertChannel(manager)

        val intent = Intent(this, EmployeeActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))

        val title = message.notification?.title ?: message.data["title"] ?: "Yatri"
        val body = message.notification?.body ?: message.data["body"] ?: "New message"

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
    }

    private fun showSleepAlert(message: RemoteMessage) {
        android.util.Log.d("FCM", "=== SHOW SLEEP ALERT ===")
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = ensureAlertChannel(manager)

        val intent = Intent(this, QuestionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("session_id", message.data["session_id"])
            putExtra("question_id", message.data["question_id"])
            putExtra("question_text", message.data["question_text"])
            // options could be a JSON string; pass through
            putExtra("options", message.data["options"] ?: "[]")
            putExtra("duration_seconds", message.data["duration_seconds"]?.toIntOrNull() ?: 30)
        }
        android.util.Log.d("FCM", "Created intent with ${intent.extras?.size()} extras")

        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(this, 101, intent, piFlags)
        android.util.Log.d("FCM", "PendingIntent created with flags: $piFlags")

        // Try to bring UI up immediately if app is foreground
        try { startActivity(intent) } catch (_: Exception) {}

        // Try to get custom sleep alert sound
        val soundUri = try {
            val resourceId = resources.getIdentifier("sleep_alert", "raw", packageName)
            if (resourceId != 0) {
                Uri.parse("android.resource://$packageName/$resourceId")
            } else {
                android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            }
        } catch (e: Exception) {
            android.util.Log.w("FCM", "Failed to load custom sleep alert sound: ${e.message}")
            android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
        }

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🚨 SLEEP ALERT 🚨")
            .setContentText(message.data["question_text"] ?: "IMMEDIATE RESPONSE REQUIRED")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000))
            .setLights(Color.RED, 500, 500)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setTimeoutAfter(30000) // 30 seconds timeout
            .build()

        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
    }
}

// local copies to avoid import cycles
data class FcmTokenBody(val fcm_token: String)
interface UsersApi { @retrofit2.http.PUT("users/me/fcm-token") suspend fun updateFcmToken(@retrofit2.http.Body body: FcmTokenBody) }



private fun Context.ensureAlertChannel(nm: NotificationManager): String {
    // Bump channel id to force devices to pick up custom sound and importance changes
    val channelId = "alerts_v5"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Clean up older channels
        try { nm.deleteNotificationChannel("alerts") } catch (_: Exception) {}
        try { nm.deleteNotificationChannel("alerts_v2") } catch (_: Exception) {}
        try { nm.deleteNotificationChannel("alerts_v3") } catch (_: Exception) {}
        try { nm.deleteNotificationChannel("alerts_v4") } catch (_: Exception) {}
        
        val existing = nm.getNotificationChannel(channelId)
        if (existing == null) {
            // Try to use custom sleep alert sound, fallback to default alarm
            val soundUri = try {
                // Check if sleep_alert file exists
                val resourceId = resources.getIdentifier("sleep_alert", "raw", packageName)
                if (resourceId != 0) {
                    Uri.parse("android.resource://$packageName/$resourceId")
                } else {
                    android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                }
            } catch (e: Exception) {
                android.util.Log.w("FCM", "Failed to load custom sleep alert sound, using default: ${e.message}")
                android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            }
            
            val ch = NotificationChannel(channelId, "Critical Sleep Alerts", NotificationManager.IMPORTANCE_MAX).apply {
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000)
                description = "Critical sleep alerts with sound"
                
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()
                
                setSound(soundUri, attrs)
                setBypassDnd(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
            android.util.Log.d("FCM", "Created notification channel: $channelId with sound: $soundUri")
        } else {
            android.util.Log.d("FCM", "Notification channel already exists: $channelId")
        }
    }
    return channelId
}

