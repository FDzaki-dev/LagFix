package com.lagfix.fstrim

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.edit

private const val JOURNAL_TAG = "CatchJournal"
private const val JOURNAL_PREFS = "lagfix_catch_journal"
private const val JOURNAL_KEY_ROWS = "rows"
private const val JOURNAL_MAX_ROWS = 12
private const val JOURNAL_SEPARATOR = "\n"
private const val JOURNAL_FIRST_MARK = " first="
private const val JOURNAL_UNKNOWN_IMPORTANCE = -1
private val JOURNAL_FIRST_REGEX = Regex("""\sfirst=(\d+)""")
private val JOURNAL_COUNT_REGEX = Regex("""\sn=(\d+)$""")

/**
 * v147 (diag X6855 18 Nov: proses hidup 20 jam, `ownLogcat` hanya 3 baris krn buffer logcat sudah tertimpa & Shizuku
 * NOT_RUNNING -> tak ada jejak KAPAN notifikasi persisten hilang): jurnal kecil di SharedPreferences (tahan mati
 * proses) berisi keadaan mentah tiap pengamatan `ProcessCatcher.capture`. Pengamatan berkeadaan SAMA dilipat jadi 1
 * baris (`first`/`last`/`n`), keadaan BERBEDA = baris baru; maksimal [JOURNAL_MAX_ROWS] baris terbaru. Hanya
 * merekam: tanpa timer/loop; ditulis dari Dispatchers.IO oleh pemanggil.
 */
internal object CatchJournal {

    /** Tak pernah melempar: kegagalan hanya dicatat ke logcat (jurnal tak boleh menjatuhkan pemanggil). */
    @Synchronized
    fun record(app: Context, key: String, nowMs: Long) {
        runCatching {
            val prefs = app.getSharedPreferences(JOURNAL_PREFS, Context.MODE_PRIVATE)
            val rows = splitRows(prefs.getString(JOURNAL_KEY_ROWS, null))
            val folded = foldObservation(rows, key, nowMs, JOURNAL_MAX_ROWS)
            prefs.edit { putString(JOURNAL_KEY_ROWS, folded.joinToString(JOURNAL_SEPARATOR)) }
        }.onFailure { Log.w(JOURNAL_TAG, "Jurnal pengamatan gagal ditulis", it) }
    }

    /** Baris jurnal apa adanya (tertua dulu); gagal baca -> `Result.failure` (exception apa adanya). */
    fun read(app: Context): Result<List<String>> = runCatching {
        splitRows(app.getSharedPreferences(JOURNAL_PREFS, Context.MODE_PRIVATE).getString(JOURNAL_KEY_ROWS, null))
    }
}

private fun splitRows(raw: String?): List<String> =
    raw.orEmpty().split(JOURNAL_SEPARATOR).filter { it.isNotEmpty() }

/** v147: kunci keadaan 1 pengamatan (nilai mentah; `null` = tak terbaca). Beda kunci = baris jurnal baru. */
internal fun observationKey(
    pid: Int,
    shown: Boolean,
    importance: Int,
    toggleOn: Boolean,
    restricted: Boolean?
): String = "pid=$pid shown=$shown importance=$importance toggleOn=$toggleOn bgRestricted=${restricted ?: "null"}"

/**
 * v147: lipat 1 pengamatan ke [rows] (tertua dulu). Kunci SAMA dgn baris terakhir -> baris itu diperbarui (`first`
 * tetap, `last` = [nowMs], `n` + 1); beda -> baris baru `n=1`. Hasil dipotong ke [maxRows] baris terbaru.
 */
internal fun foldObservation(rows: List<String>, key: String, nowMs: Long, maxRows: Int): List<String> {
    val last = rows.lastOrNull()
    val folded = if (last != null && last.startsWith("$key$JOURNAL_FIRST_MARK")) {
        val first = JOURNAL_FIRST_REGEX.find(last)?.groupValues?.get(1) ?: nowMs.toString()
        val count = (JOURNAL_COUNT_REGEX.find(last)?.groupValues?.get(1)?.toIntOrNull() ?: 0) + 1
        rows.dropLast(1) + "$key$JOURNAL_FIRST_MARK$first last=$nowMs n=$count"
    } else {
        rows + "$key$JOURNAL_FIRST_MARK$nowMs last=$nowMs n=1"
    }
    return folded.takeLast(maxRows)
}

/**
 * v147: baca SINKRON (di thread pemanggil, SEBELUM `ensureShowing`) importance proses ini & `isBackgroundRestricted`,
 * lalu susun kunci pengamatan. Tak pernah melempar: nilai yg gagal dibaca = -1 / `null` (tercetak apa adanya).
 */
internal fun readObservationKey(app: Context, pid: Int, shown: Boolean, toggleOn: Boolean): String {
    val importance = runCatching {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance
    }.getOrDefault(JOURNAL_UNKNOWN_IMPORTANCE)
    val restricted = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            app.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted
        } else {
            null
        }
    }.getOrNull()
    return observationKey(pid, shown, importance, toggleOn, restricted)
}

/**
 * v147: seksi `powerState` berkas `process_catch` — tanpa Shizuku (di X6855 18 Nov Shizuku NOT_RUNNING sehingga
 * bucket & pengecualian baterai tak terbaca): `isIgnoringBatteryOptimizations` & bucket App Standby milik app
 * sendiri (API 28+, tanpa izin). Nilai mentah; `null` = layanan sistem tak tersedia.
 */
internal fun readPowerState(app: Context): Result<List<String>> = runCatching {
    val ignoring = app.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(app.packageName)
    val bucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        app.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket?.let { "${standbyBucketName(it)} ($it)" }
    } else {
        null
    }
    listOf("ignoringBatteryOptimizations=${ignoring ?: "null"}", "standbyBucket=${bucket ?: "null"}")
}
