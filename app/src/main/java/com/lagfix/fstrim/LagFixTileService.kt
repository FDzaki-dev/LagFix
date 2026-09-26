package com.lagfix.fstrim

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile (fitur baru, prioritas user atas widget+QS tile). Tap tile -> trigger
 * `Scheduler.runOnce()` — jalur sama persis dengan tombol widget & run manual di app, 0 logic
 * Shizuku baru. Tile selalu STATE_ACTIVE (tap selalu diterima, sama seperti jadwal otomatis yang
 * tetap mencatat "dilewati" kalau Shizuku belum siap — bukan diblokir diam-diam); subtitle
 * (API 29+, `TileService` sendiri baru ada sejak API 24, jauh di bawah minSdk 26 project ini)
 * dipakai untuk info status, bukan untuk gating klik.
 */
class LagFixTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh(running = false)
    }

    override fun onClick() {
        super.onClick()
        Scheduler.runOnce(applicationContext)
        refresh(running = true)
    }

    private fun refresh(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_fstrim)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                running -> getString(R.string.widget_status_running)
                FstrimExecutor.state(applicationContext) != ShizukuState.READY ->
                    getString(R.string.tile_subtitle_not_ready)
                else -> Prefs(applicationContext).log.firstOrNull() ?: getString(R.string.widget_status_never)
            }
        }
        tile.updateTile()
    }
}
