package com.yatri.location

import android.content.Context
import android.location.Location
import android.util.Base64
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yatri.AppConfig
import com.yatri.PrefKeys
import com.yatri.TokenStore
import com.yatri.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Offline-first location upload ("outbox").
 *
 * Capture always writes to [PendingLocation]; [flush] uploads oldest-first and deletes a
 * point only after the server confirms it was stored. Each point carries its original fix
 * time, so after a no-network period the history path shows where the user actually went.
 */
object LocationSync {
    private const val TAG = "LocationSync"
    private const val WORK_NAME = "location-sync"
    private const val BATCH_SIZE = 50
    private const val MAX_FALLBACK_PER_FLUSH = 300
    private const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_QUEUE_ROWS = 20_000
    /** After a 404 on the batch endpoint, use the per-point fallback for this long. */
    private const val BATCH_RECHECK_MILLIS = 30L * 60 * 1000

    private val mutex = Mutex()
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val json = "application/json".toMediaType()

    @Volatile private var batchUnavailableUntil = 0L
    @Volatile private var eventsUnavailableUntil = 0L

    /** Server base URL; tests point this at a fake server. */
    @VisibleForTesting
    internal var baseUrl: String = AppConfig.API_BASE_URL

    @VisibleForTesting
    internal fun resetForTests() {
        batchUnavailableUntil = 0L
        eventsUnavailableUntil = 0L
        baseUrl = AppConfig.API_BASE_URL
    }

    enum class Outcome { COMPLETE, OFFLINE_OR_ERROR, THROTTLED, NO_AUTH }

    data class FlushResult(val synced: Int, val pending: Int, val outcome: Outcome) {
        val complete: Boolean get() = outcome == Outcome.COMPLETE
    }

    /** Store a fix locally. Returns false if there is no logged-in user to attribute it to. */
    suspend fun enqueue(context: Context, loc: Location, batteryLevel: Int?, isGpsEnabled: Boolean): Boolean {
        val userId = userIdFromToken(currentToken(context)) ?: return false
        LocationDatabase.get(context).pendingLocations().insert(
            PendingLocation(
                clientId = UUID.randomUUID().toString(),
                userId = userId,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                batteryLevel = batteryLevel,
                isGpsEnabled = isGpsEnabled,
                capturedAt = if (loc.time > 0) loc.time else System.currentTimeMillis(),
            )
        )
        return true
    }

    /** Store a Location-setting change (see [DeviceEvent]). Returns false if nobody is logged in. */
    suspend fun enqueueEvent(
        context: Context,
        type: String,
        occurredAt: Long,
        latitude: Double?,
        longitude: Double?,
        offSince: Long? = null,
    ): Boolean {
        val userId = userIdFromToken(currentToken(context)) ?: return false
        LocationDatabase.get(context).deviceEvents().insert(
            DeviceEvent(
                clientId = UUID.randomUUID().toString(),
                userId = userId,
                type = type,
                occurredAt = occurredAt,
                latitude = latitude,
                longitude = longitude,
                offSince = offSince,
            )
        )
        return true
    }

    suspend fun pendingCount(context: Context): Int {
        val userId = userIdFromToken(currentToken(context)) ?: return 0
        return LocationDatabase.get(context).pendingLocations().countForUser(userId)
    }

    /**
     * Upload queued points for the current user, oldest first. Safe to call often: it
     * returns quickly when offline, and concurrent callers are serialised.
     */
    suspend fun flush(context: Context): FlushResult = mutex.withLock { flushLocked(context) }

