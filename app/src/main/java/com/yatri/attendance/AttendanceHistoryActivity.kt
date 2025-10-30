package com.yatri.attendance

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import android.widget.ImageView
import com.yatri.R

class AttendanceHistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_history)
        // Ensure toolbar is visible and set as ActionBar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "My Attendance"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val startDateEdit = findViewById<TextInputEditText>(R.id.startDateEdit)
        val endDateEdit = findViewById<TextInputEditText>(R.id.endDateEdit)
        val btnRefresh = findViewById<ImageView>(R.id.btnRefresh)
        val tvTotalDays = findViewById<TextView>(R.id.tvTotalDays)
        val tvPresent = findViewById<TextView>(R.id.tvPresent)
        val tvAbsent = findViewById<TextView>(R.id.tvAbsent)
        val tvOffDay = findViewById<TextView>(R.id.tvOffDay)
        val tvLeave = findViewById<TextView>(R.id.tvLeave)
        val rvAttendance = findViewById<RecyclerView>(R.id.rvAttendance)
        rvAttendance.visibility = android.view.View.VISIBLE
        rvAttendance.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        // Date logic
        val calendar = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        var startDate = dateFormat.format(calendar.time)
        var endDate = dateFormat.format(calendar.time)
        startDateEdit.setText(startDate)
        endDateEdit.setText(endDate)

        // Date pickers
        startDateEdit.setOnClickListener {
            val c = java.util.Calendar.getInstance()
            val dpd = android.app.DatePickerDialog(this, { _, y, m, d ->
                val sel = java.util.Calendar.getInstance()
                sel.set(y, m, d)
                startDate = dateFormat.format(sel.time)
                startDateEdit.setText(startDate)
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH))
            dpd.show()
        }
        endDateEdit.setOnClickListener {
            val c = java.util.Calendar.getInstance()
            val dpd = android.app.DatePickerDialog(this, { _, y, m, d ->
                val sel = java.util.Calendar.getInstance()
                sel.set(y, m, d)
                endDate = dateFormat.format(sel.time)
                endDateEdit.setText(endDate)
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH))
            dpd.show()
        }

        // Fetch and display attendance
        fun fetchAttendance() {
            android.util.Log.d("AttendanceHistory", "fetchAttendance called (with params)")
            lifecycleScope.launch {
                try {
                    val api = com.yatri.net.Network.retrofit.create(com.yatri.attendance.AttendanceApi::class.java)
                    // Use same param logic as React Native
                    val roleId = "5" // TODO: fetch from session/user
                    val reqUrl = com.yatri.AppConfig.API_BASE_URL + "attendance/history/me?startDate=$startDate&endDate=$endDate&roleId=$roleId"
                    android.util.Log.d("AttendanceHistory", "About to call URL: $reqUrl")
                    val resp = api.getMyAttendanceHistory(startDate, endDate, roleId)
                    android.util.Log.d("AttendanceHistory", "API called. Success: ${resp.isSuccessful} | URL: $reqUrl | code: ${resp.code()} | message: ${resp.message()}")
                    if (resp.isSuccessful) {
                        val data = resp.body()?.data
                        android.util.Log.d("AttendanceHistory", "RESPONSE BODY: ${resp.raw()}\nJSON: ${resp.errorBody()?.string() ?: data}")
                        tvTotalDays.text = data?.total_days?.toString() ?: "0"
                        // Present/Absent/OffDay/Leave counts: you may need to calculate from report list
                        val present = data?.report?.count { it.status == "PRESENT" } ?: 0
                        val absent = data?.report?.count { it.status == "ABSENT" } ?: 0
                        val offDay = data?.report?.count { it.is_off_day == true } ?: 0
                        val leave = data?.report?.count { it.status == "LEAVE" } ?: 0
                        tvPresent.text = present.toString()
                        tvAbsent.text = absent.toString()
                        tvOffDay.text = offDay.toString()
                        tvLeave.text = leave.toString()
                        val attendanceList = data?.report?.map {
                            com.yatri.attendance.AttendanceItem(
                                it.date ?: "",
                                it.check_in_time,
                                it.check_out_time,
                                it.shift_name,
                                it.duration_minutes?.toString(),
                                it.status ?: ""
                            )
                        } ?: emptyList()
                        android.util.Log.d("AttendanceHistory", "Attendance records: ${attendanceList.size}")
                        rvAttendance.adapter = com.yatri.attendance.AttendanceAdapter(attendanceList)
                    } else {
                        android.util.Log.e("AttendanceHistory", "API call failed: ${resp.code()} ${resp.message()} BODY: ${resp.errorBody()?.string()}")
                        android.widget.Toast.makeText(this@AttendanceHistoryActivity, "Failed to fetch attendance", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceHistory", "Exception: ${e.message}", e)
                    android.widget.Toast.makeText(this@AttendanceHistoryActivity, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnRefresh.setOnClickListener { fetchAttendance() }
        fetchAttendance()
    }
}
