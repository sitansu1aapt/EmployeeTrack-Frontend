package com.yatri.attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.yatri.R

class AttendanceAdapter(private val items: List<AttendanceItem>) :
        RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_attendance, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        val item = items[position]

        // DEBUG: Log the raw data for this specific item
        android.util.Log.d("AttendanceAdapter", "=== BINDING ITEM $position ===")
        android.util.Log.d("AttendanceAdapter", "Date: ${item.date}")
        android.util.Log.d("AttendanceAdapter", "Check-in raw: '${item.checkIn}'")
        android.util.Log.d("AttendanceAdapter", "Check-out raw: '${item.checkOut}'")
        android.util.Log.d("AttendanceAdapter", "Status: ${item.status}")
        android.util.Log.d("AttendanceAdapter", "Current time: ${java.util.Date()}")
        android.util.Log.d(
                "AttendanceAdapter",
                "Device timezone: ${java.util.TimeZone.getDefault().id}"
        )

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
            val formattedDate =
                    try {
                        item.date.let { d ->
                            if (d.isNotEmpty()) outputDateFormat.format(inputDateFormat.parse(d))
                            else "--"
                        }
                    } catch (e: Exception) {
                        "--"
                    }
            tvDate.text = formattedDate

            // Format time functions to match React Native
            fun formatTimeWithDate(iso: String?): String {
                if (iso.isNullOrEmpty()) return "--"

                // Debug logging to see what timestamp format we're getting
                android.util.Log.d("AttendanceAdapter", "=== TIME DEBUG ===")
                android.util.Log.d("AttendanceAdapter", "Input timestamp: '$iso'")
                android.util.Log.d("AttendanceAdapter", "Current system time: ${java.util.Date()}")
                android.util.Log.d(
                        "AttendanceAdapter",
                        "System timezone: ${java.util.TimeZone.getDefault().id}"
                )
                android.util.Log.d("AttendanceAdapter", "Expected format: 2025-10-30T11:58:41.652Z")

                return try {
                    // Parse ISO timestamp - handle multiple formats that might come from server
                    val date =
                            when {
                                iso.contains("T") && iso.endsWith("Z") -> {
                                    // Full ISO format: "2025-10-27T14:30:00.000Z"
                                    val isoFormat =
                                            java.text.SimpleDateFormat(
                                                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                                    java.util.Locale.US
                                            )
                                    isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    isoFormat.parse(iso)
                                }
                                iso.contains("T") -> {
                                    // ISO without Z: "2025-10-27T14:30:00"
                                    val isoFormat =
                                            java.text.SimpleDateFormat(
                                                    "yyyy-MM-dd'T'HH:mm:ss",
                                                    java.util.Locale.US
                                            )
                                    isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    isoFormat.parse(iso)
                                }
                                else -> {
                                    // Simple date: "2025-10-27 14:30:00"
                                    val simpleFormat =
                                            java.text.SimpleDateFormat(
                                                    "yyyy-MM-dd HH:mm:ss",
                                                    java.util.Locale.US
                                            )
                                    simpleFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    simpleFormat.parse(iso)
                                }
                            }

                    if (date == null) return "--"

                    // Format with Asia/Kolkata timezone to match React Native exactly
                    // React Native formatTime12h outputs: "Oct 27, 2:30 PM"
                    val formatter =
                            java.text.SimpleDateFormat(
                                    "MMM d, h:mm a",
                                    java.util.Locale("en", "IN")
                            )
                    formatter.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")

                    val result = formatter.format(date)

                    // Additional debugging for timezone conversion
                    val utcFormatter =
                            java.text.SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm:ss 'UTC'",
                                    java.util.Locale.US
                            )
                    utcFormatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val utcTimeStr = utcFormatter.format(date)

                    val istFormatter =
                            java.text.SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm:ss 'IST'",
                                    java.util.Locale.US
                            )
                    istFormatter.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                    val istTimeStr = istFormatter.format(date)

                    android.util.Log.d("AttendanceAdapter", "Parsed UTC date object: $date")
                    android.util.Log.d("AttendanceAdapter", "UTC time: $utcTimeStr")
                    android.util.Log.d("AttendanceAdapter", "IST time: $istTimeStr")
                    android.util.Log.d("AttendanceAdapter", "Final formatted result: '$result'")
                    result
                } catch (e: Exception) {
                    android.util.Log.e(
                            "AttendanceAdapter",
                            "Failed to parse timestamp '$iso': ${e.message}"
                    )
                    // Fallback: try parsing without milliseconds
                    try {
                        val fallbackFormat =
                                java.text.SimpleDateFormat(
                                        "yyyy-MM-dd'T'HH:mm:ss'Z'",
                                        java.util.Locale("en", "IN")
                                )
                        fallbackFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")

                        val date = fallbackFormat.parse(iso)
                        if (date == null) return "--"

                        val formatter =
                                java.text.SimpleDateFormat(
                                        "MMM d, h:mm a",
                                        java.util.Locale("en", "IN")
                                )
                        formatter.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")

                        val result = formatter.format(date)
                        android.util.Log.d(
                                "AttendanceAdapter",
                                "Fallback worked - result: '$result'"
                        )
                        result
                    } catch (e2: Exception) {
                        android.util.Log.e(
                                "AttendanceAdapter",
                                "Fallback also failed: ${e2.message}"
                        )
                        "--"
                    }
                }
            }
            fun formatRelativeTime(iso: String?): String {
                if (iso.isNullOrEmpty()) return "--"
                return try {
                    val isoFormat =
                            java.text.SimpleDateFormat(
                                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                    java.util.Locale.US
                            )
                    isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val date = isoFormat.parse(iso) ?: return "--"
                    val now = System.currentTimeMillis()
                    val diffMillis = now - date.time
                    val diffMinutes = diffMillis / (60 * 1000)
                    return if (diffMinutes < 60) {
                        "$diffMinutes min ago"
                    } else {
                        val diffHours = diffMinutes / 60
                        "$diffHours h ago"
                    }
                } catch (e: Exception) {
                    "--"
                }
            }
