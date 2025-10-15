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
            // TODO: Set values and style badges/colors as in React Native
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
