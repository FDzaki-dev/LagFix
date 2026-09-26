package com.lagfix.fstrim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
        if (Prefs(context).persistentServiceEnabled) {
            PersistentTrimService.start(context)
        }
    }
}
