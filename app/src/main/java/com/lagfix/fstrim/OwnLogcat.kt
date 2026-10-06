package com.lagfix.fstrim

import android.content.Context

private const val OWN_LOGCAT_TIMEOUT_MS = 5_000L
private const val OWN_LOGCAT_MAX_LINES = 200
private const val OWN_LOGCAT_RAW_TAIL = 5
private const val OWN_LOGCAT_KEYWORD = "LIFECYCLE"
private const val OWN_LOGCAT_SCOPE_PID = "pid"
private const val OWN_LOGCAT_SCOPE_UID = "uid"
private const val SHZ_LOGCAT_TIMEOUT_MS = 8_000L
private const val SHZ_LOGCAT_BEFORE_MS = 1_000L
private const val SHZ_LOGCAT_AFTER_MS = 4_000L
private const val SHZ_LOGCAT_MAX_AGE_MS = 60_000L
private const val SHZ_LOGCAT_MAX_LINES = 150
private const val SHZ_LOGCAT_MAX_CHARS = 300
private const val SHZ_LOGCAT_RAW_TAIL = 5

/**
 * v127 (laporan user: "gak ada langkah nyata"): baca logcat MILIK APP SENDIRI (`logcat -d`; app boleh membaca log
 * UID-nya sendiri, tanpa Shizuku) dan ambil baris `LIFECYCLE` apa adanya, urutan waktu asli. Baris itu ditulis
 * komponen app sendiri (`LagFixApp`->`startIfEnabled`, servis, widget, tile, `ensureShowing`, `ProcessCatcher`).
 * v128: percobaan 1 memakai `--pid` (hasil device v127: 0 baris); bila keluarannya kosong, percobaan 2 tanpa `--pid`
 * (semua proses UID ini). Kedua meta dicetak. Blocking, batas waktu lewat [BoundedShell] — hanya dari Dispatchers.IO.
 */
internal fun readOwnLogcat(pid: Int): Result<List<String>> = runCatching {
    val first = runOwnLogcat("--pid=$pid")
    val head = ownLogcatItems(OWN_LOGCAT_SCOPE_PID, first.output, first.exit, first.timedOut)
    if (first.output.isNotBlank()) {
        head
    } else {
        val second = runOwnLogcat(null)
        head + ownLogcatItems(OWN_LOGCAT_SCOPE_UID, second.output, second.exit, second.timedOut)
    }
}

private fun runOwnLogcat(pidArg: String?): BoundedResult = BoundedShell.run(OWN_LOGCAT_TIMEOUT_MS) {
    ProcessBuilder(listOfNotNull("logcat", "-d", "-v", "threadtime", pidArg)).redirectErrorStream(true).start()
}

/**
 * v127: baris pertama = angka mentah (scope, exit, timedOut, total baris, baris terambil); sisanya baris logcat
 * bermuatan `LIFECYCLE` (maks 200 terbaru). Tak ada satu pun -> 5 baris terakhir keluaran mentah (mis. galat logcat).
 */
internal fun ownLogcatItems(scope: String, output: String, exit: Int?, timedOut: Boolean): List<String> {
    val all = output.lines().filter { it.isNotBlank() }
    val kept = all.filter { it.contains(OWN_LOGCAT_KEYWORD) }.takeLast(OWN_LOGCAT_MAX_LINES)
    val meta = "scope=$scope exit=$exit timedOut=$timedOut totalLines=${all.size} " +
        "keyword=$OWN_LOGCAT_KEYWORD keptLines=${kept.size}"
    return listOf(meta) + kept.ifEmpty { all.takeLast(OWN_LOGCAT_RAW_TAIL) }
}

/** v127: 1 baris logcat penanda broadcast widget (komponen pertama sesudah `Application.onCreate` pada proses baru). */
internal fun widgetLifecycleLine(action: String?, pid: Int, procAgeMs: Long): String =
    "LIFECYCLE widget onReceive action=$action pid=$pid procAge=${procAgeMs}ms"

/**
 * v128 (hasil v127: `ownLogcat` kosong): awal jendela logcat sistem = lahir proses - 1 dtk; proses lama (warm) dibatasi
 * 60 dtk terakhir agar jendela tak melebar. Murni hitungan ([nowMs] & [procAgeMs] dari pemanggil).
 */
internal fun logcatWindowStartMs(nowMs: Long, procAgeMs: Long): Long =
    nowMs - minOf(procAgeMs, SHZ_LOGCAT_MAX_AGE_MS) - SHZ_LOGCAT_BEFORE_MS

/**
 * v128: logcat SISTEM (shell UID via Shizuku) sejak [startMs], disaring perintah `buildKillCommand` (nama app / pid /
 * kata kunci kill): baris ActivityManager soal servis app (mis. "Background started FGS", "startForeground() not
 * allowed"), `am_proc_start`, dst. Data mentah, tanpa tafsiran. Shizuku tak READY -> 1 baris `unavailable`.
 * Blocking — hanya dari Dispatchers.IO.
 */
internal fun readShizukuLogcat(app: Context, pid: Int, startMs: Long): Result<List<String>> = runCatching {
    val state = FstrimExecutor.state(app)
    if (state == ShizukuState.READY) {
        val from = logStamp(startMs)
        val to = logStamp(System.currentTimeMillis() + SHZ_LOGCAT_AFTER_MS)
        val cmd = buildKillCommand(startMs, pid)
        val r = BoundedShell.run(SHZ_LOGCAT_TIMEOUT_MS) { LogcatSnapshot.shizukuProcess(cmd) }
        shizukuLogcatItems(r.output, from, to, r.exit, r.timedOut)
    } else {
        listOf("unavailable: shizuku=${state.name}")
    }
}

/**
 * v128: baris pertama = angka mentah (jendela, jumlah baris, exit, timedOut); sisanya baris logcat berstempel di dalam
 * [from]..[to] (maks 150, tiap baris dipotong 300 karakter). Tak ada satu pun -> 5 baris terakhir keluaran mentah.
 */
internal fun shizukuLogcatItems(output: String, from: String, to: String, exit: Int?, timedOut: Boolean): List<String> {
    val kept = output.lineSequence().filter { inWindow(it, from, to) }.take(SHZ_LOGCAT_MAX_LINES)
        .map { it.take(SHZ_LOGCAT_MAX_CHARS) }.toList()
    val meta = "shizuku=READY window=$from..$to lines=${kept.size} exit=$exit timedOut=$timedOut"
    val raw = output.lines().filter { it.isNotBlank() }.takeLast(SHZ_LOGCAT_RAW_TAIL)
        .map { it.take(SHZ_LOGCAT_MAX_CHARS) }
    return listOf(meta) + kept.ifEmpty { raw }
}
