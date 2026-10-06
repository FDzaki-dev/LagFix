package com.lagfix.fstrim

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val CATCH_TAG = "ProcessCatcher"
private const val CATCH_DIAG_TAG = "process_catch"

// v123: sama dgn `PersistentTrimService.NOTIF_ID` (private di berkas DO-NOT-TOUCH, tak disentuh).
private const val CATCH_PERSISTENT_NOTIF_ID = 42

// v123: umur proses (ms) di bawah ini = proses baru lahir; di atasnya = proses lama yg hidup (warm).
private const val CATCH_COLD_MAX_AGE_MS = 10_000L

// v123: jeda dump ke-2 `activeNotifications` (sama dgn jeda `PersistentTrimService`, 1500 ms).
private const val CATCH_DELAYED_READ_MS = 1_500L

// v123: jarak minimum antar berkas "warmAbsent" dalam SATU proses (cegah banjir Documents/LagFix).
private const val CATCH_WARM_MIN_GAP_MS = 1_800_000L
private const val CATCH_MAX_SERVICES = 100
private const val CATCH_MAX_HISTORY = 5
private const val CATCH_HEX_RADIX = 16
private const val CATCH_REASON_COLD = "cold"
private const val CATCH_REASON_WARM_ABSENT = "warmAbsent"

/**
 * v123 (permintaan user: semua kondisi ambigu diberi catcher): merekam DATA MENTAH keadaan proses saat titik
 * pemanggil berjalan — notifikasi aktif (segera & +1500 ms), importance proses, `isBackgroundRestricted`,
 * servis milik app, riwayat start proses (API 35+) & riwayat kematian proses (API 30+).
 *
 * Selalu 1 baris logcat (tag `ProcessCatcher`, kata kunci `LIFECYCLE`). Berkas `diag_process_catch` hanya ditulis:
 *  - `cold`: proses baru lahir (umur < 10 dtk) DAN pemanggil mengizinkan (`allowCold`), SEKALI per proses;
 *  - `warmAbsent`: proses lama hidup, toggle persisten ON, tapi notifikasi id 42 berflag FGS TIDAK ada;
 *    maksimal 1 berkas per 30 menit per proses.
 * Hanya merekam: tidak memulai/menghentikan servis, tanpa timer/loop (1 coroutine IO sekali jalan per berkas).
 */
internal object ProcessCatcher {
    const val SOURCE_TILE = "tile"
    const val SOURCE_ACTIVITY = "activity"
    const val SOURCE_WORKER = "worker"

    private val coldClaimed = AtomicBoolean(false)
    private val lastWarmAtMs = AtomicLong(0L)

    /** Tak pernah melempar: kegagalan hanya dicatat ke logcat (catcher tak boleh menjatuhkan pemanggil). */
    fun capture(ctx: Context, source: String, allowCold: Boolean) {
        runCatching { captureUnsafe(ctx.applicationContext, source, allowCold) }
            .onFailure { Log.w(CATCH_TAG, "Catcher gagal (source=$source)", it) }
    }

