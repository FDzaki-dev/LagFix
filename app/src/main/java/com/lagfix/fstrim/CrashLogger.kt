package com.lagfix.fstrim

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menangkap force close → Documents/LagFix/ (MediaStore API 29+, fallback app files dir).
 * v51 (permintaan user: log jangan menuhin folder Download): folder tujuan dipindah dari
 * Download/LagFix/ ke Documents/LagFix/ (koleksi `MediaStore.Files`, RELATIVE_PATH Documents/).
 * File lama di Download/LagFix/ TIDAK dipindah/dihapus, tetap terbaca di `listLogs()`.
 * v30 (fix laporan user: "crash logger tidak mencatat apa-apa, foldernya sendiri gak nampak"):
 * root cause PALING MUNGKIN dari review kode (P0 NO HALLUCINATION — dugaan terkuat, BUKAN
 * kepastian 100% krn 0 device di sandbox ini) = versi lama bisa "sukses" (0 exception, ketangkep
 * `runCatching` luar) padahal 0 byte tertulis & folder TIDAK PERNAH benar2 dibuat:
 * (a) `contentResolver.openOutputStream(uri)?.use { }` diam2 SKIP nulis kalau return null (bisa
 *     terjadi di sejumlah ROM/kondisi) — tanpa exception apapun, jadi tak pernah ketahuan.
 * (b) file diinsert TANPA siklus `IS_PENDING` resmi (insert pending → tulis → clear pending) —
 *     pola resmi Android utk Downloads collection; tanpa ini sejumlah OEM (mis. Infinix XOS,
 *     sudah terbukti agresif/berbeda di riwayat v22-v25) bisa menahan file/folder dari file-
 *     manager/MediaStore query sampai tak pernah kelihatan "nampak".
 * FIX (0 perubahan titik panggil — signature `install()`/`logDiagnostic()` tetap identik):
 * (1) siklus IS_PENDING resmi ditambahkan. (2) null/gagal kini MELEMPAR exception eksplisit
 * (bukan diam2 no-op). (3) kalau jalur MediaStore gagal total, fallback ke app-specific external
 * files dir (0 permission dibutuhkan, dijamin ada) + pesan error MediaStore asli ikut ditulis —
 * jadi SELALU ada bukti di suatu tempat, bukan diam total. (4) `Log.e` ditambah sbg lapis
 * observability terakhir (logcat), konsisten tujuan ANDROID VITAL GUARDS: bug jadi observable &
 * diagnosable, bukan menghilang tanpa jejak.
 */
