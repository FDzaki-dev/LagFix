package com.lagfix.fstrim

import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Quick Settings tile. v67 (permintaan user): tile DIALIHFUNGSIKAN — tap tile = langsung menyalakan
 * foreground service persisten (`PersistentTrimService.start()` -> notifikasi "Layanan latar depan
 * persisten" tampil seketika, `FOREGROUND_SERVICE_IMMEDIATE`). Tile TIDAK lagi menjalankan fstrim
 * (dulu `Scheduler.runOnce()`; widget & tombol di app tetap memicu fstrim manual). Tile HANYA
 * pemicu: toggle `Prefs.persistentServiceEnabled` TIDAK diubah (servis yang dinyalakan dari tile
 * baru ikut dihidupkan ulang `LagFixApp.startIfEnabled()` bila toggle di Pengaturan ON).
 *
 * Android 12+ melarang start FGS dari background kecuali ada pengecualian. Daftar resmi
 * pengecualian TIDAK menyebut QS tile; yang relevan di sini = "pengguna mematikan optimasi baterai
 * untuk app". Jadi `start()` dibungkus runCatching: kalau OS menolak
 * (`ForegroundServiceStartNotAllowedException`), muncul toast, bukan crash.
 *
 * v26 (tetap berlaku): `STATE_UNAVAILABLE` satu-satunya state yang memblokir tap; `STATE_ACTIVE`/
 * `STATE_INACTIVE` hanya beda tampilan (lit vs muted). Tile idle = INACTIVE; ACTIVE hanya sesaat
 * setelah tap berhasil (kembali INACTIVE saat panel dibuka ulang — BUKAN indikator servis hidup).
 *
 * v121 (permintaan user: catcher): `onStartListening()` (panel QS dibuka, tile TIDAK diketuk) kini
 * dicatat. Selalu 1 baris logcat (tag `LagFixTileService`, kata kunci `LIFECYCLE`); bila proses baru
 * lahir (umur < [TILE_COLD_PROCESS_MAX_AGE_MS]) juga 1 berkas diagnostik `diag_tile_listening`, SEKALI per
 * proses. Isi = data mentah (lihat [formatTileListening]); `onStartListening()` TIDAK memulai servis.
 */
class LagFixTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        refresh(triggered = false)
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh(triggered = false)
        captureListening()
    }

    override fun onClick() {
        super.onClick()
        val started = runCatching { PersistentTrimService.start(applicationContext) }
            .onFailure { Log.w(TAG, "Start servis persisten dari tile ditolak OS", it) }
            .isSuccess
        if (!started) {
            Toast.makeText(applicationContext, R.string.toast_tile_persistent_denied, Toast.LENGTH_LONG).show()
        }
        refresh(triggered = started)
    }

    private fun refresh(triggered: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (triggered) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_fstrim)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                if (triggered) R.string.tile_subtitle_triggered else R.string.tile_subtitle_idle
            )
        }
        tile.updateTile()
    }

    /**
     * v121: catcher kondisi "panel QS dibuka -> notifikasi persisten muncul lagi" (laporan lisan user, BELUM
     * dibuktikan log). Berkas hanya ditulis bila proses baru lahir (cegah banjir Documents/LagFix); dua dump
     * mentah `activeNotifications` (segera & setelah jeda) di Dispatchers.IO, tanpa timer/loop.
     */
    private fun captureListening() {
        val app = applicationContext
        val pid = Process.myPid()
        val procAgeMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        Log.i(TAG, "LIFECYCLE onStartListening pid=$pid procAge=${procAgeMs}ms")
        if (procAgeMs >= TILE_COLD_PROCESS_MAX_AGE_MS || !fileWritten.compareAndSet(false, true)) return
        CoroutineScope(Dispatchers.IO).launch {
            val toggleOn = Prefs(app).persistentServiceEnabled
            val immediate = readOwnNotifications(app)
            delay(TILE_RECHECK_DELAY_MS)
            val delayed = readOwnNotifications(app)
            CrashLogger.logDiagnostic(
                app,
                TILE_DIAG_TAG,
                formatTileListening(pid, procAgeMs, toggleOn, immediate, delayed)
            )
        }
    }

    private companion object {
        const val TAG = "LagFixTileService"

        // v121: 1 berkas per proses (panel QS bisa dibuka berkali-kali dalam jendela umur proses baru).
        val fileWritten = AtomicBoolean(false)
    }
}

// v121: umur proses (ms) di bawah ini = proses baru lahir (cold start), bukan proses lama yang hidup terus.
private const val TILE_COLD_PROCESS_MAX_AGE_MS = 10_000L

// v121: jeda dump ke-2 `activeNotifications`; sama dgn `PersistentTrimService.SHOWN_RECHECK_DELAY_MS` (1500).
private const val TILE_RECHECK_DELAY_MS = 1_500L
private const val TILE_DIAG_TAG = "tile_listening"

/** v121: baca MENTAH `activeNotifications` milik app; gagal -> `Result.failure` (exception apa adanya). */
private fun readOwnNotifications(ctx: Context): Result<List<NotifSnapshot>> = runCatching {
    ctx.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty().map {
        NotifSnapshot(it.id, it.tag, it.notification.channelId, it.notification.flags, it.postTime)
    }
}

/**
 * v121 (prinsip user: log = DATA MENTAH, tanpa kalimat penjelasan/kesimpulan): isi berkas `diag_tile_listening` —
 * 1 baris pid + umur proses + status toggle persisten, lalu dua dump `activeNotifications` (segera & setelah jeda).
 */
internal fun formatTileListening(
    pid: Int,
    procAgeMs: Long,
    toggleOn: Boolean,
    immediate: Result<List<NotifSnapshot>>,
    delayed: Result<List<NotifSnapshot>>
): String = listOf(
    "tile onStartListening pid=$pid procAge=${procAgeMs}ms persistentToggleOn=$toggleOn",
    formatActiveNotifications("immediate", immediate),
    formatActiveNotifications("+${TILE_RECHECK_DELAY_MS}ms", delayed)
).joinToString("\n")