    private fun captureUnsafe(app: Context, source: String, allowCold: Boolean) {
        val pid = Process.myPid()
        val procAgeMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        // Pembacaan segera dilakukan SINKRON di thread pemanggil (sebelum pemanggil sempat `ensureShowing`).
        val immediate = readCatchNotifications(app)
        val shown = isPersistentShown(immediate)
        Log.i(CATCH_TAG, "LIFECYCLE catch source=$source pid=$pid procAge=${procAgeMs}ms shown=$shown")
        val toggleOn = Prefs(app).persistentServiceEnabled
        val reason = claimWrite(procAgeMs, allowCold, toggleOn && !shown) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            delay(CATCH_DELAYED_READ_MS)
            val delayed = readCatchNotifications(app)
            val am = app.getSystemService(ActivityManager::class.java)
            val sections = listOf(
                formatActiveNotifications("immediate", immediate),
                formatActiveNotifications("+${CATCH_DELAYED_READ_MS}ms", delayed),
                formatCatchSection("myMemoryState", readMemoryState()),
                formatCatchSection("isBackgroundRestricted", readBackgroundRestricted(am)),
                formatCatchSection("ownServices", readOwnServices(am)),
                formatCatchSection("startHistory", readStartHistory(am)),
                formatCatchSection("exitHistory", readExitHistory(am))
            )
            val header = formatCatchHeader(source, reason, pid, procAgeMs, toggleOn)
            CrashLogger.logDiagnostic(app, CATCH_DIAG_TAG, (listOf(header) + sections).joinToString("\n"))
            KillCatcher.capture(app) // v124: pelaku kill; berkas terpisah `diag_kill_context`, sekali per kematian
        }
    }

    private fun claimWrite(procAgeMs: Long, allowCold: Boolean, warmAbsent: Boolean): String? = when {
        procAgeMs < CATCH_COLD_MAX_AGE_MS ->
            if (allowCold && coldClaimed.compareAndSet(false, true)) CATCH_REASON_COLD else null
        warmAbsent && claimWarm() -> CATCH_REASON_WARM_ABSENT
        else -> null
    }

    private fun claimWarm(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastWarmAtMs.get()
        return (last == 0L || now - last >= CATCH_WARM_MIN_GAP_MS) && lastWarmAtMs.compareAndSet(last, now)
    }

    private fun readMemoryState(): Result<List<String>> = runCatching {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        listOf("pid=${info.pid} importance=${info.importance} reasonCode=${info.importanceReasonCode} lru=${info.lru}")
    }

    private fun readBackgroundRestricted(am: ActivityManager?): Result<List<String>> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            listOf("value=${am?.isBackgroundRestricted}")
        } else {
            listOf(unavailableLine("P"))
        }
    }

    // getRunningServices: sejak API 26 hanya mengembalikan servis milik app sendiri — itu yg dibaca (sama dgn
    // `LogcatSnapshot`).
    @Suppress("DEPRECATION")
    private fun readOwnServices(am: ActivityManager?): Result<List<String>> = runCatching {
        am?.getRunningServices(CATCH_MAX_SERVICES).orEmpty().map {
            "service=${it.service.className} pid=${it.pid} foreground=${it.foreground} started=${it.started} " +
                "flags=0x${it.flags.toString(CATCH_HEX_RADIX)} activeSince=${it.activeSince} " +
                "lastActivityTime=${it.lastActivityTime} restarting=${it.restarting}"
        }
    }

    private fun readExitHistory(am: ActivityManager?): Result<List<String>> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            am?.getHistoricalProcessExitReasons(null, 0, CATCH_MAX_HISTORY).orEmpty().map { i ->
                "timestamp=${i.timestamp} pid=${i.pid} " +
                    "reason=${i.reason}(${constantName(i.javaClass, "REASON_", i.reason)}) " +
                    "status=${i.status} importance=${i.importance} description=${i.description}"
            }
        } else {
            listOf(unavailableLine("R"))
        }
    }

    // v125: tiap entri kini memuat pid, nama proses & stempel waktu start (mentah) supaya bisa dicocokkan dgn proses
    // tertentu; `anchors` = jangkar waktu utk mengubah stempel mentah. pid/proses/stempel dibaca via refleksi
    // (nama API-nya belum terbukti kompilasi di CI); gagal baca -> exception apa adanya di baris itu.
    private fun readStartHistory(am: ActivityManager?): Result<List<String>> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val anchors = formatAnchors(
                Process.myPid(),
                Process.getStartElapsedRealtime(),
                SystemClock.elapsedRealtime(),
                System.currentTimeMillis()
            )
            listOf(anchors) + am?.getHistoricalProcessStartReasons(CATCH_MAX_HISTORY).orEmpty().map { i ->
                val cls = i.javaClass
                "pid=${getterValue(i, "getPid")} process=${getterValue(i, "getProcessName")} " +
                    "reason=${i.reason}(${constantName(cls, "START_REASON_", i.reason)}) " +
                    "startType=${i.startType}(${constantName(cls, "START_TYPE_", i.startType)}) " +
                    "startupState=${i.startupState}(${constantName(cls, "STARTUP_STATE_", i.startupState)}) " +
                    "intent=${i.intent} timestamps={${timestampsText(i)}}"
            }
        } else {
            listOf(unavailableLine("VANILLA_ICE_CREAM"))
        }
    }
}

