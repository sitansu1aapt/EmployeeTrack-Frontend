package com.yatri.helpdesk

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val status: String,
    val message: String,
    val data: T?
)

@Serializable
data class HelpdeskAttachment(
    val attachment_url: String? = null,
    val attachment_file_name: String? = null,
    val attachment_mime_type: String? = null
)

@Serializable
data class CreateHelpdeskRequestBody(
    val query_type: String,
    val description: String,
    val attachment_url: String? = null,
    val attachment_file_name: String? = null,
    val attachment_mime_type: String? = null
)

@Serializable
data class HelpdeskReply(
    val helpdesk_reply_id: Long,
    val sender_user_id: Long,
    val sender_role_name: String,
    val sender_name: String? = null,
    val reply_message: String? = null,
    val attachment_url: String? = null,
    val attachment_file_name: String? = null,
    val attachment_mime_type: String? = null,
    val status_after_reply: String,
    val created_at: String
)

@Serializable
data class HelpdeskRequestItem(
    val helpdesk_request_id: Long,
    val query_type: String,
    val description: String,
    val status: String,
    val created_at: String,
    val updated_at: String,
    val site_name: String? = null,
    val department_name: String? = null,
    val latest_reply_message: String? = null,
    val attachment_url: String? = null,
    val attachment_file_name: String? = null,
    val attachment_mime_type: String? = null
)

@Serializable
data class HelpdeskRequestDetail(
    val helpdesk_request_id: Long,
    val query_type: String,
    val description: String,
    val status: String,
    val created_at: String,
    val updated_at: String,
    val site_name: String? = null,
    val department_name: String? = null,
    val attachment_url: String? = null,
    val attachment_file_name: String? = null,
    val attachment_mime_type: String? = null,
    val latest_reply_message: String? = null,
    val replies: List<HelpdeskReply> = emptyList()
)

@Serializable
data class HelpdeskListData(
    val requests: List<HelpdeskRequestItem> = emptyList(),
    val totalCount: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0
)
