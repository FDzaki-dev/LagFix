package com.lagfix.fstrim

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * v146: pengukur frame bawaan (`Window.OnFrameMetricsAvailableListener`, API 24; 0 dependency, 0 izin).
 * Latar: scroll tema Neo "masih sedikit keset" (v144-v145) tetapi HP user tanpa ADB/PC, sedangkan
 * langkah manual Developer options ditolak user. Tiap sesi layar (tutup/ganti tema/rotasi) yang
 * mengumpulkan >= [FJ_MIN_FRAMES] frame ditulis SEKALI sbg `diag_frame_metrics` ke Documents/LagFix
 * (maks [FJ_MAX_FILES] berkas per proses). Isi berkas = angka mentah, tanpa kalimat tafsiran.
 */
private const val FJ_TAG = "frame_metrics"
private const val FJ_THREAD = "lagfix-frames"
private const val FJ_CAPACITY = 7_200
private const val FJ_MIN_FRAMES = 120
private const val FJ_MAX_FILES = 3
private const val FJ_DEFAULT_HZ = 60f
private const val FJ_NS_PER_S = 1_000_000_000f
private const val FJ_NS_PER_US = 1_000L
private const val FJ_ALL_FRAMES = -1L
private const val FJ_X2 = 2L
private const val FJ_X4 = 4L
private const val FJ_BUDGET_60HZ_NS = 16_666_667L
private const val FJ_PERCENT = 100
private const val FJ_P50 = 50
private const val FJ_P90 = 90
private const val FJ_P95 = 95
private const val FJ_P99 = 99
private const val FJ_FIRST_DRAW = 1L

/**
 * Tahap per frame (8 pertama = urutan [FJ_STAGE_IDS], terakhir `gpu`); satu baris data = [FJ_STAGES] tahap + total.
 * v164: `gpu` = `FrameMetrics.GPU_DURATION` (API 31+; 0 di bawahnya) -> membuktikan/membantah frame terikat GPU.
 */
internal const val FJ_STAGES = 9
internal const val FJ_STRIDE = FJ_STAGES + 1
private const val FJ_GPU_STAGE = FJ_STAGES - 1

private val FJ_STAGE_NAMES = listOf("unknown", "input", "anim", "layout", "draw", "sync", "cmd", "swap", "gpu")
private val FJ_STAGE_IDS = intArrayOf(
    FrameMetrics.UNKNOWN_DELAY_DURATION,
    FrameMetrics.INPUT_HANDLING_DURATION,
    FrameMetrics.ANIMATION_DURATION,
    FrameMetrics.LAYOUT_MEASURE_DURATION,
    FrameMetrics.DRAW_DURATION,
    FrameMetrics.SYNC_DURATION,
    FrameMetrics.COMMAND_ISSUE_DURATION,
    FrameMetrics.SWAP_BUFFERS_DURATION
)

/** Hasil [FrameJank.take]: `data` = `count` baris x [FJ_STRIDE] nilai (ns), tahap lalu total. */
internal class FrameSnapshot(val data: LongArray, val count: Int, val dropped: Int, val firstDraw: Int)

/** Penampung frame (ditulis dari thread pengukur, diambil dari main thread) — semua akses lewat `lock`. */
internal object FrameJank {
    private val lock = Any()
    private var data = LongArray(0)
    private var count = 0
    private var dropped = 0
    private var firstDraw = 0
    private var filesTaken = 0

    fun record(metrics: FrameMetrics, droppedCallbacks: Int) {
        synchronized(lock) {
            dropped += droppedCallbacks
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == FJ_FIRST_DRAW) {
                firstDraw++
            } else if (count < FJ_CAPACITY) {
                if (data.isEmpty()) data = LongArray(FJ_CAPACITY * FJ_STRIDE)
                val base = count * FJ_STRIDE
                for (s in FJ_STAGE_IDS.indices) data[base + s] = metrics.getMetric(FJ_STAGE_IDS[s])
                data[base + FJ_GPU_STAGE] = gpuDurationNs(metrics)
                data[base + FJ_STAGES] = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                count++
            }
        }
    }

    private fun gpuDurationNs(metrics: FrameMetrics): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) metrics.getMetric(FrameMetrics.GPU_DURATION) else 0L

    /** Ambil lalu KOSONGKAN penampung; null bila frame < [FJ_MIN_FRAMES] atau jatah berkas habis. */
    fun take(): FrameSnapshot? = synchronized(lock) {
        val snap = FrameSnapshot(data, count, dropped, firstDraw)
        data = LongArray(0)
        count = 0
        dropped = 0
        firstDraw = 0
        if (snap.count >= FJ_MIN_FRAMES && filesTaken < FJ_MAX_FILES) {
            filesTaken++
            snap
        } else {
            null
        }
    }
}

