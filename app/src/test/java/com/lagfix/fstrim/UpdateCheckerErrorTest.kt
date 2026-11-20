package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * v174: pemetaan galat jaringan ke pesan UI (murni, tanpa Android/jaringan) + kontrak yg dipakai alur changelog
 * terpisah di `MainViewModel.checkUpdate` (hasil cek tampil dulu, changelog menyusul lewat `copy`).
 */
class UpdateCheckerErrorTest {
    @Test
    fun `host tak terjangkau tetap dipetakan ke tidak ada koneksi internet`() {
        assertEquals("Tidak ada koneksi internet.", UpdateChecker.friendlyError(UnknownHostException("api.github.com")))
    }

    @Test
    fun `batas waktu bukan lagi dipetakan ke tidak ada koneksi internet`() {
        val msg = UpdateChecker.friendlyError(SocketTimeoutException("failed to connect after 10000ms"))
        assertEquals("GitHub tidak merespons (waktu habis), coba lagi.", msg)
        assertFalse(msg.contains("koneksi internet"))
    }

    @Test
    fun `gagal tersambung dipetakan ke pesan sambungan GitHub`() {
        assertEquals(
            "Tidak bisa terhubung ke GitHub, coba lagi.",
            UpdateChecker.friendlyError(ConnectException("Connection refused"))
        )
    }

    @Test
    fun `HTTP 403 tetap pesan rate limit dan kode lain apa adanya`() {
        assertTrue(UpdateChecker.friendlyError(IOException("HTTP 403")).contains("rate limit"))
        assertEquals("HTTP 404", UpdateChecker.friendlyError(IOException("HTTP 404")))
    }

    @Test
    fun `galat tanpa pesan jadi tidak diketahui`() {
        assertEquals("Tidak diketahui", UpdateChecker.friendlyError(IllegalStateException()))
    }

    @Test
    fun `changelog menyusul lewat copy hanya mengganti changelog`() {
        val info = UpdateInfo("v147", "LagFix build 147", 147, "https://example.test/a.apk", CHANGELOG_LOADING)
        val first = UpdateResult.Available(146, info)
        val later = first.copy(info = first.info.copy(changelog = "isi changelog"))
        assertEquals(146, later.installedBuild)
        assertEquals(147, later.info.latestBuild)
        assertEquals("https://example.test/a.apk", later.info.downloadUrl)
        assertEquals("isi changelog", later.info.changelog)
        assertNotEquals(CHANGELOG_LOADING, CHANGELOG_UNAVAILABLE)
    }
}
