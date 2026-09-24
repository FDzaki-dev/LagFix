package com.lagfix.fstrim

import android.app.Application

class LagFixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
