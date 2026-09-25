package com.lagfix.fstrim

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Info rilis terbaru dari GitHub, untuk pembanding versi before/after + changelog di dalam app. */
data class UpdateInfo(
    val latestTag: String,
    val latestName: String,
    val latestBuild: Int,
    val downloadUrl: String,
    val changelog: String
)

sealed class UpdateResult {
    data class UpToDate(val installedBuild: Int) : UpdateResult()
    data class Available(val installedBuild: Int, val info: UpdateInfo) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

/**
 * Cek GitHub Releases (releases/latest) + CHANGELOG.md mentah dari branch main.
 * Blocking (network) — WAJIB dipanggil dari Dispatchers.IO.
 */
object UpdateChecker {
    private const val OWNER = "FDzaki-dev"
    private const val REPO = "LagFix"
    private const val RELEASE_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val CHANGELOG_RAW = "https://raw.githubusercontent.com/$OWNER/$REPO/main/CHANGELOG.md"

    fun check(installedBuild: Int): UpdateResult = try {
        val json = JSONObject(get(RELEASE_API))
        val tag = json.optString("tag_name", "")
        val name = json.optString("name", tag).ifBlank { tag }
        val htmlUrl = json.optString("html_url", "https://github.com/$OWNER/$REPO/releases/latest")
        val latestBuild = Regex("""(\d+)""").find(tag)?.value?.toIntOrNull() ?: -1

        if (latestBuild <= installedBuild) {
            UpdateResult.UpToDate(installedBuild)
        } else {
            var apkUrl = htmlUrl
            json.optJSONArray("assets")?.let { assets ->
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", htmlUrl)
                        break
                    }
                }
            }
            val changelog = runCatching { get(CHANGELOG_RAW) }
                .getOrDefault("Changelog tidak tersedia (gagal diambil dari repo).")
            UpdateResult.Available(installedBuild, UpdateInfo(tag, name, latestBuild, apkUrl, changelog))
        }
    } catch (e: Exception) {
        UpdateResult.Error(e.message ?: "Tidak diketahui")
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "LagFix-App")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
