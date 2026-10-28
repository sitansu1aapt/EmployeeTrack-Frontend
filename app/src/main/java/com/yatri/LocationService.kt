package com.yatri

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.yatri.location.DeviceEvent
import com.yatri.location.LocationSettingMonitor
import com.yatri.location.LocationSync
import com.yatri.location.TrackingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
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

    // Offline-sync toasts are a testing aid: shown only in debuggable (debug) builds so
    // employees on the release build aren't interrupted every minute.
    private val mainHandler = Handler(Looper.getMainLooper())
    private val showSyncToasts by lazy { (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
    private var lastPending = 0
    private var lastToastFixTime = 0L

    private fun toast(message: String) {
        if (!showSyncToasts) return
        mainHandler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Android 14+ requires serviceType=location; ensure notification + immediate foreground
        goForeground(notif("Starting"))
        running = true
        // Hear about the Location switch the moment it flips (the loop also re-checks every cycle).
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(this, locationSettingReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trackingIntervalMs = TrackingSettings.intervalMillis(this)
        TrackingSettings.prefs(this).registerOnSharedPreferenceChangeListener(intervalListener)
        loopJob = scope.launch { loop() }
    }

    private val locationSettingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Switching Location on/off fires several broadcasts while providers flip one by
            // one (Samsung briefly reports ON→OFF→ON). Let it settle, then read once.
            scope.launch {
                delay(LOCATION_SETTLE_MS)
                checkLocationSetting()
            }
        }
    }

    /** Queue a LOCATION_OFF / LOCATION_ON event if the setting changed, and push it right away. */
    private suspend fun checkLocationSetting() {
        val on = isLocationSwitchOn()
        val fix = lastFix
        val type = LocationSettingMonitor.check(applicationContext, on, fix?.latitude, fix?.longitude) ?: return
        android.util.Log.i("LocationService", "Location setting changed: $type")
        if (type == DeviceEvent.TYPE_LOCATION_OFF) {
            stopContinuousUpdates()
            goForeground(notif(LOCATION_OFF_TEXT))
            toast("⚠️ Location turned off · recorded")
        } else {
            startContinuousUpdates()
            toast("📍 Location back on · recorded")
        }
        val sync = LocationSync.flush(applicationContext)
        if (!sync.complete && sync.outcome != LocationSync.Outcome.NO_AUTH) {
            LocationSync.scheduleWhenOnline(applicationContext)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Tracking is (re)started on every duty refresh; each startForegroundService call must
        // be answered with startForeground, and the loop itself is only launched once in onCreate.
        goForeground(notif(lastNotifText))
        return START_STICKY
    }

    /** The typed startForeground overload only exists on Android 10+ (minSdk is 24). */
    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        running = false
        TrackingSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(intervalListener)
        try { unregisterReceiver(locationSettingReceiver) } catch (e: IllegalArgumentException) { }
        stopContinuousUpdates()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private val wakeLock: PowerManager.WakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Yatri:LocationTracking")
            .apply { setReferenceCounted(false) }
    }

    private companion object {
        const val MAX_BACKOFF_MS = 120_000L
        const val WAKE_LOCK_MARGIN_MS = 60_000L
        const val LOCATION_SETTLE_MS = 3_000L
        const val LOCATION_OFF_TEXT = "⚠️ Location is OFF — turn it on to continue duty tracking"
    }

    @Volatile private var lastNotifText = "Tracking on duty"

    // Continuous GPS updates. The OS delivers these to a location foreground service even
    // while the phone is locked in a pocket, and GPS needs no network, so offline walks get
    // a point every interval instead of whenever Doze lets the polling loop run.
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    @Volatile private var updatesActive = false

    /** How often a fix is captured and stored (online or offline); set in Profile, applied live. */
    @Volatile private var trackingIntervalMs = TrackingSettings.DEFAULT_SECONDS * 1000L
    @Volatile private var loopJob: Job? = null

    private val intervalListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != TrackingSettings.KEY_INTERVAL_SECONDS) return@OnSharedPreferenceChangeListener
        val newInterval = TrackingSettings.intervalMillis(this)
        if (newInterval == trackingIntervalMs) return@OnSharedPreferenceChangeListener
        android.util.Log.i("LocationService", "Tracking interval changed to ${newInterval / 1000}s")
        trackingIntervalMs = newInterval
        // Re-register GPS updates at the new rate, and restart the loop so it doesn't sit out
        // the rest of the old (possibly 60 s) wait.
        stopContinuousUpdates()
        loopJob?.cancel()
        loopJob = scope.launch { loop() }
    }
    @Volatile private var lastStoredFixTime = 0L
    @Volatile private var lastFix: android.location.Location? = null
    private val storeLock = kotlinx.coroutines.sync.Mutex()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            // A batched delivery can carry several fixes; keep all of them in order.
            val fixes = result.locations.toList()
            scope.launch { fixes.forEach { store(it) } }
        }
    }

    private fun startContinuousUpdates() {
        if (updatesActive || !hasLocationPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, trackingIntervalMs)
            .setMinUpdateIntervalMillis(trackingIntervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            updatesActive = true
        } catch (e: SecurityException) {
            updatesActive = false
        }
    }

    private fun stopContinuousUpdates() {
        if (!updatesActive) return
        fused.removeLocationUpdates(locationCallback)
        updatesActive = false
    }

    /**
     * Queue one fix locally (uploaded later by [LocationSync.flush]). Skips fixes we already
     * stored (the fallback lastLocation often repeats the same fix) and fixes arriving well
     * before the interval is up, so the trail has one point per interval.
     */
    private suspend fun store(loc: android.location.Location): Boolean = storeLock.withLock {
        val fixTime = if (loc.time > 0) loc.time else System.currentTimeMillis()
        if (fixTime - lastStoredFixTime < trackingIntervalMs * 8 / 10) return@withLock false
        val saved = LocationSync.enqueue(applicationContext, loc, batteryLevel(), isGpsEnabled())
        if (saved) {
            lastStoredFixTime = fixTime
            lastFix = loc
        }
        saved
    }

    private fun notif(text: String): Notification {
        lastNotifText = text
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

    /** The phone's main Location switch (what the employee toggles), not individual providers. */
    private fun isLocationSwitchOn(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return androidx.core.location.LocationManagerCompat.isLocationEnabled(lm)
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
            android.util.Log.i("LOCATION_API", "🌐 [LocationService] Server: ${com.yatri.AppConfig.API_BASE_URL} | Type: $path | URL: $url | Payload: $json")
            val reqBuilder = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
            if (!token.isNullOrBlank()) reqBuilder.header("Authorization", "Bearer $token")
            client.newCall(reqBuilder.build()).execute().use { resp ->
                android.util.Log.i("LOCATION_API", "📥 [LocationService] Response code=${resp.code} for $path")
                resp.isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.e("LOCATION_API", "❌ [LocationService] POST $path failed on server ${com.yatri.AppConfig.API_BASE_URL}", e)
            false
        }
    }

    private suspend fun loop() {
        val client = OkHttpClient()
        var interval = trackingIntervalMs
        while (running) {
            // Keep the CPU awake until the next fix. Without this, a locked phone can doze
            // through the delay below and leave multi-minute gaps in the trail. The timeout
            // is refreshed every cycle, so the lock expires on its own if the loop stops.
            wakeLock.acquire(interval + WAKE_LOCK_MARGIN_MS)
            try {
                val gpsOn = isGpsEnabled()
                val hasPerm = hasLocationPermission()
                val battery = batteryLevel()
                checkLocationSetting()

                // Fixes normally arrive through locationCallback. Only if none came in the last
                // interval (updates not running yet, or GPS just switched on) poll for one here.
                if (hasPerm && gpsOn) startContinuousUpdates() else stopContinuousUpdates()
                var stored = false
                if (hasPerm && gpsOn && System.currentTimeMillis() - lastStoredFixTime > interval + 5_000L) {
                    val polled = try {
                        val cts = CancellationTokenSource()
                        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                            ?: fused.lastLocation.await()
                    } catch (e: SecurityException) { null }
                    if (polled != null) stored = store(polled)
                }
                val loc = lastFix?.takeIf { gpsOn && System.currentTimeMillis() - lastStoredFixTime <= 2 * interval + 5_000L }

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

                // Save every fix locally first, then upload the queue oldest-first. With no
                // network the points stay queued with their real capture time and are uploaded
                // when it returns, so the history trail follows the actual route (no gaps).
                stored = stored || (loc != null && lastStoredFixTime > lastToastFixTime)
                lastToastFixTime = lastStoredFixTime
                val sync = LocationSync.flush(applicationContext)
                if (!sync.complete && sync.outcome != LocationSync.Outcome.NO_AUTH) {
                    LocationSync.scheduleWhenOnline(applicationContext)
                }

                when {
                    // No network: the latest fix was kept on the phone.
                    stored && sync.outcome == LocationSync.Outcome.OFFLINE_OR_ERROR ->
                        toast("📍 Location saved offline · ${sync.pending} queued")
                    // Network is back and the backlog went up.
                    lastPending > 0 && sync.synced > 0 ->
                        toast("✅ Uploaded ${sync.synced} offline location" + if (sync.synced == 1) "" else "s")
                }
                lastPending = sync.pending

                // Say why points are waiting: no network, or the server's per-org rate limit
                // (online, uploaded on a later cycle — nothing is lost either way).
                val queued = when {
                    sync.pending == 0 -> ""
                    sync.outcome == LocationSync.Outcome.THROTTLED -> " · ${sync.pending} waiting (server limit)"
                    else -> " · ${sync.pending} saved offline"
                }
                if (loc != null) {
                    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastStoredFixTime))
                    startForeground(1, notif("Last fix ±${String.format(Locale.US, "%.0f", loc.accuracy)} m @ $ts$queued"))
                } else if (!gpsOn) {
                    startForeground(1, notif(LOCATION_OFF_TEXT + queued))
                } else {
                    startForeground(1, notif("Not reporting: $status$queued"))
                }
                interval = trackingIntervalMs
            } catch (e: Exception) {
                interval = (interval * 1.5).toLong().coerceAtMost(MAX_BACKOFF_MS)
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


