package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v157: kontrak baris riwayat. Format tulis `Prefs.record()` =
 * "dd/MM HH:mm STATUS [trigger] <durasi>ms <pesan>[ telat=<N>m]" harus terbaca utuh oleh `parseLogLine`
 * (Riwayat, kartu Statistik, widget); baris lama tanpa token trigger tetap terbaca. Fungsi murni, tanpa Android.
 */
class LogLineParserTest {

    private fun parse(line: String): ParsedLog =
        requireNotNull(parseLogLine(line)) { "baris tak terbaca: $line" }

    @Test
    fun manualOk_parsesEveryField_andConvertsStampTo12Hour() {
        val p = parse("02/10 19:05 OK [Manual] 820ms fstrim selesai")
        assertEquals("02/10 07:05 PM", p.stamp)
        assertTrue(p.ok)
        assertEquals("Manual", p.trigger)
        assertEquals("820ms fstrim selesai", p.rest)
        assertEquals(0L, p.late)
        assertFalse(p.skipped)
    }

    @Test
    fun lateToken_isStrippedFromRest_andExposedAsMinutes() {
        val p = parse("02/10 19:05 OK [Otomatis] 820ms fstrim selesai telat=45m")
        assertEquals("Otomatis", p.trigger)
        assertEquals("820ms fstrim selesai", p.rest)
        assertEquals(45L, p.late)
    }

    @Test
    fun lateToken_midLine_isNotTreatedAsLate() {
        val p = parse("02/10 19:05 OK [Otomatis] 820ms telat=5m selesai")
        assertEquals("820ms telat=5m selesai", p.rest)
        assertEquals(0L, p.late)
    }

    @Test
    fun legacyLine_withoutTriggerToken_hasEmptyTrigger() {
        val p = parse("27/09 08:00 FAIL 500ms Shizuku tidak siap")
        assertEquals("27/09 08:00 AM", p.stamp)
        assertFalse(p.ok)
        assertEquals("", p.trigger)
        assertEquals("500ms Shizuku tidak siap", p.rest)
    }

    @Test
    fun skipped_onlyForFailLineWithSkipMessage() {
        assertTrue(parse("02/10 19:05 FAIL [Otomatis] 0ms dilewati: Shizuku belum siap").skipped)
        assertFalse(parse("02/10 19:05 FAIL [Otomatis] 90ms gagal: timeout").skipped)
        assertFalse(parse("02/10 19:05 OK [Manual] 90ms catatan dilewati").skipped)
    }

    @Test
    fun emptyMessage_stillParses_withAndWithoutLateToken() {
        val plain = parse("02/10 19:05 OK [Manual] 820ms ")
        assertTrue(plain.ok)
        assertEquals("820ms ", plain.rest)

        val late = parse("02/10 19:05 OK [Otomatis] 820ms  telat=7m")
        assertEquals("820ms ", late.rest)
        assertEquals(7L, late.late)
    }

    @Test
    fun unreadableLines_returnNull() {
        assertNull(parseLogLine(""))
        assertNull(parseLogLine("bukan baris riwayat"))
        assertNull(parseLogLine("02/10 19:05 WARN [Manual] 1ms x"))
        assertNull(parseLogLine("2/10 19:05 OK [Manual] 1ms x"))
        assertNull(parseLogLine("02/10 19:05 OK"))
    }

    @Test
    fun formatInterval_minutesHoursAndDays() {
        assertEquals("1 menit", formatInterval(1L))
        assertEquals("15 menit", formatInterval(15L))
        assertEquals("1 jam", formatInterval(60L))
        assertEquals("1 jam 30 menit", formatInterval(90L))
        assertEquals("1 hari", formatInterval(1440L))
        assertEquals("1 hari 1 jam 1 menit", formatInterval(1501L))
        assertEquals("3 hari", formatInterval(4320L))
    }

    @Test
    fun formatInterval_zeroOrNegative_fallsBackToZeroMinutes() {
        assertEquals("0 menit", formatInterval(0L))
        assertEquals("0 menit", formatInterval(-5L))
    }
}
