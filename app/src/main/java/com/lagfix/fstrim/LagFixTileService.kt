package com.lagfix.fstrim

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile. Tap tile -> trigger `Scheduler.runOnce()` — jalur sama persis dengan
 * tombol widget & run manual di app, 0 logic Shizuku baru.
 *
 * v26 (root-cause fix laporan "tile tak pernah kelihatan mati/idle, default putih menyala
 * terus"): akar masalah SEBENARNYA bukan warna icon (sudah dicoba v25, confirmed negatif
 * ke-2x) — `tile.state` di-hardcode `STATE_ACTIVE` 100% waktu sejak v18, tak pernah
 * `STATE_INACTIVE`, jadi OS SELALU render tile dalam gaya "on/lit" (tak pernah "off"), persis
 * cocok dgn laporan. Asumsi lama (komentar sebelumnya) bahwa `STATE_INACTIVE` akan
 * "memblokir tap" TIDAK akurat — kontrak resmi `TileService`: hanya `STATE_UNAVAILABLE` yang
 * membuat tile tak merespons tap; `STATE_ACTIVE`/`STATE_INACTIVE` keduanya tetap memanggil
 * `onClick()` normal, beda cuma tampilan (lit vs muted). Fix: state sekarang ikut parameter
 * `running` — `STATE_INACTIVE` saat idle (icon akhirnya bisa kelihatan "mati"), `STATE_ACTIVE`
 * cuma saat benar-benar sedang memproses (0 perubahan pada `onClick()`/kapan tap diterima).
 * Subtitle (API 29+, `TileService` sendiri baru ada sejak API 24, jauh di bawah minSdk 26
 * project ini) tetap dipakai untuk info status, bukan untuk gating klik.
 */
class LagFixTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        refresh(running = false)
    }

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
        // v26: dulu hardcode STATE_ACTIVE selalu (akar masalah icon "menyala putih" terus-terusan
        // — lihat KDoc kelas). Sekarang ikut kondisi nyata: INACTIVE = idle/mati, ACTIVE = lagi
        // memproses. Tap tetap selalu diterima di kedua state (hanya STATE_UNAVAILABLE yang
        // memblokir klik, tidak dipakai di sini).
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
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
