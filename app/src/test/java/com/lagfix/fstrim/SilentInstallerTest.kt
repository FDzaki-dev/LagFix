package com.lagfix.fstrim

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v179: bagian murni pemasangan senyap via Shizuku (perintah & penilaian hasil); v181: pengaman paket;
 * v182: detail & penilaian saat pengaliran APK gagal; v188: `feedApk` (batas waktu pengaliran) dgn Process palsu.
 * Jalur Shizuku/disk tak diuji.
 */
class SilentInstallerTest {
    @Test
    fun `perintah pasang membawa ukuran APK dan menggabung galat ke keluaran`() {
        assertEquals("pm install -r -S 26214400 2>&1", SilentInstaller.installCommand(26_214_400L))
    }

    @Test
    fun `sukses hanya bila kode keluar 0 dan keluaran memuat Success`() {
        assertTrue(SilentInstaller.isInstallSuccess(0, "Success\n"))
        assertTrue(SilentInstaller.isInstallSuccess(0, "success"))
    }

    @Test
    fun `kegagalan pm tidak dianggap sukses`() {
        assertFalse(SilentInstaller.isInstallSuccess(1, "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]"))
        assertFalse(SilentInstaller.isInstallSuccess(0, "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]"))
        assertFalse(SilentInstaller.isInstallSuccess(null, "Success"))
        assertFalse(SilentInstaller.isInstallSuccess(0, ""))
    }

    @Test
    fun `paket APK sama dengan paket aplikasi tidak ditolak`() {
        assertNull(SilentInstaller.packageMismatch("com.lagfix.fstrim", "com.lagfix.fstrim"))
    }

    @Test
    fun `paket APK lain ditolak dengan nama kedua paket`() {
        assertEquals(
            "Paket APK (com.contoh.lain) bukan com.lagfix.fstrim",
            SilentInstaller.packageMismatch("com.contoh.lain", "com.lagfix.fstrim")
        )
    }

    @Test
    fun `paket APK tak terbaca ditolak dan ditulis null apa adanya`() {
        assertEquals(
            "Paket APK (null) bukan com.lagfix.fstrim",
            SilentInstaller.packageMismatch(null, "com.lagfix.fstrim")
        )
    }

    @Test
    fun `detail tanpa galat pengaliran sama dengan format sebelum v182`() {
        assertEquals("exit=0 timedOut=false\nSuccess\n", SilentInstaller.installDetail(0, false, "Success\n", null))
    }

    @Test
    fun `detail memuat galat pengaliran sebelum keluaran mentah pm dan exit null ditulis null`() {
        assertEquals(
            "exit=null timedOut=false\nfeed=IOException: Broken pipe\nFailure [INSTALL_FAILED_USER_RESTRICTED]",
            SilentInstaller.installDetail(
                null,
                false,
                "Failure [INSTALL_FAILED_USER_RESTRICTED]",
                "IOException: Broken pipe"
            )
        )
    }

    @Test
    fun `galat pengaliran APK tidak pernah dinilai sukses walau keluaran memuat Success`() {
        assertFalse(SilentInstaller.isInstallSuccess(0, "Success", feedFailed = true))
        assertTrue(SilentInstaller.isInstallSuccess(0, "Success", feedFailed = false))
    }

    @Test
    fun `feedApk mengalirkan seluruh APK lalu menutup stdin`() {
        val bytes = ByteArray(FEED_BYTES) { (it % BYTE_MOD).toByte() }
        val stdin = TrackingStdin()
        assertNull(SilentInstaller.feedApk(FeedProcess(stdin), tempApk(bytes), GENEROUS_MS))
        assertTrue(stdin.closed)
        assertArrayEquals(bytes, stdin.toByteArray())
    }

    @Test
    fun `feedApk melepas penulis yang macet pada batas waktu dan menghentikan proses`() {
        val release = CountDownLatch(1)
        val destroyed = AtomicBoolean(false)
        val stuck = object : OutputStream() {
            override fun write(b: Int) {
                release.await()
                throw IOException("ditutup")
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                release.await()
                throw IOException("ditutup")
            }
        }
        val process = FeedProcess(stuck) {
            destroyed.set(true)
            release.countDown()
        }
        val t0 = System.nanoTime()
        val failure = SilentInstaller.feedApk(process, tempApk(ByteArray(FEED_BYTES)), SHORT_MS)
        val elapsedMs = (System.nanoTime() - t0) / NANOS_PER_MS
        assertTrue(destroyed.get())
        assertEquals("Pengaliran APK melewati $SHORT_MS ms (stdin pm tak dibaca)", failure?.message)
        assertTrue("harus kembali jauh sebelum 15 dtk, nyatanya $elapsedMs ms", elapsedMs < MAX_ELAPSED_MS)
    }

    @Test
    fun `feedApk mengembalikan galat tulis IOException apa adanya`() {
        val broken = object : OutputStream() {
            override fun write(b: Int) {
                throw IOException("Broken pipe")
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                throw IOException("Broken pipe")
            }
        }
        val failure = SilentInstaller.feedApk(FeedProcess(broken), tempApk(ByteArray(FEED_BYTES)), GENEROUS_MS)
        assertEquals("Broken pipe", failure?.message)
    }

    @Test
    fun `feedApk membungkus galat non IO agar tak menjadi uncaught exception`() {
        val bomb = object : OutputStream() {
            override fun write(b: Int) {
                throw UnsupportedOperationException("boom")
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                throw UnsupportedOperationException("boom")
            }
        }
        val failure = SilentInstaller.feedApk(FeedProcess(bomb), tempApk(ByteArray(FEED_BYTES)), GENEROUS_MS)
        assertEquals("UnsupportedOperationException: boom", failure?.message)
    }

    private fun tempApk(content: ByteArray): File =
        File.createTempFile("lagfix_feed", ".apk").also {
            it.deleteOnExit()
            it.writeBytes(content)
        }

    /** stdin palsu: menampung byte & mencatat `close()`. */
    private class TrackingStdin : ByteArrayOutputStream() {
        @Volatile
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }

    /** Process palsu: hanya stdin yang berarti; [onDestroy] dipanggil oleh `destroy()`. */
    private class FeedProcess(private val stdin: OutputStream, private val onDestroy: () -> Unit = {}) : Process() {
        override fun getOutputStream(): OutputStream = stdin

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = 0

        override fun exitValue(): Int = 0

        override fun destroy() = onDestroy()
    }

    private companion object {
        const val FEED_BYTES = 4096
        const val BYTE_MOD = 251
        const val GENEROUS_MS = 20_000L
        const val SHORT_MS = 300L
        const val NANOS_PER_MS = 1_000_000L
        const val MAX_ELAPSED_MS = 15_000L
    }
}
