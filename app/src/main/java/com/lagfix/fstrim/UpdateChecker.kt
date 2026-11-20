package com.lagfix.fstrim

import android.content.Context
import android.os.SystemClock
import android.util.Log
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.Source
import okio.buffer
import okio.sink
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Info rilis terbaru dari GitHub, untuk pembanding versi before/after + changelog di dalam app. */
data class UpdateInfo(
    val latestTag: String,
    val latestName: String,
    val latestBuild: Int,
    val downloadUrl: String,
    val changelog: String
)

sealed interface UpdateResult {
    data class UpToDate(val installedBuild: Int) : UpdateResult
    data class Available(val installedBuild: Int, val info: UpdateInfo) : UpdateResult
    data class Error(val message: String) : UpdateResult
}

private const val PERCENT_FULL = 100L
private const val PROGRESS_STEPS = 100L // v171: maks ~100 laporan per unduhan (1% per laporan)
private const val UNKNOWN_TOTAL_STEP_BYTES = 262_144L // v171: total tak diketahui -> laporan tiap 256 KiB
private const val COPY_CHUNK_BYTES = 8_192L // v180: satu segmen Okio (8 KiB) per putaran salin
private const val BYTES_PER_MB = 1_000_000L // v171: MB desimal, sama dgn Formatter.formatFileSize Android 8+
private const val TENTHS_PER_UNIT = 10L
private const val TAG = "UpdateChecker" // v174

/** v174: isi sementara `UpdateInfo.changelog` selama changelog diambil terpisah (setelah hasil cek tampil). */
internal const val CHANGELOG_LOADING = "Memuat changelog…"

/** v174: isi `UpdateInfo.changelog` bila changelog gagal diambil. Teks sama dgn versi sebelum v174. */
internal const val CHANGELOG_UNAVAILABLE = "Changelog tidak tersedia (gagal diambil dari repo)."

/**
 * v171: kemajuan unduhan APK. [total] = -1 bila server tak memberi Content-Length (bilah tak tentu).
 * Bukan bagian `UiState`: dibaca UI lewat lambda agar pembaruan sering tak merekomposisi seluruh layar.
 */
data class DownloadProgress(val done: Long, val total: Long) {
    /** Persen 0..100 (dibulatkan ke bawah, dijepit); null bila [total] tak diketahui. */
    val percent: Int?
        get() = if (total > 0L) (done * PERCENT_FULL / total).coerceIn(0L, PERCENT_FULL).toInt() else null

    /** Pecahan 0..1 utk bilah; null bila [total] tak diketahui. */
    val fraction: Float?
        get() = percent?.div(PERCENT_FULL.toFloat())
}

/** v171: byte -> "7,4 MB" (1 desimal, koma gaya Indonesia, dibulatkan). Murni. */
internal fun formatMegabytes(bytes: Long): String {
    val tenths = (bytes.coerceAtLeast(0L) * TENTHS_PER_UNIT + BYTES_PER_MB / 2) / BYTES_PER_MB
    return "${tenths / TENTHS_PER_UNIT},${tenths % TENTHS_PER_UNIT} MB"
}

/** v171: baris keterangan bawah bilah unduhan. null = belum terhubung (header belum diterima). Murni. */
internal fun downloadStatusLine(p: DownloadProgress?): String = when {
    p == null -> "Menghubungkan ke GitHub…"
    p.total > 0L -> "${formatMegabytes(p.done)} dari ${formatMegabytes(p.total)}"
    else -> "${formatMegabytes(p.done)} diunduh"
}

/** v171: jarak byte antar laporan progres; dibatasi agar laporan (dan rekomposisi UI) tak ribuan kali per unduhan. */
internal fun progressStepBytes(total: Long): Long =
    if (total > 0L) (total / PROGRESS_STEPS).coerceAtLeast(1L) else UNKNOWN_TOTAL_STEP_BYTES

/**
 * v171 (v180: aliran Okio): salin [source] ke [sink] sambil melapor lewat [onProgress]: sekali di awal (0, [total]),
 * tiap [progressStepBytes], dan sekali di akhir bila byte terakhir belum terlapor. Tiap putaran memindahkan paling
 * banyak [COPY_CHUNK_BYTES] lalu `emitCompleteSegments()` menulis segmen penuh ke tujuan, jadi memori tak menampung
 * seluruh berkas. Tak menutup aliran (penutupan = tugas `use {}` pemanggil); [sink] di-flush di akhir.
 */
