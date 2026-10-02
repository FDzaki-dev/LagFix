package com.lagfix.fstrim

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import rikka.shizuku.Shizuku
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TRUNC_MARK = "(…terpotong, hanya bagian akhir)\n"
private const val COPY_BUFFER = 64 * 1024

/** Ambil [max] karakter TERAKHIR (yang terbaru paling penting); tandai bila terpotong. */
internal fun capTail(s: String, max: Int): String =
    if (s.length <= max) s else TRUNC_MARK + s.takeLast(max)

internal fun countNonBlankLines(s: String): Int = s.lineSequence().count { it.isNotBlank() }

/** Offset zona waktu sbg "UTC+08:00" / "UTC-03:30" / "UTC+00:00" dari milidetik (pembanding jam `dumpsys mount`). */
internal fun formatUtcOffset(offsetMs: Int): String {
    val totalMin = offsetMs / 60_000
    val sign = if (totalMin < 0) '-' else '+'
    val abs = if (totalMin < 0) -totalMin else totalMin
    return "UTC%c%02d:%02d".format(Locale.US, sign, abs / 60, abs % 60)
}

/** Ukuran byte yg mudah dibaca: "512 B", "1.5 KiB", "3.2 MiB". */
internal fun formatBytes(b: Long): String = when {
    b < 1024L -> "$b B"
    b < 1024L * 1024L -> "%.1f KiB".format(Locale.US, b / 1024.0)
    else -> "%.1f MiB".format(Locale.US, b / (1024.0 * 1024.0))
}

/** Nama bucket App Standby (nilai konstanta `UsageStatsManager.STANDBY_BUCKET_*`); angka mentah ikut dicetak pemanggil. */
internal fun standbyBucketName(b: Int): String = when (b) {
    5 -> "EXEMPTED"
    10 -> "ACTIVE"
    20 -> "WORKING_SET"
    30 -> "FREQUENT"
    40 -> "RARE"
    45 -> "RESTRICTED"
    50 -> "NEVER"
    else -> "bucket#$b"
}

internal data class CopyResult(val bytes: Long, val truncated: Boolean)

/**
 * Salin [input] ke [output] secara streaming (buffer tetap, TIDAK memuat seluruh isi ke memori)
 * dgn batas [maxBytes]. Bila data lebih besar, byte berlebih dibuang & `truncated=true`.
 * Tak menutup stream apa pun (tanggung jawab pemanggil).
 */
internal fun copyCapped(input: InputStream, output: OutputStream, maxBytes: Long): CopyResult {
    val buf = ByteArray(COPY_BUFFER)
    var total = 0L
    while (true) {
        val n = input.read(buf)
        if (n < 0) return CopyResult(total, false)
        val room = maxBytes - total
        if (n > room) {
            val keep = if (room > 0) room.toInt() else 0
            if (keep > 0) output.write(buf, 0, keep)
            return CopyResult(total + keep, true)
        }
        output.write(buf, 0, n)
        total += n
    }
}

/** Ringkasan hasil [LogcatSnapshot.capture] untuk umpan balik UI. */
data class SnapshotSummary(
    val shizukuReady: Boolean,
    val fstrimLines: Int,
    val appLines: Int,
    val exitRecords: Int,
    val rawName: String? = null,
    val rawBytes: Long = 0L,
    val rawLogBytes: Long = 0L,
    val rawTruncated: Boolean = false,
    val rawError: String? = null
)

/** Kalimat umpan balik utk UI (murni -> bisa dites). Kegagalan dump mentah SELALU disebut, tak disembunyikan. */
internal fun SnapshotSummary.feedback(): String {
    if (!shizukuReady) {
        return "Snapshot tersimpan TANPA logcat sistem (Shizuku belum siap); $exitRecords catatan proses mati."
    }
    val base = "Snapshot tersimpan: $appLines baris LagFix, $fstrimLines baris fstrim sistem, " +
        "$exitRecords catatan proses mati."
    val raw = when {
        rawError != null -> " Dump mentah GAGAL: $rawError"
        rawName != null ->
            " Dump mentah: $rawName (${formatBytes(rawBytes)} zip, ${formatBytes(rawLogBytes)} logcat" +
                (if (rawTruncated) ", TERPOTONG batas ukuran" else "") + ")."
        else -> ""
    }
    return base + raw
}

