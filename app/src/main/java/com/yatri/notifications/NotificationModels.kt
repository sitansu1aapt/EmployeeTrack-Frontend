package com.yatri.notifications

import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val notification_id: String,
    val title: String,
    val details: String,
    val type: String,
    val target_user_id: String,
    val subject_user_id: String,
    val is_seen: Boolean,
    val created_at: String,
    val seen_at: String? = null,
    val metadata: NotificationMetadata? = null,
    val is_deleted: Boolean = false,
    val organization_id: String,
    val updated_at: String
) : java.io.Serializable

@Serializable
data class NotificationMetadata(
    val empid: String? = null,
    val site_id: String? = null,
    val site_name: String? = null,
    val timestamp: String? = null,
    val user_name: String? = null
)

@Serializable
data class NotificationListResponse(
    val status: String,
    val data: NotificationData
)

@Serializable
data class NotificationData(
    val notifications: List<Notification>,
    val pagination: Pagination,
    val summary: NotificationSummary
)

@Serializable
data class Pagination(
    val limit: Int,
    val offset: Int,
    val total: Int
)

@Serializable
data class NotificationSummary(
    val unreadCount: Int,
    val totalCount: Int
)

@Serializable
data class NotificationFilter(
    val type: String? = null,
    val is_seen: Boolean? = null,
    val limit: Int = 20,
    val offset: Int = 0
)

enum class NotificationType(val value: String, val displayName: String) {
    ALL("", "All"),
    GEOFENCE_ENTRY("GEOFENCE_IN", "Geofence Entry"),
    GEOFENCE_EXIT("GEOFENCE_OUT", "Geofence Exit"),
    EMERGENCY_ALERT("EMERGENCY_NOTIFICATION", "Emergency Alert"),
    MESSAGE("MESSAGE_NOTIFICATION", "Message"),
    TASK_ASSIGNMENT("TASK_ASSIGNMENT", "Task Assignment"),
    ATTENDANCE("ATTENDANCE", "Attendance"),
    ABSENT_NOTIFICATION("ABSENT_NOTIFICATION", "Attendance Absent Notification")
}

enum class NotificationStatus(val value: Boolean?, val displayName: String) {
    ALL(null, "All"),
    UNREAD(false, "Unread"),
    READ(true, "Read")
}
