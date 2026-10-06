package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v127: bagian `ownLogcat` = baris logcat MENTAH apa adanya (tanpa tafsiran) + 1 baris angka mentah di depan. */
class OwnLogcatTest {

    private fun line(i: Int) =
        "10-06 19:04:17.${i.toString().padStart(LINE_PAD, '0')} 18170 18170 I Tag: LIFECYCLE item=$i"

    @Test
    fun keepsOnlyKeywordLines_inOriginalOrder_unchanged() {
        val a = line(1)
        val b = line(2)
        val out = "--------- beginning of main\n$a\n10-06 19:04:18.000 18170 18170 D Noise: hello\n$b\n"
        val items = ownLogcatItems(out, 0, false)
        assertEquals("exit=0 timedOut=false totalLines=4 keyword=LIFECYCLE keptLines=2", items[0])
        assertEquals(listOf(a, b), items.drop(1))
    }

    @Test
    fun capsAtNewest200Lines() {
        val out = (0 until TOTAL).joinToString("\n") { line(it) }
        val items = ownLogcatItems(out, 0, false)
        assertEquals(1 + KEPT_MAX, items.size)
        assertEquals(line(TOTAL - KEPT_MAX), items[1])
        assertEquals(line(TOTAL - 1), items.last())
        assertTrue(items[0].contains("totalLines=$TOTAL"))
        assertTrue(items[0].endsWith("keptLines=$KEPT_MAX"))
    }

    @Test
    fun noKeywordLines_returnsMetaAndFiveRawTailLines() {
        val raw = (1..RAW_COUNT).map { "logcat: raw line $it" }
        val items = ownLogcatItems(raw.joinToString("\n"), 1, false)
        assertEquals("exit=1 timedOut=false totalLines=$RAW_COUNT keyword=LIFECYCLE keptLines=0", items[0])
        assertEquals(raw.takeLast(RAW_TAIL), items.drop(1))
    }

    @Test
    fun emptyOutput_returnsOnlyMeta() {
        val items = ownLogcatItems("", null, true)
        assertEquals(listOf("exit=null timedOut=true totalLines=0 keyword=LIFECYCLE keptLines=0"), items)
    }

    @Test
    fun widgetLifecycleLine_isPlainRawFields() {
        assertEquals(
            "LIFECYCLE widget onReceive action=android.appwidget.action.APPWIDGET_UPDATE pid=18170 procAge=345ms",
            widgetLifecycleLine("android.appwidget.action.APPWIDGET_UPDATE", 18170, 345L)
        )
        assertEquals("LIFECYCLE widget onReceive action=null pid=7 procAge=0ms", widgetLifecycleLine(null, 7, 0L))
    }

    private companion object {
        const val LINE_PAD = 3
        const val TOTAL = 250
        const val KEPT_MAX = 200
        const val RAW_COUNT = 7
        const val RAW_TAIL = 5
    }
}
