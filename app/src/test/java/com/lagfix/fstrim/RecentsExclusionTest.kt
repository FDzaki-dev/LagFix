package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v133: format baris log `RecentsExclusion` = angka mentah tanpa tafsiran. */
class RecentsExclusionTest {

    @Test
    fun line_excludeOn_printsRawNumbers() {
        assertEquals(
            "LIFECYCLE recentsExclusion exclude=true appTasks=1",
            recentsExclusionLine(true, 1)
        )
    }

    @Test
    fun line_excludeOffNoTasks_printsRawNumbers() {
        assertEquals(
            "LIFECYCLE recentsExclusion exclude=false appTasks=0",
            recentsExclusionLine(false, 0)
        )
    }

    @Test
    fun readBackLine_noTasks_printsCountOnly() {
        assertEquals("appTasks=0", recentsReadBackLine(emptyList()))
    }

    @Test
    fun readBackLine_printsHexFlagsAndExcludeBitPerTask() {
        assertEquals(
            "appTasks=2 flags=0x10800000 excludeBit=true | flags=0x10200000 excludeBit=false",
            recentsReadBackLine(listOf(0x10800000, 0x10200000))
        )
    }

    @Test
    fun readBackLine_nullBaseIntent_printsRawNull() {
        assertEquals("appTasks=1 baseIntent=null", recentsReadBackLine(listOf(null)))
    }

    // ---- v140: keputusan hapus-task saat user meninggalkan app (fungsi murni; bukan perilaku di perangkat) ----

    private val clear = LeaveState(
        toggleOn = true,
        breakerOpen = false,
        finishing = false,
        multiWindow = false,
        external = false,
        busy = false
    )

    @Test
    fun leaveDecision_allClear_removes() {
        assertEquals(LEAVE_REMOVE, leaveDecision(clear))
    }

    @Test
    fun leaveDecision_eachBlocker_skipsWithItsOwnReason() {
        assertEquals("skip:toggle_off", leaveDecision(clear.copy(toggleOn = false)))
        assertEquals("skip:breaker_open", leaveDecision(clear.copy(breakerOpen = true)))
        assertEquals("skip:finishing", leaveDecision(clear.copy(finishing = true)))
        assertEquals("skip:multi_window", leaveDecision(clear.copy(multiWindow = true)))
        assertEquals("skip:external", leaveDecision(clear.copy(external = true)))
        assertEquals("skip:busy", leaveDecision(clear.copy(busy = true)))
    }

    @Test
    fun leaveDecision_toggleOffBeatsEveryOtherBlocker() {
        val all = LeaveState(false, true, true, true, true, true)
        assertEquals("skip:toggle_off", leaveDecision(all))
    }

    @Test
    fun leaveDecision_priorityOrder_breakerThenFinishingThenMultiWindowThenExternalThenBusy() {
        val all = LeaveState(true, true, true, true, true, true)
        assertEquals("skip:breaker_open", leaveDecision(all))
        assertEquals("skip:finishing", leaveDecision(all.copy(breakerOpen = false)))
        assertEquals("skip:multi_window", leaveDecision(all.copy(breakerOpen = false, finishing = false)))
        assertEquals(
            "skip:external",
            leaveDecision(all.copy(breakerOpen = false, finishing = false, multiWindow = false))
        )
        assertEquals(
            "skip:busy",
            leaveDecision(all.copy(breakerOpen = false, finishing = false, multiWindow = false, external = false))
        )
    }

    @Test
    fun externalFlow_neverMarked_isFalse() {
        assertFalse(isExternalFlow(-1L, 10_000L))
    }

    @Test
    fun externalFlow_withinAndAtWindowBoundary_isTrue() {
        assertTrue(isExternalFlow(1_000L, 3_000L))
        assertTrue(isExternalFlow(1_000L, 6_000L))
        assertTrue(isExternalFlow(0L, 0L))
    }

    @Test
    fun externalFlow_afterWindowOrClockBehindMark_isFalse() {
        assertFalse(isExternalFlow(1_000L, 6_001L))
        assertFalse(isExternalFlow(5_000L, 4_999L))
    }

    @Test
    fun breakerDue_onlyAfterFullWindowAndOnlyForUncheckedRemoval() {
        assertFalse(breakerDue(removalMs = 1_000L, checkedMs = 0L, nowMs = 10_999L))
        assertTrue(breakerDue(removalMs = 1_000L, checkedMs = 0L, nowMs = 11_000L))
        assertFalse(breakerDue(removalMs = 1_000L, checkedMs = 1_000L, nowMs = 50_000L))
        assertFalse(breakerDue(removalMs = 0L, checkedMs = 0L, nowMs = 50_000L))
    }

    @Test
    fun breakerTrips_deathInsideWindowAfterRemoval_isTrue() {
        assertTrue(breakerTrips(1_000L, listOf(1_000L)))
        assertTrue(breakerTrips(1_000L, listOf(500L, 5_000L)))
        assertTrue(breakerTrips(1_000L, listOf(11_000L)))
    }

    @Test
    fun breakerTrips_deathBeforeOrFarAfterRemovalOrNoRemoval_isFalse() {
        assertFalse(breakerTrips(1_000L, listOf(999L)))
        assertFalse(breakerTrips(1_000L, listOf(11_001L)))
        assertFalse(breakerTrips(1_000L, emptyList()))
        assertFalse(breakerTrips(0L, listOf(0L, 5_000L)))
    }

    @Test
    fun formatDiag_printsRawValuesWithoutInterpretation() {
        assertEquals(
            "leaveGuard removals=2 lastDecision=skip:busy lastDecisionAt=1791379211640 trippedAt=0",
            LeaveGuard.formatDiag(2, "skip:busy", 1791379211640L, 0L)
        )
    }
}
