package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v84-v85: helper murni snapshot logcat (tanpa Android/Shizuku). */
class LogcatSnapshotTest {

    @Test
    fun capTail_shortText_returnedAsIs() {
        assertEquals("abc", capTail("abc", 10))
        assertEquals("abcd", capTail("abcd", 4))
        assertEquals("", capTail("", 5))
    }

    @Test
    fun capTail_longText_keepsNewestEndAndMarksTruncation() {
        val r = capTail("0123456789", 4)
        assertTrue(r.startsWith("(…terpotong"))
        assertTrue(r.endsWith("6789"))
        assertTrue(!r.contains("0123"))
    }

    @Test
    fun countNonBlankLines_ignoresBlankLines() {
        assertEquals(0, countNonBlankLines(""))
        assertEquals(0, countNonBlankLines("  \n\n \t\n"))
        assertEquals(2, countNonBlankLines("a\n\nb\n"))
        assertEquals(3, countNonBlankLines("a\r\nb\r\n  c"))
    }

    // ---- v85: pola filter (ERE sederhana -> Regex setara; grep -Ei = IGNORE_CASE) ----
    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private fun fstrimKept(line: String) =
        rx(PATTERN_FSTRIM).containsMatchIn(line) && !rx(PATTERN_FSTRIM_EXCLUDE).containsMatchIn(line)
    private fun appKept(line: String) =
        rx(PATTERN_APP_SCOPE).containsMatchIn(line) && rx(PATTERN_APP_EVENT).containsMatchIn(line)

    @Test
    fun fstrimFilter_dropsOwnPackageNoise_keepsSystemLine() {
        assertTrue(fstrimKept("10-02 16:31:33.100  1000  1200 I StorageManagerService: Running fstrim idle maintenance"))
        assertTrue(!fstrimKept("10-02 16:32:33.826  4975  5417 D THubApi : isTheMainScreen classname: com.lagfix.fstrim.MainActivity"))
    }

    @Test
    fun appFilter_keepsLifecycleStartProcAndKill_dropsOemNoise() {
        assertTrue(appKept("W PersistentTrimService: LIFECYCLE ensureShowing from=run-start shown=true"))
        assertTrue(appKept("I ActivityManager: Start proc 5705:com.lagfix.fstrim/u0a88 for broadcast"))
        assertTrue(appKept("D tranpm/TranManualCleanMgr: kill proc pid:7460, uid:10462, processName:moe.shizuku.privileged.api"))
        assertTrue(!appKept("D TranAppm/MTKModule: pkgName: com.lagfix.fstrim,pid: 0"))
        assertTrue(!appKept("I snet_event_log: updateFrameRateVote [pkg = com.lagfix.fstrim title = com.lagfix.fstrim/com.lagfix.fstrim.MainActivity refreshRate = 60.0]"))
    }

    @Test
    fun formatUtcOffset_formatsSignHoursMinutes() {
        assertEquals("UTC+00:00", formatUtcOffset(0))
        assertEquals("UTC+08:00", formatUtcOffset(8 * 3_600_000))
        assertEquals("UTC-03:30", formatUtcOffset(-(3 * 3_600_000 + 30 * 60_000)))
        assertEquals("UTC+05:45", formatUtcOffset(5 * 3_600_000 + 45 * 60_000))
    }
}
