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

/**
 * Menangkap force close → Download/LagFix/ (MediaStore API 29+, fallback app files dir).
 * v29: `write()` diekstrak jadi `writeToFile()` (0 perubahan perilaku jalur crash — body/nama file
 * crash tetap identik) + `logDiagnostic()` baru dipakai investigasi (mis. notifikasi service tak
 * tampil) — REUSE mekanisme yang sudah ada, BUKAN sistem logging baru (larangan eksplisit di
 * constitution: "jangan tambah sistem logging baru kecuali task memang membutuhkannya").
 */
object CrashLogger {
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val body = "Thread: ${t.name}\nSDK: ${Build.VERSION.SDK_INT}\n" +
                    "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n\n" + Log.getStackTraceString(e)
                writeToFile(app, "crash", body)
            }
            prev?.uncaughtException(t, e)
        }
    }

    /** v29: log evidence non-crash ke file (sama folder/mekanisme dgn crash), dipanggil manual dari
     * titik yg diinvestigasi (bukan otomatis tiap event — cegah membanjiri Download/LagFix/). */
    fun logDiagnostic(ctx: Context, tag: String, message: String) {
        runCatching {
            val body = "SDK: ${Build.VERSION.SDK_INT}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\n\n$message"
            writeToFile(ctx.applicationContext, "diag_$tag", body)
        }
    }

    private fun writeToFile(ctx: Context, prefix: String, body: String) {
        val name = "LagFix_${prefix}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
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
