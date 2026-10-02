package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v84: helper murni snapshot logcat (tanpa Android/Shizuku). */
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
}
