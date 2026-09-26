package com.lagfix.fstrim

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NEED_PERMISSION, READY }

/**
 * Seam non-static di sekitar 3 pemanggilan Shizuku yang dipakai `state()` (C4 kandidat b).
 * Root cause C4: `mockStatic(Shizuku::class.java)` gagal di-intercept Mockito inline mock maker
 * di worker JVM CI nyata (fail-log-16 & fail-log-18, `MissingMethodInvocationException` identik,
 * kandidat a JVM self-attach TERBUKTI tidak menyelesaikan). Interface ini di-mock biasa (non-static,
 * proxy subclass) di unit test — tidak butuh instrumentasi/self-attach sama sekali.
 */
internal interface ShizukuGateway {
    fun pingBinder(): Boolean
    fun isPreV11(): Boolean
    fun checkSelfPermission(): Int
}

/** Implementasi produksi — delegasi murni ke Shizuku asli, 0 perubahan behavior vs sebelum v17. */
internal object RealShizukuGateway : ShizukuGateway {
    override fun pingBinder(): Boolean = Shizuku.pingBinder()
    override fun isPreV11(): Boolean = Shizuku.isPreV11()
    override fun checkSelfPermission(): Int = Shizuku.checkSelfPermission()
}

/** Menjalankan `sm fstrim` sebagai shell UID lewat Shizuku (tanpa root). */
object FstrimExecutor {
    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    // Var internal khusus test (C4 kandidat b) — production selalu RealShizukuGateway (default
    // ini), test unit ganti dgn mock non-static lalu WAJIB dikembalikan di @After (lihat
    // FstrimExecutorTest.kt) supaya tidak bocor antar test/run. Tidak ada API publik baru.
    internal var gateway: ShizukuGateway = RealShizukuGateway

    fun state(ctx: Context): ShizukuState = runCatching {
        if (!gateway.pingBinder()) {
            val installed = runCatching { ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0) }.isSuccess
            if (installed) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
        } else if (gateway.isPreV11()) {
            ShizukuState.NOT_RUNNING
        } else if (gateway.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuState.READY
        } else {
            ShizukuState.NEED_PERMISSION
        }
    }.getOrDefault(ShizukuState.NOT_RUNNING)

    /** Blocking — panggil hanya dari Dispatchers.IO. */
    fun run(): TrimResult {
        val t0 = SystemClock.elapsedRealtime()
        return try {
            var res = sh("sm fstrim")
            if (res.first != 0) res = sh("sm idle-maint run") // fallback
            TrimResult(res.first == 0, "exit=${res.first} ${res.second.trim()}".trim(), SystemClock.elapsedRealtime() - t0)
        } catch (e: Throwable) {
            TrimResult(false, "${e.javaClass.simpleName}: ${e.message}", SystemClock.elapsedRealtime() - t0)
        }
    }

    // Shizuku.newProcess bersifat private sejak API 13 → akses via reflection.
    private fun sh(cmd: String): Pair<Int, String> {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", "$cmd 2>&1"), null, null) as Process
        val out = p.inputStream.bufferedReader().use { it.readText() }
        return p.waitFor() to out
    }
}
