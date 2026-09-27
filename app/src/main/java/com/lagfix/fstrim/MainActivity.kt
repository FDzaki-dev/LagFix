package com.lagfix.fstrim

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ui = vm.ui
            LagFixTheme(ui.themeMode) { HomeScreen(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}

// v10 (settings tab + tema): palet kustom "calm" terinspirasi Cupertino/iOS — biru-lavender lembut
// + sage muted, BUKAN warna Material You dinamis lama & BUKAN dark statis hitam pekat (sengaja
// pakai navy-charcoal lembut #1C1E27, bukan hitam #000000, biar tidak bikin lelah mata).
private val calmLightScheme = lightColorScheme(
    primary = Color(0xFF5A6ACF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E3FA),
    onPrimaryContainer = Color(0xFF1B2560),
    secondary = Color(0xFF6E8A7C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEAE1),
    onSecondaryContainer = Color(0xFF1E2E26),
    tertiary = Color(0xFFB98A5E),
    background = Color(0xFFF4F4F8),
    onBackground = Color(0xFF2B2C33),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B2C33),
    surfaceVariant = Color(0xFFE6E6EE),
    onSurfaceVariant = Color(0xFF5B5C66),
    outline = Color(0xFFB8B9C6),
    error = Color(0xFFC0524B),
    onError = Color(0xFFFFFFFF)
)

private val calmDarkScheme = darkColorScheme(
    primary = Color(0xFFA9B4F2),
    onPrimary = Color(0xFF1C2557),
    primaryContainer = Color(0xFF394487),
    onPrimaryContainer = Color(0xFFE1E3FA),
    secondary = Color(0xFF9FC0AE),
    onSecondary = Color(0xFF17301F),
    secondaryContainer = Color(0xFF32493B),
    onSecondaryContainer = Color(0xFFDCEAE1),
    tertiary = Color(0xFFD9B287),
    background = Color(0xFF1C1E27),
    onBackground = Color(0xFFE7E7ED),
    surface = Color(0xFF262933),
    onSurface = Color(0xFFE7E7ED),
    surfaceVariant = Color(0xFF33363F),
    onSurfaceVariant = Color(0xFFC2C3CC),
    outline = Color(0xFF6E7180),
    error = Color(0xFFE0918B),
    onError = Color(0xFF3A1210)
)

// Sudut lebih membulat drpd default M3 — kesan kartu ala Cupertino/iOS (soft rounded), visual-only.
private val calmShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
private fun LagFixTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) calmDarkScheme else calmLightScheme
    MaterialTheme(colorScheme = scheme, shapes = calmShapes, content = content)
}

