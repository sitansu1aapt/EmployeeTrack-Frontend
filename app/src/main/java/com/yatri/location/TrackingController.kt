package com.yatri.location

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import android.app.NotificationChannel
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yatri.LocationService

/**
 * Background tracking follows duty: it runs while the employee is checked in and stops at
 * check-out or logout, so an on-duty employee can't forget to switch it on and nobody is
 * tracked off duty.
 *
 * Callers report the duty state they learn (check-in/out, dashboard refresh, boot); this
 * object starts/stops [LocationService] and remembers the last known state for the boot path.
 */
object TrackingController {
    private const val TAG = "TrackingController"

    // Same SharedPreferences file/key the dashboard switch already uses, so its UI stays in sync.
    private const val PREFS = "dashboard_prefs"
    private const val KEY_TRACKING = "background_tracking"
    private const val KEY_ON_DUTY = "tracking_last_known_on_duty"

    /** Not a blocker: the service still runs so it can report "location switched off". */
    const val LOCATION_OFF_MESSAGE = "Turn on phone Location (GPS) — tracking is paused"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isTrackingOn(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_TRACKING, false)

    fun lastKnownOnDuty(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ON_DUTY, false)

    /**
     * Record the duty state and start or stop tracking to match.
     * Returns a user-facing reason when on duty but tracking can't start (e.g. a missing
     * permission), or null when tracking matches duty.
     */
    fun onDutyStatusKnown(ctx: Context, onDuty: Boolean, fromBackground: Boolean = false): String? {
        prefs(ctx).edit().putBoolean(KEY_ON_DUTY, onDuty).apply()
        return if (onDuty) {
            start(ctx, fromBackground)
        } else {
            // Always stop: the service may be running even if the pref says otherwise
            // (e.g. it was started while location was off). Stopping an idle service is a no-op.
            stop(ctx)
            null
        }
    }

    /** Start tracking; returns why it couldn't start, or null on success. */
    fun start(ctx: Context, fromBackground: Boolean = false): String? {
        val reason = missingRequirement(ctx, fromBackground)
        // Location switched off still starts the service: it records the switch-off for the
        // admin and resumes on its own when location comes back. Anything else blocks.
        if (reason != null && reason != LOCATION_OFF_MESSAGE) {
            Log.w(TAG, "Tracking not started: $reason")
            prefs(ctx).edit().putBoolean(KEY_TRACKING, false).apply()
            return reason
        }
        return try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, LocationService::class.java))
            prefs(ctx).edit().putBoolean(KEY_TRACKING, true).apply()
            NotificationManagerCompat.from(ctx).cancel(RESUME_NOTIFICATION_ID)
            reason
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException when started from the background.
            Log.w(TAG, "Could not start location service", e)
            prefs(ctx).edit().putBoolean(KEY_TRACKING, false).apply()
            "Open the app to resume tracking"
        }
    }

    /** Stop tracking. Anything captured offline stays queued and uploads when online. */
    fun stop(ctx: Context) {
        ctx.stopService(Intent(ctx, LocationService::class.java))
        prefs(ctx).edit().putBoolean(KEY_TRACKING, false).apply()
        LocationSettingMonitor.reset(ctx)
        try {
            LocationSync.scheduleWhenOnline(ctx)
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule location sync", e)
        }
    }

    private const val RESUME_CHANNEL = "tracking_resume"
    private const val RESUME_NOTIFICATION_ID = 4101

    /**
     * Tracking couldn't restart in the background (after an app update or reboot without
     * "Allow all the time"). Tell the on-duty employee; opening the app resumes it.
     */
    fun showResumeNotification(ctx: Context) {
        val app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(RESUME_CHANNEL, "Tracking paused", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        val pending = PendingIntent.getActivity(
            app, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, RESUME_CHANNEL)
            .setSmallIcon(com.yatri.R.mipmap.ic_launcher)
            .setContentTitle("Location tracking paused")
            .setContentText("You're on duty. Tap to open Yatri and resume tracking.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "You're on duty. Tap to open Yatri and resume tracking. " +
                    "Set Location to \"Allow all the time\" so this doesn't happen again."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(app).notify(RESUME_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not post resume notification", e)
        }
    }

    /** Logout: stop tracking and forget the duty state so a reboot doesn't restart it. */
    fun onLogout(ctx: Context) {
        stop(ctx)
        prefs(ctx).edit().putBoolean(KEY_ON_DUTY, false).apply()
    }

    /**
     * What stops tracking from running, as a message for the employee, or null if nothing.
     * From the background (boot) Android also needs "Allow all the time" location access.
     */
    fun missingRequirement(ctx: Context, fromBackground: Boolean = false): String? {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return "Allow location permission to start tracking"
        }
        if (fromBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            return "Set location to \"Allow all the time\" so tracking can resume after a restart"
        }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locationOn = lm != null &&
            (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        if (!locationOn) return LOCATION_OFF_MESSAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            return "Allow notifications to start tracking"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = nm?.getNotificationChannel("loc_ch")
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return "Turn on Yatri location notifications to start tracking"
            }
        }
        return null
    }
}
