package com.yatri.messages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yatri.R

class NotificationAdapter(
    private var items: List<NotificationItem>
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<NotificationItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: NotificationItem) {
            val card = itemView as CardView
            val tvTitle = card.findViewById<TextView>(R.id.tvTitle)
            val tvTime = card.findViewById<TextView>(R.id.tvTime)
            val tvDetails = card.findViewById<TextView>(R.id.tvDetails)
            val tvType = card.findViewById<TextView>(R.id.tvType)
            val ivIcon = card.findViewById<ImageView?>(R.id.ivIcon)
            val unreadDot = card.findViewById<View?>(R.id.unreadDot)
            tvTitle.text = item.title
            tvTime.text = item.time
            tvDetails.text = item.details
            tvType.text = when (item.type) {
                "GEOFENCE_IN" -> "Geofence Entry"
                "GEOFENCE_OUT" -> "Geofence Exit"
                "ATTENDANCE" -> "Attendance"
                "EMERGENCY_NOTIFICATION" -> "Emergency Alert"
                else -> item.type
            }
            tvType.setTextColor(
                ContextCompat.getColor(card.context, when (item.type) {
                    "GEOFENCE_IN" -> R.color.success
                    "GEOFENCE_OUT" -> R.color.warning
                    "ATTENDANCE" -> R.color.textLight
                    "EMERGENCY_NOTIFICATION" -> R.color.error
                    else -> R.color.text_secondary
                })
            )
            tvTitle.setTypeface(null, if (!item.isSeen) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            unreadDot?.visibility = if (!item.isSeen) View.VISIBLE else View.GONE
            ivIcon?.setImageResource(when (item.type) {
                "GEOFENCE_IN" -> R.drawable.ic_geofence_in
                "GEOFENCE_OUT" -> R.drawable.ic_geofence_out
                "ATTENDANCE" -> R.drawable.ic_attendance
                "EMERGENCY_NOTIFICATION" -> R.drawable.ic_emergency
                else -> R.drawable.ic_messages
            })
        }
    }
}

data class NotificationItem(
    val notificationId: String,
    val title: String,
    val time: String,
    val details: String,
    val type: String,
    val isSeen: Boolean
)