fun formatUtcTime(iso: String?): String {
    if (iso.isNullOrEmpty()) return "--"

    return try {
        val utcFormat = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            java.util.Locale.US
        )
        utcFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = utcFormat.parse(iso)

        val displayFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale("en", "IN"))
        displayFormat.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")

        date?.let { displayFormat.format(it) } ?: "--"
    } catch (e: Exception) {
        android.util.Log.e("TimeFormat", "Error parsing UTC time: ${e.message}")
        "--"
    }
}
            fun formatDateToKolkata(iso: String?): String {
                if (iso.isNullOrEmpty()) return "--"

                return try {
                    // Parse ISO timestamp
                    val date =
                            when {
                                iso.contains("T") && iso.endsWith("Z") -> {
                                    val isoFormat =
                                            java.text.SimpleDateFormat(
                                                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                                    java.util.Locale.US
                                            )
                                    isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    isoFormat.parse(iso)
                                }
                                iso.contains("T") -> {
                                    val isoFormat =
                                            java.text.SimpleDateFormat(
                                                    "yyyy-MM-dd'T'HH:mm:ss",
                                                    java.util.Locale.US
                                            )
                                    isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    isoFormat.parse(iso)
                                }
                                else -> {
                                    val simpleFormat =
                                            java.text.SimpleDateFormat(
                                                    "yyyy-MM-dd HH:mm:ss",
                                                    java.util.Locale.US
                                            )
                                    simpleFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    simpleFormat.parse(iso)
                                }
                            }

                    if (date == null) return "--"

                    // Format date in Asia/Kolkata timezone
                    val formatter =
                            java.text.SimpleDateFormat(
                                    "dd MMM yyyy, h:mm a",
                                    java.util.Locale("en", "IN")
                            )
                    formatter.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                    formatter.format(date)
                } catch (e: Exception) {
                    android.util.Log.e("DateFormat", "Failed to parse date: ${e.message}")
                    "--"
                }
            }
            // Simple time-only format for cleaner display
            fun formatTimeOnly(iso: String?): String {
                if (iso.isNullOrEmpty()) return "--"

                return try {
                    // Parse ISO timestamp
                    val date =
                            when {
                                iso.contains("T") && iso.endsWith("Z") -> {
                                    val isoFormat =
                                            java.text.SimpleDateFormat(
                                                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                                    java.util.Locale.US
                                            )
                                    isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    isoFormat.parse(iso)
                                }
                                iso.contains("T") -> {
                                    val isoFormat =
                                            java.text.SimpleDateFormat(
                                                    "yyyy-MM-dd'T'HH:mm:ss",
                                                    java.util.Locale.US
                                            )
                                    isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    isoFormat.parse(iso)
                                }
                                else -> {
                                    val simpleFormat =
                                            java.text.SimpleDateFormat(
                                                    "yyyy-MM-dd HH:mm:ss",
                                                    java.util.Locale.US
                                            )
                                    simpleFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    simpleFormat.parse(iso)
                                }
                            }

                    if (date == null) return "--"

                    // Format with Asia/Kolkata timezone, time only
                    val formatter =
                            java.text.SimpleDateFormat("h:mm a", java.util.Locale("en", "IN"))
                    formatter.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                    formatter.format(date)
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceAdapter", "Failed to parse time: ${e.message}")
                    "--"
                }
            }

            // Format duration to match React Native formatDuration function
            fun formatDuration(minutes: Int?): String {
                if (minutes == null) return "--"
                val hours = minutes / 60
                val mins = minutes % 60
                return "${hours}h ${mins}m"
            }

            // Debug the time formatting issue
            android.util.Log.d(
                    "AttendanceAdapter",
                    "=== FORMATTING RECORD ${bindingAdapterPosition} ==="
            )
            android.util.Log.d("AttendanceAdapter", "Raw checkIn: '${item.checkIn}'")
            android.util.Log.d("AttendanceAdapter", "Raw checkOut: '${item.checkOut}'")

            val formattedCheckIn = formatUtcTime(item.checkIn)
            val formattedCheckOut = formatUtcTime(item.checkOut)

            android.util.Log.d("AttendanceAdapter", "Formatted checkIn: '$formattedCheckIn'")
            android.util.Log.d("AttendanceAdapter", "Formatted checkOut: '$formattedCheckOut'")

            tvCheckIn.text = "Check In: $formattedCheckIn"
            tvCheckOut.text = "Check Out: $formattedCheckOut"
            tvShift.text = "Shift: " + (item.shift ?: "Not Assigned")
            tvDuration.text = "Duration: " + formatDuration(item.durationMinutes)
            tvStatus.text = item.status

            android.util.Log.d(
                    "AttendanceAdapter",
                    "Final UI - CheckIn: '${tvCheckIn.text}', CheckOut: '${tvCheckOut.text}'"
            )
        }
    }
}

data class AttendanceItem(
        val date: String,
        val checkIn: String?,
        val checkOut: String?,
        val shift: String?,
        val durationMinutes: Int?,
        val status: String
)
