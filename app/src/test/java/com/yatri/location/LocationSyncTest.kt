package com.yatri.location

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.yatri.TokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

/**
 * Offline location queue: fixes captured with no network must be kept on the device and
 * uploaded later, oldest first, each with its original capture time — so the history
 * trail follows the real route instead of a straight line across the gap.
 *
 * Runs on the JVM (Robolectric) with an in-memory Room database and a fake backend.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocationSyncTest {

    private lateinit var context: Context
    private lateinit var db: LocationDatabase
    private lateinit var dao: PendingLocationDao
    private lateinit var server: MockWebServer

    /** Start of the simulated walk: two hours ago, one fix per minute after that. */
    private val walkStart = System.currentTimeMillis() - 2 * HOUR

    private data class Req(val path: String, val body: JSONObject, val auth: String?)
    private val received = mutableListOf<Req>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LocationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        LocationDatabase.setInstanceForTests(db)
        dao = db.pendingLocations()
        LocationSync.resetForTests()
        startServer()
        TokenStore.token = jwtFor(USER_A)
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        LocationSync.resetForTests()
        LocationDatabase.setInstanceForTests(null)
        db.close()
        TokenStore.token = null
    }

    // ------------------------------------------------------------------ capture

    @Test
    fun enqueue_storesFixWithItsOwnCaptureTimeAndTheLoggedInUser() = runBlocking {
        val captured = walkStart + 5 * MINUTE

        val stored = LocationSync.enqueue(context, fix(12.9436, 77.7404, captured), 76, true)

        assertTrue(stored)
        val rows = dao.oldestForUser(USER_A, 10)
        assertEquals(1, rows.size)
        assertEquals(captured, rows[0].capturedAt)
        assertEquals(USER_A, rows[0].userId)
        assertEquals(12.9436, rows[0].latitude, 1e-9)
        assertEquals(77.7404, rows[0].longitude, 1e-9)
        assertEquals(76, rows[0].batteryLevel)
    }

    @Test
    fun enqueue_withoutLoggedInUser_storesNothing() = runBlocking {
        TokenStore.token = null

        val stored = LocationSync.enqueue(context, fix(12.9, 77.7, walkStart), null, true)

        assertFalse(stored)
        assertEquals(0, dao.countForUser(USER_A))
    }

    // ------------------------------------------------------------------ the client's scenario

    @Test
    fun oneHourOffline_keepsAll60Points_thenUploadsThemInOrderWithOriginalTimes() = runBlocking {
        enqueueWalk(60)

        // No network: nothing is lost, nothing is deleted.
        server.shutdown()
        val offline = LocationSync.flush(context)
        assertEquals(LocationSync.Outcome.OFFLINE_OR_ERROR, offline.outcome)
        assertEquals(0, offline.synced)
        assertEquals(60, offline.pending)

        // Network is back.
        startServer()
        serve { path, body -> if (path == BATCH) saveAll(body) else notFound() }
        val online = LocationSync.flush(context)

        assertTrue(online.complete)
        assertEquals(60, online.synced)
        assertEquals(0, online.pending)

        val batches = received.filter { it.path == BATCH }
        assertEquals("60 points go up in batches of 50", 2, batches.size)
        batches.forEach { assertEquals("Bearer ${jwtFor(USER_A)}", it.auth) }

        val uploaded = batches.flatMap { it.body.getJSONArray("points").toList() }
        assertEquals(60, uploaded.size)
        uploaded.forEachIndexed { i, p ->
            // Each point keeps the minute it was actually captured — not the upload time.
            assertEquals(iso(walkStart + i * MINUTE), p.getString("timestamp"))
            assertEquals(latAt(i), p.getDouble("latitude"), 1e-9)
        }
        assertEquals("every point has a distinct time, so the trail isn't collapsed",
            60, uploaded.map { it.getString("timestamp") }.toSet().size)
    }

    // ------------------------------------------------------------------ fallback via /me/update

    @Test
    fun batchEndpointMissing_fallsBackToPerPointUpload_withOriginalTimes() = runBlocking {
        enqueueWalk(5)
        serve { path, _ -> if (path == UPDATE) created() else notFound() }

        val result = LocationSync.flush(context)

        assertTrue(result.complete)
        assertEquals(5, result.synced)
        val updates = received.filter { it.path == UPDATE }
        assertEquals(5, updates.size)
        updates.forEachIndexed { i, r ->
            assertEquals(iso(walkStart + i * MINUTE), r.body.getString("timestamp"))
            assertTrue(r.body.getString("clientId").isNotBlank())
        }
    }

    @Test
    fun throttledPoint_http200_staysQueued_andIsRetriedLaterWithItsOriginalTime() = runBlocking {
        enqueueWalk(5)
        val updateCalls = AtomicInteger()
        // Backend answers 201 when it stores a point and 200 when its rate limit skips it.
        serve { path, _ ->
            when {
                path != UPDATE -> notFound()
                updateCalls.incrementAndGet() <= 2 -> created()
                else -> throttled()
            }
        }

        val first = LocationSync.flush(context)
        assertEquals(LocationSync.Outcome.THROTTLED, first.outcome)
        assertEquals(2, first.synced)
        assertEquals(3, first.pending)

        received.clear()
        serve { path, _ -> if (path == UPDATE) created() else notFound() }
        val second = LocationSync.flush(context)

        assertTrue(second.complete)
        assertEquals(3, second.synced)
        val retried = received.filter { it.path == UPDATE }
        assertEquals(iso(walkStart + 2 * MINUTE), retried.first().body.getString("timestamp"))
    }

    @Test
    fun malformedPoint_http400_isDropped_soItCannotBlockTheQueue() = runBlocking {
        enqueueWalk(3)
        val updateCalls = AtomicInteger()
        serve { path, _ ->
            when {
                path != UPDATE -> notFound()
                updateCalls.incrementAndGet() == 1 -> MockResponse().setResponseCode(400)
                else -> created()
            }
        }

        val result = LocationSync.flush(context)

        assertTrue(result.complete)
        assertEquals(2, result.synced)
        assertEquals(0, result.pending)
    }

    // ------------------------------------------------------------------ failures keep data

    @Test
    fun serverError_keepsEveryPoint() = runBlocking {
        enqueueWalk(3)
        serve { _, _ -> MockResponse().setResponseCode(500) }

        val result = LocationSync.flush(context)

        assertEquals(LocationSync.Outcome.OFFLINE_OR_ERROR, result.outcome)
        assertEquals(0, result.synced)
        assertEquals(3, result.pending)
    }

    @Test
    fun rejectedPoints_areRemoved_butNotCountedAsSynced() = runBlocking {
        enqueueWalk(3)
        serve { path, body ->
            if (path != BATCH) return@serve notFound()
            val ids = body.getJSONArray("points").toList().map { it.getString("clientId") }
            batchResponse(saved = ids.take(2), rejected = ids.drop(2))
        }

        val result = LocationSync.flush(context)

        assertTrue(result.complete)
        assertEquals(2, result.synced)
        assertEquals(0, result.pending)
    }

    @Test
    fun unexpectedBatchResponse_doesNotLoopForever() = runBlocking {
        enqueueWalk(3)
        serve { path, _ ->
            if (path == BATCH) batchResponse(saved = listOf("not-in-this-batch")) else notFound()
        }

        val result = withTimeout(10_000) { LocationSync.flush(context) }

        assertEquals(LocationSync.Outcome.OFFLINE_OR_ERROR, result.outcome)
        assertEquals(3, result.pending)
        assertEquals(1, received.count { it.path == BATCH })
    }

    // ------------------------------------------------------------------ ownership + retention

    @Test
    fun pointsOfAnotherUser_areNeverUploadedWithTheCurrentUsersToken() = runBlocking {
        enqueueWalk(2)                         // captured while USER_A was logged in
        TokenStore.token = jwtFor(USER_B)
        LocationSync.enqueue(context, fix(13.0, 77.0, walkStart + 10 * MINUTE), 50, true)
        serve { path, body -> if (path == BATCH) saveAll(body) else notFound() }

        val result = LocationSync.flush(context)

        assertEquals(1, result.synced)
        assertEquals(0, dao.countForUser(USER_B))
        assertEquals("USER_A's points wait for USER_A", 2, dao.countForUser(USER_A))
        val uploaded = received.filter { it.path == BATCH }.flatMap { it.body.getJSONArray("points").toList() }
        assertEquals(1, uploaded.size)
        assertEquals(13.0, uploaded[0].getDouble("latitude"), 1e-9)
    }

    @Test
    fun pointsOlderThanSevenDays_arePruned() = runBlocking {
        val now = System.currentTimeMillis()
        LocationSync.enqueue(context, fix(10.0, 70.0, now - 8 * DAY), 50, true)
        LocationSync.enqueue(context, fix(12.9, 77.7, now - HOUR), 50, true)
        serve { path, body -> if (path == BATCH) saveAll(body) else notFound() }

        val result = LocationSync.flush(context)

        assertEquals(1, result.synced)
        val uploaded = received.filter { it.path == BATCH }.flatMap { it.body.getJSONArray("points").toList() }
        assertEquals(12.9, uploaded.single().getDouble("latitude"), 1e-9)
    }

    // ------------------------------------------------------------------ background worker

    @Test
    fun worker_succeedsWhenTheQueueDrains() = runBlocking {
        enqueueWalk(2)
        serve { path, body -> if (path == BATCH) saveAll(body) else notFound() }

        val result = TestListenableWorkerBuilder<LocationSyncWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, dao.countForUser(USER_A))
    }

    @Test
    fun worker_retriesWhileOffline() = runBlocking {
        enqueueWalk(2)
        server.shutdown()

        val result = TestListenableWorkerBuilder<LocationSyncWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(2, dao.countForUser(USER_A))
    }

    @Test
    fun worker_stopsWhenLoggedOut() = runBlocking {
        TokenStore.token = null

        val result = TestListenableWorkerBuilder<LocationSyncWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    // ------------------------------------------------------------------ helpers

    private fun startServer() {
        server = MockWebServer().apply { start() }
        LocationSync.baseUrl = server.url("/api/v1/").toString()
        serve { _, _ -> notFound() }
    }

    /** Route every request to [handler] and record it (path relative to /api/v1/). */
    private fun serve(handler: (path: String, body: JSONObject) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/api/v1/").substringBefore("?")
                val raw = request.body.copy().readUtf8()
                val body = if (raw.isBlank()) JSONObject() else JSONObject(raw)
                synchronized(received) { received += Req(path, body, request.getHeader("Authorization")) }
                return handler(path, body)
            }
        }
    }

    private suspend fun enqueueWalk(count: Int) {
        repeat(count) { i ->
            LocationSync.enqueue(context, fix(latAt(i), 77.7404 + i * 0.0001, walkStart + i * MINUTE), 80, true)
        }
    }

    private fun latAt(i: Int) = 12.9436 + i * 0.0001

    private fun fix(lat: Double, lng: Double, timeMillis: Long) = Location("gps").apply {
        latitude = lat
        longitude = lng
        time = timeMillis
        accuracy = 5f
    }

    private fun saveAll(body: JSONObject): MockResponse =
        batchResponse(saved = body.getJSONArray("points").toList().map { it.getString("clientId") })

    private fun batchResponse(saved: List<String>, rejected: List<String> = emptyList()) =
        MockResponse().setResponseCode(200).setBody(
            JSONObject()
                .put("status", "success")
                .put("data", JSONObject()
                    .put("savedClientIds", JSONArray(saved))
                    .put("rejectedClientIds", JSONArray(rejected)))
                .toString()
        )

    private fun created() = MockResponse().setResponseCode(201).setBody("""{"status":"success"}""")
    private fun throttled() = MockResponse().setResponseCode(200)
        .setBody("""{"status":"success","data":{"message":"not processed due to frequency limit"}}""")
    private fun notFound() = MockResponse().setResponseCode(404)

    private fun JSONArray.toList(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun iso(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(millis)

    /** Minimal unsigned JWT: the app only reads `userId` from the payload. */
    private fun jwtFor(userId: String): String {
        val enc = java.util.Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = enc.encodeToString("""{"userId":"$userId","roleName":"EMPLOYEE"}""".toByteArray())
        return "$header.$payload.test-signature"
    }

    private companion object {
        const val USER_A = "242"
        const val USER_B = "999"
        const val BATCH = "locations/me/batch"
        const val UPDATE = "locations/me/update"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
