package com.lagfix.fstrim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val BOOT_TAG = "BootReceiver"

/**
 * v27: restart `PersistentTrimService` setelah device reboot — BEDA dari jadwal WorkManager
 * (`Scheduler.apply()`, 0 diubah di sini) yang sudah auto-reschedule sendiri lintas reboot;
 * Service biasa TIDAK persist otomatis, jadi butuh receiver ini. Cuma jalan kalau toggle
 * `Prefs.persistentServiceEnabled` aktif (0 efek ke user yang belum pernah mengaktifkan fitur
 * opsional ini). 0 logic fstrim di sini.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // v168 (audit statis): `start()` dulu dipanggil tanpa penangkap, padahal OS Android 12+ bisa menolak start FGS
        // dari proses latar (snapshot v95: pembatasan latar menolak) & tiga pemanggil lain (`startIfEnabled`,
        // `ensureShowing`, tile) sudah menangkapnya -> penolakan di receiver ini menjatuhkan proses saat boot.
        val denied = startAfterBoot(Prefs(context).persistentServiceEnabled) { PersistentTrimService.start(context) }
        if (denied != null) Log.w(BOOT_TAG, "LIFECYCLE boot start servis ditolak exception=$denied", denied)
    }
}

/**
 * v168: jalankan [start] hanya bila [toggleOn]; kegagalan APA PUN dikembalikan apa adanya (bukan dilempar) supaya
 * receiver tak jatuh. Null = tak dicoba (toggle OFF) atau berhasil. Murni: bisa diuji tanpa Android.
 */
internal fun startAfterBoot(toggleOn: Boolean, start: () -> Unit): Throwable? =
    if (toggleOn) runCatching { start() }.exceptionOrNull() else null
