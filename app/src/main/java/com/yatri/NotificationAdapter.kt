package com.yatri

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yatri.notifications.Notification
import com.yatri.notifications.NotificationType
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private var notifications: List<Notification>,
    private val onItemClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.ivNotificationIcon)
        val title: TextView = itemView.findViewById(R.id.tvNotificationTitle)
        val details: TextView = itemView.findViewById(R.id.tvNotificationDetails)
        val time: TextView = itemView.findViewById(R.id.tvNotificationTime)
        val unreadDot: View = itemView.findViewById(R.id.vUnreadDot)
        val container: View = itemView.findViewById(R.id.notificationContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        
        // DATA BINDING
        holder.title.text = notification.title
        holder.details.text = notification.details
        
        // Format time
        val timeFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val date = try {
            java.time.Instant.parse(notification.created_at).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            timeFormat.format(Date.from(java.time.Instant.parse(notification.created_at)))
        } catch (e: Exception) {
            "Invalid date"
        }
        holder.time.text = "Created: $date"
        
        // Set icon and color based on type
        val iconRes = getNotificationIcon(notification.type)
        val colorRes = getNotificationColor(notification.type)
        holder.icon.setImageResource(iconRes)
        holder.icon.setColorFilter(holder.itemView.context.getColor(colorRes))
        
        // Show/hide unread indicator
        holder.unreadDot.visibility = if (notification.is_seen) View.GONE else View.VISIBLE
        
        // Set background for unread notifications
        if (!notification.is_seen) {
            holder.container.setBackgroundColor(holder.itemView.context.getColor(R.color.unread_notification_bg))
        } else {
            holder.container.setBackgroundColor(holder.itemView.context.getColor(android.R.color.transparent))
        }
        
        // Set click listener
        holder.itemView.setOnClickListener {
            onItemClick(notification)
        }
    }

    override fun getItemCount(): Int = notifications.size

    fun updateData(newNotifications: List<Notification>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }

    private fun getNotificationIcon(type: String): Int {
        return when (type) {
            NotificationType.GEOFENCE_ENTRY.value -> R.drawable.ic_enter
            NotificationType.GEOFENCE_EXIT.value -> R.drawable.ic_exit
            NotificationType.EMERGENCY_ALERT.value -> R.drawable.ic_warning
            NotificationType.MESSAGE.value -> R.drawable.ic_message
            NotificationType.TASK_ASSIGNMENT.value -> R.drawable.ic_task
            NotificationType.ATTENDANCE.value -> R.drawable.ic_time
            else -> R.drawable.ic_notification
        }
    }

    private fun getNotificationColor(type: String): Int {
        return when (type) {
            NotificationType.GEOFENCE_ENTRY.value -> R.color.success_green
            NotificationType.GEOFENCE_EXIT.value -> R.color.warning_orange
            NotificationType.EMERGENCY_ALERT.value -> R.color.error_red
            NotificationType.MESSAGE.value -> R.color.blue_500
            NotificationType.TASK_ASSIGNMENT.value -> R.color.purple_500
            NotificationType.ATTENDANCE.value -> R.color.blue_600
            else -> R.color.text_secondary
        }
    }
}