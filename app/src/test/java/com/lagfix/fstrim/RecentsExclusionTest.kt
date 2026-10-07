package com.lagfix.fstrim

import org.junit.Assert.assertEquals
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
}
