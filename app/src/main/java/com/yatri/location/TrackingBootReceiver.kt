package com.yatri.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Resumes duty tracking after the phone restarts or the app is updated, if the employee
 * was on duty. Needs location "Allow all the time"; otherwise tracking resumes the next
 * time the app is opened.
 */
class TrackingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // Upload anything captured offline before the restart.
        runCatching { LocationSync.scheduleWhenOnline(context) }
        if (!TrackingController.lastKnownOnDuty(context)) return
        val blocked = TrackingController.start(context, fromBackground = true)
        Log.i("TrackingBootReceiver", "Resume after ${intent.action}: ${blocked ?: "tracking started"}")
        // Don't let an on-duty employee go untracked silently: ask them to open the app.
        if (blocked != null && blocked != TrackingController.LOCATION_OFF_MESSAGE) {
            TrackingController.showResumeNotification(context)
        }
    }
}
