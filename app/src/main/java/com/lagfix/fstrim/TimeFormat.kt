package com.lagfix.fstrim

import java.util.Locale

// v77: semua jam yang TAMPIL ke user memakai format 12 jam (AM/PM). Baris log yang TERSIMPAN tetap
// "dd/MM HH:mm" (24 jam; Prefs.record() + PrefsTest tak diubah, baris lama di HP user tetap terbaca) ->
// konversi hanya terjadi saat ditampilkan. Fungsi murni (0 dependensi Android) supaya bisa dites unit.

private const val HOURS_HALF_DAY = 12
private val storedStampRegex = Regex("""^(\d{2}/\d{2}) (\d{2}):(\d{2})$""")

/** 0->"12:xx AM", 1..11->"0h:xx AM", 12->"12:xx PM", 13..23->"0h:xx PM". Selalu 2 digit, AM/PM ASCII. */
internal fun formatClock12(hour24: Int, minute: Int): String {
    val hour12 = if (hour24 % HOURS_HALF_DAY == 0) HOURS_HALF_DAY else hour24 % HOURS_HALF_DAY
    val period = if (hour24 < HOURS_HALF_DAY) "AM" else "PM"
    return String.format(Locale.US, "%02d:%02d %s", hour12, minute, period)
}

/** "02/10 19:05" (format simpan, 24 jam) -> "02/10 07:05 PM". Teks yang tak cocok pola dikembalikan apa adanya. */
internal fun formatStamp12h(stamp: String): String {
    val match = storedStampRegex.matchEntire(stamp) ?: return stamp
    val (date, hour, minute) = match.destructured // v94 (detekt Destructuring): 3 grup, tanpa teks penuh
    return "$date ${formatClock12(hour.toInt(), minute.toInt())}"
}
