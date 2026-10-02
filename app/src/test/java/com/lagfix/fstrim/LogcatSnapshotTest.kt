package com.lagfix.fstrim

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v84-v86: helper murni snapshot logcat (tanpa Android/Shizuku). */
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
        rx(LogcatSnapshot.PATTERN_FSTRIM).containsMatchIn(line) && !rx(LogcatSnapshot.PATTERN_FSTRIM_EXCLUDE).containsMatchIn(line)
    private fun appKept(line: String) =
        rx(LogcatSnapshot.PATTERN_APP_SCOPE).containsMatchIn(line) && rx(LogcatSnapshot.PATTERN_APP_EVENT).containsMatchIn(line)

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

    // ---- v86: dump mentah streaming + helper murni ----
    @Test
    fun copyCapped_underLimit_copiesAllNotTruncated() {
        val data = ByteArray(200_000) { (it % 251).toByte() } // > 1 buffer (64 KiB)
        val out = ByteArrayOutputStream()
        val r = copyCapped(ByteArrayInputStream(data), out, 1_000_000L)
        assertEquals(200_000L, r.bytes)
        assertTrue(!r.truncated)
        assertTrue(data.contentEquals(out.toByteArray()))
    }

    @Test
    fun copyCapped_exactlyAtLimit_notTruncated() {
        val data = ByteArray(1_000) { 7 }
        val out = ByteArrayOutputStream()
        val r = copyCapped(ByteArrayInputStream(data), out, 1_000L)
        assertEquals(1_000L, r.bytes)
        assertTrue(!r.truncated)
        assertEquals(1_000, out.size())
    }

    @Test
    fun copyCapped_overLimit_keepsFirstBytesAndMarksTruncated() {
        val data = ByteArray(150_000) { (it % 100).toByte() }
        val out = ByteArrayOutputStream()
        val r = copyCapped(ByteArrayInputStream(data), out, 70_000L) // melewati batas buffer 64 KiB
        assertEquals(70_000L, r.bytes)
        assertTrue(r.truncated)
        assertEquals(70_000, out.size())
        assertTrue(data.copyOfRange(0, 70_000).contentEquals(out.toByteArray()))
    }

    @Test
    fun copyCapped_emptyInput_zeroBytes() {
        val r = copyCapped(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), 10L)
        assertEquals(0L, r.bytes)
        assertTrue(!r.truncated)
    }

    @Test
    fun formatBytes_unitsAndBoundaries() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KiB", formatBytes(1024))
        assertEquals("1.5 KiB", formatBytes(1536))
        assertEquals("1.0 MiB", formatBytes(1024L * 1024L))
        assertEquals("3.5 MiB", formatBytes(3_670_016L))
    }

    @Test
    fun standbyBucketName_knownAndUnknown() {
        assertEquals("ACTIVE", standbyBucketName(10))
        assertEquals("RARE", standbyBucketName(40))
        assertEquals("RESTRICTED", standbyBucketName(45))
        assertEquals("bucket#77", standbyBucketName(77))
    }

    @Test
    fun feedback_shizukuNotReady_saysNoSystemLogcat() {
        val m = SnapshotSummary(false, 0, 0, 3).feedback()
        assertTrue(m.contains("TANPA logcat sistem"))
        assertTrue(m.contains("3 catatan"))
    }

    @Test
    fun feedback_rawOk_mentionsNameAndTruncation() {
        val ok = SnapshotSummary(true, 2, 5, 4, "LagFix_diag_logcatraw_x.zip", 2048L, 10L * 1024L * 1024L).feedback()
        assertTrue(ok.contains("LagFix_diag_logcatraw_x.zip"))
        assertTrue(ok.contains("2.0 KiB zip"))
        assertTrue(ok.contains("10.0 MiB logcat"))
        assertTrue(!ok.contains("TERPOTONG"))
        val cut = SnapshotSummary(true, 0, 0, 0, "a.zip", 1L, 2L, rawTruncated = true).feedback()
        assertTrue(cut.contains("TERPOTONG"))
    }

    @Test
    fun feedback_rawFailure_isNeverHidden() {
        val m = SnapshotSummary(true, 1, 1, 1, rawError = "IOException: disk penuh").feedback()
        assertTrue(m.contains("Dump mentah GAGAL"))
        assertTrue(m.contains("disk penuh"))
    }
}
