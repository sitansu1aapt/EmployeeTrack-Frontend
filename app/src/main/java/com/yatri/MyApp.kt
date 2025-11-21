package com.yatri

import android.app.Application
import com.yatri.analytics.Analytics
import android.os.Build

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.context = applicationContext
        
        // Initialize Sleep Alert Manager
        SleepAlertManager.initialize(this)
        // Initialize Analytics
        Analytics.init(this)
        // Set global user properties
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) { "unknown" }
        Analytics.setUserProperty("app_version", versionName)
        Analytics.setUserProperty("device_model", Build.MODEL ?: "Android")
        Analytics.setUserProperty("os_version", Build.VERSION.RELEASE ?: "")
    }
}



