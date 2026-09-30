package com.lagfix.fstrim

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
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
        // v19 hotfix (laporan user: widget "nol feedback" — tap tak ada hasil terlihat): flag ini
        // HANYA true kalau datang dari Scheduler.runOnce() (widget/tile, manual). Jadwal periodik
        // (Scheduler.apply()) tidak pernah set input data ini -> default false -> toast TIDAK
        // pernah muncul utk run otomatis, 0 perubahan perilaku jadwal existing.
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        // v38: asal pemicu run ini utk Riwayat — manual (widget/tile via Scheduler.runOnce) vs
        // otomatis (jadwal periodik). Memakai flag `manual` yang SUDAH ada, 0 logic baru.
        val trigger = if (manual) TriggerSource.MANUAL else TriggerSource.AUTO
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
            prefs.record(TrimResult(false, "dilewati, Shizuku: $state", 0L), trigger)
            // v20 hotfix (laporan user: widget "gak berubah sama sekali" abis dipencet, widget/tile
            // gak sinkron dgn state asli): sebelumnya TIDAK ADA yang memberi tahu widget/tile kalau
            // hasil sudah ada -> widget nyangkut di teks "Sedang memproses…" sampai refresh 30 menit
            // berikutnya. Panggil UNCONDITIONAL (bukan cuma `manual`) supaya jadwal otomatis pun,
            // kalau/kapan jalan, langsung sinkron ke widget/tile juga — bukan cuma trigger manual.
            Scheduler.notifyChanged(applicationContext)
            if (manual) toast(applicationContext.getString(R.string.toast_shizuku_not_ready))
            return@withContext Result.retry()
        }
        val r = FstrimExecutor.run()
        prefs.record(r, trigger)
        Scheduler.notifyChanged(applicationContext)
        if (manual) {
            val msg = if (r.ok) {
                applicationContext.getString(R.string.toast_run_ok)
            } else {
                applicationContext.getString(R.string.toast_run_fail, r.message.take(60))
            }
            toast(msg)
        }
        Result.success()
    }

    private suspend fun toast(text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val KEY_MANUAL = "manual"
    }
}

object Scheduler {
    private const val NAME = "lagfix_fstrim"

    // v44: batas bawah interval periodik WorkManager (PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
    // = 15 menit). Nilai lebih kecil DIAM-DIAM dinaikkan ke 15 menit oleh WorkManager, jadi UI
    // menolaknya secara eksplisit & di sini diclamp juga sbg pengaman.
    const val MIN_INTERVAL_MINUTES = 15L

    fun apply(ctx: Context, p: Prefs) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (!p.enabled) {
            wm.cancelUniqueWork(NAME)
            return
        }
        // v47 (permintaan user: opsi "Hanya saat mengisi daya"/"perangkat idle" dihapus — interval
        // + manual dianggap cukup): constraint charging & idle DIBUANG. `setRequiresBatteryNotLow`
        // TETAP (bukan opsi user, pengaman baterai lama; di luar permintaan -> tak disentuh).
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val req = PeriodicWorkRequestBuilder<TrimWorker>(
            p.intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES), TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** Dipakai widget (LagFixWidgetProvider) & QS tile (LagFixTileService) untuk trigger manual. */
    fun runOnce(ctx: Context) {
        val data = Data.Builder().putBoolean(TrimWorker.KEY_MANUAL, true).build()
        WorkManager.getInstance(ctx.applicationContext)
            .enqueue(OneTimeWorkRequestBuilder<TrimWorker>().setInputData(data).build())
    }

    /**
     * v20 hotfix: dorong widget & tile refresh SEGERA stlh hasil trim apa pun berubah — dipanggil
     * dari sini (semua jalur TrimWorker: manual & otomatis) & dari MainViewModel.runNow() (run dari
     * dalam app). Ini yang bikin widget/tile/app "wajib sinkron" (bukan cuma tunggu widget
     * updatePeriodMillis 30 menit / tile onStartListening saat panel dibuka ulang).
     */
    fun notifyChanged(ctx: Context) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val ids = mgr.getAppWidgetIds(ComponentName(app, LagFixWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            app.sendBroadcast(
                Intent(app, LagFixWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
        TileService.requestListeningState(app, ComponentName(app, LagFixTileService::class.java))
    }
}
