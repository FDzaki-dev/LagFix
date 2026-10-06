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

// v120 (permintaan user: penanda terlambat di Riwayat; HP Doze menunda job & run baru tercatat saat layar
// dinyalakan): murni aritmetika, 0 dependensi Android supaya bisa dites unit.
private const val LATE_MS_PER_MINUTE = 60_000L
private const val LATE_MIN_TOLERANCE_MINUTES = 5L // jitter normal WorkManager/JobScheduler tak ditandai
private const val LATE_TOLERANCE_DIVISOR = 5L // toleransi minimal 5 menit, atau 1/5 interval bila lebih besar

/**
 * Menit keterlambatan run Otomatis = (jarak sejak acuan - interval). Acuan = run Otomatis sebelumnya atau saat
 * jadwal diterapkan. 0 bila tak ada acuan (<= 0), interval <= 0, atau keterlambatan masih di bawah toleransi.
 */
internal fun lateMinutes(nowMs: Long, refMs: Long, intervalMin: Long): Long {
    if (refMs <= 0L || intervalMin <= 0L) return 0L
    val late = (nowMs - refMs) / LATE_MS_PER_MINUTE - intervalMin
    val tolerance = maxOf(LATE_MIN_TOLERANCE_MINUTES, intervalMin / LATE_TOLERANCE_DIVISOR)
    return if (late >= tolerance) late else 0L
}