object CrashLogger {
    private const val TAG = "LagFixCrashLogger"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val body = "Thread: ${t.name}\nSDK: ${Build.VERSION.SDK_INT}\n" +
                    "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n\n" + Log.getStackTraceString(e)
                writeToFile(app, "crash", body)
            }.onFailure { Log.e(TAG, "Gagal tulis crash log", it) }
            prev?.uncaughtException(t, e)
        }
    }

    /** v29: log evidence non-crash ke file (sama folder/mekanisme dgn crash), dipanggil manual dari
     * titik yg diinvestigasi (bukan otomatis tiap event — cegah membanjiri Documents/LagFix/). */
    fun logDiagnostic(ctx: Context, tag: String, message: String) {
        runCatching {
            val body = "SDK: ${Build.VERSION.SDK_INT}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\n\n$message"
            writeToFile(ctx.applicationContext, "diag_$tag", body)
        }.onFailure { Log.e(TAG, "Gagal tulis diagnostic log ($tag)", it) }
    }

    /** v84 (perintah user: perluas catch logcat): sama dgn [logDiagnostic] tapi hasil tulis
     * dikembalikan ke caller (`Result`) supaya UI bisa melapor sukses/gagal. Aditif — isi
     * `logDiagnostic()`/`testWrite()` tidak diubah. Dipakai `LogcatSnapshot`. */
    fun writeDiagnostic(ctx: Context, tag: String, message: String): Result<Unit> = runCatching {
        val body = "SDK: ${Build.VERSION.SDK_INT}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\n\n$message"
        writeToFile(ctx.applicationContext, "diag_$tag", body)
    }

    /** v32 (laporan user: sudah pasang build v31 + spam "Muat ulang" di Log Diagnostik, 0 entri
     * sama sekali — beda dari sebelumnya krn kali ini BUKAN soal visibility file manager lagi,
     * app sendiri via ContentResolver pun 0 nemu apa-apa). Root cause paling mungkin (P0 NO
     * HALLUCINATION — dugaan, bukan pasti): `logDiagnostic()` CUMA dipanggil dari
     * `PersistentTrimService.onStartCommand()`, yg CUMA jalan kalau toggle "Layanan latar depan
     * persisten" di Pengaturan di-ON-kan; `install()` cuma jalan kalau app CRASH. Kalau user belum
     * pernah toggle ON fitur itu (atau app belum pernah crash) SEJAK pasang build ini, ya WAJAR 0
     * entri — bukan berarti penulisan filenya rusak, tapi jalur tulisnya memang belum pernah
     * dipanggil sama sekali. Fungsi ini MEMISAHKAN 2 kemungkinan itu: nulis 1 file tes LANGSUNG,
     * TANPA precondition apa pun (0 butuh toggle servis/crash) — hasilnya dilaporkan balik sbg
     * `Result` supaya UI bisa kasih tahu user LANGSUNG (bukan cuma Log.e/logcat yg tak kelihatan
     * tanpa ADB): sukses -> jalur tulis TERBUKTI berfungsi di HP ini (berarti 0 entri sebelumnya
     * krn toggle/crash memang belum pernah terjadi); gagal -> BARU itu bukti nyata jalur tulis
     * (MediaStore MAUPUN fallback app files dir) berdua bermasalah di HP ini, dgn pesan error
     * eksplisit dari keduanya (lihat `writeToFile()`). */
    fun testWrite(ctx: Context): Result<Unit> = runCatching {
        val body = "SDK: ${Build.VERSION.SDK_INT}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\n\n" +
            "Ini file TES manual dari tombol \"Tes tulis log\" — kalau file ini kebaca, penulisan " +
            "log (MediaStore atau fallback) BERHASIL di HP ini."
        writeToFile(ctx.applicationContext, "test", body)
    }

    private fun writeToFile(ctx: Context, prefix: String, body: String) {
        val name = "LagFix_${prefix}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
        if (Build.VERSION.SDK_INT >= 29) {
            val mediaError = runCatching { writeViaMediaStore(ctx, name, body) }.exceptionOrNull()
            if (mediaError == null) return
            Log.e(TAG, "MediaStore write ke Documents/LagFix gagal, fallback ke app files dir", mediaError)
            val fallbackError = runCatching {
                writeToAppFilesDir(ctx, name, body + "\n\n[MediaStore GAGAL, fallback ke app files dir: $mediaError]")
            }.exceptionOrNull()
            if (fallbackError != null) {
                Log.e(TAG, "Fallback app files dir JUGA gagal", fallbackError)
                throw IOException("MediaStore gagal ($mediaError) DAN fallback app files dir juga gagal ($fallbackError)")
            }
        } else {
            writeToAppFilesDir(ctx, name, body)
        }
    }

    /** v51: koleksi Files volume utama — dipakai penulis (insert ke Documents/) & pembaca. API 29+. */
    private fun filesCollection() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** Melempar exception eksplisit di tiap titik gagal (bukan diam2 no-op) supaya caller tahu
     * persis kenapa, dan siklus IS_PENDING resmi dipakai supaya file+folder pasti ter-finalize. */
    private fun writeViaMediaStore(ctx: Context, name: String, body: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/LagFix/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        // v51: `MediaStore.Downloads` hanya utk folder Download -> pakai koleksi Files (API 29+).
        val uri = ctx.contentResolver.insert(filesCollection(), values)
            ?: throw IOException("contentResolver.insert() return null (MediaStore menolak buat row)")
        val stream = ctx.contentResolver.openOutputStream(uri)
            ?: throw IOException("openOutputStream() return null untuk uri: $uri")
        stream.use { it.write(body.toByteArray()) }
        val clearPending = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        ctx.contentResolver.update(uri, clearPending, null, null)
    }

    /** Fallback tanpa permission (selalu tersedia): app-specific external dir, atau internal
     * filesDir kalau external tak tersedia sama sekali — supaya tidak pernah diam total. */
    private fun writeToAppFilesDir(ctx: Context, name: String, body: String) {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        File(dir, name).writeText(body)
    }

    /** v31 (laporan user: setelah fix v30 pun folder Download/LagFix/ TETAP tidak kelihatan sama
     * sekali — dipahami sbg minta pembaca log LANGSUNG di dalam aplikasi, supaya lepas total dari
     * ketergantungan file manager/OS yg mungkin tidak menampilkan folder baru di sebagian HP).
     * Baca lewat `ContentResolver` milik app sendiri (0 tergantung indexing/tampilan file manager
     * pihak lain) + app files dir. Kalau daftar ini tetap kosong, itu bukti kuat penulisannya
     * sendiri yg gagal (bukan cuma soal visibility) — beda diagnosis dari kalau daftar ini muncul
     * isi tapi file managernya yg tak menampilkan. IO — WAJIB dipanggil dari luar Main thread. */
    class LogFile(val displayName: String, val source: String, private val reader: () -> String) {
        fun readText(): String = reader()
    }

    fun listLogs(ctx: Context): List<LogFile> {
        val result = mutableListOf<LogFile>()
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val collection = filesCollection()
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH
                )
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
                val args = arrayOf("%LagFix%", "LagFix_%.txt")
                ctx.contentResolver.query(
                    collection, projection, selection, args,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    while (c.moveToNext()) {
                        val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                        val name = c.getString(nameCol)
                        // v51: label = folder asli (Documents/LagFix baru, Download/LagFix lama).
                        val folder = c.getString(pathCol)?.trimEnd('/') ?: "Documents/LagFix"
                        result += LogFile(name, folder) {
                            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { r -> r.readText() }
                                ?: "(gagal buka isi file — openInputStream() return null)"
                        }
                    }
                }
            }.onFailure { Log.e(TAG, "Gagal query log dari MediaStore", it) }
        }
        runCatching {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            dir.listFiles { f -> f.isFile && f.name.startsWith("LagFix_") && f.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { f -> result += LogFile(f.name, "App files (cadangan)") { f.readText() } }
        }.onFailure { Log.e(TAG, "Gagal baca app files dir", it) }
        return result.take(50) // cegah daftar membengkak tanpa batas kalau ada banyak entri lama
    }
}
