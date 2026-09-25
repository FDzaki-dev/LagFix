package com.lagfix.fstrim

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Fake in-memory [SharedPreferences] — dipilih daripada Mockito mock supaya semantik
 * put/apply/get beneran seperti implementasi asli (state benar2 tersimpan), bukan cuma stub.
 */
private class FakeSharedPreferences : SharedPreferences {
    val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        map[key] as? MutableSet<String> ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor(this)
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
    }
}

private class FakeEditor(private val target: FakeSharedPreferences) : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, Any?>()
    private val toRemove = mutableSetOf<String>()
    private var clearAll = false

    private fun put(key: String?, value: Any?): SharedPreferences.Editor {
        pending[key!!] = value
        return this
    }

    override fun putString(key: String?, value: String?) = put(key, value)
    override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values)
    override fun putInt(key: String?, value: Int) = put(key, value)
    override fun putLong(key: String?, value: Long) = put(key, value)
    override fun putFloat(key: String?, value: Float) = put(key, value)
    override fun putBoolean(key: String?, value: Boolean) = put(key, value)
    override fun remove(key: String?): SharedPreferences.Editor {
        toRemove.add(key!!)
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        clearAll = true
        return this
    }

    override fun commit(): Boolean {
        apply()
        return true
    }

    override fun apply() {
        if (clearAll) target.map.clear()
        toRemove.forEach { target.map.remove(it) }
        target.map.putAll(pending)
        pending.clear()
    }
}

class PrefsTest {
    private lateinit var prefs: Prefs

    @Before
    fun setup() {
        val fakeSp = FakeSharedPreferences()
        val ctx = mock(Context::class.java)
        `when`(ctx.applicationContext).thenReturn(ctx)
        `when`(ctx.getSharedPreferences("lagfix", Context.MODE_PRIVATE)).thenReturn(fakeSp)
        prefs = Prefs(ctx)
    }

    @Test
    fun `record simpan lastOk true dan format baris status OK`() {
        prefs.record(TrimResult(ok = true, message = "exit=0 done", durationMs = 964L))

        assertTrue(prefs.lastOk)
        assertEquals(1, prefs.log.size)
        val line = prefs.log[0]
        assertTrue(line.contains("OK 964ms exit=0 done"))
        assertTrue(Regex("""^\d{2}/\d{2} \d{2}:\d{2} OK""").containsMatchIn(line))
    }

    @Test
    fun `record simpan lastOk false dan status FAIL saat gagal`() {
        prefs.record(TrimResult(ok = false, message = "exit=1", durationMs = 50L))

        assertTrue(!prefs.lastOk)
        assertTrue(prefs.log[0].contains("FAIL"))
    }

    @Test
    fun `log dipotong maksimal 30 baris, entri terbaru di depan, entri lama terbuang`() {
        repeat(35) { i -> prefs.record(TrimResult(ok = true, message = "run-$i", durationMs = 1L)) }

        // token terakhir tiap baris = "run-N" persis (bukan substring, hindari salah tangkap run-14/24/34)
        val entries = prefs.log.map { it.substringAfterLast(' ') }
        assertEquals(30, entries.size)
        assertEquals("run-34", entries.first())
        assertEquals("run-5", entries.last())
        assertTrue(entries.none { it == "run-4" || it == "run-0" })
    }

    @Test
    fun `pesan log newline diganti spasi dan dipotong maksimal 120 karakter`() {
        val longMsg = "a\nb".repeat(60) // 180 char, ada newline berulang
        prefs.record(TrimResult(ok = true, message = longMsg, durationMs = 1L))

        val line = prefs.log[0]
        assertTrue(!line.contains("\n"))
        // prefix tetap: stamp "dd/MM HH:mm" (11 char, format 24 jam - selalu fixed width) + " OK 1ms " (8 char)
        val msgPart = line.substring(19)
        assertEquals(120, msgPart.length)
    }
}
