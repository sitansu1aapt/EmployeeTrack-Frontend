package com.yatri.checkin

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.Response
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SiteAssignmentApiResponse(
    val status: String?,
    val data: List<SiteAssignment>? = null
)

@Serializable
data class DiscrepancyReportRequest(
    @SerialName("site_id") val siteId: String? = null,
    @SerialName("user_latitude") val userLatitude: Double? = null,
    @SerialName("user_longitude") val userLongitude: Double? = null,
    @SerialName("accuracy_meters") val accuracyMeters: Float? = null,
    @SerialName("distance_meters") val distanceMeters: Float? = null,
    @SerialName("user_remarks") val userRemarks: String? = ""
)

@Serializable
data class DiscrepancyReportResponse(
    val status: String?,
    val message: String? = null
)

interface SiteAssignmentApi {
    @GET("site-assignments/by-userId")
    suspend fun getSiteAssignmentsByUserId(): Response<SiteAssignmentApiResponse>

    @POST("site-assignments/report-discrepancy")
    suspend fun reportDiscrepancy(@Body body: DiscrepancyReportRequest): Response<DiscrepancyReportResponse>
}
