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
        startForeground(1, notif("Starting"))
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
        var interval = 30000L
        while (running) {
            try {
                val loc = fused.lastLocation.await()
                if (loc != null) {
                    val body = JSONObject().apply {
                        put("latitude", loc.latitude)
                        put("longitude", loc.longitude)
                        put("accuracy", loc.accuracy)
                    }.toString().toRequestBody("application/json".toMediaType())
                    val req = Request.Builder()
                        .url("https://empbackend-862367621556.asia-south1.run.app/api/v1/locations/me/update")
                        .post(body)
                        .build()
                    client.newCall(req).execute().use { }
                    startForeground(1, notif("Last update sent"))
                }
                interval = 30000L
            } catch (e: Exception) {
                interval = (interval * 1.5).toLong().coerceAtMost(120000)
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


