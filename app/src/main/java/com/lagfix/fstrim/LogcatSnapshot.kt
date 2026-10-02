package com.lagfix.fstrim

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TRUNC_MARK = "(…terpotong, hanya bagian akhir)\n"

/** Ambil [max] karakter TERAKHIR (yang terbaru paling penting); tandai bila terpotong. */
internal fun capTail(s: String, max: Int): String =
    if (s.length <= max) s else TRUNC_MARK + s.takeLast(max)

internal fun countNonBlankLines(s: String): Int = s.lineSequence().count { it.isNotBlank() }

/** Offset zona waktu sbg "UTC+08:00" / "UTC-03:30" / "UTC+00:00" dari milidetik (pembanding jam `dumpsys mount`). */
internal fun formatUtcOffset(offsetMs: Int): String {
    val totalMin = offsetMs / 60_000
    val sign = if (totalMin < 0) '-' else '+'
    val abs = if (totalMin < 0) -totalMin else totalMin
    return "UTC%c%02d:%02d".format(Locale.US, sign, abs / 60, abs % 60)
}

/** Ringkasan hasil [LogcatSnapshot.capture] untuk umpan balik UI. */
data class SnapshotSummary(val shizukuReady: Boolean, val fstrimLines: Int, val appLines: Int, val exitRecords: Int)

/**
 * v84 (perintah user: "perluas catch logcat aplikasi" supaya bukti investigasi yg mandek tak lagi
 * bergantung pada adb/PC): 1 file `LagFix_diag_logcat_*.txt` di Documents/LagFix berisi
 * (a) alasan proses LagFix terakhir mati (`ApplicationExitInfo`, Android 11+, tanpa Shizuku),
 * (b) jejak fstrim OLEH SISTEM mis. saat boot (logcat, `last-fstrim`, `dumpsys mount`),
 * (c) baris logcat terkait LagFix (LIFECYCLE servis persisten, kill, FGS stop).
 * Bagian (b)/(c) butuh Shizuku READY (shell UID boleh baca buffer logcat — INFERENSI, belum
 * diuji di HP ini; kegagalan tertulis di file). Semua perintah = konstanta, 0 input user; output
 * dibatasi `tail`/`head` di shell + `capTail` di sini (tak memuat log penuh ke memori).
 * Buffer logcat hilang saat reboot & berputar -> makin cepat diambil makin lengkap.
 * Snapshot HANYA dibuat saat user menekan tombol (bukan otomatis) -> tak membanjiri Documents.
 * BLOCKING — panggil hanya dari Dispatchers.IO.
 */
object LogcatSnapshot {
    private const val BUFFERS = "-b main -b system -b events -b crash"

    // v85 (akar masalah dari snapshot v84): "fstrim" ikut cocok dgn NAMA PAKET `com.lagfix.fstrim`, jadi
    // 80 baris hasil grep terisi derau modul OEM; "lagfix" juga memenuhi 500 baris. Perbaikan: tahap 1
    // = cakupan, tahap 2 = buang baris yg mengandung nama paket (fstrim) / hanya simpan baris peristiwa (app).
    internal const val PATTERN_FSTRIM = "fstrim|idle maint|disk maintenance"
    internal const val PATTERN_FSTRIM_EXCLUDE = "lagfix"
    internal const val PATTERN_APP_SCOPE = "lagfix|PersistentTrimService|TranManualCleanMgr|FGS stop"
    internal const val PATTERN_APP_EVENT =
        "PersistentTrimService|LIFECYCLE|TranManualCleanMgr|FGS stop|Killing|am_kill|kill proc|SIGKILL|died|" +
            "am_proc|Start proc|ForegroundService|JobScheduler|SystemJobService|WorkManager"

