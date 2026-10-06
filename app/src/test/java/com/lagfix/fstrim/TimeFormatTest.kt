package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Test

/** v77: format jam tampil = 12 jam (AM/PM); format simpan log tetap 24 jam. */
class TimeFormatTest {

    @Test
    fun clock12_midnight_isTwelveAm() {
        assertEquals("12:00 AM", formatClock12(0, 0))
        assertEquals("12:59 AM", formatClock12(0, 59))
    }

    @Test
    fun clock12_morning_keepsHourAndPadsToTwoDigits() {
        assertEquals("01:05 AM", formatClock12(1, 5))
        assertEquals("11:59 AM", formatClock12(11, 59))
    }

    @Test
    fun clock12_noon_isTwelvePm() {
        assertEquals("12:00 PM", formatClock12(12, 0))
        assertEquals("12:30 PM", formatClock12(12, 30))
    }

    @Test
    fun clock12_afternoonAndNight_subtractTwelve() {
        assertEquals("01:00 PM", formatClock12(13, 0))
        assertEquals("07:05 PM", formatClock12(19, 5))
        assertEquals("11:59 PM", formatClock12(23, 59))
    }

    @Test
    fun stamp12h_convertsStoredTwentyFourHourStamp() {
        assertEquals("02/10 07:05 PM", formatStamp12h("02/10 19:05"))
        assertEquals("27/09 08:00 AM", formatStamp12h("27/09 08:00"))
        assertEquals("01/01 12:00 AM", formatStamp12h("01/01 00:00"))
        assertEquals("31/12 12:15 PM", formatStamp12h("31/12 12:15"))
    }

    @Test
    fun stamp12h_unknownText_returnedAsIs() {
        assertEquals("bukan stamp", formatStamp12h("bukan stamp"))
        assertEquals("", formatStamp12h(""))
        assertEquals("02/10 7:05 PM", formatStamp12h("02/10 7:05 PM"))
    }

    // v120: penanda terlambat. gap = jarak (menit) sejak acuan; hasil = gap - interval bila >= toleransi
    // (maks 5 menit, 1/5 interval).
    private fun late(intervalMin: Long, gapMin: Long): Long {
        val ref = 1_000_000L
        return lateMinutes(ref + gapMin * 60_000L, ref, intervalMin)
    }

    @Test
    fun lateMinutes_noReferenceOrInvalidInterval_isZero() {
        assertEquals(0L, lateMinutes(5_000_000L, 0L, 1L))
        assertEquals(0L, lateMinutes(5_000_000L, 1_000_000L, 0L))
    }

    @Test
    fun lateMinutes_onTimeOrWithinTolerance_isZero() {
        assertEquals(0L, late(intervalMin = 1L, gapMin = 1L))
        assertEquals(0L, late(intervalMin = 1L, gapMin = 5L)) // telat 4 < toleransi 5
        assertEquals(0L, late(intervalMin = 15L, gapMin = 19L)) // telat 4 < 5
        assertEquals(0L, late(intervalMin = 1440L, gapMin = 1640L)) // telat 200 < 288
    }

    @Test
    fun lateMinutes_atOrAboveTolerance_returnsLateMinutes() {
        assertEquals(5L, late(intervalMin = 1L, gapMin = 6L))
        assertEquals(45L, late(intervalMin = 15L, gapMin = 60L))
        assertEquals(300L, late(intervalMin = 1440L, gapMin = 1740L))
    }
}
