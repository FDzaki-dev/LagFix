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
        // v35: startForeground() SEKARANG BENAR2 baris pertama yang dieksekusi (sebelumnya ada 2
        // pemanggilan Binder IPC ringan - areNotificationsEnabled()/getNotificationChannelCompat()
        // - yang jalan LEBIH DULU). Sesuai poin 4 panduan notifikasi yang diberikan user: jangan
        // ada operasi apa pun sebelum startForeground(), termasuk yang "ringan", karena window 5
        // detik OS ini ketat & device ini (OEM agresif, lihat riwayat v20-v25) sudah terbukti tak
        // selalu berperilaku standar. P0 NO HALLUCINATION: ini pengetatan defensif thd 1 deviasi
        // nyata yang ditemukan dari audit ulang, BUKAN kepastian ini akar masalah tunggal — 3 poin
        // panduan lainnya (channel ID match, smallIcon valid, foregroundServiceType+property
        // manifest) sudah dicek & SESUAI, 0 perubahan di situ.
        val result = runCatching { startForeground(NOTIF_ID, buildNotification()) }

        // v29 (0 berubah dari sisi DATA yang direkam, cuma dibaca SESUDAH startForeground() skrg):
        // kumpulkan BUKTI KONKRET langsung ke Download/LagFix/ — status areNotificationsEnabled(),
        // importance channel asli, & hasil startForeground() (sukses/exception apa). Channel itu
        // sendiri dibuat di onCreate() (sebelum onStartCommand() ini pernah jalan), jadi nilainya
        // identik dibaca sebelum atau sesudah startForeground() — aman dipindah ke sini.
        val notifMgrCompat = NotificationManagerCompat.from(this)
        val preCheck = "areNotificationsEnabled() = ${notifMgrCompat.areNotificationsEnabled()}\n" +
            "channel importance (getNotificationChannelCompat) = " +
            "${notifMgrCompat.getNotificationChannelCompat(CHANNEL_ID)?.importance}"
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
