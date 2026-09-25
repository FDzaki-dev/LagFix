package com.lagfix.fstrim

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NEED_PERMISSION, READY }

/** Menjalankan `sm fstrim` sebagai shell UID lewat Shizuku (tanpa root). */
object FstrimExecutor {
    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    fun state(ctx: Context): ShizukuState = runCatching {
        if (!Shizuku.pingBinder()) {
            val installed = runCatching { ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0) }.isSuccess
            if (installed) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
        } else if (Shizuku.isPreV11()) {
            ShizukuState.NOT_RUNNING
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
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
