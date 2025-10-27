package com.yatri.notifications

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

interface NotificationApi {
    @GET("notifications")
    suspend fun getNotifications(
        @Query("userRole") userRole: String? = null,
        @Query("type") type: String? = null,
        @Query("is_seen") isSeen: Boolean? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<NotificationListResponse>

    @Serializable
    data class SimpleResponse(val status: String? = null, val message: String? = null)

    @PUT("notifications/{notificationId}/seen")
    suspend fun markNotificationAsSeen(
        @Path("notificationId") notificationId: String
    ): Response<SimpleResponse>

    @PUT("notifications/seen-all")
    suspend fun markAllNotificationsAsSeen(): Response<SimpleResponse>

    @DELETE("notifications/{notificationId}")
    suspend fun deleteNotification(
        @Path("notificationId") notificationId: String
    ): Response<SimpleResponse>
}
