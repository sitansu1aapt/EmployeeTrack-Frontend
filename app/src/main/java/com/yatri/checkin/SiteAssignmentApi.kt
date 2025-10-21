package com.yatri.checkin

import retrofit2.http.GET

import retrofit2.http.Query
import retrofit2.Response
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SiteAssignmentApiResponse(
    val status: String?,
    val data: List<SiteAssignment>? = null
)

interface SiteAssignmentApi {
    @GET("site-assignments/by-userId")
    suspend fun getSiteAssignmentsByUserId(): Response<SiteAssignmentApiResponse>
}
