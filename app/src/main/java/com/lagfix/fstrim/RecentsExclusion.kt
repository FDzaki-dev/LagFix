package com.lagfix.fstrim

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
        LeaveGuard.checkBreaker(ctx) // v140: evaluasi pemutus hapus-task (murah: 1 baca prefs bila tak ada yg menunggu)
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
    recentsReadBackLine(tasks.map { it.taskInfo.baseIntent?.flags }) + "; " + LeaveGuard.diagLine(ctx) // v140
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

// ---------------------------------------------------------------------------------------------------------
// v140 (perintah user: opsi A). Celah `excludeFromRecents` (v133): task yg SEDANG di depan tetap tampil di Recents
// bila Recents dibuka langsung dari dalam app (tanpa Home), lalu OEM `[Swipe-up clean]` membunuh proses (kill_context
// X6850 21:20:11.640: 1,055 dtk sesudah app -> Recents; 2 sampel). Saat user meninggalkan app (`onUserLeaveHint`) dan
// toggle persisten ON, task dihapus (`finishAndRemoveTask`) sehingga tak ada kartu yg bisa digeser. Pengaman:
//  - tidak dihapus bila app sendiri meluncurkan Activity lain (setelan/izin/installer; `startExternal`/`markExternal`),
//  - tidak dihapus selama ada pekerjaan yg mati bersama Activity (fstrim manual, cek/unduh update, boot-trim,
//    snapshot logcat),
//  - tidak dihapus di multi-window / saat sudah finishing,
//  - PEMUTUS: bila proses mati <= 10 dtk sesudah hapus-task (SIGNALED/OTHER), fitur dimatikan permanen.
// ---------------------------------------------------------------------------------------------------------

private const val LEAVE_EXTERNAL_WINDOW_MS = 5_000L
private const val LEAVE_TRIP_WINDOW_MS = 10_000L

internal const val LEAVE_REMOVE = "remove"

/** v140: titik tunggal "app sendiri meluncurkan Activity lain" (setelan, izin, installer): tandai dulu, lalu mulai. */
internal fun Context.startExternal(intent: Intent) {
    LeaveGuard.markExternal()
    startActivity(intent)
}

/** v140: semua masukan keputusan hapus-task, sudah dibaca saat keputusan (fungsi murni -> bisa diuji tanpa Android). */
internal data class LeaveState(
    val toggleOn: Boolean,
    val breakerOpen: Boolean,
    val finishing: Boolean,
    val multiWindow: Boolean,
    val external: Boolean,
    val busy: Boolean
)

/** v140: [LEAVE_REMOVE] atau `skip:<sebab>` (urutan sebab = prioritas). Nilai ini juga yg dicatat apa adanya. */
internal fun leaveDecision(s: LeaveState): String = when {
    !s.toggleOn -> "skip:toggle_off"
    s.breakerOpen -> "skip:breaker_open"
    s.finishing -> "skip:finishing"
    s.multiWindow -> "skip:multi_window"
    s.external -> "skip:external"
    s.busy -> "skip:busy"
    else -> LEAVE_REMOVE
}

/** v140: true bila penanda "app meluncurkan Activity lain" masih dlm jendela 5 dtk (< 0 = tak pernah ditandai). */
internal fun isExternalFlow(markMs: Long, nowMs: Long): Boolean =
    markMs >= 0L && nowMs - markMs in 0L..LEAVE_EXTERNAL_WINDOW_MS

/** v140: pemutus baru dievaluasi sesudah jendela 10 dtk lewat penuh & hanya utk hapus-task yg belum dievaluasi. */
internal fun breakerDue(removalMs: Long, checkedMs: Long, nowMs: Long): Boolean =
    removalMs > checkedMs && nowMs - removalMs >= LEAVE_TRIP_WINDOW_MS

/** v140: true bila ada kematian (SIGNALED/OTHER) dlm 10 dtk SESUDAH hapus-task = dicurigai membunuh proses. */
internal fun breakerTrips(removalMs: Long, deathMs: List<Long>): Boolean =
    removalMs > 0L && deathMs.any { it in removalMs..(removalMs + LEAVE_TRIP_WINDOW_MS) }

/** v140: keadaan proses-lebar utk keputusan hapus-task + catatan mentah utk `process_catch`/snapshot. */
internal object LeaveGuard {
    private const val PREFS_NAME = "leave_guard"
    private const val KEY_REMOVALS = "removals"
    private const val KEY_LAST_REMOVAL_MS = "last_removal_ms"
    private const val KEY_CHECKED_REMOVAL_MS = "checked_removal_ms"
    private const val KEY_TRIPPED_AT_MS = "tripped_at_ms"
    private const val KEY_LAST_DECISION = "last_decision"
    private const val KEY_LAST_DECISION_MS = "last_decision_ms"
    private const val NO_DECISION = "none"
    private const val EXIT_RECORDS = 10

