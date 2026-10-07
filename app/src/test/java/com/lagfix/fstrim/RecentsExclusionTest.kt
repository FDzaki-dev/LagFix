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
}
