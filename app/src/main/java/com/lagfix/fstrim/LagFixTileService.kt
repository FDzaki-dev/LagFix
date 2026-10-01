package com.lagfix.fstrim

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast

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
 */
class LagFixTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        refresh(triggered = false)
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh(triggered = false)
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

    private companion object {
        const val TAG = "LagFixTileService"
    }
}
