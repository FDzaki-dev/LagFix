package com.lagfix.fstrim

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

// v93 (detekt MagicNumber): nilai identik dgn literal sebelumnya.
private const val DEFAULT_INTERVAL_MINUTES = 24L * 60L
private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

data class UiState(
    val shizuku: ShizukuState = ShizukuState.NOT_RUNNING,
    val enabled: Boolean = false,
    val intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES, // v44: menit (sebelumnya jam)
    val radicalInterval: Boolean = false, // v118: izinkan interval < 15 menit
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val running: Boolean = false,
    val lastRunMs: Long = 0L,
    val lastOk: Boolean = false,
    val log: List<String> = emptyList(),
    val batteryUnrestricted: Boolean = true,
    val backgroundRestricted: Boolean = false, // v111: ActivityManager.isBackgroundRestricted
    val updateChecking: Boolean = false,
    val updateResult: UpdateResult? = null,
    val downloading: Boolean = false,
    val downloadError: String? = null,
    val persistentServiceEnabled: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val onBinder = Shizuku.OnBinderReceivedListener { refresh() }
    private val onDead = Shizuku.OnBinderDeadListener { refresh() }
    private val onPerm = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    var ui by mutableStateOf(readUiState(app, prefs))
        private set

    init {
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPerm)
    }

    fun refresh() {
        ui = readUiState(getApplication<Application>(), prefs, ui.running).copy(
            updateChecking = ui.updateChecking,
            updateResult = ui.updateResult,
            downloading = ui.downloading,
            downloadError = ui.downloadError
        )
    }

    // v93 (detekt TooManyFunctions 16 -> 10): `reschedule()` (2 pemanggil) di-inline; `setThemeMode()`
    // dihapus (0 pemanggil di seluruh source, tema = dark only sejak v60); `read()`,
    // `isBatteryUnrestricted()`, `batteryOptimizationIntent()`, `requestPermission()` dipindah ke
    // fungsi top-level di bawah class (tak menyentuh state ViewModel).
    fun setEnabled(v: Boolean) {
        prefs.enabled = v
        Scheduler.apply(getApplication<Application>(), prefs)
        refresh()
    }

    // v118: `radical` = toggle mode interval radikal (< 15 menit), bebas on/off kapan saja. Sengaja 1 fungsi
    // (bukan fungsi baru) agar `MainViewModel` tetap 10 fungsi (ambang detekt). Mati -> interval < 15 dinaikkan
    // ke 15 menit (batas periodik WorkManager) supaya UI/widget jujur.
    fun setInterval(minutes: Long, radical: Boolean = prefs.radicalInterval) {
        prefs.radicalInterval = radical
        prefs.intervalMinutes = minutes.coerceAtLeast(Scheduler.minIntervalMinutes(radical))
        Scheduler.apply(getApplication<Application>(), prefs)
        refresh()
    }

    // v27 (fitur opsional, pilihan eksplisit user): start/stop PersistentTrimService ikut toggle.
    // 0 logic fstrim/Scheduler disentuh — service ini cuma menjaga proses tetap hidup.
    fun setPersistentService(v: Boolean) {
        prefs.persistentServiceEnabled = v
        val app = getApplication<Application>()
        if (v) PersistentTrimService.start(app) else PersistentTrimService.stop(app)
        syncExcludeFromRecents(app) // v133: Recents disembunyikan hanya saat toggle ON
        ui = ui.copy(persistentServiceEnabled = v)
    }

    // v83 (H2(1), perintah user): kartu "Paksa trim saat reboot". State TERPISAH dari UiState krn
    // dibaca lewat shell Shizuku (blocking) -> tak boleh masuk read() yg jalan di Main thread.
    var bootTrim by mutableStateOf(BootTrimUi())
        private set

    fun refreshBootTrim() {
        if (bootTrim.busy) return
        bootTrim = bootTrim.copy(busy = true)
        viewModelScope.launch(Dispatchers.IO) {
            val ready = FstrimExecutor.state(getApplication<Application>()) == ShizukuState.READY
            bootTrim = BootTrimUi(reading = if (ready) BootTrimSetting.read() else null)
        }
    }

    fun changeBootTrim(enable: Boolean) {
        if (bootTrim.busy) return
        bootTrim = bootTrim.copy(busy = true, note = null)
        viewModelScope.launch(Dispatchers.IO) {
            if (FstrimExecutor.state(getApplication<Application>()) != ShizukuState.READY) {
                bootTrim = BootTrimUi(note = "Shizuku belum siap.")
                return@launch
            }
            val o = if (enable) BootTrimSetting.enableEveryReboot() else BootTrimSetting.reset()
            bootTrim = BootTrimUi(reading = o.reading, note = o.message)
        }
    }

    fun runNow() {
        if (ui.running) return
        ui = ui.copy(running = true)
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val r = if (FstrimExecutor.state(app) == ShizukuState.READY) FstrimExecutor.run()
            else TrimResult(false, "Shizuku belum siap", 0L)
            prefs.record(r, TriggerSource.MANUAL) // v38: runNow() = tombol di app = selalu manual
            Scheduler.notifyChanged(app)
            ui = readUiState(app, prefs).copy(
                updateChecking = ui.updateChecking,
                updateResult = ui.updateResult,
                downloading = ui.downloading,
                downloadError = ui.downloadError
            )
        }
    }

    fun checkUpdate() {
        if (ui.updateChecking) return
        ui = ui.copy(updateChecking = true, updateResult = null, downloadError = null)
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val installedBuild = runCatching {
                val pi = app.packageManager.getPackageInfo(app.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode.toInt()
                else @Suppress("DEPRECATION") pi.versionCode
            }.getOrDefault(1)
            val result = UpdateChecker.check(installedBuild)
            ui = ui.copy(updateChecking = false, updateResult = result)
        }
    }

    /** Unduh APK ke cache app lalu langsung buka Package Installer — tanpa browser, tanpa file menumpuk di Download. */
    // v93: catch Exception DISENGAJA — batas jaringan+disk+Intent: kegagalan apa pun jadi pesan di UI
    // (UpdateChecker.friendlyError), bukan crash.
    @Suppress("TooGenericExceptionCaught")
    fun installUpdate(url: String) {
        if (ui.downloading) return
        ui = ui.copy(downloading = true, downloadError = null)
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            try {
                val apk = UpdateChecker.download(app, url)
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", apk)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                app.startExternal(intent)
                ui = ui.copy(downloading = false)
            } catch (e: Exception) {
                ui = ui.copy(downloading = false, downloadError = UpdateChecker.friendlyError(e))
            }
        }
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onDead)
        Shizuku.removeRequestPermissionResultListener(onPerm)
    }
}

