package com.yatri.location

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Notices when an on-duty employee switches the phone's Location setting off (and back on)
 * and queues a [DeviceEvent] for each change, so the admin gets a "location turned off"
 * alert with the real time — even when it happened with no network.
 *
 * The last reported state is kept in SharedPreferences so a service restart doesn't report
 * the same change twice; [reset] clears it when tracking stops (check-out / logout).
 */
object LocationSettingMonitor {
    private const val PREFS = "location_setting_monitor"
    private const val KEY_STATE = "last_state"
    private const val KEY_OFF_SINCE = "off_since"
    private const val ON = "on"
    private const val OFF = "off"

    private val mutex = Mutex()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Compare [locationOn] with the last reported state and queue an event if it changed.
     * Returns the queued event type, or null when nothing changed (or nobody is logged in).
     */
    suspend fun check(
        ctx: Context,
        locationOn: Boolean,
        latitude: Double?,
        longitude: Double?,
        now: Long = System.currentTimeMillis(),
    ): String? = mutex.withLock {
        val prefs = prefs(ctx)
        val last = prefs.getString(KEY_STATE, null)
        val current = if (locationOn) ON else OFF
        if (last == current) return@withLock null

        // Duty started with location on: nothing to report, just remember the state.
        if (last == null && locationOn) {
            prefs.edit().putString(KEY_STATE, ON).apply()
            return@withLock null
        }

        val type: String
        val offSince: Long?
        if (locationOn) {
            type = DeviceEvent.TYPE_LOCATION_ON
            offSince = prefs.getLong(KEY_OFF_SINCE, 0L).takeIf { it > 0 }
        } else {
            type = DeviceEvent.TYPE_LOCATION_OFF
            offSince = null
        }
        if (!LocationSync.enqueueEvent(ctx, type, now, latitude, longitude, offSince)) return@withLock null

        prefs.edit().apply {
            putString(KEY_STATE, current)
            if (locationOn) remove(KEY_OFF_SINCE) else putLong(KEY_OFF_SINCE, now)
        }.apply()
        type
    }

    fun reset(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
