package com.yatri.services

import android.content.Context
import android.util.Log
import com.yatri.net.Network
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class Task(
    val id: String,
    val title: String,
    val description: String,
    val dueDate: String,
    val assignedBy: String,
    val email: String,
    val status: String,
    val priority: String
)

interface TaskApi {
    @GET("tasks/{taskId}")
    suspend fun getTask(@Path("taskId") taskId: String): Task

    @POST("tasks/{taskId}/start")
    suspend fun startTask(@Path("taskId") taskId: String): Task
}

class TaskService(private val context: Context) {
    private val TAG = "TaskService"

    private val api = Network.retrofit.create(TaskApi::class.java)

    suspend fun startTask(taskId: String): Task? = try {
        api.startTask(taskId)
    } catch (e: Exception) {
        Log.e(TAG, "Error starting task", e)
        null
    }

    suspend fun getTask(taskId: String): Task? = try {
        api.getTask(taskId)
    } catch (e: Exception) {
        Log.e(TAG, "Error getting task", e)
        null
    }
}