// v93 (detekt TooManyFunctions): dikeluarkan dari MainViewModel; isi sama persis dgn versi member.
private fun readUiState(app: Application, prefs: Prefs, running: Boolean = false): UiState = UiState(
    shizuku = FstrimExecutor.state(app),
    enabled = prefs.enabled,
    intervalMinutes = prefs.intervalMinutes,
    radicalInterval = prefs.radicalInterval,
    themeMode = prefs.themeMode,
    running = running,
    lastRunMs = prefs.lastRunMs,
    lastOk = prefs.lastOk,
    log = prefs.log,
    batteryUnrestricted = isBatteryUnrestricted(app),
    backgroundRestricted = isBackgroundRestricted(app),
    persistentServiceEnabled = prefs.persistentServiceEnabled
)

// v21: root cause laporan user "jadwal otomatis tak tercatat" — confirmed toggle sudah ON dari
// awal + device Infinix XOS, salah satu ROM yg dikenal agresif membunuh background job WorkManager
// kalau app tak dikecualikan dari optimasi baterai. Ini bukan bug logic (Prefs.record()/Riwayat
// sudah dicek unconditional, 0 filter) — ini restriksi OS/OEM di luar kendali kode.
private fun isBatteryUnrestricted(app: Application): Boolean {
    val pm = app.getSystemService(PowerManager::class.java) ?: return true
    return pm.isIgnoringBatteryOptimizations(app.packageName)
}

// v111 (kandidat (ii), perintah user): `isBackgroundRestricted` BEDA dari `isIgnoringBatteryOptimizations`.
// Snapshot v95: keduanya bisa bertentangan (dikecualikan dari optimasi = true, tapi dibatasi di background =
// true) dan OS lalu menolak `startForeground()` dari proses background. API 28+ (P); di bawahnya false.
private fun isBackgroundRestricted(app: Application): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        app.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
    } else {
        false
    }

/** Intent standar Android utk minta dikecualikan dari optimasi baterai (0 permission dialog
 * custom — sistem yg tampilkan dialog konfirmasi bawaan). Dipanggil dari SettingsTab. */
internal fun batteryOptimizationIntent(ctx: Context): Intent = Intent(
    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    Uri.parse("package:${ctx.packageName}")
)

/** Minta izin Shizuku (hanya bila binder hidup & bukan pre-v11). Dipanggil dari kartu status Shizuku. */
internal fun requestShizukuPermission() {
    runCatching {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
            LeaveGuard.markExternal() // v140: dialog izin Shizuku = Activity lain di depan; jangan hapus task
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }
}
