package com.yatri

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color

data class ReleaseNote(
    val version: String,
    val date: String,
    val notes: List<String>,
    val colorHex: String
)

class ReleaseNotesAdapter(private val notes: List<ReleaseNote>) : 
    RecyclerView.Adapter<ReleaseNotesAdapter.ReleaseNoteViewHolder>() {

    class ReleaseNoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvVersion: TextView = view.findViewById(R.id.tvVersion)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvNotes: TextView = view.findViewById(R.id.tvNotes)
        val viewSideBar: View = view.findViewById(R.id.viewSideBar)
        val cardRoot: androidx.cardview.widget.CardView = view.findViewById(R.id.cardRoot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReleaseNoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_release_note, parent, false)
        return ReleaseNoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReleaseNoteViewHolder, position: Int) {
        val note = notes[position]
        holder.tvVersion.text = note.version
        holder.tvDate.text = note.date
        
        // Format notes with bullets
        val formattedNotes = StringBuilder()
        note.notes.forEach { item ->
            formattedNotes.append("•  $item\n")
        }
        holder.tvNotes.text = formattedNotes.toString().trimEnd()

        // Apply Color
        try {
            val color = Color.parseColor(note.colorHex)
            holder.viewSideBar.setBackgroundColor(color)
            
            // Optional: Subtle tint for background
            // holder.cardRoot.setCardBackgroundColor(Color.parseColor("#F8F9FA")) 
        } catch (e: Exception) {
            // Fallback color
             holder.viewSideBar.setBackgroundColor(Color.BLUE)
        }
    }

    override fun getItemCount() = notes.size
}
