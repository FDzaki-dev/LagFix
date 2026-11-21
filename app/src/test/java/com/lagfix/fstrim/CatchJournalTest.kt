package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v147: jurnal pengamatan `process_catch` = fungsi murni (lipat baris per kunci keadaan), data mentah. */
class CatchJournalTest {

    private val keyA = observationKey(8102, formatNotifState(false, true), 300, true, true)
    private val keyB = observationKey(8102, formatNotifState(true, true), 125, true, true)

    @Test
    fun observationKey_printsRawFieldsOnOneLine() {
        assertEquals("pid=8102 shown=false present=true importance=300 toggleOn=true bgRestricted=true", keyA)
        assertEquals(
            "pid=1 shown=true present=null importance=125 toggleOn=false bgRestricted=null",
            observationKey(1, formatNotifState(true, null), 125, false, null)
        )
    }

    @Test
    fun formatNotifState_printsShownAndPresentRaw_nullLiteralWhenReadFailed() {
        assertEquals("shown=false present=true", formatNotifState(false, true))
        assertEquals("shown=false present=false", formatNotifState(false, false))
        assertEquals("shown=false present=null", formatNotifState(false, null))
    }

    @Test
    fun notificationPresent_trueWhenIdListedRegardlessOfFlags() {
        val read = Result.success(listOf(NotifSnapshot(42, null, "lagfix_keep_alive_high", 0x2000a, 1L)))
        assertEquals(true, notificationPresent(read, 42))
    }

    @Test
    fun notificationPresent_falseWhenIdAbsent() {
        val read = Result.success(listOf(NotifSnapshot(7, null, "other", 0x2, 1L)))
        assertFalse(notificationPresent(read, 42) ?: true)
        assertTrue(notificationPresent(Result.success(emptyList()), 42) == false)
    }

    @Test
    fun notificationPresent_nullWhenReadFailed() {
        assertNull(notificationPresent(Result.failure(SecurityException("raw")), 42))
    }

    @Test
    fun fold_firstObservation_startsRowWithCountOne() {
        assertEquals(listOf("$keyA first=1000 last=1000 n=1"), foldObservation(emptyList(), keyA, 1000L, 12))
    }

    @Test
    fun fold_sameKey_updatesLastRowKeepingFirstAndCounting() {
        val rows = foldObservation(foldObservation(emptyList(), keyA, 1000L, 12), keyA, 2500L, 12)
        assertEquals(listOf("$keyA first=1000 last=2500 n=2"), rows)
    }

    @Test
    fun fold_changedKey_appendsNewRow() {
        val rows = foldObservation(foldObservation(emptyList(), keyB, 1000L, 12), keyA, 2000L, 12)
        assertEquals(listOf("$keyB first=1000 last=1000 n=1", "$keyA first=2000 last=2000 n=1"), rows)
    }

    @Test
    fun fold_overCapacity_dropsOldestRows() {
        var rows = emptyList<String>()
        val state = formatNotifState(false, false)
        for (pid in 1..5) rows = foldObservation(rows, observationKey(pid, state, 300, true, false), pid * 10L, 3)
        assertEquals(3, rows.size)
        assertEquals("pid=3", rows.first().take(5))
        assertEquals("pid=5", rows.last().take(5))
    }

    @Test
    fun fold_malformedLastRow_stillFoldsWithDefaults() {
        val rows = foldObservation(listOf("$keyA first=oops"), keyA, 7L, 12)
        assertEquals(listOf("$keyA first=7 last=7 n=1"), rows)
    }
}
