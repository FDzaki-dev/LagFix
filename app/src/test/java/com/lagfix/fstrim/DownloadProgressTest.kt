package com.lagfix.fstrim

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** v171: progres unduhan APK (murni, tanpa Android/jaringan): persen, teks MB, jarak laporan, dan salinan berlapor. */
class DownloadProgressTest {
    private fun copyAndCollect(data: ByteArray, total: Long): Pair<ByteArray, List<Pair<Long, Long>>> {
        val out = ByteArrayOutputStream()
        val reports = mutableListOf<Pair<Long, Long>>()
        copyWithProgress(ByteArrayInputStream(data), out, total) { done, t -> reports += done to t }
        return out.toByteArray() to reports
    }

    @Test
    fun `persen dari total diketahui dibulatkan ke bawah dan dijepit 0 sampai 100`() {
        assertEquals(0, DownloadProgress(0L, 1000L).percent)
        assertEquals(42, DownloadProgress(429L, 1000L).percent)
        assertEquals(99, DownloadProgress(999L, 1000L).percent)
        assertEquals(100, DownloadProgress(1000L, 1000L).percent)
        assertEquals(100, DownloadProgress(5000L, 1000L).percent)
        assertEquals(0, DownloadProgress(-5L, 1000L).percent)
        assertEquals(0.5f, DownloadProgress(50L, 100L).fraction)
        assertEquals(1f, DownloadProgress(100L, 100L).fraction)
    }

    @Test
    fun `total tak diketahui tak punya persen maupun pecahan`() {
        assertNull(DownloadProgress(123L, -1L).percent)
        assertNull(DownloadProgress(123L, -1L).fraction)
        assertNull(DownloadProgress(0L, 0L).percent)
        assertNull(DownloadProgress(0L, 0L).fraction)
    }

    @Test
    fun `formatMegabytes satu desimal koma Indonesia dibulatkan dan negatif jadi nol`() {
        assertEquals("0,0 MB", formatMegabytes(0L))
        assertEquals("1,0 MB", formatMegabytes(1_000_000L))
        assertEquals("7,4 MB", formatMegabytes(7_400_000L))
        assertEquals("7,4 MB", formatMegabytes(7_449_999L))
        assertEquals("7,5 MB", formatMegabytes(7_450_000L))
        assertEquals("123,5 MB", formatMegabytes(123_456_789L))
        assertEquals("0,0 MB", formatMegabytes(-42L))
    }

    @Test
    fun `downloadStatusLine untuk belum terhubung, total diketahui, dan total tak diketahui`() {
        assertEquals("Menghubungkan ke GitHub…", downloadStatusLine(null))
        assertEquals("3,1 MB dari 7,4 MB", downloadStatusLine(DownloadProgress(3_100_000L, 7_400_000L)))
        assertEquals("3,1 MB diunduh", downloadStatusLine(DownloadProgress(3_100_000L, -1L)))
    }

    @Test
    fun `progressStepBytes seperseratus total, minimal 1, 256 KiB bila total tak diketahui`() {
        assertEquals(74_000L, progressStepBytes(7_400_000L))
        assertEquals(1L, progressStepBytes(50L))
        assertEquals(262_144L, progressStepBytes(-1L))
        assertEquals(262_144L, progressStepBytes(0L))
    }

    @Test
    fun `salinan dgn total diketahui identik, laporan awal nol, akhir penuh, naik terus, tak lebih dari 102`() {
        val data = ByteArray(1_000_000) { (it % 251).toByte() }
        val (copied, reports) = copyAndCollect(data, data.size.toLong())
        assertArrayEquals(data, copied)
        assertEquals(0L to data.size.toLong(), reports.first())
        assertEquals(data.size.toLong() to data.size.toLong(), reports.last())
        assertTrue(reports.zipWithNext().all { (a, b) -> b.first > a.first })
        assertTrue("laporan = ${reports.size}", reports.size in 3..102)
    }

    @Test
    fun `salinan dgn total tak diketahui melapor tiap 256 KiB dan sekali di akhir`() {
        val data = ByteArray(1_000_000) { (it % 251).toByte() }
        val (copied, reports) = copyAndCollect(data, -1L)
        assertArrayEquals(data, copied)
        assertEquals(listOf(0L, 262_144L, 524_288L, 786_432L, 1_000_000L), reports.map { it.first })
        assertTrue(reports.all { it.second == -1L })
    }

    @Test
    fun `salinan kosong hanya melapor awal, dan byte terakhir yg sudah terlapor tak dilapor ulang`() {
        val (emptyCopy, emptyReports) = copyAndCollect(ByteArray(0), 0L)
        assertEquals(0, emptyCopy.size)
        assertEquals(listOf(0L to 0L), emptyReports)

        val (_, exactReports) = copyAndCollect(ByteArray(100) { it.toByte() }, 100L)
        assertEquals(listOf(0L to 100L, 100L to 100L), exactReports)
    }

    @Test
    fun `galat baca di tengah salinan diteruskan apa adanya setelah laporan yg sudah terjadi`() {
        val failing = object : InputStream() {
            private var reads = 0

            override fun read(): Int = throw IOException("putus")

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reads++
                if (reads > 2) throw IOException("putus")
                return minOf(len, 100).also { count -> b.fill(1, off, off + count) }
            }
        }
        val reports = mutableListOf<Long>()
        val failure = assertThrows(IOException::class.java) {
            copyWithProgress(failing, ByteArrayOutputStream(), 200L) { done, _ -> reports += done }
        }
        assertEquals("putus", failure.message)
        assertEquals(listOf(0L, 100L, 200L), reports)
    }
}
