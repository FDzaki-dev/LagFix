package com.lagfix.fstrim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * v27 (fitur opsional, dipilih eksplisit user setelah paham trade-off-nya via dialog konfirmasi
 * di chat — bukan default aktif): foreground service `specialUse`. SENGAJA 0 logic fstrim di
 * sini (0 duplikasi/divergensi dari `Scheduler`/`TrimWorker` yang sudah ada & sudah diuji) —
 * satu-satunya tugas service ini cuma MENJAGA PROSES APP TETAP HIDUP, dengan harapan itu bikin
 * jadwal periodik WorkManager yang SUDAH ADA (`Scheduler.apply()`, 0 diubah) lebih diandalkan di
 * ROM yang agresif membunuh proses background (mis. Infinix XOS — lihat riwayat v22-v24).
 * Notifikasi permanen (importance LOW, senyap) adalah trade-off yang tak terhindarkan selama
 * Android mewajibkan setiap foreground service selalu punya notifikasi terlihat — sudah
 * dijelaskan ke & dikonfirmasi oleh user sebelum fitur ini dibuat.
 *
 * TIDAK ADA JAMINAN 100%: ROM yang SANGAT agresif tetap bisa membunuh foreground service juga
 * (P0 NO HALLUCINATION — tidak diklaim ini pasti menyelesaikan akar masalah, cuma menaikkan
 * peluang dibanding hanya WorkManager biasa, karena proses foreground biasanya diprioritaskan
 * OS drpd proses background murni).
 */
class PersistentTrimService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            mgr?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // v29: kumpulkan BUKTI KONKRET (bukan tebakan) langsung ke Download/LagFix/ — dipakai
        // investigasi laporan "notifikasi tak pernah kelihatan": rekam status
        // areNotificationsEnabled(), importance channel asli, & hasil startForeground() (sukses
        // atau exception apa) SEBELUM & SESUDAH dipanggil.
        val notifMgrCompat = NotificationManagerCompat.from(this)
        val preCheck = "areNotificationsEnabled() = ${notifMgrCompat.areNotificationsEnabled()}\n" +
            "channel importance (getNotificationChannelCompat) = " +
            "${notifMgrCompat.getNotificationChannelCompat(CHANNEL_ID)?.importance}"
        // Wajib dipanggil SEGERA (bukan di onCreate) supaya promosi foreground tidak telat
        // (Android modern lempar exception kalau startForeground() telat dipanggil stlh
        // startForegroundService()).
        val result = runCatching { startForeground(NOTIF_ID, buildNotification()) }
        val outcome = if (result.isSuccess) "startForeground() SUKSES tanpa exception" else
            "startForeground() GAGAL: ${result.exceptionOrNull()}"
        CrashLogger.logDiagnostic(this, "persistent_service", "$preCheck\n$outcome")
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE // wajib, targetSdk 35, konsisten dgn PendingIntent widget
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_fstrim)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.persistent_service_notif_text))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "lagfix_keep_alive"
        private const val NOTIF_ID = 42

        fun start(ctx: Context) {
            val app = ctx.applicationContext
            ContextCompat.startForegroundService(app, Intent(app, PersistentTrimService::class.java))
        }

        fun stop(ctx: Context) {
            val app = ctx.applicationContext
            app.stopService(Intent(app, PersistentTrimService::class.java))
        }
    }
}
