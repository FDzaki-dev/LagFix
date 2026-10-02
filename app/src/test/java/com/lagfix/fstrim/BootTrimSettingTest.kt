package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v83 (H2): parsing keluaran `settings get global` + teks tampil. Tanpa Shizuku/Android. */
class BootTrimSettingTest {

    @Test
    fun parse_null_isUnset() {
        assertEquals(BootTrimReading.Unset, parseBootTrimReading("null"))
        assertEquals(BootTrimReading.Unset, parseBootTrimReading("null\n"))
    }

    @Test
    fun parse_number_isValue() {
        assertEquals(BootTrimReading.Value(1L), parseBootTrimReading("1\n"))
        assertEquals(BootTrimReading.Value(259200000L), parseBootTrimReading(" 259200000 \n"))
    }

    @Test
    fun parse_emptyOrGarbage_isUnreadable_neverUnset() {
        assertTrue(parseBootTrimReading("") is BootTrimReading.Unreadable)
        assertTrue(parseBootTrimReading("  \n") is BootTrimReading.Unreadable)
        assertTrue(parseBootTrimReading("Exception occurred while executing") is BootTrimReading.Unreadable)
        assertTrue(parseBootTrimReading("WARNING\n1") is BootTrimReading.Unreadable)
    }

    @Test
    fun describe_formatsEachReading() {
        assertEquals("tidak diatur (default Android)", describeBootTrim(BootTrimReading.Unset))
        assertEquals("1 ms (paksa tiap reboot)", describeBootTrim(BootTrimReading.Value(1L)))
        assertEquals("3 hari (259200000 ms)", describeBootTrim(BootTrimReading.Value(259200000L)))
        assertEquals("7 ms", describeBootTrim(BootTrimReading.Value(7L)))
        assertEquals("0 ms", describeBootTrim(BootTrimReading.Value(0L)))
        assertEquals("tidak terbaca (x)", describeBootTrim(BootTrimReading.Unreadable("x")))
    }
}
