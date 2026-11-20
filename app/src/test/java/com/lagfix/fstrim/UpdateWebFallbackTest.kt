package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * v178: jalur cadangan tanpa API GitHub (kuota tanpa-token habis). Hanya bagian murni (urai Location & HTML);
 * permintaan jaringannya sendiri tidak diuji di sini.
 */
class UpdateWebFallbackTest {
    @Test
    fun `tag diambil dari header Location pengalihan releases latest`() {
        val loc = "https://github.com/FDzaki-dev/LagFix/releases/tag/build-150"
        assertEquals("build-150", UpdateWebFallback.tagFromLocation(loc))
        assertEquals("build-150", UpdateWebFallback.tagFromLocation("$loc?foo=bar"))
        assertEquals("build-150", UpdateWebFallback.tagFromLocation("/FDzaki-dev/LagFix/releases/tag/build-150"))
    }

    @Test
    fun `Location kosong atau bukan URL tag menghasilkan null`() {
        assertNull(UpdateWebFallback.tagFromLocation(null))
        assertNull(UpdateWebFallback.tagFromLocation(""))
        assertNull(UpdateWebFallback.tagFromLocation("https://github.com/FDzaki-dev/LagFix/releases"))
    }

    @Test
    fun `tautan APK relatif dijadikan URL absolut`() {
        val html = """
            <a href="/FDzaki-dev/LagFix/archive/refs/tags/build-150.zip">Source code (zip)</a>
            <a href="/FDzaki-dev/LagFix/releases/download/build-150/LagFix_build-150_release.apk" rel="nofollow">
        """.trimIndent()
        assertEquals(
            "https://github.com/FDzaki-dev/LagFix/releases/download/build-150/LagFix_build-150_release.apk",
            UpdateWebFallback.apkHref(html)
        )
    }

    @Test
    fun `tautan APK absolut dipertahankan dan tanpa APK menghasilkan null`() {
        val abs = "https://github.com/o/r/releases/download/t/app.apk"
        assertEquals(abs, UpdateWebFallback.apkHref("<a href=\"$abs\">x</a>"))
        assertNull(UpdateWebFallback.apkHref("<a href=\"/o/r/archive/refs/tags/t.zip\">Source code</a>"))
    }

    @Test
    fun `HTTP 429 dan 403 sama-sama dipetakan ke pesan rate limit`() {
        val expected = "Terlalu banyak permintaan ke GitHub (rate limit), coba lagi nanti."
        assertEquals(expected, UpdateChecker.friendlyError(IOException("HTTP 429")))
        assertEquals(expected, UpdateChecker.friendlyError(IOException("HTTP 403")))
        assertTrue(UpdateChecker.friendlyError(IOException("HTTP 500")) == "HTTP 500")
    }
}
