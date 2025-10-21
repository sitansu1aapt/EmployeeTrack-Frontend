package com.yatri

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yatri.notifications.Notification
import java.text.SimpleDateFormat
import java.util.*

class NotificationDetailActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_detail)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.notification_details)
        
        val notification = intent.getSerializableExtra("notification") as? Notification
        if (notification != null) {
            displayNotificationDetails(notification)
        } else {
            finish()
        }
    }
    
    private fun displayNotificationDetails(notification: Notification) {
        findViewById<TextView>(R.id.tvTitle).text = notification.title
        findViewById<TextView>(R.id.tvDetails).text = notification.details
        findViewById<TextView>(R.id.tvTimestamp).text = formatTimestamp(notification.created_at)
        findViewById<TextView>(R.id.tvContent).text = getNotificationContent(notification)
        findViewById<TextView>(R.id.tvType).text = notification.type
        findViewById<TextView>(R.id.tvStatus).text = if (notification.is_seen) "Read" else "Unread"
    }
    
    private fun getNotificationContent(notification: Notification): String {
        return when (notification.type) {
            "GEOFENCE_IN" -> getString(
                R.string.geofence_entry_message,
                notification.metadata?.user_name ?: "",
                notification.metadata?.empid ?: "",
                notification.metadata?.site_name ?: ""
            )
            "GEOFENCE_OUT" -> getString(
                R.string.geofence_exit_message,
                notification.metadata?.user_name ?: "",
                notification.metadata?.empid ?: "",
                notification.metadata?.site_name ?: ""
            )
            "ABSENT_NOTIFICATION" -> getString(
                R.string.absent_notification_message,
                notification.metadata?.user_name ?: "",
                notification.metadata?.empid ?: "",
                notification.created_at.substring(0, 10)
            )
            else -> notification.details
        }
    }
    
    private fun formatTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date)
        } catch (e: Exception) {
            timestamp
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

