package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Test

/** v121: isi berkas `diag_tile_listening` = data mentah (pid, umur proses, toggle, 2 dump notifikasi). */
class FormatTileListeningTest {

    @Test
    fun body_hasHeaderLineAndBothRawDumps() {
        val item = NotifSnapshot(
            id = 42,
            tag = null,
            channelId = "lagfix_keep_alive_high",
            flags = 0x62,
            postTime = 2000L
        )
        val text = formatTileListening(
            pid = 1234,
            procAgeMs = 850L,
            toggleOn = true,
            immediate = Result.success(emptyList()),
            delayed = Result.success(listOf(item))
        )
        assertEquals(
            "tile onStartListening pid=1234 procAge=850ms persistentToggleOn=true\n" +
                "activeNotifications[immediate]: count=0\n" +
                "activeNotifications[+1500ms]: count=1\n" +
                "  id=42 tag=null channel=lagfix_keep_alive_high flags=0x62 postTime=2000",
            text
        )
    }

    @Test
    fun readFailure_printsExceptionAsIs() {
        val text = formatTileListening(
            pid = 7,
            procAgeMs = 0L,
            toggleOn = false,
            immediate = Result.failure(SecurityException("ditolak")),
            delayed = Result.success(emptyList())
        )
        assertEquals(
            "tile onStartListening pid=7 procAge=0ms persistentToggleOn=false\n" +
                "activeNotifications[immediate]: read exception = java.lang.SecurityException: ditolak\n" +
                "activeNotifications[+1500ms]: count=0",
            text
        )
    }
}
