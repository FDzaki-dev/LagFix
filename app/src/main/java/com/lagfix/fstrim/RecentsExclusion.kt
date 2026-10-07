package com.lagfix.fstrim

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log

private const val RECENTS_TAG = "RecentsExclusion"

/**
 * v133 (perintah user: opsi 2 `excludeFromRecents`): geser app dari Recents = `remove-task`, lalu modul OEM
 * `TranManualCleanMgr` mengirim SIGKILL (log mentah v129 & v131, 3 dari 3 kematian tertangkap). Selama toggle
 * "Layanan latar depan persisten" ON, task app disembunyikan dari Recents lewat `AppTask.setExcludeFromRecents`
 * (tak ada kartu yg bisa digeser); toggle OFF -> dikembalikan. Dipanggil dari `MainActivity.onResume` dan
 * `MainViewModel.setPersistentService`. Efektivitas di X6855 BELUM terbukti. Tanpa timer/loop; 1 panggilan binder.
 */
internal fun syncExcludeFromRecents(ctx: Context) {
    runCatching {
        val exclude = Prefs(ctx).persistentServiceEnabled
        val tasks = ctx.getSystemService(ActivityManager::class.java)?.appTasks.orEmpty()
        tasks.forEach { it.setExcludeFromRecents(exclude) }
        Log.i(RECENTS_TAG, recentsExclusionLine(exclude, tasks.size))
    }.onFailure { Log.w(RECENTS_TAG, "Sinkron excludeFromRecents gagal", it) }
}

/** v133: angka mentah apa adanya (status target + jumlah task app yg diubah), tanpa kalimat tafsiran. */
internal fun recentsExclusionLine(exclude: Boolean, appTasks: Int): String =
    "LIFECYCLE recentsExclusion exclude=$exclude appTasks=$appTasks"

/**
 * v134 (dump 7 Okt: logcat X6850 tanpa satu pun baris app, jadi baris `RecentsExclusion` tak bisa jadi bukti):
 * baca BALIK flag `baseIntent` tiap task app langsung dari sistem ([ActivityManager.AppTask.getTaskInfo]).
 * Dipakai seksi status snapshot & `process_catch`. Tanpa logcat/Shizuku; galat dicatat sbg teks mentah.
 */
internal fun recentsReadBack(ctx: Context): String = runCatching {
    val tasks = ctx.getSystemService(ActivityManager::class.java)?.appTasks.orEmpty()
    recentsReadBackLine(tasks.map { it.taskInfo.baseIntent?.flags })
}.getOrElse { "gagal: ${it.javaClass.simpleName}: ${it.message}" }

/** v134: angka mentah per task — flag `baseIntent` (heks) & bit EXCLUDE_FROM_RECENTS; null bila `baseIntent` kosong. */
internal fun recentsReadBackLine(baseIntentFlags: List<Int?>): String {
    val perTask = baseIntentFlags.joinToString(" | ") { f ->
        if (f == null) {
            "baseIntent=null"
        } else {
            "flags=0x${Integer.toHexString(f)} excludeBit=${(f and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0}"
        }
    }
    return "appTasks=${baseIntentFlags.size}" + if (perTask.isEmpty()) "" else " $perTask"
}
