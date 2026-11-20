package com.lagfix.fstrim

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * v180: bagian murni lapisan jaringan OkHttp+Okio ([UpdateHttp]) — tanpa jaringan dan tanpa membuat klien:
 * kontrak pesan "HTTP <kode>" (dasar deteksi rate limit v178), pembacaan teks berbatas lewat aliran Okio,
 * dan header permintaan.
 */
class UpdateHttpTest {
    @Test
    fun `kode 2xx lolos dan selain itu jadi IOException dengan pesan HTTP kode`() {
        UpdateHttp.ensureOk(200)
        UpdateHttp.ensureOk(204)
        UpdateHttp.ensureOk(299)
        assertEquals("HTTP 403", assertThrows(IOException::class.java) { UpdateHttp.ensureOk(403) }.message)
        assertEquals("HTTP 429", assertThrows(IOException::class.java) { UpdateHttp.ensureOk(429) }.message)
        assertEquals("HTTP 199", assertThrows(IOException::class.java) { UpdateHttp.ensureOk(199) }.message)
        assertEquals("HTTP 300", assertThrows(IOException::class.java) { UpdateHttp.ensureOk(300) }.message)
    }

    @Test
    fun `pesan 403 dan 429 tetap dipetakan ke rate limit oleh friendlyError`() {
        val expected = "Terlalu banyak permintaan ke GitHub (rate limit), coba lagi nanti."
        listOf(403, 429).forEach { code ->
            val failure = assertThrows(IOException::class.java) { UpdateHttp.ensureOk(code) }
            assertEquals(expected, UpdateChecker.friendlyError(failure))
        }
    }

    @Test
    fun `teks dibaca sebagai UTF-8 utuh termasuk karakter bukan ASCII`() {
        val text = "Changelog v180 — Memuat… ünïcødé"
        assertEquals(text, UpdateHttp.readBoundedText(Buffer().writeUtf8(text), 1_000L))
        assertEquals("", UpdateHttp.readBoundedText(Buffer(), 10L))
    }

    @Test
    fun `teks tepat sebesar batas diterima dan satu byte lebih jadi galat`() {
        assertEquals("abcde", UpdateHttp.readBoundedText(Buffer().writeUtf8("abcde"), 5L))
        val failure = assertThrows(IOException::class.java) {
            UpdateHttp.readBoundedText(Buffer().writeUtf8("abcdef"), 5L)
        }
        assertTrue(failure.message.orEmpty().contains("5"))
    }

    @Test
    fun `permintaan membawa User-Agent aplikasi dan Accept hanya bila diminta`() {
        val withAccept = UpdateHttp.request("https://api.github.com/repos/o/r/releases/latest", "application/json")
        assertEquals("LagFix-App", withAccept.header("User-Agent"))
        assertEquals("application/json", withAccept.header("Accept"))
        assertEquals("GET", withAccept.method)

        val plain = UpdateHttp.request("https://github.com/o/r/releases/latest")
        assertEquals("LagFix-App", plain.header("User-Agent"))
        assertNull(plain.header("Accept"))
    }

    @Test
    fun `permintaan unduhan meminta identity supaya Content-Length = ukuran APK`() {
        val request = UpdateHttp.downloadRequest("https://github.com/o/r/releases/download/t/app.apk")
        assertEquals("identity", request.header("Accept-Encoding"))
        assertEquals("LagFix-App", request.header("User-Agent"))
        assertEquals("https://github.com/o/r/releases/download/t/app.apk", request.url.toString())
    }
}