// v12 (fix delay toast): showSnackbar() bawaan ANTRE kalau dipanggil beruntun cepat (mis. user
// gonta-ganti tema/interval berturut-turut) — toast baru nunggu toast lama habis durasi penuh dulu,
// kerasa "lag". Fix: dismiss yg sedang tampil lebih dulu sebelum tampilkan yg baru, jadi toast
// selalu langsung reflect aksi TERAKHIR, tidak menumpuk antrean.
private suspend fun SnackbarHostState.showFeedback(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message, duration = SnackbarDuration.Short)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: MainViewModel) {
    val ui = vm.ui
    val ctx = LocalContext.current
    val versionName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
    }
    var showAbout by rememberSaveable { mutableStateOf(false) } // v9 (E2)
    var selectedTab by rememberSaveable { mutableStateOf(0) } // v10: 0=Utama, 1=Pengaturan
    var showRunConfirm by rememberSaveable { mutableStateOf(false) } // v11: konfirmasi sebelum jalankan manual
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onFeedback: (String) -> Unit = { msg -> scope.launch { snackbarHostState.showFeedback(msg) } }

    // v11: toast hasil fstrim manual — hanya saat transisi running true->false (bukan komposisi
    // awal, biar tak muncul spontan dari riwayat lama saat app baru dibuka/rotasi).
    var wasRunning by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ui.running) {
        if (wasRunning && !ui.running) {
            snackbarHostState.showFeedback(
                if (ui.lastOk) "fstrim berhasil dijalankan." else "fstrim gagal dijalankan."
            )
        }
        wasRunning = ui.running
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("LagFix (fstrim)") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("🏠") },
                    label = { Text("Utama") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("⚙️") },
                    label = { Text("Pengaturan") }
                )
            }
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedTab == 0) {
                MainTab(
                    ui = ui,
                    ctx = ctx,
                    versionName = versionName,
                    onRunNow = { showRunConfirm = true }, // v11: minta konfirmasi dulu
                    onGrant = vm::requestPermission,
                    onCheckUpdate = vm::checkUpdate,
                    onInstallUpdate = vm::installUpdate
                )
            } else {
                SettingsTab(
                    ui = ui,
                    ctx = ctx,
                    vm = vm,
                    onFeedback = onFeedback,
                    onAboutClick = { showAbout = true }
                )
            }
        }

        if (showAbout) {
            AboutDialog(
                versionName = versionName,
                packageName = ctx.packageName,
                onOpenSource = { openUrl(ctx, AppLinks.source) },
                onDismiss = { showAbout = false }
            )
        }

        if (showRunConfirm) { // v11 (tab konfirmasi): konfirmasi sebelum memicu operasi TRIM
            AlertDialog(
                onDismissRequest = { showRunConfirm = false },
                confirmButton = {
                    TextButton(onClick = { showRunConfirm = false; vm.runNow() }) { Text("Jalankan") }
                },
                dismissButton = {
                    TextButton(onClick = { showRunConfirm = false }) { Text("Batal") }
                },
                title = { Text("Jalankan fstrim sekarang?") },
                text = { Text("Ini akan memicu operasi TRIM penyimpanan (non-root, via Shizuku).") }
            )
        }
    }
}

// v10: tab "Utama" — status Shizuku, aksi jalankan fstrim, Riwayat, Tautan (+ Tentang), Pembaruan,
// label versi. Persis konten yang sebelumnya ada di layar tunggal, cuma dipindah ke tab ini.
@Composable
private fun MainTab(
    ui: UiState,
    ctx: Context,
    versionName: String?,
    onRunNow: () -> Unit,
    onGrant: () -> Unit,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: (String) -> Unit
) {
    StatusCard(ui.shizuku, onGrant = onGrant, onOpen = { openShizuku(ctx, ui.shizuku) })

    Button(
        onClick = onRunNow,
        enabled = ui.shizuku == ShizukuState.READY && !ui.running,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (ui.running) "Menjalankan…" else "Jalankan fstrim sekarang") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Riwayat", style = MaterialTheme.typography.titleMedium)
            Text(
                if (ui.lastRunMs == 0L) "Belum pernah dijalankan"
                else formatTime(ui.lastRunMs) + when {
                    ui.log.firstOrNull()?.contains("dilewati") == true -> " — dilewati (Shizuku belum siap)"
                    ui.lastOk -> " — berhasil"
                    else -> " — gagal"
                }
            )
            ui.log.forEach { LogLine(it) }
        }
    }

    UpdateCard(
        checking = ui.updateChecking,
        result = ui.updateResult,
        downloading = ui.downloading,
        downloadError = ui.downloadError,
        onCheck = onCheckUpdate,
        onInstall = onInstallUpdate
    )

    Text(
        "LagFix" + if (!versionName.isNullOrBlank()) " • v$versionName" else "",
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )
}

