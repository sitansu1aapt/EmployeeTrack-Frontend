package com.yatri.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

object Analytics {
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private val istTz = java.util.TimeZone.getTimeZone("Asia/Kolkata")

    fun init(context: Context) {
        // Firebase automatically initializes; obtain instance and enable collection
        firebaseAnalytics = Firebase.analytics.apply {
            setAnalyticsCollectionEnabled(true)
        }
    }

    fun setUser(userId: String?, role: String? = null) {
        val fa = firebaseAnalytics ?: return
        if (!userId.isNullOrBlank()) fa.setUserId(userId)
        if (!role.isNullOrBlank()) fa.setUserProperty("role", role)
    }

    fun setUserProperty(key: String, value: String?) {
        val fa = firebaseAnalytics ?: return
        if (!key.isBlank() && !value.isNullOrBlank()) {
            fa.setUserProperty(key, value)
        }
    }

    fun logScreen(screenName: String, screenClass: String) {
        val fa = firebaseAnalytics ?: return
        val b = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
        }
        fa.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, b)
    }

    fun nowParams(): Map<String, Any> {
        val now = System.currentTimeMillis()
        val utcIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale("en", "IN")).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(now))
        val ist = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale("en", "IN")).apply {
            timeZone = istTz
        }.format(java.util.Date(now))
        return mapOf(
            "event_time_ms" to now,
            "event_time_utc" to utcIso,
            "event_time_ist" to ist
        )
    }

    fun log(event: String, params: Map<String, Any?> = emptyMap()) {
        val fa = firebaseAnalytics ?: return
        val bundle = Bundle()
        for ((k, v) in params) {
            when (v) {
                null -> Unit
                is String -> bundle.putString(k, v)
                is Boolean -> bundle.putString(k, v.toString())
                is Int -> bundle.putInt(k, v)
                is Long -> bundle.putLong(k, v)
                is Double -> bundle.putDouble(k, v)
                is Float -> bundle.putDouble(k, v.toDouble())
                else -> bundle.putString(k, v.toString())
            }
        }
        fa.logEvent(event, bundle)
    }
}


