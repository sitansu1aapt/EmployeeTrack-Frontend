package com.yatri.attendance

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface AttendanceApi {
    @GET("attendance/history/me")
    suspend fun getMyAttendanceHistory(
        @Query("startDate") startDate: String?,
        @Query("endDate") endDate: String?,
        @Query("roleId") roleId: String?
    ): Response<AttendancePageResponse>
}
