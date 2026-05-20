package com.yatri

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Body

interface ProfileApi {
    @GET("auth/me")
    suspend fun getProfile(): Response<ProfileResponse>

    @Multipart
    @POST("files/profile-photo")
    suspend fun uploadProfilePhoto(
        @Part photo: MultipartBody.Part
    ): Response<UploadPhotoResponse>

    @PATCH("users/me/profile")
    suspend fun updateProfilePicture(
        @Body body: UpdateProfileBody
    ): Response<GenericResponse>
}

@Serializable
data class UploadPhotoResponse(
    val success: Boolean,
    val data: UploadPhotoData,
    val message: String? = null
)

@Serializable
data class UploadPhotoData(
    val profile_photo_url: String
)

@Serializable
data class UpdateProfileBody(
    val profile_picture_url: String
)

@Serializable
data class GenericResponse(
    val success: Boolean,
    val message: String? = null
)

@Serializable
data class ProfileResponse(
    val success: Boolean,
    val status: String,
    val message: String,
    val data: ProfileData
)

@Serializable
data class ProfileData(
    val user: ProfileUser,
    val auth: ProfileAuth
)

@Serializable
data class ProfileUser(
    val user_id: String,
    val organization_id: String,
    val employee_id: String,
    val full_name: String,
    val email: String,
    val phone_number: String?,
    val profile_picture_url: String?,
    val preferred_language_code: String,
    val is_active: Boolean,
    val is_system_admin: Boolean,
    val last_login_at: String,
    val created_at: String,
    val updated_at: String,
    val is_deleted: Boolean,
    val is_on_duty: Boolean,
    val is_on_leave: Boolean,
    val department_id: String?,
    val fcm_token: String,
    val activeContext: ActiveContext
)

@Serializable
data class ActiveContext(
    val assignmentId: String,
    val roleName: String,
    val scopedOrganizationId: String,
    val scopedSiteId: String?,
    val scopedDepartmentId: String?
)

@Serializable
data class ProfileAuth(
    val userId: String,
    val email: String,
    val organizationId: String,
    val assignmentId: String,
    val roleIds: List<String>,
    val roleId: String,
    val roleName: String,
    val scopedOrganizationId: String,
    val scopedSiteId: String?,
    val scopedDepartmentId: String?,
    val iat: Long,
    val exp: Long
)


