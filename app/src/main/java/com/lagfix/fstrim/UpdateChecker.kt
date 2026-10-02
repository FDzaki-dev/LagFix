package com.lagfix.fstrim

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

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

    // v92 (detekt MagicNumber): nilai identik dgn literal sebelumnya.
    private const val DOWNLOAD_TIMEOUT_MS = 15_000
    private const val API_TIMEOUT_MS = 10_000
    private const val HTTP_OK_MIN = 200
    private const val HTTP_OK_MAX = 299

    // Batas jaringan+parse: JSONException/IOException/runtime lain semuanya dipetakan ke
    // UpdateResult.Error (cek update tak boleh bikin app crash) -> catch generik DISENGAJA.
    @Suppress("TooGenericExceptionCaught")
    fun check(installedBuild: Int): UpdateResult = try {
        val json = JSONObject(get(RELEASE_API))
        val tag = json.optString("tag_name", "")
        val name = json.optString("name", tag).ifBlank { tag }
        val htmlUrl = json.optString("html_url", "https://github.com/$OWNER/$REPO/releases/latest")
        val latestBuild = Regex("""(\d+)""").find(tag)?.value?.toIntOrNull() ?: -1

        if (latestBuild <= installedBuild) {
            UpdateResult.UpToDate(installedBuild)
        } else {
            val apkUrl = findApkUrl(json, htmlUrl)
            val changelog = runCatching { get(CHANGELOG_RAW) }
                .getOrDefault("Changelog tidak tersedia (gagal diambil dari repo).")
            UpdateResult.Available(installedBuild, UpdateInfo(tag, name, latestBuild, apkUrl, changelog))
        }
    } catch (e: Exception) {
        UpdateResult.Error(friendlyError(e))
    }

    // v13 (B2): sebelumnya UpdateResult.Error/downloadError tampilkan e.message mentah (mis.
    // "Unable to resolve host..." atau "HTTP 403") langsung ke user. Sekarang dipetakan ke pesan
    // Indonesia yg jelas utk 2 kasus yg disebut eksplisit di roadmap (tanpa koneksi, rate-limit
    // GitHub) — kasus lain tetap fallback ke e.message apa adanya (tidak coba tangani semua jenis
    // exception, sesuai "logic minimum"). Dipakai jg oleh MainViewModel.installUpdate().
    fun friendlyError(e: Exception): String = when {
        e is UnknownHostException || e is SocketTimeoutException -> "Tidak ada koneksi internet."
        e.message == "HTTP 403" -> "Terlalu banyak permintaan ke GitHub (rate limit), coba lagi nanti."
        else -> e.message ?: "Tidak diketahui"
    }

    // v92 (detekt NestedBlockDepth + LoopWithTooManyJumpStatements): diekstrak dari check().
    // Perilaku identik: asset non-null PERTAMA yg namanya berakhiran .apk -> browser_download_url
    // (fallback ke [fallback] bila kunci tak ada); tak ada asset/APK -> [fallback].
    private fun findApkUrl(json: JSONObject, fallback: String): String {
        val assets = json.optJSONArray("assets") ?: return fallback
        val apk = (0 until assets.length())
            .asSequence()
            .mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
        return apk?.optString("browser_download_url", fallback) ?: fallback
    }

    /**
     * Unduh APK ke cache privat app (BUKAN folder Download publik) -> dipasang lewat
     * Package Installer langsung (FileProvider), tidak lewat browser, tidak menumpuk
     * (sisa unduhan lama dihapus dulu tiap kali unduh baru dimulai).
     * Blocking (network+disk) — WAJIB dipanggil dari Dispatchers.IO.
     */
    // catch generik DISENGAJA: apa pun penyebab gagal di tengah unduhan -> hapus APK parsial,
    // lalu exception yg SAMA dilempar ulang (tidak ditelan).
    @Suppress("TooGenericExceptionCaught")
    fun download(context: Context, url: String): File {
        val dir = File(context.cacheDir, "updates").apply {
            deleteRecursively()
            mkdirs()
        }
        val dest = File(dir, "update.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = DOWNLOAD_TIMEOUT_MS
            conn.readTimeout = DOWNLOAD_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "LagFix-App")
            conn.requestMethod = "GET"
            if (conn.responseCode !in HTTP_OK_MIN..HTTP_OK_MAX) throw IOException("HTTP ${conn.responseCode}")
            try {
                saveStream(conn, dest)
            } catch (e: Exception) {
                dest.delete() // v13 (B3): koneksi putus di tengah unduhan -> jangan tinggalkan APK parsial
                throw e
            }
        } finally {
            conn.disconnect()
        }
        return dest
    }

    // v92 (detekt NestedBlockDepth): diekstrak dari download(); isi persis sama.
    private fun saveStream(conn: HttpURLConnection, dest: File) {
        conn.inputStream.use { input -> FileOutputStream(dest).use { output -> input.copyTo(output) } }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = API_TIMEOUT_MS
            conn.readTimeout = API_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "LagFix-App")
            if (conn.responseCode !in HTTP_OK_MIN..HTTP_OK_MAX) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
