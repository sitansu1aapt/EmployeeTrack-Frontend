package com.yatri.tasks

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Body

interface TasksApi {
    @GET("tasks/assigned/{roleId}")
    suspend fun getAssignedTasks(@Path("roleId") roleId: String): List<AssignedTask>

    @POST("tasks/update-status/{roleId}/{assignmentId}")
    suspend fun updateTaskStatusByAssignee(
        @Path("roleId") roleId: String,
        @Path("assignmentId") assignmentId: String,
        @Body status: Map<String, String>
    )
}
