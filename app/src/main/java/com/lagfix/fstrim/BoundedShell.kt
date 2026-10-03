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
        val finished = !timedOut && runCatching { p.waitFor(JOIN_GRACE_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        val exit = if (finished) runCatching { p.exitValue() }.getOrNull() else null
        return BoundedResult(exit, sink.toString(), timedOut)
    }
}
