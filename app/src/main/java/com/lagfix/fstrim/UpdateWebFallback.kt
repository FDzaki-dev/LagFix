package com.lagfix.fstrim

import java.io.IOException

/** Rilis terbaru dari jalur mana pun (API atau web): tag, nama rilis, URL APK. */
internal data class ReleaseRef(val tag: String, val name: String, val apkUrl: String)

/**
 * v178 (laporan user, tangkapan layar kartu Pembaruan: "Gagal memeriksa pembaruan: Terlalu banyak permintaan ke
 * GitHub (rate limit)"): API GitHub tanpa token dibatasi 60 permintaan/jam per alamat IP, dan IP seluler dipakai
 * bersama banyak pengguna -> mudah habis. Jalur cadangan TANPA API: `github.com/<owner>/<repo>/releases/latest`
 * mengalihkan (302) ke `/releases/tag/<tag>`, lalu `releases/expanded_assets/<tag>` memuat tautan APK. Dipakai HANYA
 * bila API menjawab 403/429 ([UpdateChecker.check]). Blocking (network) — WAJIB dari Dispatchers.IO.
 * v180: permintaan lewat OkHttp ([UpdateHttp]). Akar build gagal #152: KDoc [apkHref] memuat pola berkas APK dengan
 * tanda bintang setelah garis miring, yang di Kotlin MEMBUKA komentar bersarang (tak pernah tertutup); kini diganti
 * kata-kata. JANGAN menulis urutan garis-miring-bintang di dalam komentar.
 */
internal object UpdateWebFallback {
    private const val ACCEPT_HTML = "text/html"
    private const val HTTP_REDIRECT_MIN = 300
    private const val HTTP_REDIRECT_MAX = 399
    private val TAG_IN_LOCATION = Regex("/releases/tag/([^/?#]+)")
    private val APK_HREF = Regex("href=\"([^\"]*/releases/download/[^\"]+\\.apk)\"", RegexOption.IGNORE_CASE)
    private val BUILD_DIGITS = Regex("(\\d+)")

    fun fetchLatest(owner: String, repo: String): ReleaseRef {
        val tag = latestTag(owner, repo)
        val assetsUrl = "https://github.com/$owner/$repo/releases/expanded_assets/$tag"
        val page = UpdateHttp.getText(UpdateHttp.api, assetsUrl, ACCEPT_HTML)
        val href = apkHref(page) ?: throw IOException("APK rilis $tag tidak ditemukan")
        val build = BUILD_DIGITS.find(tag)?.value
        return ReleaseRef(tag, if (build != null) "LagFix build $build" else tag, href)
    }

    /** Tag dari header `Location` pengalihan releases/latest; null bila bukan URL `/releases/tag/<tag>`. */
    internal fun tagFromLocation(location: String?): String? =
        location?.let { TAG_IN_LOCATION.find(it)?.groupValues?.get(1) }

    /** Tautan APK pertama (jalur releases/download, berakhiran .apk) di HTML daftar aset, dijadikan URL absolut. */
    internal fun apkHref(html: String): String? {
        val href = APK_HREF.find(html)?.groupValues?.get(1)?.replace("&amp;", "&") ?: return null
        return if (href.startsWith("http")) href else "https://github.com$href"
    }

    private fun latestTag(owner: String, repo: String): String {
        val request = UpdateHttp.request("https://github.com/$owner/$repo/releases/latest")
        return UpdateHttp.noRedirect.newCall(request).execute().use { response ->
            if (response.code !in HTTP_REDIRECT_MIN..HTTP_REDIRECT_MAX) throw IOException("HTTP ${response.code}")
            tagFromLocation(response.header("Location")) ?: throw IOException("Tag rilis terbaru tidak terbaca")
        }
    }
}