/**
 * v84 (perintah user: "perluas catch logcat aplikasi" supaya bukti investigasi yg mandek tak lagi
 * bergantung pada adb/PC) + v86 (perintah user: logcat harus TOTALITAS, jangan setengah-setengah dan
 * hasil tangkapan jangan terhalang bug). Hasil 2 berkas di Documents/LagFix:
 *  - `LagFix_diag_logcat_*.txt`  = RINGKASAN (dibaca di dalam app): alasan proses mati
 *    (`ApplicationExitInfo`, 30 terbaru), status sisi-app (tanpa Shizuku), status sistem
 *    (jobscheduler/notifikasi/servis/appops/standby/baterai/idle/last-fstrim), tampilan terfilter.
 *  - `LagFix_diag_logcatraw_*.zip` = DUMP MENTAH logcat LENGKAP (main+system+events+crash, TANPA filter
 *    apa pun) yg dialirkan (streaming) ke zip -> tak ada filter/grep yg bisa menyembunyikan bukti, dan
 *    tak memuat log besar ke memori. Berkas `.zip` sengaja BUKAN `.txt` supaya tak ikut daftar pembaca
 *    log di app (membuka berkas besar di dialog bisa merusak state).
 * Prinsip isolasi: tiap bagian punya try/catch sendiri; gagalnya satu bagian (mis. dumpsys) tak
 * membatalkan bagian lain; kegagalan dump mentah dicatat & dilaporkan, bukan membuat snapshot gagal.
 * Setelah dump, buffer logcat DIPERBESAR ke 16M (sementara, kembali normal saat reboot) supaya
 * pengambilan berikutnya mencakup puluhan menit, bukan ~30 detik seperti pada snapshot v84.
 * Bagian Shizuku butuh READY (shell UID boleh baca logcat — TERBUKTI di snapshot v84). Semua perintah =
 * konstanta, 0 input user. BLOCKING — panggil hanya dari Dispatchers.IO.
 * Snapshot HANYA dibuat saat user menekan tombol (bukan otomatis) -> tak membanjiri Documents.
 */
object LogcatSnapshot {
    private const val BUFFERS = "-b main -b system -b events -b crash"
    private const val RAW_CMD = "logcat -d -v threadtime $BUFFERS"
    private const val BUFFER_RESIZE_CMD = "logcat $BUFFERS -G 16M"
    private const val RAW_CAP_BYTES = 192L * 1024L * 1024L // pagar pengaman; buffer 16M ~ <130 MiB

    // v85 (akar masalah dari snapshot v84): "fstrim" ikut cocok dgn NAMA PAKET `com.lagfix.fstrim`, jadi
    // 80 baris hasil grep terisi derau modul OEM; "lagfix" juga memenuhi 500 baris. Perbaikan: tahap 1
    // = cakupan, tahap 2 = buang baris yg mengandung nama paket (fstrim) / hanya simpan baris peristiwa (app).
    internal const val PATTERN_FSTRIM = "fstrim|idle maint|disk maintenance"
    internal const val PATTERN_FSTRIM_EXCLUDE = "lagfix"
    internal const val PATTERN_APP_SCOPE = "lagfix|PersistentTrimService|TranManualCleanMgr|FGS stop"
    internal const val PATTERN_APP_EVENT =
        "PersistentTrimService|LIFECYCLE|TranManualCleanMgr|FGS stop|Killing|am_kill|kill proc|SIGKILL|died|" +
            "am_proc|Start proc|ForegroundService|JobScheduler|SystemJobService|WorkManager"

    private class StateCmd(val title: String, val cmd: String, val cap: Int)

