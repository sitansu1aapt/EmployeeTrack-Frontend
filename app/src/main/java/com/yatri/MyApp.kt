package com.yatri

import android.app.Application

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.context = applicationContext
    }
}


