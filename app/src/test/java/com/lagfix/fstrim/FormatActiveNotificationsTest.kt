package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Test

/** v113: dump `activeNotifications` (+ v184: konteks start) di log persistent_service = data mentah. */
class FormatActiveNotificationsTest {

    @Test
    fun empty_listsOnlyCountZero() {
        val text = formatActiveNotifications("immediate", Result.success(emptyList()))
        assertEquals("activeNotifications[immediate]: count=0", text)
    }

    @Test
    fun oneNotification_printsRawFieldsWithHexFlags() {
        val item = NotifSnapshot(id = 42, tag = null, channelId = "lagfix_keep_alive", flags = 0x62, postTime = 1000L)
        val text = formatActiveNotifications("+1500ms", Result.success(listOf(item)))
        assertEquals(
            "activeNotifications[+1500ms]: count=1\n" +
                "  id=42 tag=null channel=lagfix_keep_alive flags=0x62 postTime=1000",
            text
        )
    }

    @Test
    fun readFailure_printsExceptionAsIs() {
        val text = formatActiveNotifications("immediate", Result.failure(SecurityException("ditolak")))
        assertEquals("activeNotifications[immediate]: read exception = java.lang.SecurityException: ditolak", text)
    }

    @Test
    fun startContext_printsRawOriginImportanceAndRestriction() {
        val text = formatStartContext("start pid=14905 procAge=309ms startId=1 flags=0 intentNull=false", 100, true)
        assertEquals(
            "start pid=14905 procAge=309ms startId=1 flags=0 intentNull=false\n" +
                "myMemoryState importance=100\n" +
                "isBackgroundRestricted = true",
            text
        )
    }

    @Test
    fun startContext_unreadableValuesArePrintedAsNull() {
        val text = formatStartContext("start pid=1 procAge=2ms startId=1 flags=0 intentNull=true", null, null)
        assertEquals(
            "start pid=1 procAge=2ms startId=1 flags=0 intentNull=true\n" +
                "myMemoryState importance=null\n" +
                "isBackgroundRestricted = null",
            text
        )
    }
}