    private suspend fun flushLocked(context: Context): FlushResult {
        val dao = LocationDatabase.get(context).pendingLocations()
        val eventDao = LocationDatabase.get(context).deviceEvents()
        dao.deleteOlderThan(System.currentTimeMillis() - RETENTION_MILLIS)
        dao.trimTo(MAX_QUEUE_ROWS)
        eventDao.deleteOlderThan(System.currentTimeMillis() - RETENTION_MILLIS)

        val token = currentToken(context)
        val userId = userIdFromToken(token)
        if (token == null || userId == null) return FlushResult(0, 0, Outcome.NO_AUTH)

        // Location on/off events are few and time-sensitive for the admin, so they go first.
        val eventsNeedRetry = flushEvents(token, userId, eventDao)

        var synced = 0
        var outcome = Outcome.COMPLETE
        var fallbackSent = 0

        while (true) {
            val batch = dao.oldestForUser(userId, BATCH_SIZE)
            if (batch.isEmpty()) break

            val batchResult = if (System.currentTimeMillis() >= batchUnavailableUntil) {
                postBatch(token, batch)
            } else {
                BatchResult.Unsupported
            }

            when (batchResult) {
                is BatchResult.Saved -> {
                    // Only ack ids that were actually in this batch; otherwise the same batch
                    // would be fetched again and the loop could spin.
                    val inBatch = batch.mapTo(HashSet()) { it.clientId }
                    val saved = batchResult.saved.filter { it in inBatch }
                    val rejected = batchResult.rejected.filter { it in inBatch }
                    if (saved.isEmpty() && rejected.isEmpty()) { outcome = Outcome.OFFLINE_OR_ERROR; break }
                    dao.deleteByClientIds(saved + rejected)
                    synced += saved.size
                }
                BatchResult.Unsupported -> {
                    // Batch endpoint not deployed yet: replay through /me/update, one point at a time.
                    val r = sendOneByOne(token, batch) { savedIds -> dao.deleteByClientIds(savedIds) }
                    synced += r.saved
                    fallbackSent += r.saved
                    if (r.stop != null) { outcome = r.stop; break }
                    if (fallbackSent >= MAX_FALLBACK_PER_FLUSH) { outcome = Outcome.THROTTLED; break }
                }
                BatchResult.Failed -> { outcome = Outcome.OFFLINE_OR_ERROR; break }
            }
        }

        val pending = dao.countForUser(userId)
        if (synced > 0 || pending > 0) Log.d(TAG, "flush: synced=$synced pending=$pending outcome=$outcome")
        val finalOutcome = when {
            pending > 0 -> outcome
            eventsNeedRetry -> Outcome.OFFLINE_OR_ERROR
            else -> Outcome.COMPLETE
        }
        return FlushResult(synced, pending, finalOutcome)
    }

    /**
     * POST locations/me/events → { data: { savedClientIds, rejectedClientIds } }.
     * Returns true when events are still queued and worth retrying soon (offline or server
     * error). A 404 (backend not updated yet) keeps them queued but doesn't trigger retries.
     */
    private suspend fun flushEvents(token: String, userId: String, dao: DeviceEventDao): Boolean {
        if (System.currentTimeMillis() < eventsUnavailableUntil) return false
        while (true) {
            val batch = dao.oldestForUser(userId, BATCH_SIZE)
            if (batch.isEmpty()) return false
            val body = JSONObject().put("events", JSONArray().apply { batch.forEach { put(toJson(it)) } })
            val done: List<String> = try {
                http.newCall(request(token, "locations/me/events", body)).execute().use { resp ->
                    Log.i("LOCATION_API", "📥 [LocationSync Events] Response code=${resp.code} for ${batch.size} events")
                    when {
                        resp.code == 404 -> {
                            eventsUnavailableUntil = System.currentTimeMillis() + BATCH_RECHECK_MILLIS
                            return false
                        }
                        resp.isSuccessful -> {
                            val raw = resp.body?.string()
                            val inBatch = batch.mapTo(HashSet()) { it.clientId }
                            (parseIds(raw, "savedClientIds", "saved_client_ids") +
                                parseIds(raw, "rejectedClientIds", "rejected_client_ids")).filter { it in inBatch }
                        }
                        else -> return true
                    }
                }
            } catch (e: IOException) {
                return true
            }
            if (done.isEmpty()) return true
            dao.deleteByClientIds(done)
        }
    }

