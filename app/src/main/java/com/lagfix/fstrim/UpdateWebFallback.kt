package com.lagfix.fstrim

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Rilis terbaru dari jalur mana pun (API atau web): tag, nama rilis, URL APK. */
internal data class ReleaseRef(val tag: String, val name: String, val apkUrl: String)

/**
 * v178 (laporan user, tangkapan layar kartu Pembaruan: "Gagal memeriksa pembaruan: Terlalu banyak permintaan ke
 * GitHub (rate limit)"): API GitHub tanpa token dibatasi 60 permintaan/jam per alamat IP, dan IP seluler dipakai
 * bersama banyak pengguna -> mudah habis. Jalur cadangan TANPA API: `github.com/<owner>/<repo>/releases/latest`
 * mengalihkan (302) ke `/releases/tag/<tag>`, lalu `releases/expanded_assets/<tag>` memuat tautan APK. Dipakai HANYA
 * bila API menjawab 403/429 ([UpdateChecker.check]). Blocking (network) — WAJIB dari Dispatchers.IO.
 */
internal object UpdateWebFallback {
    private const val CONNECT_TIMEOUT_MS = 6_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val HTTP_OK_MIN = 200
    private const val HTTP_OK_MAX = 299
    private const val HTTP_REDIRECT_MIN = 300
    private const val HTTP_REDIRECT_MAX = 399
    private val TAG_IN_LOCATION = Regex("/releases/tag/([^/?#]+)")
    private val APK_HREF = Regex("href=\"([^\"]*/releases/download/[^\"]+\\.apk)\"", RegexOption.IGNORE_CASE)
    private val BUILD_DIGITS = Regex("(\\d+)")

    fun fetchLatest(owner: String, repo: String): ReleaseRef {
        val tag = latestTag(owner, repo)
        val href = apkHref(pageText("https://github.com/$owner/$repo/releases/expanded_assets/$tag"))
            ?: throw IOException("APK rilis $tag tidak ditemukan")
        val build = BUILD_DIGITS.find(tag)?.value
        return ReleaseRef(tag, if (build != null) "LagFix build $build" else tag, href)
    }

    /** Tag dari header `Location` pengalihan releases/latest; null bila bukan URL `/releases/tag/<tag>`. */
    internal fun tagFromLocation(location: String?): String? =
        location?.let { TAG_IN_LOCATION.find(it)?.groupValues?.get(1) }

    /** Tautan APK pertama (`/releases/download/.../*.apk`) di HTML daftar aset, dijadikan URL absolut. */
    internal fun apkHref(html: String): String? {
        val href = APK_HREF.find(html)?.groupValues?.get(1)?.replace("&amp;", "&") ?: return null
        return if (href.startsWith("http")) href else "https://github.com$href"
    }

    private fun latestTag(owner: String, repo: String): String {
        val conn = open("https://github.com/$owner/$repo/releases/latest")
        return try {
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            if (code !in HTTP_REDIRECT_MIN..HTTP_REDIRECT_MAX) throw IOException("HTTP $code")
            tagFromLocation(conn.getHeaderField("Location")) ?: throw IOException("Tag rilis terbaru tidak terbaca")
        } finally {
            conn.disconnect()
        }
    }

    private fun pageText(url: String): String {
        val conn = open(url)
        return try {
            conn.setRequestProperty("Accept", "text/html")
            if (conn.responseCode !in HTTP_OK_MIN..HTTP_OK_MAX) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "LagFix-App")
        return conn
    }
}
