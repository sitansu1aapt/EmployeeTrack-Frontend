package com.yatri

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

interface ProfileApi {
    @GET("auth/me")
    suspend fun getProfile(): Response<ProfileResponse>
}

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