    private val externalMarkMs = AtomicLong(-1L)
    private val workCount = AtomicInteger(0)

    fun markExternal() {
        externalMarkMs.set(SystemClock.elapsedRealtime())
    }

    fun beginWork() {
        workCount.incrementAndGet()
    }

    fun endWork() {
        workCount.decrementAndGet()
    }

    /** Dipanggil `MainActivity.onUserLeaveHint`. [vmBusy] = pekerjaan ViewModel yg ikut mati bersama Activity. */
    fun onUserLeave(activity: Activity, vmBusy: Boolean) {
        runCatching {
            val app = activity.applicationContext
            val sp = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val state = LeaveState(
                toggleOn = Prefs(app).persistentServiceEnabled,
                // API < 30 tak bisa membaca ApplicationExitInfo -> pemutus mustahil dievaluasi -> fitur tak jalan.
                breakerOpen = sp.getLong(KEY_TRIPPED_AT_MS, 0L) > 0L || Build.VERSION.SDK_INT < Build.VERSION_CODES.R,
                finishing = activity.isFinishing,
                multiWindow = activity.isInMultiWindowMode,
                external = isExternalFlow(externalMarkMs.get(), SystemClock.elapsedRealtime()),
                busy = vmBusy || workCount.get() > 0
            )
            val decision = leaveDecision(state)
            record(sp, decision)
            if (decision == LEAVE_REMOVE) activity.finishAndRemoveTask()
        }.onFailure { Log.w(RECENTS_TAG, "Hapus task saat meninggalkan app gagal", it) }
    }

    /** Dipanggil dari [syncExcludeFromRecents]: putuskan fitur bila hapus-task diikuti kematian proses. */
    fun checkBreaker(ctx: Context) {
        runCatching {
            val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val removalMs = sp.getLong(KEY_LAST_REMOVAL_MS, 0L)
            val now = System.currentTimeMillis()
            if (breakerDue(removalMs, sp.getLong(KEY_CHECKED_REMOVAL_MS, 0L), now)) {
                val edit = sp.edit().putLong(KEY_CHECKED_REMOVAL_MS, removalMs)
                if (breakerTrips(removalMs, readKillTimestamps(ctx))) edit.putLong(KEY_TRIPPED_AT_MS, now)
                edit.apply()
            }
        }.onFailure { Log.w(RECENTS_TAG, "Cek pemutus hapus-task gagal", it) }
    }

    /** Angka/teks mentah apa adanya (tanpa tafsiran) utk `recentsReadBack`. */
    fun diagLine(ctx: Context): String {
        val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return formatDiag(
            sp.getInt(KEY_REMOVALS, 0),
            sp.getString(KEY_LAST_DECISION, NO_DECISION) ?: NO_DECISION,
            sp.getLong(KEY_LAST_DECISION_MS, 0L),
            sp.getLong(KEY_TRIPPED_AT_MS, 0L)
        )
    }

    internal fun formatDiag(removals: Int, lastDecision: String, lastDecisionMs: Long, trippedAtMs: Long): String =
        "leaveGuard removals=$removals lastDecision=$lastDecision lastDecisionAt=$lastDecisionMs trippedAt=$trippedAtMs"

    // commit() DISENGAJA: proses bisa dimatikan sesaat sesudah hapus-task, catatan harus sudah tertulis di disk.
    @SuppressLint("ApplySharedPref")
    private fun record(sp: SharedPreferences, decision: String) {
        val now = System.currentTimeMillis()
        val edit = sp.edit().putString(KEY_LAST_DECISION, decision).putLong(KEY_LAST_DECISION_MS, now)
        if (decision == LEAVE_REMOVE) {
            edit.putInt(KEY_REMOVALS, sp.getInt(KEY_REMOVALS, 0) + 1).putLong(KEY_LAST_REMOVAL_MS, now).commit()
        } else {
            edit.apply()
        }
    }

    private fun readKillTimestamps(ctx: Context): List<Long> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ctx.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(null, 0, EXIT_RECORDS).orEmpty()
                .filter {
                    it.reason == ApplicationExitInfo.REASON_SIGNALED ||
                        it.reason == ApplicationExitInfo.REASON_OTHER
                }
                .map { it.timestamp }
        } else {
            emptyList()
        }
}
