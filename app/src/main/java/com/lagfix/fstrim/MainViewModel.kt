package com.lagfix.fstrim

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

data class UiState(
    val shizuku: ShizukuState = ShizukuState.NOT_RUNNING,
    val enabled: Boolean = false,
    val intervalHours: Long = 24L,
    val requireCharging: Boolean = true,
    val requireIdle: Boolean = false,
    val running: Boolean = false,
    val lastRunMs: Long = 0L,
    val lastOk: Boolean = false,
    val log: List<String> = emptyList(),
    val updateChecking: Boolean = false,
    val updateResult: UpdateResult? = null,
    val downloading: Boolean = false,
    val downloadError: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val onBinder = Shizuku.OnBinderReceivedListener { refresh() }
    private val onDead = Shizuku.OnBinderDeadListener { refresh() }
    private val onPerm = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    var ui by mutableStateOf(read())
        private set

    init {
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPerm)
    }

    private fun read(running: Boolean = false) = UiState(
        shizuku = FstrimExecutor.state(getApplication<Application>()),
        enabled = prefs.enabled,
        intervalHours = prefs.intervalHours,
        requireCharging = prefs.requireCharging,
        requireIdle = prefs.requireIdle,
        running = running,
        lastRunMs = prefs.lastRunMs,
        lastOk = prefs.lastOk,
        log = prefs.log
    )

    fun refresh() {
        ui = read(ui.running).copy(
            updateChecking = ui.updateChecking,
            updateResult = ui.updateResult,
            downloading = ui.downloading,
            downloadError = ui.downloadError
        )
    }

    private fun reschedule() {
        Scheduler.apply(getApplication<Application>(), prefs)
        refresh()
    }

    fun setEnabled(v: Boolean) { prefs.enabled = v; reschedule() }
    fun setInterval(h: Long) { prefs.intervalHours = h; reschedule() }
    fun setCharging(v: Boolean) { prefs.requireCharging = v; reschedule() }
    fun setIdle(v: Boolean) { prefs.requireIdle = v; reschedule() }

    fun requestPermission() {
        runCatching {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(1001)
        }
    }

    fun runNow() {
        if (ui.running) return
        ui = ui.copy(running = true)
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val r = if (FstrimExecutor.state(app) == ShizukuState.READY) FstrimExecutor.run()
            else TrimResult(false, "Shizuku belum siap", 0L)
            prefs.record(r)
            ui = read(false).copy(
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
                if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt()
                else @Suppress("DEPRECATION") pi.versionCode
            }.getOrDefault(1)
            val result = UpdateChecker.check(installedBuild)
            ui = ui.copy(updateChecking = false, updateResult = result)
        }
    }

    /** Unduh APK ke cache app lalu langsung buka Package Installer — tanpa browser, tanpa file menumpuk di Download. */
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
                app.startActivity(intent)
                ui = ui.copy(downloading = false)
            } catch (e: Exception) {
                ui = ui.copy(downloading = false, downloadError = e.message ?: "Tidak diketahui")
            }
        }
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onDead)
        Shizuku.removeRequestPermissionResultListener(onPerm)
    }
}
