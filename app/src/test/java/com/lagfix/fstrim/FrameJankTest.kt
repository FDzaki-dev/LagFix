package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v146: format berkas `diag_frame_metrics` = fungsi murni (tanpa FrameMetrics/Android). */
class FrameJankTest {
    private val budgetNs = 10_000_000L

    /** Tiap pasangan = (nilai SEMUA tahap, total) dalam ns. */
    private fun rows(vararg frames: Pair<Long, Long>): LongArray {
        val data = LongArray(frames.size * FJ_STRIDE)
        frames.forEachIndexed { i, (stage, total) ->
            for (s in 0 until FJ_STAGES) data[i * FJ_STRIDE + s] = stage
            data[i * FJ_STRIDE + FJ_STAGES] = total
        }
        return data
    }

    @Test
    fun report_countsFramesOverEachBudgetMultiple() {
        val data = rows(1L to 4_000_000L, 1L to 12_000_000L, 1L to 25_000_000L, 1L to 45_000_000L)
        val report = formatFrameReport(data, 4, budgetNs, "H")
        assertTrue(report, report.contains("over1x=3 over2x=2 over4x=1"))
    }

    @Test
    fun report_percentilesUseNearestRankInMicroseconds() {
        val frames = (1..10).map { 1L to it * 1_000_000L }.toTypedArray()
        val report = formatFrameReport(rows(*frames), 10, budgetNs, "H")
        assertTrue(report, report.contains("totalUs p50=5000 p90=9000 p95=10000 p99=10000 max=10000"))
    }

    @Test
    fun stageAverages_overBudgetOnly_excludeFastFrames() {
        val data = rows(1_000_000L to 4_000_000L, 3_000_000L to 12_000_000L, 5_000_000L to 25_000_000L)
        val all = "unknown=3000 input=3000 anim=3000 layout=3000 draw=3000 sync=3000 cmd=3000 swap=3000"
        val slow = "unknown=4000 input=4000 anim=4000 layout=4000 draw=4000 sync=4000 cmd=4000 swap=4000"
        assertEquals(all, stageAverages(data, 3, -1L))
        assertEquals(slow, stageAverages(data, 3, budgetNs))
    }

    @Test
    fun report_withoutFrames_printsZerosAndKeepsHeaderFirst() {
        val report = formatFrameReport(LongArray(0), 0, budgetNs, "HEADER")
        assertTrue(report, report.startsWith("HEADER\n"))
        assertTrue(report, report.contains("totalUs p50=0 p90=0 p95=0 p99=0 max=0"))
        assertTrue(report, report.contains("over1x=0 over2x=0 over4x=0"))
        assertTrue(report, report.contains("avgUs[over1x] unknown=0 input=0"))
    }
}
