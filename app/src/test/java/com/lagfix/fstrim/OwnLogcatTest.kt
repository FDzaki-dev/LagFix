package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val items = ownLogcatItems("pid", out, 0, false)
        assertEquals("scope=pid exit=0 timedOut=false totalLines=4 keyword=LIFECYCLE keptLines=2", items[0])
        assertEquals(listOf(a, b), items.drop(1))
    }

    @Test
    fun capsAtNewest200Lines() {
        val out = (0 until TOTAL).joinToString("\n") { line(it) }
        val items = ownLogcatItems("pid", out, 0, false)
        assertEquals(1 + KEPT_MAX, items.size)
        assertEquals(line(TOTAL - KEPT_MAX), items[1])
        assertEquals(line(TOTAL - 1), items.last())
        assertTrue(items[0].contains("totalLines=$TOTAL"))
        assertTrue(items[0].endsWith("keptLines=$KEPT_MAX"))
    }

    @Test
    fun noKeywordLines_returnsMetaAndFiveRawTailLines() {
        val raw = (1..RAW_COUNT).map { "logcat: raw line $it" }
        val items = ownLogcatItems("pid", raw.joinToString("\n"), 1, false)
        assertEquals("scope=pid exit=1 timedOut=false totalLines=$RAW_COUNT keyword=LIFECYCLE keptLines=0", items[0])
        assertEquals(raw.takeLast(RAW_TAIL), items.drop(1))
    }

    @Test
    fun emptyOutput_returnsOnlyMeta() {
        val items = ownLogcatItems("uid", "", null, true)
        assertEquals(listOf("scope=uid exit=null timedOut=true totalLines=0 keyword=LIFECYCLE keptLines=0"), items)
    }

    @Test
    fun widgetLifecycleLine_isPlainRawFields() {
        assertEquals(
            "LIFECYCLE widget onReceive action=android.appwidget.action.APPWIDGET_UPDATE pid=18170 procAge=345ms",
            widgetLifecycleLine("android.appwidget.action.APPWIDGET_UPDATE", 18170, 345L)
        )
        assertEquals("LIFECYCLE widget onReceive action=null pid=7 procAge=0ms", widgetLifecycleLine(null, 7, 0L))
    }

    @Test
    fun buildLogcatInfoCommand_printsBufferSizeOldestAndSinceWithoutAppFilter() {
        val cmd = buildLogcatInfoCommand(1_791_327_037_282L)
        assertTrue(cmd.contains("echo '## logcat -g'; logcat -g -b main -b system -b events -b crash 2>&1"))
        assertTrue(cmd.contains("echo '## oldest'; logcat -d -v threadtime -b main -b system -b events -b crash"))
        assertTrue(cmd.contains("echo '## since 1791327037.282'"))
        assertTrue(cmd.contains("-t '1791327037.282' 2>&1 | grep -m 2 -E"))
        assertFalse(cmd.contains("lagfix"))
    }

    @Test
    fun buildLogcatInfoCommand_padsMillisToThreeDigits() {
        assertTrue(buildLogcatInfoCommand(1_000_005L).contains("echo '## since 1000.005'"))
    }

    @Test
    fun logcatInfoItems_metaThenRawLines_capped30AndCharCut() {
        val out = (1..INFO_TOTAL).joinToString("\n") { "x".repeat(INFO_LONG) + it } + "\n\n"
        val items = logcatInfoItems(out, 0, false)
        assertEquals("shizuku=READY exit=0 timedOut=false lines=$INFO_TOTAL", items[0])
        assertEquals(1 + INFO_KEPT, items.size)
        assertEquals(INFO_CHARS, items[1].length)
    }

    @Test
    fun logcatInfoItems_emptyOutput_onlyMeta() {
        assertEquals(listOf("shizuku=READY exit=null timedOut=true lines=0"), logcatInfoItems("", null, true))
    }

    private fun sys(sec: Int, msg: String) =
        "10-06 19:04:${sec.toString().padStart(LINE_PAD - 1, '0')}.000  1000  1000 I ActivityManager: $msg"

    @Test
    fun shizukuLogcatItems_keepsOnlyLinesInsideWindow() {
        val a = sys(SEC_IN_A, "in a")
        val b = sys(SEC_IN_B, "in b")
        val out = listOf(sys(SEC_OLD, "old"), a, b, sys(SEC_LATE, "late"), "not a log line").joinToString("\n")
        val items = shizukuLogcatItems(out, FROM, TO, 0, false)
        assertEquals("shizuku=READY window=$FROM..$TO lines=2 exit=0 timedOut=false", items[0])
        assertEquals(listOf(a, b), items.drop(1))
    }

    @Test
    fun shizukuLogcatItems_capsLinesAndTrimsLongLines() {
        val many = (0 until SHZ_MANY).joinToString("\n") { sys(SEC_IN_A, "m$it") }
        val capped = shizukuLogcatItems(many, FROM, TO, 0, false)
        assertEquals(1 + SHZ_MAX, capped.size)
        assertTrue(capped[0].contains("lines=$SHZ_MAX"))
        val long = shizukuLogcatItems(sys(SEC_IN_A, "x".repeat(SHZ_LONG)), FROM, TO, 0, false)
        assertEquals(SHZ_CHARS, long[1].length)
    }

    @Test
    fun shizukuLogcatItems_noLinesInWindow_returnsMetaAndRawTail() {
        val raw = (1..RAW_COUNT).map { "logcat: error $it" }
        val items = shizukuLogcatItems(raw.joinToString("\n"), FROM, TO, 1, false)
        assertEquals("shizuku=READY window=$FROM..$TO lines=0 exit=1 timedOut=false", items[0])
        assertEquals(raw.takeLast(RAW_TAIL), items.drop(1))
    }

    @Test
    fun logcatWindowStartMs_coldStartsAtBirthMinusOneSecond_warmIsCappedAtOneMinute() {
        assertEquals(NOW - COLD_AGE - ONE_SECOND, logcatWindowStartMs(NOW, COLD_AGE))
        assertEquals(NOW - ONE_MINUTE - ONE_SECOND, logcatWindowStartMs(NOW, WARM_AGE))
    }

    private companion object {
        const val LINE_PAD = 3
        const val TOTAL = 250
        const val KEPT_MAX = 200
        const val RAW_COUNT = 7
        const val RAW_TAIL = 5
        const val INFO_TOTAL = 40
        const val INFO_KEPT = 30
        const val INFO_LONG = 400
        const val INFO_CHARS = 300
        const val FROM = "10-06 19:04:16.000"
        const val TO = "10-06 19:04:30.000"
        const val SEC_OLD = 10
        const val SEC_IN_A = 17
        const val SEC_IN_B = 20
        const val SEC_LATE = 50
        const val SHZ_MANY = 160
        const val SHZ_MAX = 150
        const val SHZ_LONG = 400
        const val SHZ_CHARS = 300
        const val NOW = 1_000_000L
        const val COLD_AGE = 116L
        const val WARM_AGE = 3_600_000L
        const val ONE_SECOND = 1_000L
        const val ONE_MINUTE = 60_000L
    }
}
