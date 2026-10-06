package com.lagfix.fstrim

private const val OWN_LOGCAT_TIMEOUT_MS = 5_000L
private const val OWN_LOGCAT_MAX_LINES = 200
private const val OWN_LOGCAT_RAW_TAIL = 5
private const val OWN_LOGCAT_KEYWORD = "LIFECYCLE"

/**
 * v127 (laporan user: "gak ada langkah nyata"; v125/v126 tak bisa menunjuk jalur pemanggil `startForeground` &
 * pemicu lahir proses): baca logcat MILIK PID SENDIRI (`logcat -d --pid`; app boleh membaca log UID-nya sendiri,
 * tanpa Shizuku) dan ambil baris `LIFECYCLE` apa adanya, urutan waktu asli. Baris itu ditulis komponen app sendiri
 * (`LagFixApp`->`startIfEnabled`, `onCreate`/`onStartCommand`, widget, tile, `ensureShowing`, `ProcessCatcher`).
 * Blocking, batas waktu lewat [BoundedShell] — panggil hanya dari Dispatchers.IO. Gagal -> exception apa adanya.
 */
internal fun readOwnLogcat(pid: Int): Result<List<String>> = runCatching {
    val r = BoundedShell.run(OWN_LOGCAT_TIMEOUT_MS) {
        ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=$pid").redirectErrorStream(true).start()
    }
    ownLogcatItems(r.output, r.exit, r.timedOut)
}

/**
 * v127: baris pertama = angka mentah (exit, timedOut, total baris, baris terambil); sisanya baris logcat bermuatan
 * `LIFECYCLE` (maks 200 terbaru). Tak ada satu pun -> 5 baris terakhir keluaran mentah (mis. pesan galat logcat).
 */
internal fun ownLogcatItems(output: String, exit: Int?, timedOut: Boolean): List<String> {
    val all = output.lines().filter { it.isNotBlank() }
    val kept = all.filter { it.contains(OWN_LOGCAT_KEYWORD) }.takeLast(OWN_LOGCAT_MAX_LINES)
    val meta = "exit=$exit timedOut=$timedOut totalLines=${all.size} keyword=$OWN_LOGCAT_KEYWORD " +
        "keptLines=${kept.size}"
    return listOf(meta) + kept.ifEmpty { all.takeLast(OWN_LOGCAT_RAW_TAIL) }
}

/** v127: 1 baris logcat penanda broadcast widget (komponen pertama sesudah `Application.onCreate` pada proses baru). */
internal fun widgetLifecycleLine(action: String?, pid: Int, procAgeMs: Long): String =
    "LIFECYCLE widget onReceive action=$action pid=$pid procAge=${procAgeMs}ms"
