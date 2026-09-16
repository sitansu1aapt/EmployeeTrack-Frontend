package com.yatri.checkin

import android.content.Context
import android.location.Location
import com.yatri.AppConfig
import com.yatri.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

data class GeofenceCheckDetails(
    val isInside: Boolean,
    val userLocation: Location? = null,
    val assignments: List<SiteAssignment> = emptyList(),
    val matchedAssignment: SiteAssignment? = null,
    val geofencePolygon: List<LatLngData> = emptyList(),
    val distanceToCenterMeters: Float = 0f,
    val accuracyMeters: Float = 0f,
    val statusMessage: String = ""
)

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

    suspend fun checkUserInGeofence(context: Context): Result<Boolean> {
        val details = getGeofenceDetails(context)
        return if (details.userLocation == null && details.statusMessage.contains("Unable")) {
            Result.failure(Exception(details.statusMessage))
        } else {
            Result.success(details.isInside)
        }
    }

    suspend fun getGeofenceDetails(context: Context): GeofenceCheckDetails = withContext(Dispatchers.IO) {
        // 1. Fetch User Location (Fresh High Accuracy first, fallback to lastLocation)
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationTokenSource = CancellationTokenSource()

        val location: Location? = try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await() ?: fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            try {
                fusedLocationClient.lastLocation.await()
            } catch (ex: Exception) {
                null
            }
        }

        if (location == null) {
            return@withContext GeofenceCheckDetails(
                isInside = false,
                statusMessage = "Unable to fetch GPS location. Ensure Location Services / GPS is turned ON."
            )
        }

        // 2. Fetch Site Assignments.
        // Distinguish a real "no assignment" (HTTP 200 with empty list) from auth/server/network
        // failures — otherwise an expired session (401) is misreported as "No site assigned".
        var fetchErrorMessage: String? = null
        val assignments: List<SiteAssignment> = try {
            val response = api.getSiteAssignmentsByUserId()
            if (response.isSuccessful) {
                response.body()?.data ?: emptyList()
            } else {
                fetchErrorMessage = when (response.code()) {
                    401, 403 -> "Session expired. Please log out and log in again."
                    in 500..599 -> "Server error while checking your site assignment. Please try again in a moment."
                    else -> "Couldn't verify your site assignment (error ${response.code()}). Please try again."
                }
                emptyList()
            }
        } catch (e: Exception) {
            fetchErrorMessage = "Network error while checking your site assignment. Check your connection and try again."
            emptyList()
        }

        if (assignments.isEmpty()) {
            return@withContext GeofenceCheckDetails(
                isInside = false,
                userLocation = location,
                accuracyMeters = location.accuracy,
                statusMessage = fetchErrorMessage
                    ?: "No site assignment found for your account. Please contact your Admin."
            )
        }

        // 3. Evaluate location against each assignment's geofence
        for (assignment in assignments) {
            val poly = assignment.geofenceShapeData?.coordinates ?: emptyList()
            if (poly.size < 3) {
                continue
            }

            val insideRaw = isPointInPolygon(location.latitude, location.longitude, poly)
            val centerLat = poly.map { it.lat }.average()
            val centerLng = poly.map { it.lng }.average()

            val resultsArr = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                centerLat, centerLng,
                resultsArr
            )
            val distToCenter = resultsArr[0]

            // Calculate shortest distance to any geofence boundary vertex
            val minVertexDist = poly.minOf { vertex ->
                val res = FloatArray(1)
                Location.distanceBetween(location.latitude, location.longitude, vertex.lat, vertex.lng, res)
                res[0]
            }

            // Security Guard Perimeter Buffer: Allow 30m perimeter tolerance or device accuracy radius
            val guardBufferMeters = maxOf(30f, location.accuracy)
            val isInside = insideRaw || (minVertexDist <= guardBufferMeters)

            if (isInside) {
                return@withContext GeofenceCheckDetails(
                    isInside = true,
                    userLocation = location,
                    assignments = assignments,
                    matchedAssignment = assignment,
                    geofencePolygon = poly,
                    distanceToCenterMeters = distToCenter,
                    accuracyMeters = location.accuracy,
                    statusMessage = if (insideRaw) "You are inside the site boundary." else "Inside site perimeter buffer (${minVertexDist.toInt()}m from boundary)."
                )
            } else {
                return@withContext GeofenceCheckDetails(
                    isInside = false,
                    userLocation = location,
                    assignments = assignments,
                    matchedAssignment = assignment,
                    geofencePolygon = poly,
                    distanceToCenterMeters = distToCenter,
                    accuracyMeters = location.accuracy,
                    statusMessage = "Outside site boundary (${distToCenter.toInt()}m from site center)."
                )
            }
        }

        return@withContext GeofenceCheckDetails(
            isInside = false,
            userLocation = location,
            assignments = assignments,
            matchedAssignment = assignments.firstOrNull(),
            accuracyMeters = location.accuracy,
            statusMessage = "You are outside your assigned site boundary."
        )
    }

    fun isPointInPolygon(pointLat: Double, pointLng: Double, polygon: List<LatLngData>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].lng
            val yi = polygon[i].lat
            val xj = polygon[j].lng
            val yj = polygon[j].lat

            val intersect = ((yi > pointLat) != (yj > pointLat)) &&
                    (pointLng < (xj - xi) * (pointLat - yi) / (yj - yi + 0.0000000001) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }
}
