package com.lagfix.fstrim

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * v55 (permintaan user: "Pakai jalur aksesibilitas"): layanan aksesibilitas KOSONG — satu2nya
 * tugasnya menjadi jangkar yang di-bind SISTEM (bukan kode app), dengan harapan sistem membuat
 * proses LagFix lahir lagi lebih cepat setelah dibunuh (mis. swipe-Recents di XOS) daripada
 * menunggu interval WorkManager. Begitu terhubung, ia memanggil `PersistentTrimService.startIfEnabled()`
 * (0 diubah) — jadi notifikasi hanya kembali kalau toggle "Layanan latar depan persisten" ON.
 *
 * 0 baca layar/ketikan: `accessibilityEventTypes` kosong & `canRetrieveWindowContent=false` di
 * `res/xml/accessibility_keepalive_config.xml`; `onAccessibilityEvent()`/`onInterrupt()` no-op.
 *
 * TIDAK ADA JAMINAN (P0 NO HALLUCINATION): (a) user WAJIB mengaktifkannya sendiri di Setelan
 * Aksesibilitas (app tak bisa menyalakannya); (b) Android 13+ bisa mengunci opsi ini utk APK di luar
 * Play Store ("Pengaturan terbatas"); (c) di ROM agresif, layanan aksesibilitas yg proses-nya dibunuh
 * berulang bisa ditandai "crashed" & tak di-bind ulang — belum teruji di XOS; (d) aksesibilitas BUKAN
 * eksepsi resmi start FGS dari background: kalau OS menolak, `startIfEnabled()` menangkapnya (v40).
 * Baris log `LIFECYCLE a11y ...` (tag `KeepAliveA11y`) dipakai membuktikan jalur ini jalan/tidak.
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "LIFECYCLE a11y onServiceConnected pid=${Process.myPid()} procAge=${procAgeMs()}ms")
        PersistentTrimService.startIfEnabled(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "LIFECYCLE a11y onUnbind pid=${Process.myPid()}")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "LIFECYCLE a11y onDestroy pid=${Process.myPid()}")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "KeepAliveA11y"

        // Umur proses (ms) sejak dibuat; kecil = layanan lahir bersama proses baru (revive).
        fun procAgeMs(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
    }
}
