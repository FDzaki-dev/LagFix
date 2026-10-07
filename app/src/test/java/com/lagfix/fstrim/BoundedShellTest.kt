package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * v98: [BoundedShell] — perintah normal selesai utuh; perintah macet dihentikan sesuai batas
 * & tak menahan pemanggil.
 */
class BoundedShellTest {

    private fun sh(cmd: String): Process =
        ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()

    @Test
    fun normalCommand_returnsFullOutputAndExitCode() {
        val r = BoundedShell.run(TIMEOUT_GENEROUS_MS) { sh("echo hi") }
        assertFalse(r.timedOut)
        assertEquals(0, r.exit)
        assertEquals("hi", r.output.trim())
    }

    @Test
    fun failingCommand_keepsNonZeroExit() {
        val r = BoundedShell.run(TIMEOUT_GENEROUS_MS) { sh("exit 3") }
        assertFalse(r.timedOut)
        assertEquals(3, r.exit)
    }

    @Test
    fun stuckCommand_isStoppedAtTimeoutAndKeepsPartialOutput() {
        val t0 = System.nanoTime()
        val r = BoundedShell.run(TIMEOUT_SHORT_MS) { sh("echo partial; exec sleep 30") }
        val elapsedMs = (System.nanoTime() - t0) / NANOS_PER_MS
        assertTrue(r.timedOut)
        assertNull(r.exit)
        assertTrue(r.output.contains("partial"))
        assertTrue("harus kembali jauh sebelum 30 dtk, nyatanya $elapsedMs ms", elapsedMs < MAX_ELAPSED_MS)
    }

    @Test
    fun remoteLikeProcess_exitValueThrowsOtherThanIllegalThreadState_stillGetsExitCode() {
        val r = BoundedShell.run(TIMEOUT_GENEROUS_MS) { RemoteLikeProcess(STILL_RUNNING_POLLS, EXIT_CODE_SEVEN) }
        assertFalse(r.timedOut)
        assertEquals(EXIT_CODE_SEVEN, r.exit)
        assertEquals("ok", r.output)
    }

    /** v135: `exitValue()` melempar `IllegalArgumentException` (bukan ITSE) selagi belum dituai. */
    private class RemoteLikeProcess(private val stillRunningPolls: Int, private val code: Int) : Process() {
        private var polls = 0

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream("ok".toByteArray())

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = code

        override fun exitValue(): Int {
            polls++
            require(polls > stillRunningPolls) { "belum dituai" }
            return code
        }

        override fun destroy() = Unit
    }

    private companion object {
        const val STILL_RUNNING_POLLS = 2
        const val EXIT_CODE_SEVEN = 7
        const val TIMEOUT_GENEROUS_MS = 20_000L
        const val TIMEOUT_SHORT_MS = 500L
        const val NANOS_PER_MS = 1_000_000L
        const val MAX_ELAPSED_MS = 15_000L
    }
}
