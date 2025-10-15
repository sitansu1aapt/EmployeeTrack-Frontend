package com.yatri.tasks

data class AssignedTask(
    val assignmentId: String,
    val taskTitle: String,
    val taskDescription: String,
    val taskPriority: String,
    val taskStatus: String,
    val dueDate: String,
    val assignedBy: String,
    val assignedByEmail: String
)
