package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
