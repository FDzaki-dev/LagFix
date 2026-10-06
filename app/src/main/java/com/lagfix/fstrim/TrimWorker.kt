package com.lagfix.fstrim

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
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
        // v78: jaga notifikasi servis persisten (hilang saat interval berjalan, laporan user) — cek di awal...
        PersistentTrimService.ensureShowing(applicationContext, "worker-start")
        // v19 hotfix (laporan user: widget "nol feedback" — tap tak ada hasil terlihat): flag ini
        // HANYA true kalau datang dari Scheduler.runOnce() (widget/tile, manual). Jadwal periodik
        // (Scheduler.apply()) tidak pernah set input data ini -> default false -> toast TIDAK
        // pernah muncul utk run otomatis, 0 perubahan perilaku jadwal existing.
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        // v38: asal pemicu run ini utk Riwayat — manual (widget/tile via Scheduler.runOnce) vs
        // otomatis (jadwal periodik). Memakai flag `manual` yang SUDAH ada, 0 logic baru.
        val trigger = if (manual) TriggerSource.MANUAL else TriggerSource.AUTO
        // v118: true hanya utk rantai interval radikal (< 15 menit, Scheduler.scheduleNext). Periodik &
        // manual tak pernah set -> default false -> 0 perubahan perilaku jalur lama.
        val chain = inputData.getBoolean(KEY_CHAIN, false)
        var waited = 0L
        while (!Shizuku.pingBinder() && waited < SHIZUKU_WAIT_MAX_MS) {
            delay(SHIZUKU_POLL_MS)
            waited += SHIZUKU_POLL_MS
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
            PersistentTrimService.ensureShowing(applicationContext, "worker-end") // v78
            if (manual) toast(applicationContext.getString(R.string.toast_shizuku_not_ready))
            // v118: rantai radikal tak pakai retry/backoff (bisa lebih lama dari interval & dobel jadwal);
            // cukup jadwalkan tik berikutnya.
            if (chain) {
                Scheduler.scheduleNext(applicationContext, prefs)
                return@withContext Result.success()
            }
            return@withContext Result.retry()
        }
        val r = FstrimExecutor.run()
        prefs.record(r, trigger)
        Scheduler.notifyChanged(applicationContext)
        PersistentTrimService.ensureShowing(applicationContext, "worker-end") // v78: ...dan setelah run selesai
        if (manual) {
            val msg = if (r.ok) {
                applicationContext.getString(R.string.toast_run_ok)
            } else {
                applicationContext.getString(R.string.toast_run_fail, r.message.take(TOAST_MESSAGE_MAX_CHARS))
            }
            toast(msg)
        }
        // v118: terakhir sebelum return (REPLACE membatalkan worker ini sendiri; semua kerja sudah selesai).
        if (chain) Scheduler.scheduleNext(applicationContext, prefs)
        Result.success()
    }

    private suspend fun toast(text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val KEY_MANUAL = "manual"
        const val KEY_CHAIN = "chain" // v118

        // v92 (detekt MagicNumber): nilai identik dgn literal sebelumnya (tunggu Shizuku maks 5 dtk,
        // cek tiap 0,5 dtk; pesan toast gagal dipotong 60 karakter).
        private const val SHIZUKU_WAIT_MAX_MS = 5_000L
        private const val SHIZUKU_POLL_MS = 500L
        private const val TOAST_MESSAGE_MAX_CHARS = 60
    }
}

object Scheduler {
    private const val NAME = "lagfix_fstrim"
    private const val NAME_RADICAL = "lagfix_fstrim_radical" // v118: rantai OneTime utk interval < 15 menit

    // v44: batas bawah interval periodik WorkManager (PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
    // = 15 menit). Nilai lebih kecil DIAM-DIAM dinaikkan ke 15 menit oleh WorkManager, jadi UI
    // menolaknya secara eksplisit & di sini diclamp juga sbg pengaman.
    const val MIN_INTERVAL_MINUTES = 15L

    // v118: batas bawah saat mode radikal aktif (rantai OneTimeWorkRequest, bukan periodik).
    const val RADICAL_MIN_INTERVAL_MINUTES = 1L

    fun minIntervalMinutes(radical: Boolean): Long =
        if (radical) RADICAL_MIN_INTERVAL_MINUTES else MIN_INTERVAL_MINUTES

    fun apply(ctx: Context, p: Prefs) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (!p.enabled) {
            wm.cancelUniqueWork(NAME)
            wm.cancelUniqueWork(NAME_RADICAL)
            return
        }
        p.scheduleAnchorMs = System.currentTimeMillis() // v120: acuan penanda terlambat di Riwayat
        // v118: mode radikal + interval < 15 menit -> periodik WorkManager (min 15 menit) diganti rantai
        // OneTimeWorkRequest (initial delay = interval; tik berikutnya dijadwalkan TrimWorker stlh run).
        if (p.radicalInterval && p.intervalMinutes < MIN_INTERVAL_MINUTES) {
            wm.cancelUniqueWork(NAME)
            enqueueRadical(wm, p.intervalMinutes)
            return
        }
        wm.cancelUniqueWork(NAME_RADICAL)
        // v48 (permintaan user): SEMUA constraint dibuang (charging & idle sejak v47, baterai-rendah
        // sejak v48) -> request periodik murni interval, tanpa `setConstraints()`.
        val req = PeriodicWorkRequestBuilder<TrimWorker>(
            p.intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES), TimeUnit.MINUTES
        )
            .build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** v118: dipanggil TrimWorker (mode rantai) stlh run; no-op bila jadwal/mode radikal sudah dimatikan. */
    fun scheduleNext(ctx: Context, p: Prefs) {
        if (!p.enabled || !p.radicalInterval || p.intervalMinutes >= MIN_INTERVAL_MINUTES) return
        enqueueRadical(WorkManager.getInstance(ctx.applicationContext), p.intervalMinutes)
    }

    private fun enqueueRadical(wm: WorkManager, minutes: Long) {
        val data = Data.Builder().putBoolean(TrimWorker.KEY_CHAIN, true).build()
        val req = OneTimeWorkRequestBuilder<TrimWorker>()
            .setInitialDelay(minutes.coerceAtLeast(RADICAL_MIN_INTERVAL_MINUTES), TimeUnit.MINUTES)
            .setInputData(data)
            .build()
        wm.enqueueUniqueWork(NAME_RADICAL, ExistingWorkPolicy.REPLACE, req)
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
