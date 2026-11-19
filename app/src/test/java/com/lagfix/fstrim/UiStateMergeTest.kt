package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v166: `mergeUiState` = bacaan lambat ([UiState] baru) digabung dgn state UI SAAT TULIS (tanpa Android/Shizuku). */
class UiStateMergeTest {
    private val fresh = UiState(
        shizuku = ShizukuState.READY,
        enabled = true,
        intervalMinutes = 30L,
        radicalInterval = true,
        lastRunMs = 5L,
        lastOk = true,
        log = listOf("baris-1", "baris-2"),
        batteryUnrestricted = false,
        backgroundRestricted = true,
        persistentServiceEnabled = true
    )

    private val busy = UiState(
        running = true,
        updateChecking = true,
        updateResult = UpdateResult.UpToDate(installedBuild = 7),
        downloading = true,
        downloadError = "galat-unduh"
    )

    @Test
    fun `hasil baca dari fresh, field aksi berjalan dari current`() {
        val m = mergeUiState(fresh, busy)
        assertEquals(ShizukuState.READY, m.shizuku)
        assertTrue(m.enabled)
        assertEquals(30L, m.intervalMinutes)
        assertTrue(m.radicalInterval)
        assertEquals(5L, m.lastRunMs)
        assertTrue(m.lastOk)
        assertEquals(listOf("baris-1", "baris-2"), m.log)
        assertFalse(m.batteryUnrestricted)
        assertTrue(m.backgroundRestricted)
        assertTrue(m.persistentServiceEnabled)
        assertTrue(m.running)
        assertTrue(m.updateChecking)
        assertEquals(UpdateResult.UpToDate(installedBuild = 7), m.updateResult)
        assertTrue(m.downloading)
        assertEquals("galat-unduh", m.downloadError)
    }

    @Test
    fun `running basi di fresh diabaikan, run selesai di jendela baca tak dihidupkan lagi`() {
        // `fresh.running = true` meniru nilai yg dulu ditangkap di AWAL baca; `current` = run sudah selesai.
        val stale = fresh.copy(running = true)
        val finished = busy.copy(running = false)
        assertFalse(mergeUiState(stale, finished).running)
    }

    @Test
    fun `running dari current dipertahankan saat run masih berjalan`() {
        val started = busy.copy(running = true)
        assertTrue(mergeUiState(fresh.copy(running = false), started).running)
    }

    @Test
    fun `parameter running memaksa nilai, dipakai runNow di finally`() {
        val m = mergeUiState(fresh, busy, running = false)
        assertFalse(m.running)
        assertTrue(m.updateChecking)
        assertEquals("galat-unduh", m.downloadError)
        assertEquals(30L, m.intervalMinutes)
    }

    @Test
    fun `fresh null, hanya running yg diubah dan sisanya dari current`() {
        val m = mergeUiState(null, busy, running = false)
        assertEquals(busy.copy(running = false), m)
        assertNull(mergeUiState(null, UiState(), running = false).updateResult)
    }
}
