package com.lagfix.fstrim

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Menangkap force close → Download/LagFix/ (MediaStore API 29+, fallback app files dir). */
object CrashLogger {
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(app, t, e) }
            prev?.uncaughtException(t, e)
        }
    }

    private fun write(ctx: Context, t: Thread, e: Throwable) {
        val name = "LagFix_crash_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
        val body = "Thread: ${t.name}\nSDK: ${Build.VERSION.SDK_INT}\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n\n" + Log.getStackTraceString(e)
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LagFix")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
        } else {
            val dir = ctx.getExternalFilesDir(null) ?: return
            File(dir, name).writeText(body)
        }
    }
}
