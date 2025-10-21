package com.yatri.attendance

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface AttendanceApi {
    @GET("employee/attendance")
    suspend fun getMyAttendance(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String
    ): Response<AttendancePageResponse>
}
