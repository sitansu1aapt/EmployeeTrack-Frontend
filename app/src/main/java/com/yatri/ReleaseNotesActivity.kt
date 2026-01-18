package com.yatri

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ReleaseNotesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_release_notes)

        val btnBack = findViewById<View>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val rvReleaseNotes = findViewById<RecyclerView>(R.id.rvReleaseNotes)
        rvReleaseNotes.layoutManager = LinearLayoutManager(this)

        val notes = getDummyReleaseNotes()
        rvReleaseNotes.adapter = ReleaseNotesAdapter(notes)
    }

    private fun getDummyReleaseNotes(): List<ReleaseNote> {
        return listOf(
            ReleaseNote(
                "v1.2.11",
                "Jan 09, 2026",
                listOf(
                    "Location mandatory fix: User Can not use app until location is on"
                ),
                "#E91E63" // Pink
            )
        )
    }
}