    // Status SISTEM saat ini (tak berputar seperti logcat). Format keluaran tiap ROM bisa beda: filter
    // yg tak cocok hanya menghasilkan "(kosong)", bukan error — dan tak menghalangi bagian lain.
    private fun stateCommands(pkg: String): List<StateCmd> = listOf(
        StateCmd("Build fingerprint", "getprop ro.build.fingerprint", 600),
        StateCmd("Standby bucket (am get-standby-bucket)", "am get-standby-bucket $pkg", 400),
        StateCmd("Baterai (dumpsys battery)", "dumpsys battery | head -n 20", 1_500),
        StateCmd(
            "Power (wakefulness/charging/hemat daya; format belum terbukti)",
            "dumpsys power | grep -Ei 'mWakefulness=|mIsPowered=|mPlugType|BatterySaver|Low Power' | head -n 20",
            2_000
        ),
        StateCmd(
            "Doze/deviceidle (format belum terbukti)",
            "dumpsys deviceidle | grep -Ei 'mState=|mLightState=|mCharging=|mScreenOn=|$pkg' | head -n 20",
            2_500
        ),
        StateCmd(
            "Paket (versi & waktu pasang)",
            "dumpsys package $pkg | grep -Ei 'versionName|versionCode|firstInstallTime|lastUpdateTime|" +
                "installerPackageName|stopped=' | head -n 12",
            2_000
        ),
        StateCmd("AppOps (cmd appops get)", "cmd appops get $pkg | head -n 80", 6_000),
        StateCmd("Servis (dumpsys activity services)", "dumpsys activity services $pkg | head -n 120", 8_000),
        StateCmd(
            "Notifikasi (dumpsys notification)",
            "dumpsys notification --noredact | grep -i -B2 -A14 'pkg=$pkg' | head -n 160",
            10_000
        ),
        StateCmd("JobScheduler / WorkManager (dumpsys jobscheduler)", "dumpsys jobscheduler $pkg | head -n 150", 12_000)
    )

