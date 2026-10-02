package com.lagfix.fstrim

/** Hasil baca `settings get global fstrim_mandatory_interval`. */
sealed class BootTrimReading {
    /** Kunci belum diatur -> Android memakai nilai bawaannya sendiri. */
    data object Unset : BootTrimReading()

    /** Kunci terisi [ms] milisekon. */
    data class Value(val ms: Long) : BootTrimReading()

    /** Perintah gagal / keluaran tak bisa dibaca (tidak diklaim sebagai Unset). */
    data class Unreadable(val detail: String) : BootTrimReading()
}

/** Hasil menulis/reset: [ok] hanya true bila baca-ulang membuktikan nilainya berubah. */
data class BootTrimOutcome(val ok: Boolean, val reading: BootTrimReading, val message: String)

/** State UI kartu "Paksa trim saat reboot" ([reading] null = belum/tak bisa dibaca). */
data class BootTrimUi(
    val reading: BootTrimReading? = null,
    val busy: Boolean = false,
    val note: String? = null
)

private const val MS_PER_DAY = 86_400_000L

// v92 (detekt MagicNumber): batas potong teks detail/pesan; nilai identik dgn literal sebelumnya.
private const val DETAIL_MAX_CHARS = 80
private const val MESSAGE_MAX_CHARS = 120

/** Parsing murni keluaran `settings get global ...` (mudah dites tanpa Shizuku). */
internal fun parseBootTrimReading(out: String): BootTrimReading {
    val t = out.trim()
    return when {
        t.isEmpty() -> BootTrimReading.Unreadable("keluaran kosong")
        t == "null" -> BootTrimReading.Unset
        else -> t.toLongOrNull()?.let { BootTrimReading.Value(it) }
            ?: BootTrimReading.Unreadable(t.take(DETAIL_MAX_CHARS))
    }
}

/** Teks tampil untuk [r] — hanya format, tanpa klaim perilaku sistem. */
internal fun describeBootTrim(r: BootTrimReading): String = when (r) {
    BootTrimReading.Unset -> "tidak diatur (default Android)"
    is BootTrimReading.Value -> when {
        r.ms == BootTrimSetting.EVERY_REBOOT_MS -> "1 ms (paksa tiap reboot)"
        r.ms > 0L && r.ms % MS_PER_DAY == 0L -> "${r.ms / MS_PER_DAY} hari (${r.ms} ms)"
        else -> "${r.ms} ms"
    }
    is BootTrimReading.Unreadable -> "tidak terbaca (${r.detail})"
}

/**
 * v83 (H2(1), perintah user): setting sistem `fstrim_mandatory_interval` = batas "paksa fstrim"
 * saat boot (sumber: thread XDA mFSTRIM; BELUM diverifikasi di HP ini). Ditulis lewat Shizuku
 * (shell UID, `settings put global`), SELALU dibaca-ulang sebagai bukti, dan bisa dihapus
 * (`settings delete global`) krn nilainya bertahan walau app di-uninstall.
 * Semua fungsi BLOCKING — panggil hanya dari Dispatchers.IO. Perintah = konstanta, 0 input user.
 */
object BootTrimSetting {
    const val KEY = "fstrim_mandatory_interval"

    /** 1 ms = selalu "terlambat" -> dipaksa tiap boot (per laporan XDA). */
    const val EVERY_REBOOT_MS = 1L

    // Batas I/O-shell (Shizuku/binder): kegagalan apa pun dilaporkan sbg Unreadable, bukan crash
    // -> catch Throwable DISENGAJA.
    @Suppress("TooGenericExceptionCaught")
    fun read(): BootTrimReading = try {
        val (code, out) = FstrimExecutor.sh("settings get global $KEY")
        if (code == 0) {
            parseBootTrimReading(out)
        } else {
            BootTrimReading.Unreadable("exit=$code ${out.trim().take(DETAIL_MAX_CHARS)}".trim())
        }
    } catch (e: Throwable) {
        BootTrimReading.Unreadable("${e.javaClass.simpleName}: ${e.message}".take(DETAIL_MAX_CHARS))
    }

    fun enableEveryReboot(): BootTrimOutcome = write(
        cmd = "settings put global $KEY $EVERY_REBOOT_MS",
        successMessage = "Berhasil: nilai sistem kini 1 ms (dibaca ulang).",
        verify = { it == BootTrimReading.Value(EVERY_REBOOT_MS) }
    )

    fun reset(): BootTrimOutcome = write(
        cmd = "settings delete global $KEY",
        successMessage = "Berhasil: nilai sistem dihapus, kembali ke default Android (dibaca ulang).",
        verify = { it == BootTrimReading.Unset }
    )

    // Batas I/O-shell yg sama dgn read(): gagal eksekusi -> BootTrimOutcome(ok=false), bukan crash.
    @Suppress("TooGenericExceptionCaught")
    private fun write(cmd: String, successMessage: String, verify: (BootTrimReading) -> Boolean): BootTrimOutcome {
        val res = try {
            FstrimExecutor.sh(cmd)
        } catch (e: Throwable) {
            val failMessage = "Gagal: ${e.javaClass.simpleName}: ${e.message}".take(MESSAGE_MAX_CHARS)
            return BootTrimOutcome(false, read(), failMessage)
        }
        val after = read()
        return if (verify(after)) {
            BootTrimOutcome(true, after, successMessage)
        } else {
            val detail = "exit=${res.first} ${res.second.trim().take(DETAIL_MAX_CHARS)}".trim()
            BootTrimOutcome(false, after, "Gagal ($detail). Nilai terbaca: ${describeBootTrim(after)}")
        }
    }
}