/** v123: baca MENTAH `activeNotifications` milik app; gagal -> `Result.failure` (exception apa adanya). */
private fun readCatchNotifications(ctx: Context): Result<List<NotifSnapshot>> = runCatching {
    ctx.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty().map {
        NotifSnapshot(it.id, it.tag, it.notification.channelId, it.notification.flags, it.postTime)
    }
}

// Definisi "tampil" sama dgn `PersistentTrimService.ensureShowing`: id 42 DAN berflag FOREGROUND_SERVICE.
// Gagal baca -> false (dianggap tidak tampil; kegagalannya tetap tercetak apa adanya di berkas).
private fun isPersistentShown(read: Result<List<NotifSnapshot>>): Boolean = read.getOrNull()?.any {
    it.id == CATCH_PERSISTENT_NOTIF_ID && (it.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
} ?: false

private fun unavailableLine(needs: String): String = "unavailable: SDK_INT=${Build.VERSION.SDK_INT} needs=$needs"

/**
 * v123: nama konstanta platform (mis. `REASON_SIGNALED`) utk nilai Int via refleksi pada kelas milik platform itu
 * sendiri — nama tak ditulis tangan, jadi tak bisa salah-peta. Tak ada yg cocok -> "UNKNOWN".
 */
internal fun constantName(holder: Class<*>, prefix: String, value: Int): String =
    holder.fields.firstOrNull { f ->
        f.name.startsWith(prefix) && f.type == Int::class.javaPrimitiveType && f.getInt(null) == value
    }?.name ?: "UNKNOWN"

/** v125: jangkar waktu mentah (proses ini + jam sekarang) utk menafsirkan stempel waktu `startHistory`. */
internal fun formatAnchors(pid: Int, procStartElapsedMs: Long, nowElapsedMs: Long, nowWallMs: Long): String =
    "anchors: myPid=$pid processStartElapsedMs=$procStartElapsedMs nowElapsedMs=$nowElapsedMs nowWallMs=$nowWallMs"

/** v125: nilai getter publik tanpa argumen lewat refleksi; gagal (mis. getter tak ada) -> exception apa adanya. */
internal fun getterValue(target: Any, getter: String): String =
    runCatching { "${target.javaClass.getMethod(getter).invoke(target)}" }
        .getOrElse { "read exception = $it" }

/** v125: `getStartupTimestamps()` (peta kode -> nilai mentah) jadi `NAMA=nilai,...` urut kode; nama dari platform. */
internal fun timestampsText(info: Any): String = runCatching {
    val map = info.javaClass.getMethod("getStartupTimestamps").invoke(info) as Map<*, *>
    map.entries.sortedBy { (it.key as? Int) ?: -1 }.joinToString(",") { e ->
        "${constantName(info.javaClass, "START_TIMESTAMP_", (e.key as? Int) ?: -1)}=${e.value}"
    }
}.getOrElse { "read exception = $it" }

/** v123 (prinsip user: log = DATA MENTAH): baris pertama berkas `diag_process_catch`. */
internal fun formatCatchHeader(source: String, reason: String, pid: Int, procAgeMs: Long, toggleOn: Boolean): String =
    "catch source=$source reason=$reason pid=$pid procAge=${procAgeMs}ms persistentToggleOn=$toggleOn"

/** v123: 1 seksi berkas: jumlah + 1 baris per item, atau exception pembacaan apa adanya. */
internal fun formatCatchSection(label: String, read: Result<List<String>>): String {
    val items = read.getOrNull() ?: return "$label: read exception = ${read.exceptionOrNull()}"
    return (listOf("$label: count=${items.size}") + items.map { "  $it" }).joinToString("\n")
}
