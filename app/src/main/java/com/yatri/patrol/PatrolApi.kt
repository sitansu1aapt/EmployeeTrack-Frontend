package com.yatri.patrol

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable data class Envelope<T>(val status: String? = null, val data: T? = null, val message: String? = null)

@Serializable data class PatrolSession(
    val patrol_session_id: String,
    val route_name: String,
    val status: String,
    val scheduled_start_time: String? = null,
    val actual_start_time: String? = null,
    val actual_end_time: String? = null,
    val notes: String? = null
)

@Serializable data class StartPatrolBody(val routeId: Int)
@Serializable data class EndPatrolBody(val notes: String?)

interface PatrolApi {
    @GET("employee/patrol/sessions")
    suspend fun getEmpSessions(@Query("roleId") roleId: String): Response<Envelope<List<PatrolSession>>>

    @POST("employee/patrol/sessions/start")
    suspend fun startSession(@Query("roleId") roleId: String, @Body body: StartPatrolBody): Response<Envelope<Unit>>

    @POST("employee/patrol/sessions/{id}/end")
    suspend fun endSession(@Path("id") sessionId: String, @Query("roleId") roleId: String, @Body body: EndPatrolBody): Response<Envelope<Unit>>
}