    /** Ask WorkManager to flush as soon as a network is available (survives the service being killed). */
    fun scheduleWhenOnline(context: Context) {
        val request = OneTimeWorkRequestBuilder<LocationSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    // ---------------------------------------------------------------- upload

    private sealed class BatchResult {
        /** [saved]: stored (or already stored). [rejected]: permanently refused, e.g. invalid or too old. */
        data class Saved(val saved: List<String>, val rejected: List<String>) : BatchResult()
        object Unsupported : BatchResult()
        object Failed : BatchResult()
    }

    /** POST locations/me/batch → { data: { savedClientIds: [...], rejectedClientIds: [...] } }. */
    private fun postBatch(token: String, batch: List<PendingLocation>): BatchResult {
        val points = JSONArray().apply { batch.forEach { put(toJson(it)) } }
        val body = JSONObject().put("points", points)
        val url = baseUrl + "locations/me/batch"
        Log.i("LOCATION_API", "🌐 [LocationSync Batch] Server: $baseUrl | Type: locations/me/batch | URL: $url | Points: ${batch.size}")
        return try {
            http.newCall(request(token, "locations/me/batch", body)).execute().use { resp ->
                Log.i("LOCATION_API", "📥 [LocationSync Batch] Response code=${resp.code} for $url")
                when {
                    resp.code == 404 -> {
                        batchUnavailableUntil = System.currentTimeMillis() + BATCH_RECHECK_MILLIS
                        Log.i(TAG, "Batch endpoint not available; using per-point fallback")
                        BatchResult.Unsupported
                    }
                    resp.isSuccessful -> {
                        val raw = resp.body?.string()
                        BatchResult.Saved(
                            saved = parseIds(raw, "savedClientIds", "saved_client_ids"),
                            rejected = parseIds(raw, "rejectedClientIds", "rejected_client_ids"),
                        )
                    }
                    else -> {
                        Log.w(TAG, "Batch upload failed: HTTP ${resp.code}")
                        BatchResult.Failed
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("LOCATION_API", "❌ [LocationSync Batch] Failed to connect to $url: ${e.message}")
            BatchResult.Failed // offline or timeout: keep everything queued
        }
    }

    private data class OneByOneResult(val saved: Int, val stop: Outcome?)

    /**
     * Fallback through the existing endpoint. It answers 201 when the point is stored and
     * 200 ("not processed due to frequency limit") when throttled — only 201 counts as saved,
     * so a throttled point stays queued and is retried on the next flush.
     */
    private suspend fun sendOneByOne(
        token: String,
        batch: List<PendingLocation>,
        onSaved: suspend (List<String>) -> Unit,
    ): OneByOneResult {
        var saved = 0
        for (point in batch) {
            val url = baseUrl + "locations/me/update"
            Log.i("LOCATION_API", "🌐 [LocationSync OneByOne] Server: $baseUrl | Type: locations/me/update | URL: $url | Lat=${point.latitude}, Lng=${point.longitude}, Acc=${point.accuracy}m")
            val code = try {
                http.newCall(request(token, "locations/me/update", toJson(point))).execute().use { 
                    val respCode = it.code
                    Log.i("LOCATION_API", "📥 [LocationSync OneByOne] Response code=$respCode for $url")
                    respCode
                }
            } catch (e: IOException) {
                Log.e("LOCATION_API", "❌ [LocationSync OneByOne] Failed to connect to $url: ${e.message}")
                return OneByOneResult(saved, Outcome.OFFLINE_OR_ERROR)
            }
            when (code) {
                201 -> { onSaved(listOf(point.clientId)); saved++ }
                200 -> return OneByOneResult(saved, Outcome.THROTTLED)
                400 -> onSaved(listOf(point.clientId)) // malformed point can never succeed; drop it
                else -> {
                    Log.w(TAG, "Fallback upload stopped: HTTP $code")
                    return OneByOneResult(saved, Outcome.OFFLINE_OR_ERROR)
                }
            }
        }
        return OneByOneResult(saved, null)
    }

    private fun request(token: String, path: String, body: JSONObject): Request =
        Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(json))
            .build()

    private fun toJson(p: PendingLocation): JSONObject = JSONObject().apply {
        put("clientId", p.clientId)
        put("latitude", p.latitude)
        put("longitude", p.longitude)
        put("accuracy", p.accuracy.toDouble())
        put("timestamp", isoUtc(p.capturedAt))
        put("isGpsEnabled", p.isGpsEnabled)
        p.batteryLevel?.let { put("batteryLevel", it) }
    }

    private fun toJson(e: DeviceEvent): JSONObject = JSONObject().apply {
        put("clientId", e.clientId)
        put("type", e.type)
        put("timestamp", isoUtc(e.occurredAt))
        e.latitude?.let { put("latitude", it) }
        e.longitude?.let { put("longitude", it) }
        e.offSince?.let { put("offSince", isoUtc(it)) }
    }

    private fun parseIds(raw: String?, key: String, altKey: String): List<String> = try {
        val root = JSONObject(raw ?: "{}")
        val data = root.optJSONObject("data") ?: root
        val arr = data.optJSONArray(key) ?: data.optJSONArray(altKey) ?: JSONArray()
        List(arr.length()) { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    // ---------------------------------------------------------------- auth helpers

    /** In-memory token, or the one persisted at login (the process may have been restarted). */
    private suspend fun currentToken(context: Context): String? {
        TokenStore.token?.takeIf { it.isNotBlank() }?.let { return it }
        val stored = try {
            context.applicationContext.dataStore.data.first()[PrefKeys.AUTH_TOKEN]
        } catch (e: Exception) {
            null
        }
        if (!stored.isNullOrBlank()) TokenStore.token = stored
        return stored?.takeIf { it.isNotBlank() }
    }

    /** The backend JWT carries `userId` in its payload. */
    private fun userIdFromToken(token: String?): String? {
        val payload = token?.split(".")?.getOrNull(1) ?: return null
        return try {
            val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            JSONObject(decoded).optString("userId").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(millis)
}
