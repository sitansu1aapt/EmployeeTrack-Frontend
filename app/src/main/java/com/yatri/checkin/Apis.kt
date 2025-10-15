package com.yatri.checkin

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

@Serializable
data class CheckInData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val check_in_time: String,
    val selfie_url: String,
    val device_info: DeviceInfo,
    val notes: String? = null,
    val siteAssignmentId: String? = null
)

@Serializable
data class DeviceInfo(
    val device_model: String,
    val platform: String,
    val platform_version: String
)

interface FilesApi {
    @Multipart
    @POST("files/attendance-selfie")
    suspend fun uploadAttendanceSelfie(@Part photo: MultipartBody.Part): UploadResponse
}

@Serializable
data class UploadResponse(val data: UploadData?)

@Serializable
data class UploadData(val selfie_url: String? = null)

interface AttendanceApi {
    @POST("attendance/check-in")
    suspend fun checkIn(@Body body: CheckInData)

    @POST("attendance/check-out")
    suspend fun checkOut(@Body body: CheckInData)
}


