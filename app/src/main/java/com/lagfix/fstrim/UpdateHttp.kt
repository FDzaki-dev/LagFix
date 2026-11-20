package com.lagfix.fstrim

import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * v180 (perintah user: "Perbaiki dan terapkan OkHttp+Okio"): satu-satunya pintu jaringan alur pembaruan (cek rilis,
 * changelog, jalur cadangan web, unduh APK); menggantikan `HttpURLConnection`. Tubuh respons dibaca sebagai aliran
 * Okio (`ResponseBody.source()`), tidak pernah dimuat utuh lewat `bytes()`. Klien dibuat malas supaya memuat kelas
 * ini tak menyentuh TLS/jaringan. Semua pemanggilan blocking — WAJIB dari Dispatchers.IO.
 */
internal object UpdateHttp {
    private const val USER_AGENT = "LagFix-App"
    private const val CONNECT_TIMEOUT_MS = 6_000L
    private const val READ_TIMEOUT_MS = 8_000L
    private const val DOWNLOAD_TIMEOUT_MS = 15_000L
    private const val HTTP_OK_MIN = 200
    private const val HTTP_OK_MAX = 299

    /** Batas teks (JSON rilis, changelog, HTML): 4 MiB; lebih dari itu = galat, bukan dimuat ke memori. */
    const val MAX_TEXT_BYTES = 4L * 1_024L * 1_024L

    /** Cek rilis/changelog/halaman web: sambung 6 dtk + baca 8 dtk (nilai v174), mengikuti pengalihan. */
    val api: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    /** Jalur cadangan: TIDAK mengikuti pengalihan (header `Location` dibaca apa adanya); pool dipakai bersama. */
    val noRedirect: OkHttpClient by lazy {
        api.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }

    /** Unduh APK: sambung & baca 15 dtk (nilai v171); pengalihan ke host aset GitHub diikuti. */
    val download: OkHttpClient by lazy {
        api.newBuilder()
            .connectTimeout(DOWNLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(DOWNLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    /** GET [url] dengan User-Agent aplikasi; [accept] bernilai null = tanpa header Accept. */
    fun request(url: String, accept: String? = null): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .apply { if (accept != null) header("Accept", accept) }
            .build()

    /** Permintaan unduhan: tanpa kompresi supaya Content-Length = ukuran APK asli (bilah progres akurat, v171). */
    fun downloadRequest(url: String): Request =
        request(url).newBuilder().header("Accept-Encoding", "identity").build()

    /** GET [url] lalu baca tubuhnya sebagai UTF-8 (maks [MAX_TEXT_BYTES]); bukan 2xx -> IOException("HTTP <kode>"). */
    fun getText(client: OkHttpClient, url: String, accept: String): String =
        client.newCall(request(url, accept)).execute().use { response ->
            ensureOk(response.code)
            readBoundedText(response.body.source(), MAX_TEXT_BYTES)
        }

    /** Pesan "HTTP <kode>" identik dgn sebelum v180 (dipakai `UpdateChecker.friendlyError` & deteksi rate limit). */
    fun ensureOk(code: Int) {
        if (code !in HTTP_OK_MIN..HTTP_OK_MAX) throw IOException("HTTP $code")
    }

    /** Baca sisa [source] sebagai UTF-8; lebih dari [maxBytes] byte -> IOException (tak dimuat utuh ke memori). */
    fun readBoundedText(source: BufferedSource, maxBytes: Long): String {
        if (source.request(maxBytes + 1L)) throw IOException("Respons melebihi $maxBytes byte")
        return source.readUtf8()
    }
}
