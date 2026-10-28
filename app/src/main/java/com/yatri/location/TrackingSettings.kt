package com.yatri.location

import android.content.Context
import android.content.SharedPreferences

/**
 * How often a location is captured and sent while on duty. Chosen in Profile and read live by
 * [com.yatri.LocationService], so a change applies to the running tracking without a restart.
 */
object TrackingSettings {
    const val PREFS = "tracking_settings"
    const val KEY_INTERVAL_SECONDS = "interval_seconds"
    val OPTIONS_SECONDS = listOf(10, 15, 30, 60)
    const val DEFAULT_SECONDS = 30

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun intervalSeconds(ctx: Context): Int =
        prefs(ctx).getInt(KEY_INTERVAL_SECONDS, DEFAULT_SECONDS).takeIf { it in OPTIONS_SECONDS } ?: DEFAULT_SECONDS

    fun intervalMillis(ctx: Context): Long = intervalSeconds(ctx) * 1000L

    fun setIntervalSeconds(ctx: Context, seconds: Int) {
        require(seconds in OPTIONS_SECONDS) { "Unsupported interval: $seconds" }
        prefs(ctx).edit().putInt(KEY_INTERVAL_SECONDS, seconds).apply()
    }
}
