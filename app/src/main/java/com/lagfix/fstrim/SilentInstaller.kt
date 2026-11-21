package com.lagfix.fstrim

import android.content.Context
import android.util.Log
import okio.buffer
import okio.sink
import okio.source
import rikka.shizuku.Shizuku
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Hasil [SilentInstaller.install]: [success] = terpasang; selain itu [detail] berisi alasan/keluaran mentah. */
internal class SilentInstallResult(val success: Boolean, val detail: String)

/**
 * v179 (adaptasi SONIX Batch 694, `UpdateManager.install`): bila Shizuku SIAP, APK update dipasang SENYAP lewat
 * `pm install` di proses Shizuku (tanpa dialog installer); APK dialirkan lewat stdin karena UID shell tak
 * bisa membaca cache privat aplikasi. Jalur ini tak pernah menjadi satu-satunya: tak siap/gagal -> pemanggil
 * ([MainViewModel.installUpdate]) memakai installer sistem seperti sebelumnya; keluaran mentah gagal dicatat ke log.
 * Pemasangan SUKSES membuat sistem mematikan proses aplikasi ini, jadi [install] bisa saja tak pernah kembali.
 * Blocking — WAJIB dari Dispatchers.IO.
 */
internal object SilentInstaller {
    private const val TAG = "SilentInstaller"

    /** Batas waktu `pm install` SESUDAH APK selesai dialirkan (pemasangan + verifikasi). */
    private const val INSTALL_TIMEOUT_MS = 120_000L

    /** v188: batas waktu pengaliran APK ke stdin `pm` (dulu tanpa batas: `pm` tak membaca stdin = menggantung). */
    private const val FEED_TIMEOUT_MS = 120_000L

    /** v188: tunggu penulis berhenti sesudah proses dihentikan (pipa tertutup -> penulisan gagal). */
    private const val FEED_JOIN_GRACE_MS = 2_000L

    /** Perintah shell pemasangan; `-S` = ukuran byte APK di stdin, `-r` = timpa versi terpasang. Murni. */
    internal fun installCommand(sizeBytes: Long): String = "pm install -r -S $sizeBytes 2>&1"

    /** Sukses = kode keluar 0 DAN keluaran memuat "Success", dan APK tak gagal dialirkan ([feedFailed]). Murni. */
    internal fun isInstallSuccess(exit: Int?, output: String, feedFailed: Boolean = false): Boolean =
        !feedFailed && exit == 0 && output.contains("Success", ignoreCase = true)

    /** Detail hasil: `exit=.. timedOut=..`, `feed=..` HANYA bila pengaliran APK gagal, lalu keluaran mentah. Murni. */
    internal fun installDetail(exit: Int?, timedOut: Boolean, output: String, feedFailure: String?): String {
        val feed = if (feedFailure == null) "" else "feed=$feedFailure\n"
        return "exit=${exit ?: "null"} timedOut=$timedOut\n$feed$output"
    }

    /** Alasan menolak bila paket APK bukan [ownPackage] (null = tak terbaca juga ditolak); cocok -> null. Murni. */
    internal fun packageMismatch(archivePackage: String?, ownPackage: String): String? =
        if (archivePackage == ownPackage) null else "Paket APK (${archivePackage ?: "null"}) bukan $ownPackage"

    // v93-style: catch Exception DISENGAJA — batas I/O-shell Shizuku (reflection, binder, proses, disk): kegagalan
    // apa pun harus jatuh ke installer sistem, bukan menggagalkan update.
    @Suppress("TooGenericExceptionCaught")
    fun install(context: Context, apk: File): SilentInstallResult {
        // Pengaman: Shizuku harus siap DAN hanya APK paket aplikasi ini yang boleh dipasang tanpa dialog konfirmasi.
        val refusal = if (FstrimExecutor.state(context) != ShizukuState.READY) {
            "Shizuku belum siap"
        } else {
            packageMismatch(archivePackage(context, apk), context.packageName)
        }
        if (refusal != null) return SilentInstallResult(false, refusal)
        Log.w(TAG, "Pasang senyap via Shizuku dimulai: ${apk.name} ${apk.length()} byte")
        return try {
            var feedFailure: IOException? = null
            val r = BoundedShell.run(INSTALL_TIMEOUT_MS) { spawnAndFeed(apk) { feedFailure = it } }
            val feed = feedFailure?.let { "${it.javaClass.simpleName}: ${it.message ?: "null"}" }
            val detail = installDetail(r.exit, r.timedOut, r.output, feed)
            val ok = isInstallSuccess(r.exit, r.output, feedFailed = feed != null)
            if (ok) {
                Log.w(TAG, "Pasang senyap via Shizuku selesai: $detail")
            } else {
                Log.e(TAG, "Pasang senyap via Shizuku GAGAL (jatuh ke installer sistem). Keluaran mentah:\n$detail")
            }
            SilentInstallResult(ok, detail)
        } catch (e: Exception) {
            Log.e(TAG, "Pasang senyap via Shizuku melempar (jatuh ke installer sistem)", e)
            SilentInstallResult(false, "${e.javaClass.simpleName}: ${e.message ?: "null"}")
        }
    }

