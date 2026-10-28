package com.yatri.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Uploads queued location points once a network is available. Scheduled with a
 * CONNECTED constraint, so it also runs after the tracking service has been killed.
 */
class LocationSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = LocationSync.flush(applicationContext)
        return when {
            result.complete -> Result.success()
            // Logged out: nothing we can upload until the user signs in again.
            result.outcome == LocationSync.Outcome.NO_AUTH -> Result.success()
            // Keep retrying with backoff; the tracking loop and app start also reschedule.
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 20
    }
}