    fun capture(ctx: Context): Result<SnapshotSummary> = runCatching {
        val app = ctx.applicationContext
        val ready = FstrimExecutor.state(app) == ShizukuState.READY
        val now = System.currentTimeMillis()
        val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        val sb = StringBuilder()

        // 1) DUMP MENTAH lebih dulu (data paling cepat berputar). Terisolasi: gagal -> tercatat, bukan fatal.
        val raw: RawOutcome? = if (ready) captureRaw(app, "LagFix_diag_logcatraw_$fileStamp.zip", now) else null

        sb.append("Snapshot logcat LagFix (v86)\n")
        sb.append("Waktu: ${stamp(now)} (zona app ${formatUtcOffset(TimeZone.getDefault().getOffset(now))})\n")
        sb.append("Perkiraan boot terakhir: ${stamp(now - SystemClock.elapsedRealtime())}\n")
        sb.append("Shizuku: ${if (ready) "READY" else "BELUM SIAP (bagian logcat sistem dilewati)"}\n")
        if (ready) {
            val bootTrim = runCatching { describeBootTrim(BootTrimSetting.read()) }
                .getOrElse { "(gagal: ${it.javaClass.simpleName})" }
            sb.append("fstrim_mandatory_interval (sistem): $bootTrim\n")
        }

        sb.append("\n== Dump mentah logcat (zip, TANPA filter) ==\n")
        sb.append(describeRaw(raw, ready)).append('\n')

        sb.append("\n== Status sisi-app (tanpa Shizuku) ==\n")
        sb.append(capTail(appSideState(app, now), 6_000)).append('\n')

        sb.append("\n== Alasan proses LagFix terakhir mati (ApplicationExitInfo) ==\n")
        val (exitText, exitCount) = if (Build.VERSION.SDK_INT >= 30) exitReasons(app)
        else "(butuh Android 11+)" to 0
        sb.append(exitText).append('\n')

        var fstrimLines = 0
        var appLines = 0
        if (ready) {
            sb.append("\n== Ukuran buffer logcat & entri tertua buffer system (SEBELUM diperbesar) ==\n")
            sb.append(section("logcat -g 2>&1 | head -n 8").first).append('\n')
            sb.append(section("logcat -d -v threadtime -b system 2>&1 | head -n 3").first).append('\n')

            sb.append("\n== Jejak fstrim oleh sistem (logcat; boot = 'Running fstrim idle maintenance') ==\n")
            val f = section(
                "logcat -d -v threadtime $BUFFERS 2>&1 | grep -Ei '$PATTERN_FSTRIM' | " +
                    "grep -vi '$PATTERN_FSTRIM_EXCLUDE' | tail -n 80"
            )
            fstrimLines = f.second
            sb.append(capTail(f.first, 12_000)).append('\n')

            sb.append("\n== Berkas penanda fstrim terakhir (ls = presisi menit; stat = presisi detik) ==\n")
            sb.append(capTail(section("ls -l /data/system/last-fstrim 2>&1").first, 2_000)).append('\n')
            sb.append(capTail(section("stat /data/system/last-fstrim 2>&1").first, 2_000)).append('\n')

            sb.append("\n== dumpsys mount (baris maint/trim; jam kemungkinan UTC — bandingkan dgn zona app di atas) ==\n")
            sb.append(capTail(section("dumpsys mount 2>&1 | grep -Ei 'maint|trim' | head -n 10").first, 4_000)).append('\n')

            for (c in stateCommands(app.packageName)) {
                sb.append("\n== ${c.title} ==\n")
                sb.append(capTail(section(c.cmd).first, c.cap)).append('\n')
            }

            sb.append("\n== Logcat terkait LagFix, hanya baris peristiwa (300 baris terakhir; versi lengkap ada di zip) ==\n")
            val a = section(
                "logcat -d -v threadtime $BUFFERS 2>&1 | grep -Ei '$PATTERN_APP_SCOPE' | " +
                    "grep -Ei '$PATTERN_APP_EVENT' | tail -n 300"
            )
            appLines = a.second
            sb.append(capTail(a.first, 40_000)).append('\n')

            // Perbesar buffer SETELAH semua pengambilan (tak mengubah data yg sedang ditangkap).
            sb.append("\n== Perbesar buffer logcat ke 16M (sementara, kembali normal saat reboot) ==\n")
            sb.append("perintah: ").append(BUFFER_RESIZE_CMD).append('\n')
            sb.append("hasil: ").append(section("$BUFFER_RESIZE_CMD 2>&1").first).append('\n')
            sb.append(section("logcat -g 2>&1 | head -n 8").first).append('\n')
        }

        CrashLogger.writeDiagnostic(app, "logcat", sb.toString()).getOrThrow()
        SnapshotSummary(
            shizukuReady = ready,
            fstrimLines = fstrimLines,
            appLines = appLines,
            exitRecords = exitCount,
            rawName = raw?.takeIf { it.error == null }?.name,
            rawBytes = raw?.zipBytes ?: 0L,
            rawLogBytes = raw?.logBytes ?: 0L,
            rawTruncated = raw?.truncated ?: false,
            rawError = raw?.error
        )
    }

    // ---------------------------------------------------------------- dump mentah (streaming -> zip)

    private class RawOutcome(
        val name: String,
        val where: String,
        val zipBytes: Long,
        val logBytes: Long,
        val truncated: Boolean,
        val exit: Int?,
        val error: String?
    )

    private fun describeRaw(r: RawOutcome?, ready: Boolean): String = when {
        !ready -> "(dilewati: butuh Shizuku READY)"
        r == null -> "(tidak dijalankan)"
        r.error != null -> "GAGAL: ${r.error}"
        else -> "${r.name} -> ${r.where}; zip ${formatBytes(r.zipBytes)}, logcat ${formatBytes(r.logBytes)}" +
            ", exit=${r.exit ?: "?"}, terpotong=${r.truncated}. Kirim berkas .zip ini bila ringkasan kurang."
    }

