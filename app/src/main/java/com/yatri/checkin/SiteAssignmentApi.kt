package com.yatri.checkin

import retrofit2.http.GET
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface SiteAssignmentApi {
    @GET("site-assignments/me/active")
    suspend fun getSiteAssignmentsByUserId(): List<SiteAssignment>
}
