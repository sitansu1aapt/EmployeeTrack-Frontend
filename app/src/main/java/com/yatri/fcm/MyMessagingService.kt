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
import android.media.RingtoneManager
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

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val notificationType = remoteMessage.data["type"]
        android.util.Log.d("FCM", "onMessageReceived: type=$notificationType data=${remoteMessage.data}")

        when (notificationType) {
            "TASK_ASSIGNED" -> {
                val taskTitle = remoteMessage.data["task_title"]
                val taskDescription = remoteMessage.data["task_description"]
                showTaskNotification(taskTitle, taskDescription)
            }

            "EMERGENCY_ALERT" -> {
                showEmergencyNotification(
                    remoteMessage.notification?.title ?: "Emergency Alert",
                    remoteMessage.notification?.body ?: "Emergency assistance required"
                )
            }

            "PATROL_ASSIGNED" -> {
                val routeName = remoteMessage.data["route_name"]
                val checkpointName = remoteMessage.data["checkpoint_name"]
                showPatrolNotification(routeName, checkpointName)
            }

            "sleep_alert" -> {
                showSleepAlertNotification(remoteMessage)
            }

            else -> {
                // Handle generic notification
                showDefaultNotification(remoteMessage)
            }
        }
    }

    // Different notification channels for different types
    private fun showTaskNotification(title: String?, description: String?) {
        val channelId = "task_channel"
        val soundUri = Uri.parse("android.resource://$packageName/${R.raw.task_notification}")
        createNotification(
            channelId,
            title ?: "New Task",
            description ?: "You have a new task assignment",
            R.drawable.ic_task,
            soundUri = soundUri,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    private fun showEmergencyNotification(title: String?, body: String?) {
        val channelId = "emergency_channel"
        // Use alarm sound for emergency
        val soundUri = Uri.parse("android.resource://$packageName/${R.raw.emergency_alert}")
        createNotification(
            channelId,
            title,
            body,
            R.drawable.ic_emergency,
            soundUri = soundUri,
            priority = NotificationCompat.PRIORITY_HIGH,
            vibrationPattern = longArrayOf(0, 1000, 500, 1000) // Strong vibration
        )
    }

    private fun showPatrolNotification(routeName: String?, checkpointName: String?) {
        val channelId = "patrol_channel"
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        createNotification(
            channelId,
            "Patrol Assignment",
            "Route: $routeName, Checkpoint: $checkpointName",
            R.drawable.ic_patrols,
            soundUri = soundUri,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    private fun showSleepAlertNotification(message: RemoteMessage) {
        val channelId = "sleep_alert_channel"
        
        val intent = Intent(this, QuestionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("session_id", message.data["session_id"])
            putExtra("question_id", message.data["question_id"])
            putExtra("question_text", message.data["question_text"])
            // options could be a JSON string; pass through
            putExtra("options", message.data["options"] ?: "[]")
            putExtra("duration_seconds", message.data["duration_seconds"]?.toIntOrNull() ?: 30)
        }

        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(this, 101, intent, piFlags)

        // Try to bring UI up immediately if app is foreground
        try { startActivity(intent) } catch (_: Exception) {}

        // Use ringtone for sleep alerts (louder than notification)
        val soundUri = Uri.parse("android.resource://$packageName/${R.raw.sleep_alert}")
        createNotification(
            channelId,
            message.notification?.title ?: "Sleep Tracking Alert",
            message.data["question_text"] ?: "Are you awake? Please respond.",
            R.drawable.ic_emergency_alert, // Using emergency alert icon as fallback for sleep alert
            soundUri = soundUri,
            priority = NotificationCompat.PRIORITY_HIGH,
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500), // Repeated vibration
            fullScreenIntent = pi
        )
    }

    private fun showDefaultNotification(message: RemoteMessage) {
        val channelId = "default_channel"
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        createNotification(
            channelId,
            message.notification?.title ?: message.data["title"] ?: "Notification",
            message.notification?.body ?: message.data["body"] ?: "New Message",
            R.mipmap.ic_launcher,
            soundUri = soundUri,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    // Generic notification creation method
    private fun createNotification(
        channelId: String,
        title: String?,
        body: String?,
        icon: Int,
        soundUri: Uri? = null,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        vibrationPattern: LongArray? = null,
        fullScreenIntent: PendingIntent? = null
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(channelId, soundUri, priority, vibrationPattern)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(icon)
            .setPriority(priority)
            .setAutoCancel(true)

        // Set sound
        soundUri?.let {
            notificationBuilder.setSound(it)
        }

        // Set vibration
        vibrationPattern?.let {
            notificationBuilder.setVibrate(it)
        }

        // Set full screen intent
        fullScreenIntent?.let {
            notificationBuilder.setFullScreenIntent(it, true)
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_ALARM)
        }

        // Add notification LED for emergency and sleep alerts
        if (channelId == "emergency_channel") {
            notificationBuilder.setLights(Color.RED, 1000, 500)
        } else if (channelId == "sleep_alert_channel") {
            notificationBuilder.setLights(Color.YELLOW, 1000, 500)
        }

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    // Create notification channel for Android O+
    private fun createNotificationChannel(
        channelId: String,
        soundUri: Uri?,
        priority: Int,
        vibrationPattern: LongArray?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val importance = when (priority) {
                NotificationCompat.PRIORITY_HIGH -> NotificationManager.IMPORTANCE_HIGH
                NotificationCompat.PRIORITY_DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
                else -> NotificationManager.IMPORTANCE_LOW
            }

            val channelName = when (channelId) {
                "task_channel" -> "Task Notifications"
                "emergency_channel" -> "Emergency Alerts"
                "patrol_channel" -> "Patrol Assignments"
                "sleep_alert_channel" -> "Sleep Tracking Alerts"
                else -> "General Notifications"
            }

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for $channelName"

                // Set sound
                soundUri?.let {
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                    setSound(it, audioAttributes)
                }

                // Set vibration
                vibrationPattern?.let {
                    enableVibration(true)
                    this.vibrationPattern = it
                }

                // Enable LED
                enableLights(true)
                when (channelId) {
                    "emergency_channel" -> lightColor = Color.RED
                    "sleep_alert_channel" -> lightColor = Color.YELLOW
                    else -> lightColor = Color.BLUE
                }
            }

            notificationManager.createNotificationChannel(channel)
        }
    }
}

// local copies to avoid import cycles
data class FcmTokenBody(val fcm_token: String)
interface UsersApi { @retrofit2.http.PUT("users/me/fcm-token") suspend fun updateFcmToken(@retrofit2.http.Body body: FcmTokenBody) }