    private fun captureRaw(app: Context, name: String, now: Long): RawOutcome {
        var logBytes = 0L
        var truncated = false
        var exit: Int? = null
        var shellError: String? = null
        return try {
            val written = writeRawZip(app, name) { zip ->
                logBytes = 0L // fill bisa dijalankan ulang (fallback) -> mulai dari nol
                truncated = false
                exit = null
                shellError = null
                zip.putNextEntry(ZipEntry("logcat_all.txt"))
                try {
                    val p = shizukuProcess(RAW_CMD)
                    val r = p.inputStream.use { copyCapped(it, zip, RAW_CAP_BYTES) }
                    logBytes = r.bytes
                    truncated = r.truncated
                    if (r.truncated) {
                        runCatching { p.destroy() }
                    } else {
                        exit = runCatching { p.waitFor() }.getOrNull()
                    }
                } catch (e: Throwable) {
                    shellError = "${e.javaClass.simpleName}: ${e.message}".take(300)
                    runCatching { zip.write("\n[ERROR dump: $shellError]\n".toByteArray(Charsets.UTF_8)) }
                }
                zip.closeEntry()
                val meta = "Dump mentah LagFix (v86)\nWaktu: ${stamp(now)} (zona app " +
                    "${formatUtcOffset(TimeZone.getDefault().getOffset(now))})\nPerintah: $RAW_CMD\n" +
                    "Byte logcat: $logBytes\nTerpotong batas ${RAW_CAP_BYTES} byte: $truncated\nExit: $exit\n" +
                    "Galat shell: ${shellError ?: "-"}\nFormat baris: logcat -v threadtime (tanpa tahun/zona).\n"
                zip.putNextEntry(ZipEntry("meta.txt"))
                zip.write(meta.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            RawOutcome(name, written.where, written.zipBytes, logBytes, truncated, exit, shellError)
        } catch (e: Throwable) {
            RawOutcome(name, "-", 0L, logBytes, truncated, exit, "${e.javaClass.simpleName}: ${e.message}".take(300))
        }
    }

    private class RawWrite(val where: String, val zipBytes: Long)

    private class CountingStream(private val inner: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            inner.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            inner.write(b, off, len)
            count += len
        }

        override fun flush() = inner.flush()
        override fun close() = inner.close()
    }

    /**
     * Tulis zip ke Documents/LagFix (MediaStore API 29+, siklus IS_PENDING resmi seperti CrashLogger);
     * gagal -> baris MediaStore dihapus & fallback ke app files dir. [fill] boleh dijalankan 2x bila
     * jalur pertama gagal di tengah (isi diambil ulang) — karena itu [fill] tak boleh melempar utk
     * galat shell (galat shell dicatat di dalam zip), hanya galat I/O tujuan yg memicu fallback.
     */
    private fun writeRawZip(ctx: Context, name: String, fill: (ZipOutputStream) -> Unit): RawWrite {
        var mediaError: Throwable? = null
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/LagFix/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = runCatching {
                resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            }.getOrElse {
                mediaError = it
                null
            }
            if (uri != null) {
                val attempt = runCatching {
                    val os = resolver.openOutputStream(uri)
                        ?: throw IOException("openOutputStream() return null untuk uri: $uri")
                    val counting = CountingStream(os)
                    ZipOutputStream(counting.buffered()).use(fill)
                    counting.count
                }
                if (attempt.isSuccess) {
                    runCatching {
                        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    }
                    return RawWrite("Documents/LagFix/$name", attempt.getOrDefault(0L))
                }
                mediaError = attempt.exceptionOrNull()
                runCatching { resolver.delete(uri, null, null) } // jangan tinggalkan baris pending 0 byte
            } else if (mediaError == null) {
                mediaError = IOException("contentResolver.insert() return null (MediaStore menolak buat row)")
            }
        }
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val file = File(dir, name)
        ZipOutputStream(file.outputStream().buffered()).use(fill)
        val note = mediaError?.let { " [MediaStore gagal: ${it.message}]" } ?: ""
        return RawWrite("App files (cadangan): ${file.absolutePath}$note", file.length())
    }

