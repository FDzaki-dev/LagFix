package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch

/**
 * v167: `BoundedShell.run` SELALU membereskan proses (stdin/stderr ditutup + destroy), juga saat timeout/galat.
 * v170: `release` dipanggil langsung oleh `LogcatSnapshot.captureRaw` (dump mentah, tak lewat `run`).
 */
class BoundedShellReleaseTest {
    /** Baca macet sampai ditutup (meniru pipa proses yg tak kunjung EOF); `close()` = pipa putus -> EOF. */
    private class BlockingInput : InputStream() {
        private val released = CountDownLatch(1)

        override fun read(): Int {
            released.await()
            return -1
        }

        override fun close() = released.countDown()
    }

    private class FakeProcess(
        private val stdout: InputStream,
        private val exit: Int = 0,
        private val destroyFails: Boolean = false,
        private val exitFailsAfterDestroy: Boolean = false
    ) : Process() {
        @Volatile var destroyed = false

        @Volatile var stdinClosed = false

        @Volatile var stderrClosed = false
        private val stdin = object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun close() {
                stdinClosed = true
            }
        }
        private val stderr = object : ByteArrayInputStream(ByteArray(0)) {
            override fun close() {
                stderrClosed = true
            }
        }

        override fun getOutputStream(): OutputStream = stdin
        override fun getInputStream(): InputStream = stdout
        override fun getErrorStream(): InputStream = stderr
        override fun waitFor(): Int = exit
        override fun exitValue(): Int {
            check(!(exitFailsAfterDestroy && destroyed)) { "exitValue sesudah destroy" }
            return exit
        }

        override fun destroy() {
            destroyed = true
            runCatching { stdout.close() } // proses dihentikan -> pipa keluaran putus
            if (destroyFails) throw IllegalStateException("destroy gagal")
        }
    }

    private fun streamOf(text: String): InputStream = ByteArrayInputStream(text.toByteArray())

    @Test
    fun normalRun_releasesProcessAfterReadingExit() {
        val p = FakeProcess(streamOf("ok"), exit = EXIT_CODE_SEVEN, exitFailsAfterDestroy = true)
        val r = BoundedShell.run(TIMEOUT_GENEROUS_MS) { p }
        assertFalse(r.timedOut)
        assertEquals("kode keluar dibaca SEBELUM destroy", EXIT_CODE_SEVEN, r.exit)
        assertEquals("ok", r.output)
        assertTrue("destroy", p.destroyed)
        assertTrue("stdin ditutup", p.stdinClosed)
        assertTrue("stderr ditutup", p.stderrClosed)
    }

    @Test
    fun timeout_stopsReaderAndReleasesProcess() {
        val p = FakeProcess(BlockingInput())
        val r = BoundedShell.run(TIMEOUT_SHORT_MS) { p }
        assertTrue(r.timedOut)
        assertNull(r.exit)
        assertTrue("destroy", p.destroyed)
        assertTrue("stdin ditutup", p.stdinClosed)
        assertTrue("stderr ditutup", p.stderrClosed)
    }

    @Test
    fun destroyThrows_resultStillReturned() {
        val p = FakeProcess(streamOf("ok"), exit = 0, destroyFails = true)
        val r = BoundedShell.run(TIMEOUT_GENEROUS_MS) { p }
        assertFalse(r.timedOut)
        assertEquals(0, r.exit)
        assertEquals("ok", r.output)
        assertTrue("destroy dicoba", p.destroyed)
    }

    @Test
    fun startThrows_exceptionPropagatesUnchanged() {
        try {
            BoundedShell.run(TIMEOUT_GENEROUS_MS) { throw IllegalStateException("start gagal") }
            fail("seharusnya melempar IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("start gagal", e.message)
        }
    }

    @Test
    fun release_closesBothStreamsAndDestroys() {
        val p = FakeProcess(streamOf("ok"))
        BoundedShell.release(p)
        assertTrue("destroy", p.destroyed)
        assertTrue("stdin ditutup", p.stdinClosed)
        assertTrue("stderr ditutup", p.stderrClosed)
    }

    @Test
    fun release_destroyThrows_isSwallowedAndRepeatable() {
        val p = FakeProcess(streamOf("ok"), destroyFails = true)
        BoundedShell.release(p)
        BoundedShell.release(p)
        assertTrue("destroy dicoba", p.destroyed)
        assertTrue("stdin ditutup", p.stdinClosed)
        assertTrue("stderr ditutup", p.stderrClosed)
    }

    private companion object {
        const val EXIT_CODE_SEVEN = 7
        const val TIMEOUT_GENEROUS_MS = 20_000L
        const val TIMEOUT_SHORT_MS = 200L
    }
}
