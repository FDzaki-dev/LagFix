package com.lagfix.fstrim

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrimResult(val ok: Boolean, val message: String, val durationMs: Long)

// v10 (tab Pengaturan + tema): pilihan tema manual, disimpan sbg String biar aman kalau urutan
// enum berubah nanti. Default SYSTEM = perilaku lama (ikut sistem), non-breaking utk user existing.
enum class ThemeMode { SYSTEM, LIGHT, DARK }

// v92 (detekt MagicNumber): angka bernama; nilai identik dgn literal sebelumnya.
private const val LEGACY_INTERVAL_DEFAULT_HOURS = 24L
private const val LEGACY_HOURS_TO_MINUTES = 60L
private const val LOG_MESSAGE_MAX_CHARS = 120
private const val LOG_MAX_LINES = 30

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
        get() = sp.getLong(
            "intervalMin",
            sp.getLong("interval", LEGACY_INTERVAL_DEFAULT_HOURS) * LEGACY_HOURS_TO_MINUTES
        )
        set(v) { sp.edit().putLong("intervalMin", v).apply() }

    // v118 (permintaan user): mode interval radikal (< 15 menit). Toggle bebas on/off kapan saja, tanpa
    // batasan. Default false = perilaku lama (interval minimal 15 menit, periodik WorkManager).
    var radicalInterval: Boolean
        get() = sp.getBoolean("radicalInterval", false)
        set(v) { sp.edit().putBoolean("radicalInterval", v).apply() }

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
        val msg = r.message.replace('\n', ' ').take(LOG_MESSAGE_MAX_CHARS)
        val lines = (listOf("$stamp $status [$trigger] ${r.durationMs}ms $msg") + log).take(LOG_MAX_LINES)
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

