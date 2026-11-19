package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** v165: `FstrimExecutor.collect` = baca keluaran + tuai kode keluar + SELALU bereskan proses (tanpa Shizuku). */
class FstrimExecutorCollectTest {
    private class Tracked(private val onClose: () -> Unit) : OutputStream() {
        override fun write(b: Int) = Unit
        override fun close() = onClose()
    }

    private class FakeProcess(
        private val stdout: InputStream,
        private val exit: Int = 0,
        private val destroyFails: Boolean = false
    ) : Process() {
        var destroyed = false
        var stdinClosed = false
        var stderrClosed = false
        private val stdin = Tracked { stdinClosed = true }
        private val stderr = object : ByteArrayInputStream(ByteArray(0)) {
            override fun close() {
                stderrClosed = true
            }
        }

        override fun getOutputStream(): OutputStream = stdin
        override fun getInputStream(): InputStream = stdout
        override fun getErrorStream(): InputStream = stderr
        override fun waitFor(): Int = exit
        override fun exitValue(): Int = exit
        override fun destroy() {
            destroyed = true
            if (destroyFails) throw IllegalStateException("destroy gagal")
        }
    }

    private fun streamOf(text: String): InputStream = ByteArrayInputStream(text.toByteArray())

    @Test
    fun collect_returnsExitAndOutput_andReleasesProcess() {
        val p = FakeProcess(streamOf("Trimmed: /data\n"), exit = 0)
        val result = FstrimExecutor.collect(p)
        assertEquals(0 to "Trimmed: /data\n", result)
        assertTrue("destroy", p.destroyed)
        assertTrue("stdin ditutup", p.stdinClosed)
        assertTrue("stderr ditutup", p.stderrClosed)
    }

    @Test
    fun collect_keepsNonZeroExitCode() {
        val p = FakeProcess(streamOf("gagal"), exit = 1)
        assertEquals(1 to "gagal", FstrimExecutor.collect(p))
    }

    @Test
    fun collect_whenReadThrows_rethrowsOriginalAndStillDestroys() {
        val boom = IOException("pipa putus")
        val broken = object : InputStream() {
            override fun read(): Int = throw boom
        }
        val p = FakeProcess(broken)
        try {
            FstrimExecutor.collect(p)
            fail("seharusnya melempar IOException")
        } catch (e: IOException) {
            assertSame(boom, e)
        }
        assertTrue("destroy", p.destroyed)
        assertTrue("stdin ditutup", p.stdinClosed)
    }

    @Test
    fun collect_whenDestroyThrows_stillReturnsResult() {
        val p = FakeProcess(streamOf("ok"), exit = 0, destroyFails = true)
        assertEquals(0 to "ok", FstrimExecutor.collect(p))
        assertTrue("destroy dicoba", p.destroyed)
    }
}
