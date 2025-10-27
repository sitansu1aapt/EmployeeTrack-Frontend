package com.yatri.attendance

import kotlinx.serialization.Serializable

@Serializable
data class AttendanceReportItem(
    val id: String? = null,
    val date: String? = null,
    val status: String? = null,
    val shift_name: String? = null,
    val check_in_time: String? = null,
    val check_out_time: String? = null,
    val check_in_image: String? = null,
    val check_out_image: String? = null,
    val is_late: Boolean? = null,
    val is_early_checkout: Boolean? = null,
    val is_off_day: Boolean? = null,
    val duration_minutes: Int? = null
)

@Serializable
data class AttendanceData(
    val user_id: String? = null,
    val date_range: DateRange? = null,
    val total_days: Int? = null,
    val report: List<AttendanceReportItem> = emptyList()
)

@Serializable
data class DateRange(
    val start_date: String? = null,
    val end_date: String? = null
)

@Serializable
data class AttendancePageResponse(
    val success: Boolean? = null,
    val status: String? = null,
    val message: String? = null,
    val data: AttendanceData? = null
)
