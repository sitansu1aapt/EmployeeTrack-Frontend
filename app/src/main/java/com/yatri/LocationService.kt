package com.yatri

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.yatri.net.Network
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

    private suspend fun loop() {
        val fused = LocationServices.getFusedLocationProviderClient(this)
        val client = OkHttpClient()
        var interval = 60000L // 1 minute default interval
        while (running) {
            try {
                val loc = fused.lastLocation.await()
                if (loc != null) {
                    val body = JSONObject().apply {
                        put("latitude", loc.latitude)
                        put("longitude", loc.longitude)
                        put("accuracy", loc.accuracy)
                    }.toString().toRequestBody("application/json".toMediaType())
                    val url = com.yatri.AppConfig.API_BASE_URL + "locations/me/update"
                    val token = com.yatri.TokenStore.token
                    android.util.Log.d("LocationService", "POST $url lat=${loc.latitude} lng=${loc.longitude} acc=${loc.accuracy} tokenPrefix=${token?.take(12)}")
                    val reqBuilder = Request.Builder()
                        .url(url)
                        .post(body)
                    if (!token.isNullOrBlank()) reqBuilder.header("Authorization", "Bearer $token")
                    val req = reqBuilder.build()
                    client.newCall(req).execute().use { resp ->
                        val code = resp.code
                        val respStr = resp.body?.string().orEmpty()
                        android.util.Log.d("LocationService", "Response code=$code body=${respStr.take(200)}")
                    }
                    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                    startForeground(1, notif("Last update ${String.format(Locale.US, "%.0f", loc.accuracy)}m @ $ts"))
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


