package com.yatri.tasks

import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Body
import retrofit2.http.Query

interface TasksApi {
    @GET("task-assignments/assigned-to-me/organization")
    suspend fun getAssignedTasks(@Query("roleId") roleId: String): AssignedTasksEnvelope

    @PUT("task-assignments/{assignmentId}/status/assignee")
    suspend fun updateTaskStatusByAssignee(
        @Query("roleId") roleId: String,
        @Path("assignmentId") assignmentId: String,
        @Body status: Map<String, String>
    )
}
