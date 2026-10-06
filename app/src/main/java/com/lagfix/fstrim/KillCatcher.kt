package com.lagfix.fstrim

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val KILL_TAG = "KillCatcher"
private const val KILL_DIAG_TAG = "kill_context"
private const val KILL_PREFS = "lagfix_kill_catcher"
private const val KILL_KEY_LAST_TS = "last_investigated_ts"

// Sama dgn `LogcatSnapshot.BUFFERS` (private di sana). Buffer "kernel" SENGAJA tak dipakai: nama buffer tak dikenal
// membuat seluruh perintah logcat gagal.
private const val KILL_BUFFERS = "-b main -b system -b events -b crash"
private const val KILL_PATTERN_BASE =
    "lagfix|persistenttrimservice|lmkd|killing|am_kill|am_proc_died|sigkill|signal 9|force.?stop"
private const val KILL_WINDOW_BEFORE_MS = 20_000L
private const val KILL_WINDOW_AFTER_MS = 5_000L
private const val KILL_CMD_TIMEOUT_MS = 25_000L
private const val KILL_SHELL_MAX_LINES = 300
private const val KILL_MAX_LINES = 150
private const val KILL_MAX_LINE_CHARS = 300
private const val KILL_MAX_RECORDS = 16
private const val KILL_MAX_DEATHS = 3
private const val KILL_MAX_AGE_MS = 43_200_000L
private const val KILL_STAMP_LEN = 18
private const val KILL_MILLIS_PER_SECOND = 1_000L
private const val KILL_MILLIS_DIGITS = 3
private val KILL_STAMP_REGEX = Regex("""\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}""")

/** v124: 1 kematian proses `SIGNALED` (SIGKILL) dari `ApplicationExitInfo`. */
internal data class KillDeath(val timestampMs: Long, val pid: Int)

/**
 * v124 (perintah user: "lanjut kesitu" = bongkar PELAKU kill): proses di-SIGKILL tanpa `am_kill` framework
 * (bukti v95) — app TIDAK bisa tahu pengirim sinyalnya, tapi log sistem pada detik kill kadang menyebut siapa
 * (daemon OEM / lmkd / "Killing ..."). Catcher ini menyalin baris logcat di sekitar tiap kematian `SIGNALED`
 * baru ke berkas `diag_kill_context`: jendela 20 dtk sebelum s/d 5 dtk sesudah waktu kematian, hanya baris yg
 * mengandung nama app, pid, atau kata kunci kill. Data MENTAH, tanpa tafsiran.
 *
 * Syarat: Shizuku READY (shell UID boleh baca logcat — terbukti v84). Tak READY -> hanya 1 baris logcat,
 * tanpa berkas & kematian TIDAK dianggap sudah diperiksa. Tiap kematian diperiksa SEKALI (timestamp terakhir
 * disimpan di SharedPreferences). Dipanggil dari `ProcessCatcher` (coroutine IO, sesudah berkas utamanya).
 * Efektivitas bergantung pada buffer logcat: v86 memperbesarnya ke 16M (sementara, sampai reboot) setiap kali
 * tombol "Ambil logcat sistem" ditekan; kill yg barisnya sudah tertimpa tak bisa dibongkar.
 */
internal object KillCatcher {

    /** Blocking (shell) — panggil dari Dispatchers.IO. Tak pernah melempar. */
    fun capture(app: Context) {
        runCatching { captureUnsafe(app) }.onFailure { Log.w(KILL_TAG, "KillCatcher gagal", it) }
    }

    private fun captureUnsafe(app: Context) {
        val prefs = app.getSharedPreferences(KILL_PREFS, Context.MODE_PRIVATE)
        val lastTs = prefs.getLong(KILL_KEY_LAST_TS, 0L)
        val deaths = selectNewDeaths(readSignaledDeaths(app), lastTs, System.currentTimeMillis())
        if (deaths.isEmpty()) return
        val state = FstrimExecutor.state(app)
        if (state != ShizukuState.READY) {
            Log.i(KILL_TAG, "LIFECYCLE kill context dilewati: shizuku=${state.name} newDeaths=${deaths.size}")
            return
        }
        prefs.edit().putLong(KILL_KEY_LAST_TS, deaths.maxOf { it.timestampMs }).apply()
        val lines = deaths.flatMap { d ->
            runCatching { investigate(d) }.getOrElse {
                listOf("death timestamp=${d.timestampMs} pid=${d.pid} read exception = $it")
            }
        }
        CrashLogger.logDiagnostic(app, KILL_DIAG_TAG, formatKillContext(deaths.size, lines))
    }

