package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FakeStartInfo(private val pidValue: Int, private val stamps: Map<Int, Long>) {
    fun getPid(): Int = pidValue

    fun getStartupTimestamps(): Map<Int, Long> = stamps

    companion object {
        const val START_TIMESTAMP_LAUNCH = 0
        const val START_TIMESTAMP_FORK = 1
    }
}

internal object FakeCodes {
    const val REASON_ONE = 1
    const val REASON_TWO = 2
    const val OTHER_TWO = 2
}

/** v123: format berkas `diag_process_catch` + pencari nama konstanta platform = data mentah, tanpa tafsiran. */
class ProcessCatcherTest {

    @Test
    fun header_printsRawFieldsOnOneLine() {
        val text = formatCatchHeader("tile", "cold", 21951, 245L, true)
        assertEquals("catch source=tile reason=cold pid=21951 procAge=245ms persistentToggleOn=true", text)
    }

    @Test
    fun section_printsCountAndIndentedItems() {
        val text = formatCatchSection("exitHistory", Result.success(listOf("a", "b")))
        assertEquals("exitHistory: count=2\n  a\n  b", text)
    }

    @Test
    fun section_readFailurePrintsExceptionAsIs() {
        val text = formatCatchSection("startHistory", Result.failure(SecurityException("ditolak")))
        assertEquals("startHistory: read exception = java.lang.SecurityException: ditolak", text)
    }

    @Test
    fun constantName_matchesPrefixAndValue_orUnknown() {
        assertEquals("REASON_TWO", constantName(FakeCodes::class.java, "REASON_", 2))
        assertEquals("UNKNOWN", constantName(FakeCodes::class.java, "REASON_", 99))
    }

    @Test
    fun anchors_printsRawClockReferences() {
        assertEquals(
            "anchors: myPid=11860 processStartElapsedMs=5 nowElapsedMs=10 nowWallMs=20",
            formatAnchors(11860, 5L, 10L, 20L)
        )
    }

    @Test
    fun getterValue_readsPublicGetter_orPrintsException() {
        val info = FakeStartInfo(4242, emptyMap())
        assertEquals("4242", getterValue(info, "getPid"))
        assertTrue(getterValue(info, "getTidakAda").startsWith("read exception = "))
    }

    @Test
    fun timestampsText_sortsByCodeAndNamesFromPlatformConstants() {
        val info = FakeStartInfo(1, mapOf(1 to 100L, 0 to 50L))
        assertEquals("START_TIMESTAMP_LAUNCH=50,START_TIMESTAMP_FORK=100", timestampsText(info))
    }
}
