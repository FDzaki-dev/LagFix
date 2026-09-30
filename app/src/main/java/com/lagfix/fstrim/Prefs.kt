package com.lagfix.fstrim

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrimResult(val ok: Boolean, val message: String, val durationMs: Long)

// v10 (tab Pengaturan + tema): pilihan tema manual, disimpan sbg String biar aman kalau urutan
// enum berubah nanti. Default SYSTEM = perilaku lama (ikut sistem), non-breaking utk user existing.
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("lagfix", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(sp.getString("themeMode", ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        set(v) { sp.edit().putString("themeMode", v.name).apply() }

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    // v44: interval kini dalam MENIT (mendukung nilai kustom dari user). Kunci lama "interval"
    // (jam, dipakai v1-v43) TETAP dibaca sbg fallback -> user existing tak kehilangan pilihannya,
    // 0 migrasi data. Nilai baru cuma ditulis ke kunci "intervalMin".
    var intervalMinutes: Long
        get() = sp.getLong("intervalMin", sp.getLong("interval", 24L) * 60L)
        set(v) { sp.edit().putLong("intervalMin", v).apply() }

    // v27 (fitur opsional, pilihan eksplisit user — lihat SettingsTab): toggle foreground service
    // "keep-alive" (PersistentTrimService). Default false, non-breaking utk user existing yg belum
    // pernah lihat/pilih opsi ini.
    var persistentServiceEnabled: Boolean
        get() = sp.getBoolean("persistentService", false)
        set(v) { sp.edit().putBoolean("persistentService", v).apply() }

    val lastRunMs: Long get() = sp.getLong("lastRun", 0L)
    val lastOk: Boolean get() = sp.getBoolean("lastOk", false)

    val log: List<String>
        get() = (sp.getString("log", "") ?: "").split("\n").filter { it.isNotBlank() }

    // v38 (permintaan user: tampilkan run ini dipicu oleh apa — Manual/Otomatis): parameter
    // `trigger` WAJIB diisi eksplisit di semua call site (sengaja tanpa default value, biar tak
    // ada yang kelewat diam-diam — compiler yang memaksa). Perubahan format baris log CUMA satu:
    // token "[trigger]" disisipkan di antara status & durasi; stamp/status/durasi/pesan sisanya
    // identik persis. Baris LAMA (tanpa token) tetap terbaca di sisi UI (regex backward-compat,
    // 0 migrasi data).
    fun record(r: TrimResult, trigger: String) {
        val now = System.currentTimeMillis()
        val stamp = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(now))
        val status = if (r.ok) "OK" else "FAIL"
        val msg = r.message.replace('\n', ' ').take(120)
        val lines = (listOf("$stamp $status [$trigger] ${r.durationMs}ms $msg") + log).take(30)
        sp.edit()
            .putLong("lastRun", now)
            .putBoolean("lastOk", r.ok)
            .putString("log", lines.joinToString("\n"))
            .apply()
    }
}

/** v38: nilai `trigger` yang sah untuk [Prefs.record] — satu sumber kebenaran, bukan string liar. */
object TriggerSource {
    const val MANUAL = "Manual"     // user sendiri: tombol di app, widget, atau QS tile
    const val AUTO = "Otomatis"     // jadwal periodik WorkManager (interval)
}

