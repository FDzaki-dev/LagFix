package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Test

/** v147: jurnal pengamatan `process_catch` = fungsi murni (lipat baris per kunci keadaan), data mentah. */
class CatchJournalTest {

    private val keyA = observationKey(8102, false, 300, true, true)
    private val keyB = observationKey(8102, true, 125, true, true)

    @Test
    fun observationKey_printsRawFieldsOnOneLine() {
        assertEquals("pid=8102 shown=false importance=300 toggleOn=true bgRestricted=true", keyA)
        assertEquals(
            "pid=1 shown=true importance=125 toggleOn=false bgRestricted=null",
            observationKey(1, true, 125, false, null)
        )
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
        for (pid in 1..5) rows = foldObservation(rows, observationKey(pid, false, 300, true, false), pid * 10L, 3)
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
