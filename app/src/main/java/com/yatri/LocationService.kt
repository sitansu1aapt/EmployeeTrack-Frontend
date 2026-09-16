package com.yatri

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LocationService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Android 14+ requires serviceType=location; ensure notification + immediate foreground
        startForeground(1, notif("Starting"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        running = true
        scope.launch { loop() }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun notif(text: String): Notification {
        val channelId = "loc_ch"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Location", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Yatri")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isGpsEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) { false }
    }

    private fun batteryLevel(): Int? {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        } catch (e: Exception) { null }
    }

    /** POST a JSON body to an API path with the auth header; returns false on failure. */
    private fun postJson(client: OkHttpClient, path: String, json: JSONObject): Boolean {
        return try {
            val url = com.yatri.AppConfig.API_BASE_URL + path
            val token = com.yatri.TokenStore.token
            val reqBuilder = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
            if (!token.isNullOrBlank()) reqBuilder.header("Authorization", "Bearer $token")
            client.newCall(reqBuilder.build()).execute().use { resp ->
                android.util.Log.d("LocationService", "POST $path <- code=${resp.code}")
                resp.isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationService", "POST $path failed", e)
            false
        }
    }

    private suspend fun loop() {
        val fused = LocationServices.getFusedLocationProviderClient(this)
        val client = OkHttpClient()
        var interval = 60000L // 1 minute default interval
        while (running) {
            try {
                val gpsOn = isGpsEnabled()
                val hasPerm = hasLocationPermission()
                val battery = batteryLevel()

                // Actively request a fresh fix (lastLocation is often null when nothing else uses GPS).
                var loc: android.location.Location? = null
                if (hasPerm && gpsOn) {
                    loc = try {
                        val cts = CancellationTokenSource()
                        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                            ?: fused.lastLocation.await()
                    } catch (e: SecurityException) { null }
                }

                val status = when {
                    !hasPerm -> "PERMISSION_DENIED"
                    !gpsOn -> "GPS_OFF"
                    loc == null -> "NO_FIX"
                    else -> "OK"
                }

                // Always send a status heartbeat so the admin can see WHY location is missing.
                val statusBody = JSONObject().apply {
                    put("status", status)
                    put("isGpsEnabled", gpsOn)
                    put("hasLocationPermission", hasPerm)
                    if (battery != null) put("batteryLevel", battery)
                    if (loc != null) {
                        put("latitude", loc.latitude)
                        put("longitude", loc.longitude)
                    }
                }
                postJson(client, "locations/me/status", statusBody)

                // When we have a fix, also send the actual location update.
                if (loc != null) {
                    val body = JSONObject().apply {
                        put("latitude", loc.latitude)
                        put("longitude", loc.longitude)
                        put("accuracy", loc.accuracy)
                        put("isGpsEnabled", gpsOn)
                        if (battery != null) put("batteryLevel", battery)
                    }
                    postJson(client, "locations/me/update", body)
                    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                    startForeground(1, notif("Last update ${String.format(Locale.US, "%.0f", loc.accuracy)}m @ $ts"))
                } else {
                    startForeground(1, notif("Not reporting: $status"))
                }
                interval = 60000L
            } catch (e: Exception) {
                interval = (interval * 1.5).toLong().coerceAtMost(120000)
                android.util.Log.e("LocationService", "Update failed, backing off to ${interval/1000}s", e)
            }
            delay(interval)
        }
    }
}

// Simple await for Task<Result> without importing coroutines-play-services to keep deps small
private suspend fun com.google.android.gms.tasks.Task<android.location.Location>.await(): android.location.Location? {
    return suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
    }
}


