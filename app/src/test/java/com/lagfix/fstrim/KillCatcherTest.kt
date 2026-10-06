package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v124: pemilih kematian baru, perintah logcat, penyaring jendela waktu, dan format berkas `diag_kill_context`. */
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
        assertTrue(cmd.endsWith("head -n 300"))
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
    fun formatKillContext_headerThenRawLines() {
        val text = formatKillContext(2, listOf("a", "  b"))
        assertEquals("kill_context shizuku=READY newDeaths=2\na\n  b", text)
    }
}
