package com.yatri.attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.yatri.R

class AttendanceAdapter(private val items: List<AttendanceItem>) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    class AttendanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: AttendanceItem) {
            val card = itemView as CardView
            val tvDate = card.findViewById<TextView>(R.id.tvDate)
            val tvCheckIn = card.findViewById<TextView>(R.id.tvCheckIn)
            val tvCheckOut = card.findViewById<TextView>(R.id.tvCheckOut)
            val tvShift = card.findViewById<TextView>(R.id.tvShift)
            val tvDuration = card.findViewById<TextView>(R.id.tvDuration)
            val tvStatus = card.findViewById<TextView>(R.id.tvStatus)

            // Format date: "2025-10-27" -> "Mon, 27 Oct"
            val inputDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val outputDateFormat = java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.US)
            val formattedDate = try {
                item.date.let { d ->
                    if (d.isNotEmpty()) outputDateFormat.format(inputDateFormat.parse(d)) else "--"
                }
            } catch (e: Exception) { "--" }
            tvDate.text = formattedDate

            // Format time to match React Native formatTime12h function exactly
            fun formatTime(iso: String?): String {
                if (iso.isNullOrEmpty()) return "--"
                return try {
                    // Parse UTC timestamp properly (like React Native's new Date(utcDateString))
                    val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale("en", "IN"))
                    isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    
                    val date = isoFormat.parse(iso)
                    if (date == null) return "--"
                    
                    // Convert to Asia/Kolkata timezone like React Native
                    val formatter = java.text.SimpleDateFormat("h:mm a", java.util.Locale("en", "IN"))
                    formatter.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                    
                    formatter.format(date)
                } catch (e: Exception) {
                    // Fallback: try parsing without milliseconds
                    try {
                        val fallbackFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale("en", "IN"))
                        fallbackFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        
                        val date = fallbackFormat.parse(iso)
                        if (date == null) return "--"
                        
                        val formatter = java.text.SimpleDateFormat("h:mm a", java.util.Locale("en", "IN"))
                        formatter.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                        
                        formatter.format(date)
                    } catch (e2: Exception) { "--" }
                }
            }

            tvCheckIn.text = "Check In: " + formatTime(item.checkIn)
            tvCheckOut.text = "Check Out: " + formatTime(item.checkOut)
            tvShift.text = "Shift: " + (item.shift ?: "--")
            tvDuration.text = "Duration: " + (item.duration ?: "--")
            tvStatus.text = item.status
        }
    }
}

data class AttendanceItem(
    val date: String,
    val checkIn: String?,
    val checkOut: String?,
    val shift: String?,
    val duration: String?,
    val status: String
)
