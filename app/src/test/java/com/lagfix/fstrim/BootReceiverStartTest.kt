package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * v168: `BootReceiver` tak boleh jatuh bila OS menolak start servis persisten saat boot (Android 12+ bisa menolak
 * start FGS dari proses latar). Logikanya diekstrak ke [startAfterBoot] (murni) supaya teruji tanpa Android.
 */
class BootReceiverStartTest {

    @Test
    fun toggleOff_neverStarts_noFailure() {
        var calls = 0
        val failure = startAfterBoot(toggleOn = false) { calls++ }
        assertNull(failure)
        assertEquals(0, calls)
    }

    @Test
    fun toggleOn_startsOnce_noFailure() {
        var calls = 0
        val failure = startAfterBoot(toggleOn = true) { calls++ }
        assertNull(failure)
        assertEquals(1, calls)
    }

    @Test
    fun toggleOn_startDenied_returnsSameExceptionInsteadOfThrowing() {
        val denied = IllegalStateException("start servis latar ditolak")
        val failure = startAfterBoot(toggleOn = true) { throw denied }
        assertSame(denied, failure)
    }

    @Test
    fun toggleOn_securityException_isCapturedToo() {
        val denied = SecurityException("izin servis tak ada")
        var calls = 0
        val failure = startAfterBoot(toggleOn = true) {
            calls++
            throw denied
        }
        assertSame(denied, failure)
        assertEquals(1, calls)
    }
}