    private fun readSignaledDeaths(app: Context): List<KillDeath> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            app.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(null, 0, KILL_MAX_RECORDS).orEmpty()
                .filter { it.reason == ApplicationExitInfo.REASON_SIGNALED }
                .map { KillDeath(it.timestamp, it.pid) }
        } else {
            emptyList()
        }

    private fun investigate(d: KillDeath): List<String> {
        val startMs = d.timestampMs - KILL_WINDOW_BEFORE_MS
        val from = logStamp(startMs)
        val to = logStamp(d.timestampMs + KILL_WINDOW_AFTER_MS)
        val cmd = buildKillCommand(startMs, d.pid)
        val r = BoundedShell.run(KILL_CMD_TIMEOUT_MS) { LogcatSnapshot.shizukuProcess(cmd) }
        val kept = r.output.lineSequence().filter { inWindow(it, from, to) }.take(KILL_MAX_LINES).toList()
        val head = "death timestamp=${d.timestampMs} pid=${d.pid} window=$from..$to lines=${kept.size} " +
            "exit=${r.exit} timedOut=${r.timedOut}"
        return listOf(head) + kept.map { "  ${it.take(KILL_MAX_LINE_CHARS)}" }
    }
}

/**
 * v124: kematian `SIGNALED` yg BELUM pernah diperiksa (timestamp > [lastTs]) dan masih dlm 12 jam terakhir
 * (log lebih tua pasti sudah tertimpa), terbaru dulu, maksimal 3.
 */
internal fun selectNewDeaths(all: List<KillDeath>, lastTs: Long, nowMs: Long): List<KillDeath> =
    all.filter { it.timestampMs > lastTs && nowMs - it.timestampMs <= KILL_MAX_AGE_MS }
        .sortedByDescending { it.timestampMs }
        .take(KILL_MAX_DEATHS)

/** v124: stempel logcat `MM-dd HH:mm:ss.SSS` zona perangkat (sama dgn kolom waktu `logcat -v threadtime`). */
internal fun logStamp(ms: Long): String = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(ms))

/**
 * v124: perintah shell (konstanta + 2 angka, 0 input user): logcat sejak [startMs] (format epoch `-t sss.mmm`),
 * disaring `grep -Ei` di sisi shell (nama app / kata kunci kill / pid) & dibatasi 300 baris -> keluaran kecil.
 */
internal fun buildKillCommand(startMs: Long, pid: Int): String {
    val millis = (startMs % KILL_MILLIS_PER_SECOND).toString().padStart(KILL_MILLIS_DIGITS, '0')
    val since = "${startMs / KILL_MILLIS_PER_SECOND}.$millis"
    val pattern = "$KILL_PATTERN_BASE|(^|[^0-9])$pid([^0-9]|$)"
    return "logcat -d -v threadtime $KILL_BUFFERS -t '$since' 2>&1 | " +
        "grep -Ei '$pattern' | head -n $KILL_SHELL_MAX_LINES"
}

/** v124: baris logcat berstempel `MM-dd HH:mm:ss.SSS` yg jatuh di [from]..[to] (perbandingan string, lebar tetap). */
internal fun inWindow(line: String, from: String, to: String): Boolean {
    val ts = line.take(KILL_STAMP_LEN)
    return KILL_STAMP_REGEX.matches(ts) && ts >= from && ts <= to
}

/** v124 (prinsip user: log = DATA MENTAH): isi berkas `diag_kill_context`. */
internal fun formatKillContext(newDeaths: Int, lines: List<String>): String =
    (listOf("kill_context shizuku=READY newDeaths=$newDeaths") + lines).joinToString("\n")