    /** Proses shell via Shizuku (refleksi, pola sama dgn `FstrimExecutor.sh`) — stdout+stderr digabung. */
    private fun shizukuProcess(cmd: String): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, arrayOf("sh", "-c", "$cmd 2>&1"), null, null) as Process
    }

    // ---------------------------------------------------------------- status sisi-app (tanpa Shizuku)

    private fun appSideState(ctx: Context, now: Long): String {
        val lines = mutableListOf<String>()
        fun item(label: String, block: () -> Any?) {
            lines += "$label: " + runCatching { block().toString() }
                .getOrElse { "(gagal: ${it.javaClass.simpleName}: ${it.message})".take(160) }
        }
        item("Toggle layanan persisten (Prefs)") { Prefs(ctx).persistentServiceEnabled }
        item("Umur proses app") {
            "${(SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()) / 1000} dtk"
        }
        item("Notifikasi diizinkan") { ctx.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() }
        item("Notifikasi aktif milik app") {
            val list = ctx.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty()
            if (list.isEmpty()) "(tidak ada)" else list.joinToString(" | ") {
                "id=${it.id} channel=${it.notification.channelId} " +
                    "flags=0x${Integer.toHexString(it.notification.flags)} umur=${(now - it.postTime) / 1000}dtk"
            }
        }
        item("Servis PersistentTrimService berjalan") {
            val am = ctx.getSystemService(ActivityManager::class.java)
            @Suppress("DEPRECATION") // sejak API 26 hanya mengembalikan servis milik app sendiri — cukup.
            val svc = am?.getRunningServices(100).orEmpty()
                .firstOrNull { it.service.className == PersistentTrimService::class.java.name }
            if (svc == null) "tidak" else "ya (foreground=${svc.foreground})"
        }
        item("Pengecualian optimasi baterai") {
            ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName)
        }
        if (Build.VERSION.SDK_INT >= 28) {
            item("Dibatasi di latar belakang (isBackgroundRestricted)") {
                ctx.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted
            }
            item("Standby bucket") {
                val b = ctx.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket
                if (b == null) "(tidak tersedia)" else "${standbyBucketName(b)} ($b)"
            }
        }
        return lines.joinToString("\n")
    }

    // ---------------------------------------------------------------- helper

    private fun stamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    /** Jalankan [cmd] lewat Shizuku; (teks, jumlah baris non-kosong). Tak pernah melempar. */
    private fun section(cmd: String): Pair<String, Int> = try {
        val (code, out) = FstrimExecutor.sh(cmd)
        val t = out.trim()
        if (t.isEmpty()) "(kosong, exit=$code)" to 0 else t to countNonBlankLines(t)
    } catch (e: Throwable) {
        "(gagal: ${e.javaClass.simpleName}: ${e.message})".take(200) to 0
    }

    @RequiresApi(30)
    private fun exitReasons(ctx: Context): Pair<String, Int> {
        return try {
            val am = ctx.getSystemService(ActivityManager::class.java)
                ?: return "(ActivityManager tidak tersedia)" to 0
            val list = am.getHistoricalProcessExitReasons(null, 0, 30)
            if (list.isEmpty()) {
                "(kosong)" to 0
            } else {
                list.joinToString("\n") { i ->
                    "${stamp(i.timestamp)} pid=${i.pid} reason=${exitReasonName(i.reason)}(${i.reason}) " +
                        "status=${i.status} importance=${i.importance} desc=${i.description ?: "-"}"
                } to list.size
            }
        } catch (e: Throwable) {
            "(gagal: ${e.javaClass.simpleName}: ${e.message})".take(200) to 0
        }
    }

    /** Nama alasan; angka mentah selalu ikut dicetak di pemanggil, jadi nama tak dikenal aman. */
    private fun exitReasonName(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        else -> "reason#$r"
    }
}
