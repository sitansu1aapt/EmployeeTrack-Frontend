package com.yatri.attendance

import kotlinx.serialization.Serializable

@Serializable
data class AttendanceDetail(
    val date: String,
    val dayOfWeek: String,
    val checkInTime: String?,
    val checkOutTime: String?,
    val status: String,
    val shiftName: String?,
    val duration: String?
)

@Serializable
data class AttendanceSummary(
    val totalDays: Int,
    val presentDays: Int,
    val absentDays: Int,
    val offDays: Int,
    val leaveDays: Int
)

@Serializable
data class AttendancePageResponse(
    val summary: AttendanceSummary,
    val attendanceRecords: List<AttendanceDetail>
)
