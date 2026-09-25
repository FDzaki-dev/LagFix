package com.lagfix.fstrim

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LagFixTheme { HomeScreen(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}

@Composable
private fun LagFixTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: MainViewModel) {
    val ui = vm.ui
    val ctx = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("LagFix (fstrim)") }) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(ui.shizuku, onGrant = vm::requestPermission, onOpen = { openShizuku(ctx, ui.shizuku) })

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleRow("Jadwal otomatis", ui.enabled, vm::setEnabled)
                    Text("Interval")
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(6L to "6 jam", 12L to "12 jam", 24L to "1 hari", 72L to "3 hari", 168L to "7 hari")
                            .forEach { (h, label) ->
                                FilterChip(
                                    selected = ui.intervalHours == h,
                                    onClick = { vm.setInterval(h) },
                                    label = { Text(label) }
                                )
                            }
                    }
                    ToggleRow("Hanya saat mengisi daya", ui.requireCharging, vm::setCharging)
                    ToggleRow("Hanya saat perangkat idle", ui.requireIdle, vm::setIdle)
                }
            }

            Button(
                onClick = vm::runNow,
                enabled = ui.shizuku == ShizukuState.READY && !ui.running,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (ui.running) "Menjalankan…" else "Jalankan fstrim sekarang") }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Riwayat", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (ui.lastRunMs == 0L) "Belum pernah dijalankan"
                        else formatTime(ui.lastRunMs) + if (ui.lastOk) " — berhasil" else " — gagal"
                    )
                    ui.log.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tautan", style = MaterialTheme.typography.titleMedium)
                    LinkRow("Unduh rilis terbaru") { openUrl(ctx, AppLinks.releases) }
                    LinkRow("Lihat kode sumber") { openUrl(ctx, AppLinks.source) }
                    LinkRow("Laporkan masalah") { openUrl(ctx, AppLinks.newIssue) }
                }
            }

            UpdateCard(
                checking = ui.updateChecking,
                result = ui.updateResult,
                downloading = ui.downloading,
                downloadError = ui.downloadError,
                onCheck = vm::checkUpdate,
                onInstall = vm::installUpdate
            )

            val versionName = remember {
                runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
            }
            Text(
                "LagFix" + if (!versionName.isNullOrBlank()) " • v$versionName" else "",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text("↗")
    }
}

private fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun UpdateCard(
    checking: Boolean,
    result: UpdateResult?,
    downloading: Boolean,
    downloadError: String?,
    onCheck: () -> Unit,
    onInstall: (String) -> Unit
) {
    var showChangelog by rememberSaveable { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pembaruan", style = MaterialTheme.typography.titleMedium)

            when (result) {
                null -> Text(if (checking) "Memeriksa pembaruan…" else "Ketuk untuk memeriksa versi terbaru di GitHub.")
                is UpdateResult.UpToDate -> Text("Sudah versi terbaru (build ${result.installedBuild}).")
                is UpdateResult.Error -> Text("Gagal memeriksa pembaruan: ${result.message}")
                is UpdateResult.Available -> {
                    Text("Versi terpasang: build ${result.installedBuild}")
                    Text("Versi tersedia: build ${result.info.latestBuild} (${result.info.latestName})")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showChangelog = true }) { Text("Lihat changelog") }
                        Button(
                            onClick = { onInstall(result.info.downloadUrl) },
                            enabled = !downloading
                        ) { Text(if (downloading) "Memasang…" else "Update sekarang") }
                    }
                    if (downloadError != null) {
                        Text(
                            "Gagal memasang: $downloadError",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (checking) {
                CircularProgressIndicator(Modifier.padding(top = 4.dp))
            } else {
                Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                    Text(if (result == null) "Cek pembaruan" else "Cek ulang")
                }
            }
        }
    }

    if (showChangelog && result is UpdateResult.Available) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            confirmButton = { TextButton(onClick = { showChangelog = false }) { Text("Tutup") } },
            title = { Text("Changelog — ${result.info.latestName}") },
            text = {
                Text(
                    result.info.changelog,
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        )
    }
}

@Composable
private fun StatusCard(state: ShizukuState, onGrant: () -> Unit, onOpen: () -> Unit) {
    val (title, body, action) = when (state) {
        ShizukuState.NOT_INSTALLED -> Triple("Shizuku belum terpasang", "Pasang Shizuku, aktifkan via Wireless debugging (tanpa root).", "Unduh Shizuku")
        ShizukuState.NOT_RUNNING -> Triple("Shizuku tidak aktif", "Buka Shizuku lalu jalankan layanannya.", "Buka Shizuku")
        ShizukuState.NEED_PERMISSION -> Triple("Izin diperlukan", "Beri izin LagFix untuk memakai Shizuku.", "Beri izin")
        ShizukuState.READY -> Triple("Siap", "Shizuku aktif dan izin diberikan.", null)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
            if (action != null) {
                Button(onClick = if (state == ShizukuState.NEED_PERMISSION) onGrant else onOpen) { Text(action) }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun openShizuku(ctx: Context, state: ShizukuState) {
    val intent = if (state == ShizukuState.NOT_INSTALLED) {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
    } else {
        ctx.packageManager.getLaunchIntentForPackage(FstrimExecutor.SHIZUKU_PKG)
    }
    runCatching { intent?.let { ctx.startActivity(it) } }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))
