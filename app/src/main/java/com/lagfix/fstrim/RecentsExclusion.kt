package com.lagfix.fstrim

import android.app.ActivityManager
import android.content.Context
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
