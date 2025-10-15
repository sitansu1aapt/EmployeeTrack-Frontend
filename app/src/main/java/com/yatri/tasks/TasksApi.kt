package com.yatri.tasks

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Body
import retrofit2.http.Query

interface TasksApi {
    @GET("task-assignments/assigned-to-me/organization")
    suspend fun getAssignedTasks(@Query("roleId") roleId: String): AssignedTasksEnvelope

    @POST("tasks/update-status/{roleId}/{assignmentId}")
    suspend fun updateTaskStatusByAssignee(
        @Path("roleId") roleId: String,
        @Path("assignmentId") assignmentId: String,
        @Body status: Map<String, String>
    )
}
