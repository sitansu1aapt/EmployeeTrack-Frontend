package com.yatri.attendance

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.yatri.R

class AttendanceHistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_history)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val startDateEdit = findViewById<TextInputEditText>(R.id.startDateEdit)
        val endDateEdit = findViewById<TextInputEditText>(R.id.endDateEdit)
        val btnRefresh = findViewById<MaterialButton>(R.id.btnRefresh)
        val tvTotalDays = findViewById<TextView>(R.id.tvTotalDays)
        val tvPresent = findViewById<TextView>(R.id.tvPresent)
        val tvAbsent = findViewById<TextView>(R.id.tvAbsent)
        val tvOffDay = findViewById<TextView>(R.id.tvOffDay)
        val tvLeave = findViewById<TextView>(R.id.tvLeave)
        val rvAttendance = findViewById<RecyclerView>(R.id.rvAttendance)

        // TODO: Implement logic for date pickers, fetching data, and populating summary and RecyclerView
    }
}
