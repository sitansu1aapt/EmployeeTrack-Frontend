package com.yatri.helpdesk

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yatri.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HelpdeskAdapter(
    private var items: List<HelpdeskRequestItem>,
    private val onItemClick: (HelpdeskRequestItem) -> Unit
) : RecyclerView.Adapter<HelpdeskAdapter.ViewHolder>() {

    fun updateData(newItems: List<HelpdeskRequestItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_helpdesk_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvType: TextView = itemView.findViewById(R.id.tvRequestType)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvRequestStatus)
        private val tvCreatedAt: TextView = itemView.findViewById(R.id.tvRequestCreatedAt)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvRequestDescription)
        private val tvLatestReply: TextView = itemView.findViewById(R.id.tvRequestLatestReply)

        fun bind(item: HelpdeskRequestItem) {
            tvType.text = item.query_type.replace('_', ' ')
            tvStatus.text = item.status
            tvCreatedAt.text = formatTimestamp(item.created_at)
            tvDescription.text = item.description
            if (!item.latest_reply_message.isNullOrBlank()) {
                tvLatestReply.visibility = View.VISIBLE
                tvLatestReply.text = "Latest reply: ${item.latest_reply_message}"
            } else {
                tvLatestReply.visibility = View.GONE
            }
            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val outputFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        fun formatTimestamp(raw: String): String {
            return try {
                val date = inputFormat.parse(raw) ?: return raw
                outputFormat.format(date)
            } catch (_: Exception) { raw }
        }
    }
}
