package com.lagfix.fstrim

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrimResult(val ok: Boolean, val message: String, val durationMs: Long)

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("lagfix", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    var intervalHours: Long
        get() = sp.getLong("interval", 24L)
        set(v) { sp.edit().putLong("interval", v).apply() }

    var requireCharging: Boolean
        get() = sp.getBoolean("charging", true)
        set(v) { sp.edit().putBoolean("charging", v).apply() }

    var requireIdle: Boolean
        get() = sp.getBoolean("idle", false)
        set(v) { sp.edit().putBoolean("idle", v).apply() }

    val lastRunMs: Long get() = sp.getLong("lastRun", 0L)
    val lastOk: Boolean get() = sp.getBoolean("lastOk", false)

    val log: List<String>
        get() = (sp.getString("log", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun record(r: TrimResult) {
        val now = System.currentTimeMillis()
        val stamp = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(now))
        val status = if (r.ok) "OK" else "FAIL"
        val msg = r.message.replace('\n', ' ').take(120)
        val lines = (listOf("$stamp $status ${r.durationMs}ms $msg") + log).take(30)
        sp.edit()
            .putLong("lastRun", now)
            .putBoolean("lastOk", r.ok)
            .putString("log", lines.joinToString("\n"))
            .apply()
    }
}
