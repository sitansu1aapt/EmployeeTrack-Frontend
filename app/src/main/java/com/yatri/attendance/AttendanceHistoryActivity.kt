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

        // Date logic - match React Native formatDate function
        val calendar = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        
        // Set default date range to current date (like React Native)
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
                    // Get roleId from user session like React Native uses activeRoleId
                    val roleId =  "5" // fallback to "5" if not available
                    val reqUrl = com.yatri.AppConfig.API_BASE_URL + "attendance/history/me?startDate=$startDate&endDate=$endDate&roleId=$roleId"
                    android.util.Log.d("AttendanceHistory", "About to call URL: $reqUrl")
                    val resp = api.getMyAttendanceHistory(startDate, endDate, roleId)
                    android.util.Log.d("AttendanceHistory", "API called. Success: ${resp.isSuccessful} | URL: $reqUrl | code: ${resp.code()} | message: ${resp.message()}")
                    if (resp.isSuccessful) {
                        val data = resp.body()?.data
                        android.util.Log.d("AttendanceHistory", "RESPONSE BODY: ${resp.raw()}\nJSON: ${resp.errorBody()?.string() ?: data}")
                        tvTotalDays.text = data?.total_days?.toString() ?: "0"
                        // Count statuses to match React Native logic
                        val report = data?.report ?: emptyList()
                        var present = 0
                        var absent = 0
                        var offDay = 0
                        var leave = 0
                        
                        for (r in report) {
                            when (r.status) {
                                "CHECKED_IN", "CHECKED_OUT", "PRESENT" -> present++
                                "ABSENT" -> absent++
                                "OFF_DAY" -> offDay++
                                "ON_LEAVE" -> leave++
                            }
                        }
                        tvPresent.text = present.toString()
                        tvAbsent.text = absent.toString()
                        tvOffDay.text = offDay.toString()
                        tvLeave.text = leave.toString()
                        val attendanceList = data?.report?.map {
                            android.util.Log.d("AttendanceHistory", "=== RAW API DATA ===")
                            android.util.Log.d("AttendanceHistory", "Date: ${it.date}")
                            android.util.Log.d("AttendanceHistory", "Check-in timestamp: '${it.check_in_time}'")
                            android.util.Log.d("AttendanceHistory", "Check-out timestamp: '${it.check_out_time}'")
                            android.util.Log.d("AttendanceHistory", "Status: ${it.status}")
                            android.util.Log.d("AttendanceHistory", "Duration minutes: ${it.duration_minutes}")
                            
                            // Compare with current time to understand the issue
                            val currentTime = java.util.Date()
                            android.util.Log.d("AttendanceHistory", "Current time: $currentTime")
                            android.util.Log.d("AttendanceHistory", "Current timezone: ${java.util.TimeZone.getDefault().id}")
                            
                            com.yatri.attendance.AttendanceItem(
                                it.date ?: "",
                                it.check_in_time,
                                it.check_out_time,
                                it.shift_name,
                                it.duration_minutes,
                                it.status ?: ""
                            )
                        } ?: emptyList()
                        android.util.Log.d("AttendanceHistory", "Attendance records: ${attendanceList.size}")
                        
                        // Debug each record that will be displayed
                        attendanceList.forEachIndexed { index, attendance ->
                            android.util.Log.d("AttendanceHistory", "=== RECORD $index ===")
                            android.util.Log.d("AttendanceHistory", "Raw check_in_time: '${attendance.checkIn}'")
                            android.util.Log.d("AttendanceHistory", "Raw check_out_time: '${attendance.checkOut}'")
                            android.util.Log.d("AttendanceHistory", "Date: '${attendance.date}'")
                            android.util.Log.d("AttendanceHistory", "Status: '${attendance.status}'")
                        }
                        
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
