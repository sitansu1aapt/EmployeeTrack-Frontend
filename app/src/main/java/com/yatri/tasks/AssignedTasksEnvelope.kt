package com.yatri.tasks

@kotlinx.serialization.Serializable
data class AssignedTasksEnvelope(
    val data: List<AssignedTask>
)
