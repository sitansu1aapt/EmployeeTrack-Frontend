package com.yatri.checkin

import android.content.Context
import android.location.Location
import com.yatri.AppConfig
import com.yatri.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.android.gms.location.LocationServices
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

object GeofenceChecker {

    private val json = Json { ignoreUnknownKeys = true }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
        val token = TokenStore.token
        if (!token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        chain.proceed(requestBuilder.build())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(SiteAssignmentApi::class.java)

    suspend fun checkUserInGeofence(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 1. Get User Location
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val location: Location = try {
                fusedLocationClient.lastLocation.await() ?: return@withContext Result.failure(Exception("Location unavailable"))
            } catch (e: SecurityException) {
                return@withContext Result.failure(Exception("Permission denied"))
            }

            // 2. Fetch Site Assignments
            val response = api.getSiteAssignmentsByUserId()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API Error: ${response.code()}"))
            }

            val assignmentResponse = response.body()
            val assignments = assignmentResponse?.data
            if (assignments.isNullOrEmpty()) {
                // If no assignment, checking fails (assuming must be assigned to site)
                return@withContext Result.success(false)
            }

            // 3. Check each assignment
            for (assignment in assignments) {
                if (isPointInPolygon(location, assignment.geofenceShapeData.coordinates)) {
                    return@withContext Result.success(true)
                }
            }

            return@withContext Result.success(false)

        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun isPointInPolygon(point: Location, polygon: List<LatLngData>): Boolean {
        var intersectCount = 0
        for (j in polygon.indices) {
            val k = (j + 1) % polygon.size
            val a = polygon[j]
            val b = polygon[k]
            if (((a.lat > point.latitude) != (b.lat > point.latitude)) &&
                (point.longitude < (b.lng - a.lng) * (point.latitude - a.lat) / (b.lat - a.lat) + a.lng)
            ) {
                intersectCount++
            }
        }
        return (intersectCount % 2 == 1)
    }
}
