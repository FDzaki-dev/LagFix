package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Test

/** v132: perintah perbesar buffer (dipakai bersama tombol snapshot) + format baris log = data mentah. */
class LogBufferTest {

    @Test
    fun resizeCommand_isSharedConstantWithSnapshot() {
        assertEquals("logcat -b main -b system -b events -b crash -G 16M", LogcatSnapshot.BUFFER_RESIZE_CMD)
    }

    @Test
    fun resultLine_emptyOutput_printsRawNumbersOnly() {
        assertEquals(
            "LIFECYCLE logBuffer resize exit=0 timedOut=false output=",
            logBufferResultLine("", 0, false)
        )
    }

    @Test
    fun resultLine_joinsNonBlankLinesAndCapsAt300() {
        val joined = logBufferResultLine("a\n\nb\n", null, true)
        assertEquals("LIFECYCLE logBuffer resize exit=null timedOut=true output=a | b", joined)
        val capped = logBufferResultLine("x".repeat(400), 1, false)
        assertEquals("LIFECYCLE logBuffer resize exit=1 timedOut=false output=" + "x".repeat(300), capped)
    }

    @Test
    fun skippedLine_printsRawStateName() {
        assertEquals(
            "LIFECYCLE logBuffer resize skipped shizuku=NOT_RUNNING",
            logBufferSkippedLine("NOT_RUNNING")
        )
    }
}