// v10: tab "Pengaturan" — jadwal otomatis + interval + charging/idle (dipindah dari Utama, sama
// persis logic/callback-nya, cuma beda lokasi tab) + BARU: pemilih tema (Ikuti sistem/Terang/Gelap).
@Composable
private fun SettingsTab(
    ui: UiState,
    ctx: Context,
    vm: MainViewModel,
    onFeedback: (String) -> Unit,
    onAboutClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Jadwal", style = MaterialTheme.typography.titleMedium)
            ToggleRow("Jadwal otomatis", ui.enabled) {
                vm.setEnabled(it)
                onFeedback(if (it) "Jadwal otomatis diaktifkan." else "Jadwal otomatis dinonaktifkan.")
            }
            Text("Interval")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(6L to "6 jam", 12L to "12 jam", 24L to "1 hari", 72L to "3 hari", 168L to "7 hari")
                    .forEach { (h, label) ->
                        FilterChip(
                            selected = ui.intervalHours == h,
                            onClick = { vm.setInterval(h); onFeedback("Interval diubah ke $label.") },
                            label = { Text(label) }
                        )
                    }
            }
            ToggleRow("Hanya saat mengisi daya", ui.requireCharging) {
                vm.setCharging(it)
                onFeedback(if (it) "Hanya saat mengisi daya: aktif." else "Hanya saat mengisi daya: nonaktif.")
            }
            ToggleRow("Hanya saat perangkat idle", ui.requireIdle) {
                vm.setIdle(it)
                onFeedback(if (it) "Hanya saat perangkat idle: aktif." else "Hanya saat perangkat idle: nonaktif.")
            }
            // v22 (root cause laporan user: jadwal otomatis tak tercatat di beberapa HP — toggle
            // sudah ON tapi OS/OEM (mis. XOS/MIUI/ColorOS) diam-diam membunuh job background kalau
            // app tak dikecualikan dari optimasi baterai). v23 (laporan user: sblmnya tombol cuma
            // LENYAP diam-diam stlh diizinkan — "tanpa kepastian"): sekarang ada baris konfirmasi
            // checkmark sbg pengganti, bukan cuma menghilang tanpa jejak.
            if (ui.batteryUnrestricted) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Baterai: berjalan tanpa batas", Modifier.weight(1f))
                    Text("✓")
                }
            } else {
                Text(
                    "Jadwal otomatis bisa meleset di HP ini kalau baterai masih dioptimasi sistem.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = { runCatching { ctx.startActivity(vm.batteryOptimizationIntent()) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Izinkan berjalan tanpa batas", Modifier.weight(1f))
                    Text("↗")
                }
            }
            // v24 (laporan user: sudah exempt battery optimization tapi jadwal 6 jam TETAP 0 entri
            // otomatis — dikonfirmasi ini BUKAN cukup di sebagian HP, khususnya Infinix/Tecno/itel
            // (XOS/HiOS) & Xiaomi/dst: ROM ini punya toggle "Autostart"/"Latar belakang" TERPISAH
            // di App Management/Phone Master/Security App, DI LUAR API baterai standar Android — 0
            // API publik utk app pihak ketiga minta ini secara terprogram, cuma bisa dipandu manual,
            // makanya di sini teks saja, bukan tombol/intent (nebak nama package/activity spesifik
            // per versi ROM berisiko ActivityNotFoundException, jadi TIDAK dilakukan).
            Text(
                "Kalau jadwal otomatis tetap tidak jalan walau sudah diizinkan di atas, cek juga " +
                    "pengaturan \"Autostart\" / \"Latar belakang\" yang terpisah di App Management " +
                    "atau Phone Master/Security App bawaan HP (umum di Infinix, Tecno, Xiaomi, dll — " +
                    "nama menu beda-beda tiap merek & versi).",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    // v27 (fitur opsional, HANYA utk user yg sudah coba opsi baterai/Autostart di atas & masih
    // bermasalah — konsultasi eksplisit dgn user sebelum dibuat, lihat riwayat v25/v26, dikerjakan v27). 0 logic
    // fstrim baru — cuma menjaga proses tetap hidup, jadwal periodik tetap lewat WorkManager
    // (Scheduler.apply(), 0 diubah).
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Keandalan latar belakang (opsional)", style = MaterialTheme.typography.titleMedium)
            val notifPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {
                // v27: granted atau tidak, service tetap dinyalakan — kalau ditolak, cuma
                // notifikasinya yang tak tampil (kontrak Android), foreground service-nya sendiri
                // tetap jalan seperti biasa.
                vm.setPersistentService(true)
            }
            ToggleRow("Layanan latar depan persisten", ui.persistentServiceEnabled) { turnOn ->
                if (turnOn &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.setPersistentService(turnOn)
                }
                onFeedback(if (turnOn) "Layanan latar depan diaktifkan." else "Layanan latar depan dimatikan.")
            }
            Text(
                "Opsional — memaksa proses LagFix tetap hidup di HP yang agresif mematikan " +
                    "aplikasi latar belakang, dengan notifikasi permanen yang tak bisa disembunyikan " +
                    "selama aktif. Coba dulu opsi baterai & Autostart di atas sebelum ini.",
                style = MaterialTheme.typography.bodySmall
            )
            // v28 (investigasi laporan "toggle aktif, izin POST_NOTIFICATIONS muncul, tapi
            // notifikasi permanen tak pernah kelihatan"): izin runtime POST_NOTIFICATIONS BEDA dari
            // toggle "Izinkan notifikasi" di level OS/Setelan HP (mis. XOS/MIUI kadang punya toggle
            // sendiri per app terpisah dari izin Android) — service TETAP jalan di kedua kasus
            // (`startForeground()` tidak butuh notifikasi benar2 tampil, cuma butuh izin utk boleh
            // MENCOBA menampilkan), makanya proses bisa hidup tanpa user pernah lihat notifnya.
            // `ui` sbg key: re-check tiap `vm.refresh()` (mis. balik dari Setelan HP via onResume).
            if (ui.persistentServiceEnabled) {
                val notifEnabled = remember(ui) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
                if (!notifEnabled) {
                    Text(
                        "Terdeteksi: notifikasi App LagFix nonaktif di Setelan HP ini — makanya " +
                            "notifikasi permanen tidak kelihatan meski izinnya sudah diberikan. " +
                            "Layanan tetap berjalan di latar belakang, cuma indikatornya tersembunyi. " +
                            "Aktifkan lewat Setelan > Aplikasi > LagFix > Notifikasi kalau mau terlihat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    // v31 (laporan user: fix v30 blm cukup — folder Download/LagFix/ tetap 0 muncul di file
    // manager HP ini). Pembaca log LANGSUNG di dalam aplikasi (lepas dari ketergantungan file
    // manager/OS pihak lain menampilkan folder baru) + tombol salin teks.
    LogReaderCard(ctx = ctx, onFeedback = onFeedback)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tema", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to "Ikuti sistem",
                    ThemeMode.LIGHT to "Terang",
                    ThemeMode.DARK to "Gelap"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = ui.themeMode == mode,
                        onClick = { vm.setThemeMode(mode); onFeedback("Tema: $label.") },
                        label = { Text(label) }
                    )
                }
            }
        }
    }

    // v12: dipindah dari tab Utama biar tab Utama cuma isi fitur utama (status/aksi/riwayat/pembaruan).
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tautan", style = MaterialTheme.typography.titleMedium)
            LinkRow("Unduh rilis terbaru") { openUrl(ctx, AppLinks.releases) }
            LinkRow("Lihat kode sumber") { openUrl(ctx, AppLinks.source) }
            LinkRow("Laporkan masalah") { openUrl(ctx, AppLinks.newIssue) }
            AboutRow("Tentang aplikasi") { onAboutClick() } // v9 (E2): konsolidasi info app
        }
    }
}

