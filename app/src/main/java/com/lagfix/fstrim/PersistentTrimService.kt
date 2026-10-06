package com.lagfix.fstrim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // v49: waktu onCreate (elapsedRealtime) utk menghitung umur servis di log onDestroy().
    private var createdAtMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createdAtMs = SystemClock.elapsedRealtime()
        // v49 (milestone "Log.i lifecycle servis", jawab pertanyaan terbuka v42: siapa yg
        // menghentikan FGS ~1,7 dtk SEBELUM SIGKILL XOS, & apa yg menghidupkan proses lagi).
        // Filter logcat: tag `PersistentTrimService`, kata kunci `LIFECYCLE`.
        Log.i(TAG, "LIFECYCLE onCreate pid=${Process.myPid()} procAge=${procAgeMs()}ms")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.persistent_service_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                // v104: nama & deskripsi saluran jelas di Setelan Notifikasi sistem.
                description = getString(R.string.persistent_service_channel_desc)
                setShowBadge(false)
                // v105 (konfigurasi_bypass_restricted_os.md bagian 1, perintah user): MIN (bukan LOW) +
                // disembunyikan dari layar kunci. Status FGS tetap terdaftar. Catatan: Android hanya
                // menurunkan importance channel yang SUDAH ada bila user belum mengubahnya di Setelan.
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
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
        val result = runCatching { startAsForeground(buildNotification()) }
        // v111 (log jujur): `startForeground()` yang KEMBALI tanpa exception BUKAN bukti notifikasi tampil —
        // OS menolak diam-diam (hanya log WARN "not allowed due to bg restriction", bukti snapshot v95).
        // Karena itu dicatat `returned`, bukan `ok`; status tampil diamati terpisah (lihat bawah).
        // v49: log SETELAH startForeground() (aturan v35: tak ada operasi apa pun sebelumnya).
        // `intentNull=true` = servis dihidupkan ulang SISTEM (START_STICKY, intent null);
        // `false` = distart kode app (`start()`/`startIfEnabled()`/BootReceiver).
        Log.i(
            TAG,
            "LIFECYCLE onStartCommand pid=${Process.myPid()} procAge=${procAgeMs()}ms " +
                "startId=$startId flags=$flags intentNull=${intent == null} " +
                "startForeground=${if (result.isSuccess) "returned" else result.exceptionOrNull()}"
        )

        // v29 (0 berubah dari sisi DATA yang direkam, cuma dibaca SESUDAH startForeground() skrg):
        // kumpulkan BUKTI KONKRET langsung ke Download/LagFix/ — status areNotificationsEnabled(),
        // importance channel asli, & hasil startForeground() (sukses/exception apa). Channel itu
        // sendiri dibuat di onCreate() (sebelum onStartCommand() ini pernah jalan), jadi nilainya
        // identik dibaca sebelum atau sesudah startForeground() — aman dipindah ke sini.
        // v78: start "quiet" (re-assert dari TrimWorker tiap interval, lihat `ensureShowing`) HANYA log
        // logcat, TANPA file diagnostik — interval 15 mnt = 96 file/hari di Documents/LagFix (user sudah
        // pernah minta folder log tak penuh, v51). Start biasa (toggle/cold start/sticky) tak berubah.
        val failure = result.exceptionOrNull()
        val appCtx = applicationContext
        if (intent?.getBooleanExtra(EXTRA_QUIET, false) == true) {
            // v111: tetap tanpa file; hanya 1 baris logcat hasil pengamatan tampil (di IO, bukan Main).
            CoroutineScope(Dispatchers.IO).launch { recheckShown(appCtx, failure) }
            return START_STICKY
        }
        val notifMgrCompat = NotificationManagerCompat.from(this)
        val preCheck = "areNotificationsEnabled() = ${notifMgrCompat.areNotificationsEnabled()}\n" +
            "channel importance (getNotificationChannelCompat) = " +
            "${notifMgrCompat.getNotificationChannelCompat(CHANNEL_ID)?.importance}"
        // v40: tulis log ke MediaStore di Dispatchers.IO, BUKAN di Main thread — servis kini juga
        // bisa distart tiap cold start proses (`startIfEnabled()`), jadi IO ini tak boleh menahan
        // Main thread di jendela sempit sebelum OS sempat membunuh proses lagi.
        CoroutineScope(Dispatchers.IO).launch {
            // v112 (log jujur): file ditulis SETELAH cek ulang berjeda. Cek langsung (v111) = false negative di HP
            // user (notifikasi ternyata tampil). Trade-off: proses dibunuh sebelum jeda habis = file tak tertulis
            // (baris logcat `LIFECYCLE onStartCommand` tetap ada).
            val shownNow = isForegroundNotificationShown(appCtx)
            val shownLater = recheckShown(appCtx, failure)
            val outcome = describeStartOutcome(failure, shownNow, shownLater, SHOWN_RECHECK_DELAY_MS)
            CrashLogger.logDiagnostic(appCtx, "persistent_service", "$preCheck\n$outcome")
        }
        return START_STICKY
    }

    // v49: TANPA override onTaskRemoved() — manifest tak set `stopWithTask="false"`, jadi sistem
    // langsung menghentikan servis saat task dihapus & callback itu TIDAK dipanggil (dead code).
    // Bukti stop dari sistem = onDestroy() di bawah TANPA baris "stop() dipanggil kode app" sebelumnya.
    override fun onDestroy() {
        Log.i(
            TAG,
            "LIFECYCLE onDestroy pid=${Process.myPid()} " +
                "serviceUptime=${SystemClock.elapsedRealtime() - createdAtMs}ms"
        )
        super.onDestroy()
    }

    // v104 (konfigurasi_notifikasi_persistent.md, bagian 2): Android 14+ (API 34) memberi tipe FGS
    // eksplisit = tipe yang SUDAH dideklarasikan manifest (`specialUse`), jadi 0 perubahan perilaku.
    // API < 34 memakai bentuk 2-argumen seperti sebelumnya. Dipanggil dari dalam runCatching.
    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
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
            // v104 (konfigurasi_notifikasi_persistent.md, bagian 2 & 4): atribut standar notifikasi persisten.
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            // v40: Android 12+ MENUNDA tampilnya notifikasi foreground service ~10 detik kecuali
            // diminta langsung. Di ROM yang membunuh proses dalam hitungan detik (XOS, lihat v39),
            // jeda itu = notifikasi tak pernah sempat muncul sebelum proses dibunuh.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "lagfix_keep_alive"
        private const val NOTIF_ID = 42

        private const val TAG = "PersistentTrimService"
        private const val EXTRA_QUIET = "quiet"

        // v111: jeda pengecekan ulang tampil. ASUMSI (BELUM terbukti di device): sistem memposting notifikasi
        // FGS secara asinkron, jadi cek segera setelah startForeground() bisa false walau akhirnya tampil.
        private const val SHOWN_RECHECK_DELAY_MS = 1500L

        // v49: umur proses (ms) sejak dibuat. Kecil (< beberapa detik) saat onCreate servis =
        // servis lahir bersamaan proses baru (cold start / revive), bukan servis lama yg hidup terus.
        // `getStartElapsedRealtime()` API 24+, minSdk 26.
        private fun procAgeMs(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()

        fun start(ctx: Context, quiet: Boolean = false) {
            val app = ctx.applicationContext
            ContextCompat.startForegroundService(
                app,
                Intent(app, PersistentTrimService::class.java).putExtra(EXTRA_QUIET, quiet)
            )
            Log.i(TAG, "LIFECYCLE start() -> startForegroundService() dipanggil quiet=$quiet pid=${Process.myPid()}")
        }

        /**
         * v78 (laporan user: notifikasi persisten mendadak hilang saat interval otomatis berjalan
         * sementara app terbuka/nangkring di Recents). Audit kode: TIDAK ada jalur app yang menghentikan
         * servis/notifikasi saat interval jalan (`stop()` cuma dari toggle OFF; 0 `stopForeground`/
         * `stopSelf`/`cancel`) -> penghentiannya datang dari SISTEM (atau user menggeser notifikasi).
         * Mitigasi: `TrimWorker` memanggil ini di awal & akhir tiap run. Bila toggle ON tapi notifikasi
         * TIDAK tampil sebagai FGS (hilang / diturunkan sistem), servis di-start ulang -> `startForeground()`
         * memostingnya lagi. Idempoten, tanpa timer/alarm/loop (guard Battery). Start dari background
         * bisa ditolak OS Android 12+ -> ditangkap, bukan crash. Tak ada jaminan 100% (ROM tetap bisa
         * mencabutnya lagi / membunuh proses).
         */
        fun ensureShowing(ctx: Context, from: String) {
            val app = ctx.applicationContext
            val enabled = Prefs(app).persistentServiceEnabled
            val shown = isForegroundNotificationShown(app)
            Log.i(
                TAG,
                "LIFECYCLE ensureShowing from=$from pid=${Process.myPid()} procAge=${procAgeMs()}ms " +
                    "toggleOn=$enabled shown=$shown"
            )
            if (!enabled || shown) return
            runCatching { start(app, quiet = true) }
                .onFailure { Log.w(TAG, "Re-assert notifikasi persisten ditolak OS (from=$from)", it) }
        }

        // Notifikasi id 42 tampil DAN masih berflag FOREGROUND_SERVICE (kalau sistem menurunkan FGS,
        // notifikasi bisa tetap ada tanpa flag itu -> dianggap tidak tampil). Gagal baca -> false (aman:
        // start ulang itu idempoten).
        private fun isForegroundNotificationShown(ctx: Context): Boolean = runCatching {
            ctx.getSystemService(NotificationManager::class.java)?.activeNotifications?.any {
                it.id == NOTIF_ID && (it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
            } ?: false
        }.getOrDefault(false)

        /**
         * v112 (log jujur; v111 `logShownRecheck` kini mengembalikan hasil): pengamatan ke-2, SEKALI (tanpa loop/
         * timer), setelah [SHOWN_RECHECK_DELAY_MS]; dipanggil dari coroutine Dispatchers.IO. Mencatat 1 baris
         * logcat dan mengembalikan status tampil. File hanya ditulis oleh start non-quiet (aturan v78).
         */
        private suspend fun recheckShown(ctx: Context, startFailure: Throwable?): Boolean {
            delay(SHOWN_RECHECK_DELAY_MS)
            val shown = isForegroundNotificationShown(ctx)
            Log.i(
                TAG,
                "LIFECYCLE notifShown=$shown after=${SHOWN_RECHECK_DELAY_MS}ms " +
                    "startForeground=${startFailure ?: "returned"}"
            )
            return shown
        }

        /**
         * v40: nyalakan lagi servis kalau toggle persisten aktif — dipanggil dari
         * `LagFixApp.onCreate()` (tiap cold start proses). Root cause: XOS membunuh proses saat
         * swipe-Recents & sengaja tak me-restart servis (v39), sedangkan servis sebelumnya cuma
         * distart dari toggle/BootReceiver, jadi setelah app dibuka lagi notifikasi tak pernah
         * kembali. Proses baru dari sumber background (worker/tile/widget) bisa ditolak OS
         * (Android 12+ `ForegroundServiceStartNotAllowedException`) — itu ditangkap, bukan crash.
         */
        fun startIfEnabled(ctx: Context) {
            val enabled = Prefs(ctx).persistentServiceEnabled
            Log.i(TAG, "LIFECYCLE startIfEnabled pid=${Process.myPid()} procAge=${procAgeMs()}ms toggleOn=$enabled")
            if (!enabled) return
            runCatching { start(ctx) }
                .onFailure { Log.w(TAG, "Start servis persisten ditolak OS (proses dari background)", it) }
        }

        fun stop(ctx: Context) {
            Log.i(TAG, "LIFECYCLE stop() dipanggil dari kode app pid=${Process.myPid()}")
            val app = ctx.applicationContext
            app.stopService(Intent(app, PersistentTrimService::class.java))
        }
    }
}

