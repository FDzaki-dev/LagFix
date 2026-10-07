package com.lagfix.fstrim

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val LOG_BUFFER_TAG = "LogBuffer"
private const val LOG_BUFFER_TIMEOUT_MS = 8_000L
private const val LOG_BUFFER_MAX_CHARS = 300

// Satu percobaan sukses per proses; gagal/Shizuku belum READY -> klaim dilepas, titik catcher berikutnya mencoba lagi.
private val logBufferClaimed = AtomicBoolean(false)

/**
 * v132 (data v129-v131: baris logcat kill yg dibutuhkan `KillCatcher` sudah tertimpa saat dibaca, buffer bawaan
 * ±30 dtk (snapshot v84); buffer 16M baru ada bila user menekan "Ambil logcat sistem"): perbesar buffer logcat
 * ke 16M OTOMATIS lewat Shizuku (perintah sama dgn tombol snapshot, [LogcatSnapshot.BUFFER_RESIZE_CMD]).
 * Mengubah setelan sistem SEMENTARA (kembali normal saat reboot). Tak memicu apa pun di sisi app: bukan
 * timer/loop; dipanggil dari titik `ProcessCatcher`, 1 coroutine IO, 1 percobaan sukses per proses.
 */
internal fun ensureLogBufferEnlarged(app: Context) {
    if (!logBufferClaimed.compareAndSet(false, true)) return
    CoroutineScope(Dispatchers.IO).launch {
        val done = runCatching { enlargeLogBuffer(app) }
            .onFailure { Log.w(LOG_BUFFER_TAG, "Perbesar buffer logcat gagal", it) }
            .getOrDefault(false)
        if (!done) logBufferClaimed.set(false)
    }
}

/** Blocking — hanya dari Dispatchers.IO. True bila perintah selesai (exit 0, tanpa timeout). */
private fun enlargeLogBuffer(app: Context): Boolean {
    val state = FstrimExecutor.state(app)
    if (state == ShizukuState.READY) {
        val cmd = LogcatSnapshot.BUFFER_RESIZE_CMD
        val r = BoundedShell.run(LOG_BUFFER_TIMEOUT_MS) { LogcatSnapshot.shizukuProcess(cmd) }
        Log.i(LOG_BUFFER_TAG, logBufferResultLine(r.output, r.exit, r.timedOut))
        return r.exit == 0 && !r.timedOut
    }
    Log.i(LOG_BUFFER_TAG, logBufferSkippedLine(state.name))
    return false
}

/** v132: angka mentah (exit, timedOut) + keluaran shell apa adanya (baris digabung ` | `, maks 300 karakter). */
internal fun logBufferResultLine(output: String, exit: Int?, timedOut: Boolean): String {
    val raw = output.lines().filter { it.isNotBlank() }.joinToString(" | ").take(LOG_BUFFER_MAX_CHARS)
    return "LIFECYCLE logBuffer resize exit=$exit timedOut=$timedOut output=$raw"
}

/** v132: Shizuku belum READY -> perintah tak dijalankan; hanya nama status mentah. */
internal fun logBufferSkippedLine(shizukuState: String): String =
    "LIFECYCLE logBuffer resize skipped shizuku=$shizukuState"
