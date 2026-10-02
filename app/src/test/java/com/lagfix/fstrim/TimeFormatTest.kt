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
}
