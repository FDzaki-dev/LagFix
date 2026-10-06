package com.lagfix.fstrim

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v112: log `diag_persistent_service` jujur — kembali tanpa exception BUKAN bukti notifikasi tampil. */
class DescribeStartOutcomeTest {

    @Test
    fun failure_reportedAsFailed_regardlessOfShown() {
        val text = describeStartOutcome(IllegalStateException("ditolak"), true, true, DELAY_MS)
        assertTrue(text.contains("GAGAL"))
        assertTrue(text.contains("ditolak"))
    }

    @Test
    fun notShownImmediately_butShownAfterDelay_isReportedAsVisible() {
        val text = describeStartOutcome(null, shownNow = false, shownLater = true, delayMs = DELAY_MS)
        assertTrue(text.contains("TERLIHAT"))
        assertTrue(text.contains("cek segera: tidak terlihat"))
        assertTrue(text.contains("setelah ${DELAY_MS}ms: terlihat"))
        assertFalse(text.contains("TIDAK terlihat"))
    }

    @Test
    fun notShownEvenAfterDelay_isNotReportedAsSuccess() {
        val text = describeStartOutcome(null, shownNow = false, shownLater = false, delayMs = DELAY_MS)
        assertTrue(text.contains("TIDAK terlihat"))
        assertTrue(text.contains("BUKAN bukti"))
        assertFalse(text.contains("SUKSES"))
    }

    private companion object {
        const val DELAY_MS = 1500L
    }
}