    // Overload Int-flags dipakai karena minSdk 26 < 33 (PackageInfoFlags butuh API 33).
    @Suppress("DEPRECATION")
    private fun archivePackage(context: Context, apk: File): String? =
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName

    /**
     * Buat proses `pm install` lalu alirkan APK ke stdin-nya lewat Okio (v180): `writeAll` memindahkan per segmen
     * 8 KiB tanpa memuat APK utuh; `use {}` pada sink menutup stdin (= EOF bagi pm).
     * v182 (audit statis): pm bisa menolak SEBELUM membaca stdin (mis. pembatasan pemasangan) -> penulisan APK kena
     * EPIPE; dulu proses dibuang & hanya "IOException: Broken pipe" yg tercatat, alasan penolakan pm hilang. Kini galat
     * pengaliran dilaporkan lewat [onFeedFailure] dan proses TETAP dikembalikan, jadi [BoundedShell.run] membaca kode
     * keluar + keluaran mentah pm (dibersihkan di `finally`-nya); hasilnya tetap bukan sukses ([isInstallSuccess]).
     */
    private fun spawnAndFeed(apk: File, onFeedFailure: (IOException) -> Unit): Process {
        val p = spawn(arrayOf("sh", "-c", installCommand(apk.length())))
        feedApk(p, apk, FEED_TIMEOUT_MS)?.let(onFeedFailure)
        return p
    }

    /**
     * v188 (temuan terbuka v182, audit statis; BELUM teramati di perangkat): `writeAll` ke stdin `pm` memblokir bila
     * `pm` tak membaca stdin (pipa penuh) dan, karena dipanggil dari `start()` milik [BoundedShell.run] SEBELUM
     * `timeoutMs`-nya mulai, pemasangan senyap bisa menggantung tanpa batas (tak pernah jatuh ke installer sistem).
     * Kini APK dialirkan di thread daemon dan ditunggu paling lama [timeoutMs]; lewat itu proses dihentikan
     * ([BoundedShell.release]) sehingga penulis terlepas, dan hasilnya galat pengaliran. Mengembalikan galat itu
     * (`null` = APK selesai dialirkan & stdin ditutup = EOF bagi pm). Galat non-IO di thread dibungkus (tak boleh
     * menjadi uncaught exception yang menjatuhkan proses). Blocking — WAJIB dari Dispatchers.IO.
     */
    @Suppress("TooGenericExceptionCaught")
    internal fun feedApk(p: Process, apk: File, timeoutMs: Long): IOException? {
        val failure = AtomicReference<IOException?>(null)
        val feeder = thread(isDaemon = true, name = "lagfix-install-feeder") {
            try {
                p.outputStream.sink().buffer().use { out -> apk.source().use { input -> out.writeAll(input) } }
            } catch (e: IOException) {
                failure.set(e)
            } catch (e: Exception) {
                failure.set(IOException("${e.javaClass.simpleName}: ${e.message ?: "null"}", e))
            }
        }
        feeder.join(timeoutMs)
        if (!feeder.isAlive) return failure.get()
        BoundedShell.release(p)
        feeder.join(FEED_JOIN_GRACE_MS)
        return IOException("Pengaliran APK melewati $timeoutMs ms (stdin pm tak dibaca)")
    }

    // Shizuku.newProcess bersifat private sejak API 13 -> reflection (sama dgn FstrimExecutor.sh).
    private fun spawn(argv: Array<String>): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, argv, null, null) as Process
    }
}
