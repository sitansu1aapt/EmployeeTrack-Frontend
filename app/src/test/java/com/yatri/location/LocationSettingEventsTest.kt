package com.yatri.location

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.yatri.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * An on-duty employee switching the phone's Location off must reach the admin as a
 * LOCATION_OFF alert with the real time and place — even if it happened with no network —
 * followed by LOCATION_ON with how long it was off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocationSettingEventsTest {

    private lateinit var context: Context
    private lateinit var db: LocationDatabase
    private lateinit var events: DeviceEventDao
    private lateinit var server: MockWebServer

    private data class Req(val path: String, val body: JSONObject)
    private val received = mutableListOf<Req>()

    private val offAt = System.currentTimeMillis() - 40 * MINUTE
    private val onAt = offAt + 25 * MINUTE

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LocationDatabase::class.java).allowMainThreadQueries().build()
        LocationDatabase.setInstanceForTests(db)
        events = db.deviceEvents()
        LocationSync.resetForTests()
        LocationSettingMonitor.reset(context)
        server = MockWebServer().apply { start() }
        LocationSync.baseUrl = server.url("/api/v1/").toString()
        serve { _, _ -> MockResponse().setResponseCode(404) }
        TokenStore.token = jwtFor(USER)
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        LocationSync.resetForTests()
        LocationSettingMonitor.reset(context)
        LocationDatabase.setInstanceForTests(null)
        db.close()
        TokenStore.token = null
    }

    @Test
    fun switchedOffAndOnWhileOffline_bothReachServerLaterWithRealTimesAndPlace() = runBlocking {
        LocationSettingMonitor.check(context, locationOn = true, latitude = null, longitude = null, now = offAt - MINUTE)
        server.shutdown() // no network during the walk

        assertEquals(DeviceEvent.TYPE_LOCATION_OFF,
            LocationSettingMonitor.check(context, false, 20.2961, 85.8245, now = offAt))
        assertEquals(DeviceEvent.TYPE_LOCATION_ON,
            LocationSettingMonitor.check(context, true, 20.2961, 85.8245, now = onAt))
        assertFalse(LocationSync.flush(context).complete)
        assertEquals(2, events.countForUser(USER))

        // Network back.
        server = MockWebServer().apply { start() }
        LocationSync.baseUrl = server.url("/api/v1/").toString()
        serve { path, body ->
            if (path == EVENTS) saveAll(body) else MockResponse().setResponseCode(404)
        }
        val result = LocationSync.flush(context)

        assertTrue(result.complete)
        assertEquals(0, events.countForUser(USER))
        val sent = received.filter { it.path == EVENTS }.flatMap { it.body.getJSONArray("events").toList() }
        assertEquals(listOf("LOCATION_OFF", "LOCATION_ON"), sent.map { it.getString("type") })
        assertEquals(iso(offAt), sent[0].getString("timestamp"))
        assertEquals(20.2961, sent[0].getDouble("latitude"), 1e-9)
        assertEquals(iso(onAt), sent[1].getString("timestamp"))
        assertEquals(iso(offAt), sent[1].getString("offSince"))
    }

    @Test
    fun dutyStartingWithLocationOn_reportsNothing_andRepeatedChecksDontDuplicate() = runBlocking {
        assertNull(LocationSettingMonitor.check(context, true, null, null))
        assertNull(LocationSettingMonitor.check(context, true, null, null))
        assertEquals(DeviceEvent.TYPE_LOCATION_OFF, LocationSettingMonitor.check(context, false, null, null))
        // Receiver and loop both notice the same switch-off: only one event.
        assertNull(LocationSettingMonitor.check(context, false, null, null))
        assertEquals(1, events.countForUser(USER))
    }

    @Test
    fun dutyStartingWithLocationOff_isReported() = runBlocking {
        assertEquals(DeviceEvent.TYPE_LOCATION_OFF, LocationSettingMonitor.check(context, false, null, null))
        assertEquals(1, events.countForUser(USER))
    }

    @Test
    fun checkOut_resetsState_soNextDutyStartsClean() = runBlocking {
        LocationSettingMonitor.check(context, false, null, null)
        LocationSettingMonitor.reset(context)
        assertNull(LocationSettingMonitor.check(context, true, null, null))
        assertEquals(1, events.countForUser(USER)) // only the original switch-off
    }

    @Test
    fun loggedOut_nothingRecorded() = runBlocking {
        TokenStore.token = null
        assertNull(LocationSettingMonitor.check(context, false, null, null))
        assertEquals(0, events.countForUser(USER))
    }

    @Test
    fun backendWithoutEventsEndpoint_keepsEvents_andPointsStillUpload() = runBlocking {
        serve { path, _ ->
            when (path) {
                "locations/me/update" -> MockResponse().setResponseCode(201).setBody("{}")
                else -> MockResponse().setResponseCode(404)
            }
        }
        LocationSettingMonitor.check(context, false, null, null)
        LocationSync.enqueue(context, fix(offAt), 80, true)

        val result = LocationSync.flush(context)

        assertEquals(1, result.synced)
        assertTrue("404 must not cause endless retries", result.complete)
        assertEquals(1, events.countForUser(USER))
    }

    @Test
    fun serverErrorOnEvents_keepsThem_andAsksForRetry() = runBlocking {
        serve { _, _ -> MockResponse().setResponseCode(500) }
        LocationSettingMonitor.check(context, false, null, null)

        val result = LocationSync.flush(context)

        assertFalse(result.complete)
        assertEquals(1, events.countForUser(USER))
    }

    @Test
    fun upgradeFromV1_keepsPointsQueuedOffline() {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val v1 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `pending_locations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clientId` TEXT NOT NULL, `userId` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracy` REAL NOT NULL, `batteryLevel` INTEGER, `isGpsEnabled` INTEGER NOT NULL, `capturedAt` INTEGER NOT NULL)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_locations_clientId` ON `pending_locations` (`clientId`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_locations_userId_capturedAt` ON `pending_locations` (`userId`, `capturedAt`)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build()
        )
        v1.writableDatabase.execSQL(
            "INSERT INTO pending_locations (clientId, userId, latitude, longitude, accuracy, batteryLevel, isGpsEnabled, capturedAt) " +
                "VALUES ('c1', '$USER', 20.1, 85.1, 5.0, 80, 1, $offAt)"
        )
        v1.close()

        val v2 = Room.databaseBuilder(context, LocationDatabase::class.java, name)
            .addMigrations(LocationDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        runBlocking {
            assertEquals(1, v2.pendingLocations().countForUser(USER))
            v2.deviceEvents().insert(DeviceEvent(clientId = "e1", userId = USER, type = "LOCATION_OFF",
                occurredAt = offAt, latitude = null, longitude = null, offSince = null))
            assertEquals(1, v2.deviceEvents().countForUser(USER))
        }
        v2.close()
        context.deleteDatabase(name)
    }

    // ------------------------------------------------------------------ helpers

    private fun serve(handler: (path: String, body: JSONObject) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/api/v1/").substringBefore("?")
                val raw = request.body.copy().readUtf8()
                val body = if (raw.isBlank()) JSONObject() else JSONObject(raw)
                synchronized(received) { received += Req(path, body) }
                return handler(path, body)
            }
        }
    }

    private fun saveAll(body: JSONObject): MockResponse {
        val ids = body.getJSONArray("events").toList().map { it.getString("clientId") }
        return MockResponse().setResponseCode(201).setBody(
            JSONObject().put("data", JSONObject().put("savedClientIds", JSONArray(ids))).toString()
        )
    }

    private fun fix(timeMillis: Long) = Location("gps").apply {
        latitude = 20.29; longitude = 85.82; time = timeMillis; accuracy = 5f
    }

    private fun JSONArray.toList(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun iso(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(millis)

    private fun jwtFor(userId: String): String {
        val enc = java.util.Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = enc.encodeToString("""{"userId":"$userId"}""".toByteArray())
        return "$header.$payload.sig"
    }

    private companion object {
        const val USER = "238"
        const val EVENTS = "locations/me/events"
        const val MINUTE = 60_000L
    }
}
