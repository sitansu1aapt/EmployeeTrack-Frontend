package com.yatri.helpdesk

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface HelpdeskApi {
    @Multipart
    @POST("/api/v1/helpdesk/attachments/upload")
    suspend fun uploadAttachment(
        @Part attachment: MultipartBody.Part
    ): ApiResponse<HelpdeskAttachment>

    @POST("/api/v1/helpdesk")
    suspend fun createRequest(
        @Body body: CreateHelpdeskRequestBody
    ): ApiResponse<HelpdeskRequestItem>

    @GET("/api/v1/helpdesk")
    suspend fun getRequests(
        @Query("status") status: String? = null,
        @Query("query_type") queryType: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): ApiResponse<HelpdeskListData>

    @GET("/api/v1/helpdesk/{requestId}")
    suspend fun getRequestDetail(
        @Path("requestId") requestId: Long
    ): ApiResponse<HelpdeskRequestDetail>
}
