package com.lagfix.fstrim

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

class TrimWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        var waited = 0
        while (!Shizuku.pingBinder() && waited < 5000) {
            delay(500)
            waited += 500
        }
        val state = FstrimExecutor.state(applicationContext)
        if (state != ShizukuState.READY) {
            // v13 (B1+B4): sebelumnya cuma dicatat lalu Result.success() -> nunggu jadwal
            // periodik berikutnya (bisa sampai berhari-hari kalau interval besar). Sekarang tetap
            // dicatat (biar kelihatan di Riwayat, bukan silent skip), lalu Result.retry() supaya
            // WorkManager coba lagi dgn backoff bawaan (tak perlu setBackoffCriteria manual).
            // TIDAK retry kalau fstrim SUDAH dicoba tapi gagal (exit code non-0) — itu beda kelas
            // masalah (bukan precondition Shizuku), retry tak akan menolong, lihat bawah.
            prefs.record(TrimResult(false, "dilewati, Shizuku: $state", 0L))
            return@withContext Result.retry()
        }
        val r = FstrimExecutor.run()
        prefs.record(r)
        Result.success()
    }
}

object Scheduler {
    private const val NAME = "lagfix_fstrim"

    fun apply(ctx: Context, p: Prefs) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (!p.enabled) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiresCharging(p.requireCharging)
            .setRequiresDeviceIdle(p.requireIdle)
            .setRequiresBatteryNotLow(true)
            .build()
        val req = PeriodicWorkRequestBuilder<TrimWorker>(p.intervalHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** Dipakai widget (LagFixWidgetProvider) & QS tile (LagFixTileService) untuk trigger manual. */
    fun runOnce(ctx: Context) {
        WorkManager.getInstance(ctx.applicationContext).enqueue(OneTimeWorkRequestBuilder<TrimWorker>().build())
    }
}
