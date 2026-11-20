package com.lagfix.fstrim

import android.content.Context
import android.util.Log
import okio.buffer
import okio.sink
import okio.source
import rikka.shizuku.Shizuku
import java.io.File
import java.io.IOException

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

    /** Perintah shell pemasangan; `-S` = ukuran byte APK di stdin, `-r` = timpa versi terpasang. Murni. */
    internal fun installCommand(sizeBytes: Long): String = "pm install -r -S $sizeBytes 2>&1"

    /** `pm install` sukses = kode keluar 0 DAN keluaran memuat "Success". Murni. */
    internal fun isInstallSuccess(exit: Int?, output: String): Boolean =
        exit == 0 && output.contains("Success", ignoreCase = true)

    // v93-style: catch Exception DISENGAJA — batas I/O-shell Shizuku (reflection, binder, proses, disk): kegagalan
    // apa pun harus jatuh ke installer sistem, bukan menggagalkan update.
    @Suppress("TooGenericExceptionCaught")
    fun install(context: Context, apk: File): SilentInstallResult {
        if (FstrimExecutor.state(context) != ShizukuState.READY) {
            return SilentInstallResult(false, "Shizuku belum siap")
        }
        // Pengaman: hanya APK paket aplikasi ini yang boleh dipasang tanpa dialog konfirmasi.
        val archivePackage = archivePackage(context, apk)
        if (archivePackage != context.packageName) {
            return SilentInstallResult(false, "Paket APK ($archivePackage) bukan ${context.packageName}")
        }
        Log.w(TAG, "Pasang senyap via Shizuku dimulai: ${apk.name} ${apk.length()} byte")
        return try {
            val r = BoundedShell.run(INSTALL_TIMEOUT_MS) { spawnAndFeed(apk) }
            val detail = "exit=${r.exit} timedOut=${r.timedOut}\n${r.output}"
            val ok = isInstallSuccess(r.exit, r.output)
            if (ok) {
                Log.w(TAG, "Pasang senyap via Shizuku selesai: $detail")
            } else {
                Log.e(TAG, "Pasang senyap via Shizuku GAGAL (jatuh ke installer sistem). Keluaran mentah:\n$detail")
            }
            SilentInstallResult(ok, detail)
        } catch (e: Exception) {
            Log.e(TAG, "Pasang senyap via Shizuku melempar (jatuh ke installer sistem)", e)
            SilentInstallResult(false, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // Overload Int-flags dipakai karena minSdk 26 < 33 (PackageInfoFlags butuh API 33).
    @Suppress("DEPRECATION")
    private fun archivePackage(context: Context, apk: File): String? =
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName

    /**
     * Buat proses `pm install` lalu alirkan APK ke stdin-nya lewat Okio (v180): `writeAll` memindahkan per segmen
     * 8 KiB tanpa memuat APK utuh; `use {}` pada sink menutup stdin (= EOF bagi pm).
     */
    private fun spawnAndFeed(apk: File): Process {
        val p = spawn(arrayOf("sh", "-c", installCommand(apk.length())))
        try {
            p.outputStream.sink().buffer().use { out -> apk.source().use { input -> out.writeAll(input) } }
        } catch (e: IOException) {
            BoundedShell.release(p)
            throw e
        }
        return p
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
