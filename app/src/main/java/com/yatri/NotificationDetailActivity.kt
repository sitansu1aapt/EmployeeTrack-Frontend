package com.yatri

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yatri.notifications.Notification
import java.text.SimpleDateFormat
import java.util.*

class NotificationDetailActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_detail)
        
        val notification = intent.getSerializableExtra("notification") as? Notification
        if (notification != null) {
            displayNotificationDetails(notification)
        } else {
            finish()
        }
    }
    
    private fun displayNotificationDetails(notification: Notification) {
        findViewById<TextView>(R.id.tvDetailTitle).text = notification.title
        findViewById<TextView>(R.id.tvDetailContent).text = notification.details
        
        // Format and display time
        val timeFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val date = try {
            java.time.Instant.parse(notification.created_at).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            timeFormat.format(Date.from(java.time.Instant.parse(notification.created_at)))
        } catch (e: Exception) {
            "Invalid date"
        }
        findViewById<TextView>(R.id.tvDetailTime).text = "Created: $date"
        
        // Set notification type
        findViewById<TextView>(R.id.tvDetailType).text = "Type: ${notification.type}"
        
        // Set status
        val status = if (notification.is_seen) "Read" else "Unread"
        findViewById<TextView>(R.id.tvDetailStatus).text = "Status: $status"
        
        // Display metadata if available
        val metadataText = findViewById<TextView>(R.id.tvDetailMetadata)
        if (notification.metadata != null) {
            val metadata = notification.metadata
            val metadataString = buildString {
                metadata.empid?.let { append("Employee ID: $it\n") }
                metadata.site_name?.let { append("Site: $it\n") }
                metadata.user_name?.let { append("User: $it\n") }
                metadata.timestamp?.let { append("Timestamp: $it\n") }
            }
            if (metadataString.isNotEmpty()) {
                metadataText.text = "Additional Info:\n$metadataString"
                metadataText.visibility = android.view.View.VISIBLE
            } else {
                metadataText.visibility = android.view.View.GONE
            }
        } else {
            metadataText.visibility = android.view.View.GONE
        }
    }
}