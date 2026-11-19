package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v124: pemilih kematian baru, perintah logcat, penyaring jendela waktu, format berkas `diag_kill_context`.
 * v129: pemilih baris terdekat dgn waktu kematian.
 * v169: jendela waktu yg melintasi lilitan tahun (31 Des -> 1 Jan).
 */
class KillCatcherTest {

    @Test
    fun selectNewDeaths_skipsInvestigatedAndTooOld_newestFirst_max3() {
        val now = 100_000_000L
        val all = listOf(
            KillDeath(now - 1_000L, 1),
            KillDeath(now - 2_000L, 2),
            KillDeath(now - 3_000L, 3),
            KillDeath(now - 4_000L, 4),
            KillDeath(now - 50_000_000L, 5)
        )
        val picked = selectNewDeaths(all, now - 3_500L, now)
        assertEquals(listOf(1, 2, 3), picked.map { it.pid })
    }

    @Test
    fun selectNewDeaths_ignoresOlderThanLastTs() {
        val now = 10_000L
        val picked = selectNewDeaths(listOf(KillDeath(5_000L, 9)), 5_000L, now)
        assertTrue(picked.isEmpty())
    }

    @Test
    fun buildKillCommand_hasEpochSinceFilterAndPid() {
        val cmd = buildKillCommand(1_791_257_615_299L, 21951)
        assertTrue(cmd.startsWith("logcat -d -v threadtime -b main -b system -b events -b crash"))
        assertTrue(cmd.contains("-t '1791257615.299'"))
        assertTrue(cmd.contains("21951"))
        assertTrue(cmd.contains("grep -Ei"))
        assertTrue(cmd.endsWith("head -n 3000"))
    }

    @Test
    fun selectNearDeath_keepsAllWhenWithinLimits_inOriginalOrder() {
        val lines = listOf("10-06 10:00:01.000 a", "10-06 10:00:02.000 b", "10-06 10:00:03.000 c")
        assertEquals(lines, selectNearDeath(lines, "10-06 10:00:02.500", "10-06 10:00:00.000"))
    }

    @Test
    fun selectNearDeath_keepsLast100BeforeAndFirst50After_notTheFirstLines() {
        val before = (1..130).map { "10-06 10:00:%02d.%03d noise$it".format(it / 10, it % 1000) }
        val kill = "10-06 10:00:20.100 tranpm/TranManualCleanMgr: kill proc pid:1"
        val after = (1..70).map { "10-06 10:00:21.%03d after$it".format(it) }
        val kept = selectNearDeath(before + kill + after, "10-06 10:00:20.200", "10-06 10:00:00.000")
        assertEquals(150, kept.size)
        assertEquals(before.takeLast(99) + kill + after.take(50), kept)
        assertTrue(kept.contains(kill))
        assertFalse(kept.contains(before.first()))
    }

    @Test
    fun selectNearDeath_emptyInput_isEmpty() {
        assertTrue(selectNearDeath(emptyList(), "10-06 10:00:00.000", "10-06 09:59:40.000").isEmpty())
    }

    @Test
    fun buildKillCommand_padsMillisToThreeDigits() {
        assertTrue(buildKillCommand(1_000_005L, 1).contains("-t '1000.005'"))
    }

    @Test
    fun inWindow_comparesStampsAndRejectsNonLogLines() {
        val from = "10-06 08:09:20.000"
        val to = "10-06 08:09:48.000"
        assertTrue(inWindow("10-06 08:09:43.123  1000  1000 I lmkd: Kill", from, to))
        assertFalse(inWindow("10-06 08:09:19.999  1000  1000 I x: y", from, to))
        assertFalse(inWindow("10-06 08:09:48.001  1000  1000 I x: y", from, to))
        assertFalse(inWindow("--------- beginning of events", from, to))
    }

    @Test
    fun inWindow_acrossYearWrap_keepsBothSidesAndRejectsOutside() {
        val from = "12-31 23:59:40.000"
        val to = "01-01 00:00:05.000"
        assertTrue(inWindow("12-31 23:59:40.000  1000  1000 I x: awal", from, to))
        assertTrue(inWindow("12-31 23:59:59.999  1000  1000 I x: sebelum lilitan", from, to))
        assertTrue(inWindow("01-01 00:00:00.000  1000  1000 I x: sesudah lilitan", from, to))
        assertTrue(inWindow("01-01 00:00:05.000  1000  1000 I x: akhir", from, to))
        assertFalse(inWindow("12-31 23:59:39.999  1000  1000 I x: sebelum awal", from, to))
        assertFalse(inWindow("01-01 00:00:05.001  1000  1000 I x: lewat akhir", from, to))
        assertFalse(inWindow("06-15 12:00:00.000  1000  1000 I x: hari lain", from, to))
        assertFalse(inWindow("--------- beginning of events", from, to))
    }

    @Test
    fun selectNearDeath_deathBeforeYearWrap_linesAfterWrapCountAsAfter() {
        val from = "12-31 23:59:40.000"
        val before = listOf("12-31 23:59:50.000 a", "12-31 23:59:58.000 kill")
        val after = (1..60).map { "01-01 00:00:01.%03d after$it".format(it) }
        val kept = selectNearDeath(before + after, "12-31 23:59:58.500", from)
        assertEquals(before + after.take(50), kept)
    }

    @Test
    fun selectNearDeath_deathAfterYearWrap_linesBeforeWrapCountAsBefore() {
        val from = "12-31 23:59:50.000"
        val before = (0 until 130).map { "12-31 23:59:%02d.%03d noise$it".format(50 + it / 20, it % 20 * 50) }
        val kill = "01-01 00:00:02.500 kill"
        val after = (1..60).map { "01-01 00:00:04.%03d after$it".format(it) }
        val kept = selectNearDeath(before + kill + after, "01-01 00:00:03.000", from)
        assertEquals(150, kept.size)
        assertEquals(before.takeLast(99) + kill + after.take(50), kept)
    }

    @Test
    fun shizukuLogcatItems_acrossYearWrap_keepsLinesOnBothSides() {
        val output = listOf(
            "12-31 23:59:58.000  1000  1000 I ActivityManager: sebelum lilitan",
            "01-01 00:00:01.000  1000  1000 I ActivityManager: sesudah lilitan",
            "01-01 00:00:09.000  1000  1000 I ActivityManager: lewat akhir"
        ).joinToString("\n")
        val items = shizukuLogcatItems(output, "12-31 23:59:57.000", "01-01 00:00:04.000", 0, false)
        assertEquals(3, items.size)
        assertTrue(items[0].startsWith("shizuku=READY window=12-31 23:59:57.000..01-01 00:00:04.000 lines=2"))
        assertTrue(items[1].contains("sebelum lilitan"))
        assertTrue(items[2].contains("sesudah lilitan"))
    }

    @Test
    fun formatKillContext_headerThenRawLines() {
        val text = formatKillContext(2, listOf("a", "  b"))
        assertEquals("kill_context shizuku=READY newDeaths=2\na\n  b", text)
    }
}
