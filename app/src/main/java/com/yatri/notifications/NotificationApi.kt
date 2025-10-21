package com.yatri.notifications

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

    @PUT("notifications/{notificationId}/seen")
    suspend fun markNotificationAsSeen(
        @Path("notificationId") notificationId: String
    ): Response<Map<String, Any>>

    @PUT("notifications/seen-all")
    suspend fun markAllNotificationsAsSeen(): Response<Map<String, Any>>

    @DELETE("notifications/{notificationId}")
    suspend fun deleteNotification(
        @Path("notificationId") notificationId: String
    ): Response<Map<String, Any>>
}