internal fun copyWithProgress(
    source: Source,
    sink: BufferedSink,
    total: Long,
    onProgress: (done: Long, total: Long) -> Unit
) {
    val step = progressStepBytes(total)
    var done = 0L
    var reported = 0L
    onProgress(0L, total)
    var n = source.read(sink.buffer, COPY_CHUNK_BYTES)
    while (n >= 0L) {
        sink.emitCompleteSegments()
        done += n
        if (done - reported >= step) {
            reported = done
            onProgress(done, total)
        }
        n = source.read(sink.buffer, COPY_CHUNK_BYTES)
    }
    sink.flush()
    if (done != reported) onProgress(done, total)
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

    // v180: klien & batas waktu (cek: sambung 6 + baca 8 dtk dari v174; unduh: 15 + 15 dtk dari v171)
    // ada di [UpdateHttp].
    private const val ACCEPT_GITHUB = "application/vnd.github+json"

    // v178: jawaban API yg berarti kuota tanpa-token habis (403 = batas utama, 429 = batas sekunder).
    private val RATE_LIMITED_CODES = setOf("HTTP 403", "HTTP 429")

    // Batas jaringan+parse: JSONException/IOException/runtime lain semuanya dipetakan ke
    // UpdateResult.Error (cek update tak boleh bikin app crash) -> catch generik DISENGAJA.
    // v174 (laporan user: "Periksa pembaruan" lama, rekaman layar >= 41 dtk): changelog TIDAK lagi diambil di sini
    // (dulu permintaan kedua berurutan, sampai 10+10 dtk tambahan) -> lihat [fetchChangelog]; durasi tiap tahap dan
    // pengecualian mentah dicatat ke logcat (tag UpdateChecker) utk membuktikan tahap mana yg lambat di perangkat.
    @Suppress("TooGenericExceptionCaught")
    fun check(installedBuild: Int): UpdateResult {
        val start = SystemClock.elapsedRealtime()
        return try {
            val release = latestRelease()
            val latestBuild = Regex("""(\d+)""").find(release.tag)?.value?.toIntOrNull() ?: -1
            Log.i(TAG, "check: ${SystemClock.elapsedRealtime() - start}ms tag=${release.tag} terpasang=$installedBuild")

            if (latestBuild <= installedBuild) {
                UpdateResult.UpToDate(installedBuild)
            } else {
                val info = UpdateInfo(release.tag, release.name, latestBuild, release.apkUrl, CHANGELOG_LOADING)
                UpdateResult.Available(installedBuild, info)
            }
        } catch (e: Exception) {
            Log.w(TAG, "check gagal setelah ${SystemClock.elapsedRealtime() - start}ms", e)
            UpdateResult.Error(friendlyError(e))
        }
    }

    /**
     * v178: rilis terbaru lewat API GitHub; bila API menolak karena kuota tanpa-token habis (HTTP 403/429), jatuh ke
     * [UpdateWebFallback] (tanpa API). Galat lain (tanpa jaringan, batas waktu, JSON rusak) tetap dilempar apa adanya.
     */
    private fun latestRelease(): ReleaseRef = try {
        val json = JSONObject(get(RELEASE_API))
        val tag = json.optString("tag_name", "")
        val htmlUrl = json.optString("html_url", "https://github.com/$OWNER/$REPO/releases/latest")
        ReleaseRef(tag, json.optString("name", tag).ifBlank { tag }, findApkUrl(json, htmlUrl))
    } catch (e: IOException) {
        if (e.message !in RATE_LIMITED_CODES) throw e
        Log.w(TAG, "API GitHub menolak (${e.message ?: "null"}); pakai jalur web tanpa API", e)
        UpdateWebFallback.fetchLatest(OWNER, REPO)
    }

    /**
     * v174: CHANGELOG.md mentah dari branch main, dipanggil SETELAH hasil cek tampil di UI. Gagal apa pun ->
     * [CHANGELOG_UNAVAILABLE] (pengecualian mentah dicatat). Blocking (network) — WAJIB dari Dispatchers.IO.
     */
    fun fetchChangelog(): String {
        val start = SystemClock.elapsedRealtime()
        return runCatching { get(CHANGELOG_RAW) }
            .onSuccess { Log.i(TAG, "changelog: ${SystemClock.elapsedRealtime() - start}ms ${it.length} karakter") }
            .onFailure { Log.w(TAG, "changelog gagal setelah ${SystemClock.elapsedRealtime() - start}ms", it) }
            .getOrDefault(CHANGELOG_UNAVAILABLE)
    }

    // v13 (B2): sebelumnya UpdateResult.Error/downloadError tampilkan e.message mentah (mis.
    // "Unable to resolve host..." atau "HTTP 403") langsung ke user. Sekarang dipetakan ke pesan
    // Indonesia yg jelas utk 2 kasus yg disebut eksplisit di roadmap (tanpa koneksi, rate-limit
    // GitHub) — kasus lain tetap fallback ke e.message apa adanya (tidak coba tangani semua jenis
    // exception, sesuai "logic minimum"). Dipakai jg oleh MainViewModel.installUpdate().
    // v174: batas waktu (SocketTimeoutException) dulu ikut dipetakan ke "Tidak ada koneksi internet." padahal Wi-Fi
    // aktif (rekaman layar: 2x "Gagal memasang: Tidak ada koneksi internet." lalu unduhan berhasil di percobaan
    // ke-3). Kini hanya UnknownHostException (DNS/tanpa jaringan) yg memakai pesan itu.
    fun friendlyError(e: Exception): String = when {
        e is UnknownHostException -> "Tidak ada koneksi internet."
        e is SocketTimeoutException -> "GitHub tidak merespons (waktu habis), coba lagi."
        e is ConnectException -> "Tidak bisa terhubung ke GitHub, coba lagi."
        e.message in RATE_LIMITED_CODES -> "Terlalu banyak permintaan ke GitHub (rate limit), coba lagi nanti."
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
     * v171: [onProgress] (done, total) dipanggil dari thread pemanggil, total = -1 bila tak diketahui.
     * v180: OkHttp + Okio: `ResponseBody.source()` dialirkan ke berkas lewat `sink().buffer().use {}`,
     * tanpa `bytes()`.
     * Blocking (network+disk) — WAJIB dipanggil dari Dispatchers.IO.
     */
    // catch generik DISENGAJA: apa pun penyebab gagal di tengah unduhan -> hapus APK parsial,
    // lalu exception yg SAMA dilempar ulang (tidak ditelan).
    @Suppress("TooGenericExceptionCaught")
    fun download(context: Context, url: String, onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }): File {
        val dest = File(prepareUpdateDir(context), "update.apk")
        val start = SystemClock.elapsedRealtime()
        try {
            UpdateHttp.download.newCall(UpdateHttp.downloadRequest(url)).execute().use { response ->
                UpdateHttp.ensureOk(response.code)
                val total = response.body.contentLength()
                // v174: waktu sampai header diterima = fase "Menghubungkan ke GitHub…" di UI (termasuk redirect).
                Log.i(TAG, "download: terhubung ${SystemClock.elapsedRealtime() - start}ms total=$total")
                saveBody(response.body, dest, onProgress)
                Log.i(TAG, "download: selesai ${SystemClock.elapsedRealtime() - start}ms")
            }
        } catch (e: Exception) {
            // v13 (B3): koneksi putus di tengah unduhan -> jangan tinggalkan APK parsial.
            // v149: hasil delete() dipakai; gagal hapus = suppressed, exception ASLI tetap dilempar ulang.
            if (!dest.delete() && dest.exists()) e.addSuppressed(IOException("gagal menghapus ${dest.name}"))
            throw e
        }
        return dest
    }

    // v150 (detekt ThrowsCount, run 123 + laporan CI run 2: `download` punya 3 `throw`, maks 2): diekstrak dari
    // download(); isi persis sama dan dipanggil di titik yang sama (sebelum koneksi dibuka).
    private fun prepareUpdateDir(context: Context): File {
        val dir = File(context.cacheDir, "updates")
        dir.deleteRecursively()
        // v149 (SpotBugs RV_RETURN_VALUE_IGNORED_BAD_PRACTICE): hasil mkdirs() dipakai; gagal = galat jelas.
        if (!dir.mkdirs() && !dir.isDirectory) throw IOException("gagal membuat folder ${dir.name}")
        return dir
    }

    // v92 (detekt NestedBlockDepth): diekstrak dari download(). v180: aliran Okio; total dari Content-Length
    // (-1 bila tak ada); `use {}` menutup berkas tujuan (flush terakhir) baik sukses maupun galat.
    private fun saveBody(body: ResponseBody, dest: File, onProgress: (done: Long, total: Long) -> Unit) {
        dest.sink().buffer().use { out -> copyWithProgress(body.source(), out, body.contentLength(), onProgress) }
    }

    private fun get(url: String): String = UpdateHttp.getText(UpdateHttp.api, url, ACCEPT_GITHUB)
}