// v31: pembaca log diagnostik/crash LANGSUNG di dalam aplikasi (CrashLogger.listLogs()) — dibuat
// krn laporan user file Download/LagFix/ tetap 0 muncul di file manager HP walau v30 sudah
// perbaiki penulisannya. App baca lewat ContentResolver miliknya sendiri, lepas total dari
// ketergantungan indexing/tampilan file manager OS/OEM pihak lain. Kalau daftar di sini tetap
// kosong stlh "Muat ulang", itu bukti kuat penulisannya sendiri yg gagal (beda diagnosis dari
// soal visibility saja). IO (query MediaStore + baca file) dijalankan di Dispatchers.IO, state
// dialog/isi log yg sedang dibuka pakai rememberSaveable spy bertahan dari rotasi layar.
@Composable
private fun LogReaderCard(ctx: Context, onFeedback: (String) -> Unit) {
    var loading by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<List<CrashLogger.LogFile>>(emptyList()) }
    var loadError by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedContent by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = remember { ctx.getSystemService(ClipboardManager::class.java) }

    fun load() {
        loading = true
        loadError = null
        scope.launch(Dispatchers.IO) {
            val result = runCatching { CrashLogger.listLogs(ctx) }
            withContext(Dispatchers.Main) {
                loading = false
                result.onSuccess { logs = it }.onFailure { loadError = it.message ?: "Gagal memuat daftar log." }
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Log Diagnostik", style = MaterialTheme.typography.titleMedium)
            Text(
                "Baca langsung dari dalam aplikasi — tidak bergantung file manager/folder Download " +
                    "yang mungkin tidak menampilkan file baru di sebagian HP.",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { load() }) { Text(if (loading) "Memuat…" else "Muat ulang") }
            when {
                loadError != null -> Text(
                    "Error: $loadError",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                !loading && logs.isEmpty() -> Text(
                    "Belum ada file log ditemukan (baik di Download/LagFix maupun cadangan internal app).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            logs.forEach { log ->
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val text = runCatching { log.readText() }.getOrElse { "(gagal baca: ${it.message})" }
                            withContext(Dispatchers.Main) {
                                selectedName = log.displayName
                                selectedContent = text
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${log.displayName} · ${log.source}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    val currentLogContent = selectedContent
    if (currentLogContent != null) {
        AlertDialog(
            onDismissRequest = { selectedContent = null; selectedName = null },
            confirmButton = {
                TextButton(onClick = {
                    clipboard?.setPrimaryClip(ClipData.newPlainText("LagFix log", currentLogContent))
                    onFeedback("Isi log disalin ke clipboard.")
                }) { Text("Salin") }
            },
            dismissButton = {
                TextButton(onClick = { selectedContent = null; selectedName = null }) { Text("Tutup") }
            },
            title = { Text(selectedName ?: "Log") },
            text = {
                Text(
                    currentLogContent,
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
private fun LinkRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text("↗")
    }
}

private fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

// v9 (E2): beda dari LinkRow ("↗" = buka browser) — "›" krn ini buka dialog in-app, bukan tautan.
@Composable
private fun AboutRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text("›")
    }
}

// v9 (E2): konsolidasi info app (sebelumnya cuma tersebar: label versi di footer + card Tautan
// terpisah) jadi satu dialog ringkas. Tidak menghapus/mengubah Tautan/footer yang sudah ada.
@Composable
private fun AboutDialog(
    versionName: String?,
    packageName: String,
    onOpenSource: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text("Tentang LagFix") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pemicu & penjadwal fstrim non-root (setara mFSTRIM), via Shizuku.")
                Text("Versi: " + (versionName?.takeIf { it.isNotBlank() } ?: "tidak diketahui"))
                Text("Paket: $packageName")
                TextButton(onClick = onOpenSource, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Source & developer", Modifier.weight(1f))
                    Text("↗")
                }
            }
        }
    )
}

// v8 (E1): indikator visual OK/FAIL di Riwayat. Parse-only di sisi UI — format baris log
// ("dd/MM HH:mm" + status + durasi + pesan) tetap dari Prefs.record() apa adanya (sudah
// dites PrefsTest.kt, tidak disentuh). Baris yang tak cocok pola tetap tampil polos (fallback aman).
private val logLineRegex = Regex("""^(\d{2}/\d{2} \d{2}:\d{2}) (OK|FAIL) (.*)$""")
private val successGreen = Color(0xFF2E7D32)
private val skippedAmber = Color(0xFFB26A00) // v13 (B4): beda dari FAIL asli — precondition Shizuku, bukan error eksekusi

@Composable
private fun LogLine(line: String) {
    val match = logLineRegex.find(line)
    if (match == null) {
        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        return
    }
    val (stamp, status, rest) = match.destructured
    val ok = status == "OK"
    val skipped = !ok && rest.contains("dilewati")
    val tint = when {
        ok -> successGreen
        skipped -> skippedAmber
        else -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("●", color = tint, style = MaterialTheme.typography.bodySmall)
        Text(
            "$stamp $status $rest",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = tint
        )
    }
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