/**
 * v112 (log jujur, `diag_persistent_service`; v111 hanya cek langsung = false negative di HP user): kembali tanpa
 * exception != notifikasi tampil. Hasil ditentukan pengamatan TERAKHIR (`shownLater`, setelah `delayMs`); `shownNow`
 * (cek segera) dilaporkan sebagai pembanding. OS bisa menolak `startForeground()` diam-diam (hanya log WARN).
 */
internal fun describeStartOutcome(failure: Throwable?, shownNow: Boolean, shownLater: Boolean, delayMs: Long): String {
    val observed = "(cek segera: ${if (shownNow) "terlihat" else "tidak terlihat"}; " +
        "setelah ${delayMs}ms: ${if (shownLater) "terlihat" else "tidak terlihat"})"
    return when {
        failure != null -> "startForeground() GAGAL: $failure"
        shownLater -> "startForeground() kembali tanpa exception; notifikasi foreground service TERLIHAT $observed."
        else -> "startForeground() kembali tanpa exception, TAPI notifikasi foreground service TIDAK terlihat " +
            "$observed (atau gagal dibaca). Kembali tanpa exception BUKAN bukti tampil: OS bisa menolak diam-diam " +
            "(mis. pembatasan background). Lihat logcat `Service.startForeground() not allowed`."
    }
}