    fun capture(ctx: Context): Result<SnapshotSummary> = runCatching {
        val app = ctx.applicationContext
        val ready = FstrimExecutor.state(app) == ShizukuState.READY
        val sb = StringBuilder()
        val now = System.currentTimeMillis()
        sb.append("Snapshot logcat LagFix (v85)\n")
        sb.append("Waktu: ${stamp(now)} (zona app ${formatUtcOffset(TimeZone.getDefault().getOffset(now))})\n")
        sb.append("Perkiraan boot terakhir: ${stamp(now - SystemClock.elapsedRealtime())}\n")
        sb.append("Shizuku: ${if (ready) "READY" else "BELUM SIAP (bagian logcat sistem dilewati)"}\n")
        if (ready) sb.append("fstrim_mandatory_interval (sistem): ${describeBootTrim(BootTrimSetting.read())}\n")

        sb.append("\n== Alasan proses LagFix terakhir mati (ApplicationExitInfo) ==\n")
        val (exitText, exitCount) = if (Build.VERSION.SDK_INT >= 30) exitReasons(app)
        else "(butuh Android 11+)" to 0
        sb.append(exitText).append('\n')

        var fstrimLines = 0
        var appLines = 0
        if (ready) {
            sb.append("\n== Ukuran buffer logcat & entri tertua buffer system ==\n")
            sb.append(section("logcat -g 2>&1 | head -n 8").first).append('\n')
            sb.append(section("logcat -d -v threadtime -b system 2>&1 | head -n 3").first).append('\n')

            sb.append("\n== Jejak fstrim oleh sistem (logcat; boot = 'Running fstrim idle maintenance') ==\n")
            val f = section("logcat -d -v threadtime $BUFFERS 2>&1 | grep -Ei '$PATTERN_FSTRIM' | grep -vi '$PATTERN_FSTRIM_EXCLUDE' | tail -n 80")
            fstrimLines = f.second
            sb.append(capTail(f.first, 12_000)).append('\n')

            sb.append("\n== Berkas penanda fstrim terakhir (akses shell belum terbukti) ==\n")
            sb.append(capTail(section("ls -l /data/system/last-fstrim 2>&1").first, 2_000)).append('\n')

            sb.append("\n== dumpsys mount (baris maint/trim, format belum terbukti) ==\n")
            sb.append(capTail(section("dumpsys mount 2>&1 | grep -Ei 'maint|trim' | head -n 10").first, 4_000)).append('\n')

            sb.append("\n== Logcat terkait LagFix (500 baris terakhir; LIFECYCLE, kill, FGS stop) ==\n")
            val a = section("logcat -d -v threadtime $BUFFERS 2>&1 | grep -Ei '$PATTERN_APP_SCOPE' | grep -Ei '$PATTERN_APP_EVENT' | tail -n 500")
            appLines = a.second
            sb.append(capTail(a.first, 60_000)).append('\n')
        }

        CrashLogger.writeDiagnostic(app, "logcat", sb.toString()).getOrThrow()
        SnapshotSummary(ready, fstrimLines, appLines, exitCount)
    }

    private fun stamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    /** Jalankan [cmd] lewat Shizuku; (teks, jumlah baris non-kosong). Tak pernah melempar. */
    private fun section(cmd: String): Pair<String, Int> = try {
        val (code, out) = FstrimExecutor.sh(cmd)
        val t = out.trim()
        if (t.isEmpty()) "(kosong, exit=$code)" to 0 else t to countNonBlankLines(t)
    } catch (e: Throwable) {
        "(gagal: ${e.javaClass.simpleName}: ${e.message})".take(200) to 0
    }

    @RequiresApi(30)
    private fun exitReasons(ctx: Context): Pair<String, Int> {
        return try {
            val am = ctx.getSystemService(ActivityManager::class.java)
                ?: return "(ActivityManager tidak tersedia)" to 0
            val list = am.getHistoricalProcessExitReasons(null, 0, 10)
            if (list.isEmpty()) {
                "(kosong)" to 0
            } else {
                list.joinToString("\n") { i ->
                    "${stamp(i.timestamp)} pid=${i.pid} reason=${exitReasonName(i.reason)}(${i.reason}) " +
                        "status=${i.status} importance=${i.importance} desc=${i.description ?: "-"}"
                } to list.size
            }
        } catch (e: Throwable) {
            "(gagal: ${e.javaClass.simpleName}: ${e.message})".take(200) to 0
        }
    }

    /** Nama alasan; angka mentah selalu ikut dicetak di pemanggil, jadi nama tak dikenal aman. */
    private fun exitReasonName(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        else -> "reason#$r"
    }
}
