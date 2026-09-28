package com.lagfix.fstrim

import android.app.Application

class LagFixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        PersistentTrimService.startIfEnabled(this) // v40: hidupkan lagi notifikasi setelah proses dibunuh OS
    }
}
