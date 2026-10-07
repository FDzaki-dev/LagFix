package com.lagfix.fstrim

import java.io.StringWriter
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Hasil [BoundedShell.run]: [exit] null bila dihentikan/tak sempat selesai, [output] bisa parsial. */
internal class BoundedResult(val exit: Int?, val output: String, val timedOut: Boolean)

/**
 * v98 (laporan user: tombol "Ambil logcat sistem" terus menampilkan "Mengambil…"): `FstrimExecutor.sh`
 * membaca keluaran sampai EOF TANPA batas waktu, jadi satu perintah dumpsys/logcat yang macet menahan
 * seluruh snapshot selamanya. Di sini keluaran dibaca di thread daemon terpisah dan pemanggil hanya
 * menunggu sampai [run] `timeoutMs`; lewat itu proses dihentikan dan keluaran parsial dikembalikan.
 * Blocking — panggil hanya dari Dispatchers.IO. Proses dibuat oleh [start] (di produksi: jalur Shizuku).
 */
internal object BoundedShell {
    private const val JOIN_GRACE_MS = 2_000L
    private const val EXIT_POLL_MS = 50L

    fun run(timeoutMs: Long, start: () -> Process): BoundedResult {
        val p = start()
        // StringWriter berbasis StringBuffer (thread-safe): aman dibaca walau reader masih berjalan.
        val sink = StringWriter()
        val reader = thread(isDaemon = true, name = "lagfix-shell-reader") {
            runCatching { p.inputStream.bufferedReader().use { it.copyTo(sink) } }
        }
        reader.join(timeoutMs)
        val timedOut = reader.isAlive
        if (timedOut) {
            runCatching { p.destroy() } // menutup pipa -> reader berhenti
            reader.join(JOIN_GRACE_MS)
        }
        val exit = if (timedOut) null else waitExitCode(p)
        return BoundedResult(exit, sink.toString(), timedOut)
    }

    /**
     * v135 (data X6850: `exit=null` + `timedOut=false` di 3 seksi Shizuku; `ownLogcat` lokal `exit=0`):
     * `Process.waitFor(timeout)` bawaan hanya menangkap `IllegalThreadStateException`; DIDUGA `exitValue()` proses
     * Shizuku (lewat binder) melempar jenis lain selagi proses belum dituai -> exit hilang (dugaan, bukan bukti).
     * Di sini `exitValue()` dipoll sampai [JOIN_GRACE_MS] dgn menelan SEMUA galat per percobaan.
     */
    private fun waitExitCode(p: Process): Int? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(JOIN_GRACE_MS)
        var code = readExit(p)
        while (code == null && System.nanoTime() < deadline && sleepQuietly()) {
            code = readExit(p)
        }
        return code
    }

    private fun readExit(p: Process): Int? = runCatching { p.exitValue() }.getOrNull()

    private fun sleepQuietly(): Boolean = runCatching { Thread.sleep(EXIT_POLL_MS) }.isSuccess
}
