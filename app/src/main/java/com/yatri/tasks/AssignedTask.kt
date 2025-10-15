package com.yatri.tasks

@kotlinx.serialization.Serializable
data class AssignedTask(
    @kotlinx.serialization.SerialName("assignment_id") val assignmentId: String,
    @kotlinx.serialization.SerialName("task_id") val taskId: String,
    @kotlinx.serialization.SerialName("assigned_by_user_id") val assignedByUserId: String,
    @kotlinx.serialization.SerialName("assigned_to_user_id") val assignedToUserId: String,
    @kotlinx.serialization.SerialName("organization_id") val organizationId: String,
    @kotlinx.serialization.SerialName("task_status") val taskStatus: String,
    @kotlinx.serialization.SerialName("attachment_url") val attachmentUrl: String? = null,
    @kotlinx.serialization.SerialName("notes") val notes: String? = null,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String,
    @kotlinx.serialization.SerialName("is_deleted") val isDeleted: Boolean,
    @kotlinx.serialization.SerialName("task_title") val taskTitle: String,
    @kotlinx.serialization.SerialName("task_description") val taskDescription: String,
    @kotlinx.serialization.SerialName("task_priority") val taskPriority: String,
    @kotlinx.serialization.SerialName("task_due_date") val taskDueDate: String,
    @kotlinx.serialization.SerialName("assigned_by_name") val assignedByName: String,
    @kotlinx.serialization.SerialName("assigned_by_email") val assignedByEmail: String,
    @kotlinx.serialization.SerialName("assigned_to_name") val assignedToName: String,
    @kotlinx.serialization.SerialName("assigned_to_email") val assignedToEmail: String,
    @kotlinx.serialization.SerialName("organization_name") val organizationName: String,
    @kotlinx.serialization.SerialName("site_name") val siteName: String? = null,
    @kotlinx.serialization.SerialName("department_name") val departmentName: String? = null
)

