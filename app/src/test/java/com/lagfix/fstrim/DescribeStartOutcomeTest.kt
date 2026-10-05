package com.lagfix.fstrim

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v111: log `diag_persistent_service` jujur — kembali tanpa exception BUKAN bukti notifikasi tampil. */
class DescribeStartOutcomeTest {

    @Test
    fun failure_reportedAsFailed_regardlessOfShown() {
        val text = describeStartOutcome(IllegalStateException("ditolak"), shownNow = true)
        assertTrue(text.contains("GAGAL"))
        assertTrue(text.contains("ditolak"))
    }

    @Test
    fun returnedAndShown_reportedAsVisible() {
        val text = describeStartOutcome(null, shownNow = true)
        assertTrue(text.contains("TERLIHAT"))
        assertFalse(text.contains("TIDAK terlihat"))
    }

    @Test
    fun returnedButNotShown_isNotReportedAsSuccess() {
        val text = describeStartOutcome(null, shownNow = false)
        assertTrue(text.contains("TIDAK terlihat"))
        assertTrue(text.contains("BUKAN bukti"))
        assertFalse(text.contains("SUKSES"))
    }
}