/** Satu sesi pengukuran untuk satu Activity + satu tema; `start`/`stop` dipanggil dari main thread. */
internal class FrameJankSession(private val activity: Activity, private val label: String) {
    private val thread = HandlerThread(FJ_THREAD)
    private val handler by lazy { Handler(thread.looper) }
    private val lifecycle = (activity as? LifecycleOwner)?.lifecycle
    private var refreshHz = FJ_DEFAULT_HZ
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        FrameJank.record(metrics, dropped)
    }
    private val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) flush()
    }

    fun start() {
        runCatching {
            thread.start()
            activity.window.addOnFrameMetricsAvailableListener(listener, handler)
            lifecycle?.addObserver(observer)
        }.onFailure { Log.e(FJ_TAG, "start gagal", it) }
    }

    fun stop() {
        runCatching {
            lifecycle?.removeObserver(observer)
            activity.window.removeOnFrameMetricsAvailableListener(listener)
            flush()
        }.onFailure { Log.e(FJ_TAG, "stop gagal", it) }
        thread.quitSafely()
    }

    private fun flush() {
        val snap = FrameJank.take() ?: return
        activity.window.decorView.display?.refreshRate?.let { refreshHz = it }
        val budgetNs = (FJ_NS_PER_S / refreshHz).toLong()
        val header = "theme=$label refreshHz=$refreshHz budgetUs=${budgetNs / FJ_NS_PER_US} " +
            "frames=${snap.count} dropCallbacks=${snap.dropped} firstDraw=${snap.firstDraw}"
        val app = activity.applicationContext
        handler.post {
            CrashLogger.logDiagnostic(app, FJ_TAG, formatFrameReport(snap.data, snap.count, budgetNs, header))
        }
    }
}

/** Dipasang sekali di `LagFixTheme`; sesi baru tiap Activity/tema berganti, ditutup (dan ditulis) di `onDispose`. */
@Composable
internal fun FrameJankRecorder(theme: AppTheme) {
    val activity = LocalView.current.context as? Activity
    DisposableEffect(activity, theme) {
        val session = activity?.let { FrameJankSession(it, theme.name) }
        session?.start()
        onDispose { session?.stop() }
    }
}

/**
 * Teks berkas: `header`, persentil + rerata total (us), jumlah frame di atas 1x/2x/4x anggaran layar
 * (+ `over60hz` = di atas 16,667 ms, tak bergantung refresh rate yg terbaca), rerata tahap (us).
 */
internal fun formatFrameReport(data: LongArray, count: Int, budgetNs: Long, header: String): String {
    val totals = LongArray(count) { data[it * FJ_STRIDE + FJ_STAGES] }
    val sorted = totals.sortedArray()
    val over1 = totals.count { it > budgetNs }
    val over2 = totals.count { it > budgetNs * FJ_X2 }
    val over4 = totals.count { it > budgetNs * FJ_X4 }
    val over60 = totals.count { it > FJ_BUDGET_60HZ_NS }
    val avgUs = if (count == 0) 0L else totals.sum() / count / FJ_NS_PER_US
    return listOf(
        header,
        "totalUs p50=${percentileUs(sorted, FJ_P50)} p90=${percentileUs(sorted, FJ_P90)} " +
            "p95=${percentileUs(sorted, FJ_P95)} p99=${percentileUs(sorted, FJ_P99)} " +
            "max=${(sorted.lastOrNull() ?: 0L) / FJ_NS_PER_US} avg=$avgUs",
        "over1x=$over1 over2x=$over2 over4x=$over4 over60hz=$over60",
        "avgUs[all] ${stageAverages(data, count, FJ_ALL_FRAMES)}",
        "avgUs[over1x] ${stageAverages(data, count, budgetNs)}"
    ).joinToString("\n")
}

/** Rerata (us, pembulatan ke bawah) tiap tahap atas frame yang totalnya > `overNs`; 0 bila tak ada frame. */
internal fun stageAverages(data: LongArray, count: Int, overNs: Long): String {
    val sums = LongArray(FJ_STAGES)
    var frames = 0
    for (i in 0 until count) {
        val base = i * FJ_STRIDE
        if (data[base + FJ_STAGES] > overNs) {
            frames++
            for (s in 0 until FJ_STAGES) sums[s] += data[base + s]
        }
    }
    return FJ_STAGE_NAMES.indices.joinToString(" ") { s ->
        val avg = if (frames == 0) 0L else sums[s] / frames / FJ_NS_PER_US
        "${FJ_STAGE_NAMES[s]}=$avg"
    }
}

/** Persentil nearest-rank dari total terurut (ns) -> us. */
private fun percentileUs(sorted: LongArray, pct: Int): Long {
    if (sorted.isEmpty()) return 0L
    val rank = (pct * sorted.size + FJ_PERCENT - 1) / FJ_PERCENT
    return sorted[(rank - 1).coerceIn(0, sorted.size - 1)] / FJ_NS_PER_US
}
