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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
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
            // v38: pemicu run terakhir (Manual/Otomatis) dari baris log terbaru. Baris lama tanpa
            // token -> label null -> tidak ditampilkan (bukan menebak).
            val lastTrigger = ui.log.firstOrNull()?.let { parseLogLine(it)?.trigger }.orEmpty()
            val lastTriggerLabel = if (ui.lastRunMs != 0L) triggerLabel(lastTrigger) else null
            if (lastTriggerLabel != null) {
                Text("Dipicu oleh: $lastTriggerLabel", style = MaterialTheme.typography.bodySmall)
            }
            ui.log.forEach { LogLine(it) }
        }
    }

    // v37 (fitur user-facing baru, permintaan eksplisit user): kartu "Statistik" — ringkasan +
    // grafik batang durasi dari Riwayat yang sama persis (0 data baru, 0 perubahan Prefs.kt).
    StatsCard(ui.log)

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

// v44: menit -> teks manusiawi ("15 menit", "1 jam 30 menit", "3 hari"). Murni format, 0 side-effect.
internal fun formatInterval(minutes: Long): String {
    val d = minutes / 1440L
    val h = (minutes % 1440L) / 60L
    val m = minutes % 60L
    val parts = buildList {
        if (d > 0) add("$d hari")
        if (h > 0) add("$h jam")
        if (m > 0) add("$m menit")
    }
    return parts.joinToString(" ").ifEmpty { "0 menit" }
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
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Jadwal", style = MaterialTheme.typography.titleMedium)
            ToggleRow("Jadwal otomatis", ui.enabled) {
                vm.setEnabled(it)
                onFeedback(if (it) "Jadwal otomatis diaktifkan." else "Jadwal otomatis dinonaktifkan.")
            }
            Text("Interval")
            // v44 (permintaan user: interval kustom diisi sendiri, biar tak kelamaan nunggu saat
            // uji): chip preset TETAP (nilai jam x 60 -> menit), ditambah kolom angka menit.
            // Batas bawah 15 menit = batas periodik WorkManager (di bawah itu WorkManager diam-diam
            // menaikkannya, jadi ditolak eksplisit di sini). Teks input rememberSaveable -> tahan rotasi.
            val focusManager = LocalFocusManager.current
            val presets = listOf(6L to "6 jam", 12L to "12 jam", 24L to "1 hari", 72L to "3 hari", 168L to "7 hari")
            var customText by rememberSaveable {
                mutableStateOf(
                    if (presets.any { it.first * 60L == ui.intervalMinutes }) "" else ui.intervalMinutes.toString()
                )
            }
            val customValue = customText.toLongOrNull()
            val customValid = customValue != null && customValue >= Scheduler.MIN_INTERVAL_MINUTES
            val applyCustom: () -> Unit = {
                if (customValue != null && customValid) {
                    vm.setInterval(customValue)
                    onFeedback("Interval diubah ke ${formatInterval(customValue)}.")
                    focusManager.clearFocus()
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { (h, label) ->
                    FilterChip(
                        selected = ui.intervalMinutes == h * 60L,
                        onClick = {
                            customText = ""
                            vm.setInterval(h * 60L)
                            onFeedback("Interval diubah ke $label.")
                        },
                        label = { Text(label) }
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Interval kustom (menit)") },
                    singleLine = true,
                    isError = customText.isNotEmpty() && !customValid,
                    supportingText = {
                        Text(
                            if (customText.isNotEmpty() && !customValid) {
                                "Minimal ${Scheduler.MIN_INTERVAL_MINUTES} menit (batas WorkManager)."
                            } else {
                                "Interval aktif: ${formatInterval(ui.intervalMinutes)}"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { applyCustom() })
                )
                Button(
                    onClick = applyCustom,
                    enabled = customValid,
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("Terapkan") }
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
                // v34 (investigasi lanjutan) — user sudah toggle ON, tapi 0 entri
                // diag_persistent_service baru muncul sama sekali di Log Diagnostik — beda dari
                // sekedar dugaan, ini instrumentasi utk BISECT 2 kemungkinan: handler toggle Compose
                // ini sendiri yg 0 pernah tereksekusi (masalah UI), ATAU handler jalan normal tapi
                // `PersistentTrimService.onStartCommand()` yg 0 pernah ke-trigger OS (masalah
                // service/OS). File `diag_toggle_pressed_*` ini ditulis SEGERA saat toggle ditekan,
                // 0 tergantung apapun stlh ini — kalau file ini ADA tapi
                // `diag_persistent_service_*` TETAP 0 ada, itu bukti kuat masalahnya di level
                // OS/service (`PersistentTrimService` 0 pernah benar2 di-start OS meski
                // `ContextCompat.startForegroundService()` dipanggil). Kalau file toggle_pressed
                // ini SENDIRI 0 ada, masalahnya malah di level UI (toggle 0 ke-trigger).
                scope.launch(Dispatchers.IO) {
                    CrashLogger.logDiagnostic(
                        ctx, "toggle_pressed",
                        "Toggle 'Layanan latar depan persisten' ditekan -> target=$turnOn " +
                            "(state sebelumnya=${ui.persistentServiceEnabled}, SDK ${Build.VERSION.SDK_INT})"
                    )
                }
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
    var testing by remember { mutableStateOf(false) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { load() }) { Text(if (loading) "Memuat…" else "Muat ulang") }
                TextButton(
                    onClick = {
                        testing = true
                        scope.launch(Dispatchers.IO) {
                            val result = CrashLogger.testWrite(ctx)
                            withContext(Dispatchers.Main) {
                                testing = false
                                if (result.isSuccess) {
                                    onFeedback("Tes tulis BERHASIL — daftar log dimuat ulang otomatis.")
                                    load()
                                } else {
                                    onFeedback("Tes tulis GAGAL: ${result.exceptionOrNull()?.message ?: "error tidak diketahui"}")
                                }
                            }
                        }
                    }
                ) { Text(if (testing) "Menguji…" else "Tes tulis log") }
            }
            Text(
                "\"Tes tulis log\" langsung menulis 1 file percobaan — tanpa perlu toggle apa pun — " +
                    "untuk memastikan penulisan file bisa berhasil di HP ini.",
                style = MaterialTheme.typography.bodySmall
            )
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

// v8 (E1): indikator visual OK/FAIL di Riwayat. Parse-only di sisi UI — baris log ditulis oleh
// Prefs.record() (dites PrefsTest.kt). v38: baris BARU punya token opsional "[Manual]"/"[Otomatis]"
// di antara status & durasi; regex dibuat backward-compatible supaya baris LAMA (tanpa token, sudah
// tersimpan di HP user) tetap terbaca — group 3 kosong kalau token tak ada. Baris yang tak cocok
// pola sama sekali tetap tampil polos (fallback aman).
private val logLineRegex = Regex("""^(\d{2}/\d{2} \d{2}:\d{2}) (OK|FAIL)(?: \[([^\]]+)\])? (.*)$""")
private val durationRegex = Regex("""^(\d+)ms""")
private val successGreen = Color(0xFF2E7D32)
private val skippedAmber = Color(0xFFB26A00) // v13 (B4): beda dari FAIL asli — precondition Shizuku, bukan error eksekusi

internal data class ParsedLog(val stamp: String, val ok: Boolean, val trigger: String, val rest: String) {
    val skipped: Boolean get() = !ok && rest.contains("dilewati")
}

internal fun parseLogLine(line: String): ParsedLog? {
    val g = logLineRegex.find(line)?.groupValues ?: return null
    return ParsedLog(stamp = g[1], ok = g[2] == "OK", trigger = g[3], rest = g[4])
}

// v38: label ramah utk user awam. Trigger tak dikenal / baris lama tanpa token -> null (tidak ditebak).
private fun triggerLabel(trigger: String): String? = when (trigger) {
    TriggerSource.MANUAL -> "Manual (dijalankan sendiri oleh pengguna)"
    TriggerSource.AUTO -> "Otomatis (jadwal interval)"
    else -> null
}

@Composable
private fun LogLine(line: String) {
    val parsed = parseLogLine(line)
    if (parsed == null) {
        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        return
    }
    val tint = when {
        parsed.ok -> successGreen
        parsed.skipped -> skippedAmber
        else -> MaterialTheme.colorScheme.error
    }
    val status = if (parsed.ok) "OK" else "FAIL"
    // v38: tampilkan pemicu run (Manual/Otomatis) kalau ada; baris lama tanpa token -> tanpa tag.
    val triggerTag = if (parsed.trigger.isNotEmpty()) " (${parsed.trigger})" else ""
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("●", color = tint, style = MaterialTheme.typography.bodySmall)
        Text(
            "${parsed.stamp} $status$triggerTag ${parsed.rest}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = tint
        )
    }
}

// v37 (fitur user-facing, permintaan user "grafik statistik ... bikin project hidup, gak teknis
// banget") + v38 (feedback user: batang grafik "kurang informatif utk user awam"): kartu "Statistik"
// di tab Utama. PARSE-ONLY di sisi UI (reuse parseLogLine/successGreen/skippedAmber), 0 data baru.
// Kalau riwayat kosong, kartu tidak ditampilkan.
private data class RunStat(
    val stamp: String, val ok: Boolean, val skipped: Boolean, val durationMs: Long, val trigger: String
)

private fun parseRunStats(log: List<String>): List<RunStat> =
    log.mapNotNull { line ->
        val p = parseLogLine(line) ?: return@mapNotNull null
        val ms = durationRegex.find(p.rest)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        RunStat(p.stamp, p.ok, p.skipped, ms, p.trigger)
    }

// v38: "820ms" tak bermakna bagi user awam -> tampilkan dalam detik (1 desimal, koma gaya
// Indonesia). Di bawah 0,1 detik ditulis "< 0,1 detik" supaya tak jadi "0,0 detik" yg membingungkan.
private fun formatDuration(ms: Long): String {
    if (ms < 100L) return "< 0,1 detik"
    val tenths = (ms + 50L) / 100L
    return "${tenths / 10},${tenths % 10} detik"
}

@Composable
private fun StatsCard(log: List<String>) {
    val stats = remember(log) { parseRunStats(log) }
    if (stats.isEmpty()) return

    val okCount = stats.count { it.ok }
    val skippedCount = stats.count { it.skipped }
    val failCount = stats.size - okCount - skippedCount
    val okDurations = stats.filter { it.ok }.map { it.durationMs }
    val avgOkMs = if (okDurations.isEmpty()) 0L else okDurations.average().toLong()
    // v45 (permintaan user: statistik "kurang jelas" apakah run Otomatis ikut terhitung): rincian
    // dari data yang SUDAH ada di setiap RunStat, 0 sumber data baru. Baris lama pra-v38 tanpa
    // token trigger -> tidak diklaim (dikeluarkan dari rincian, biar tak salah label "0 otomatis").
    val withTrigger = stats.filter { it.trigger.isNotEmpty() }
    val autoCount = withTrigger.count { it.trigger == TriggerSource.AUTO }
    val manualCount = withTrigger.count { it.trigger == TriggerSource.MANUAL }
    val hasManual = withTrigger.any { it.trigger == TriggerSource.MANUAL }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Statistik", style = MaterialTheme.typography.titleMedium)
            Text(
                "Dari ${stats.size} proses terakhir: $okCount berhasil · $skippedCount dilewati · $failCount gagal" +
                    if (withTrigger.isNotEmpty()) " ($autoCount otomatis · $manualCount manual)" else "",
                style = MaterialTheme.typography.bodySmall
            )
            if (okDurations.isNotEmpty()) {
                Text(
                    "Rata-rata ${formatDuration(avgOkMs)} · tercepat ${formatDuration(okDurations.minOrNull() ?: 0L)}" +
                        " · terlama ${formatDuration(okDurations.maxOrNull() ?: 0L)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "Lama tiap proses — makin tinggi batang, makin lama.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Kiri = riwayat terlama, kanan = terbaru (alur waktu wajar); ui.log sendiri urutannya
            // terbaru-dulu (dipakai apa adanya oleh LogLine), jadi dibalik cuma utk grafik.
            RunHistoryChart(stats.reversed(), avgOkMs)
            StatsLegend(okCount > 0, skippedCount > 0, failCount > 0, avgOkMs > 0L, hasManual)
        }
    }
}

@Composable
private fun RunHistoryChart(stats: List<RunStat>, avgOkMs: Long) {
    val rawMax = stats.maxOfOrNull { it.durationMs } ?: 0L
    val maxDuration = rawMax.coerceAtLeast(1L)
    // Warna di-resolve di scope Composable (bukan di dalam DrawScope).
    val errorColor = MaterialTheme.colorScheme.error
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val avgLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val hasManualMarker = stats.any { it.trigger == TriggerSource.MANUAL }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Sumbu vertikal: nilai tertinggi di atas, 0 di bawah — biar tinggi batang ada acuan angka.
            Column(
                Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    if (rawMax > 0L) formatDuration(rawMax) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = axisColor
                )
                Text("0", style = MaterialTheme.typography.labelSmall, color = axisColor)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val barGapPx = 4f
                val n = stats.size.coerceAtLeast(1)
                val barWidth = ((size.width - barGapPx * (n - 1)) / n).coerceAtLeast(1f)
                drawLine(
                    color = axisColor.copy(alpha = 0.4f),
                    start = Offset(0f, size.height - 1f),
                    end = Offset(size.width, size.height - 1f),
                    strokeWidth = 2f
                )
                stats.forEachIndexed { i, s ->
                    val tint = when {
                        s.ok -> successGreen
                        s.skipped -> skippedAmber
                        else -> errorColor
                    }
                    // Tinggi minimum kecil biar durasi 0 (dilewati) tetap kelihatan sbg bar tipis,
                    // bukan hilang total — murni visual, 0 pengaruh ke data asli.
                    val ratio = (s.durationMs.toFloat() / maxDuration).coerceIn(0f, 1f)
                    val barHeight = (size.height * ratio).coerceAtLeast(4f)
                    drawRect(
                        color = tint,
                        topLeft = Offset(i * (barWidth + barGapPx), size.height - barHeight),
                        size = Size(barWidth, barHeight)
                    )
                    if (hasManualMarker && s.trigger == TriggerSource.MANUAL) {
                        drawCircle(
                            color = axisColor,
                            radius = 3f,
                            center = Offset(
                                i * (barWidth + barGapPx) + barWidth / 2f,
                                (size.height - barHeight - 8f).coerceAtLeast(3f)
                            )
                        )
                    }
                }
                // Garis putus-putus = rata-rata proses yang berhasil: acuan cepat "di atas/di bawah biasanya".
                if (avgOkMs > 0L) {
                    val y = size.height - (size.height * (avgOkMs.toFloat() / maxDuration)).coerceIn(0f, size.height)
                    drawLine(
                        color = avgLineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                }
            }
        }
        // Sumbu waktu: tanggal-jam proses tertua (kiri) & terbaru (kanan).
        if (stats.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stats.first().stamp, style = MaterialTheme.typography.labelSmall, color = axisColor)
                Text(stats.last().stamp, style = MaterialTheme.typography.labelSmall, color = axisColor)
            }
        } else {
            Text(stats.first().stamp, style = MaterialTheme.typography.labelSmall, color = axisColor)
        }
    }
}

// v38: legenda warna — satu Text ber-AnnotatedString (bukan Row berlapis) supaya otomatis turun
// baris & tidak terpotong di layar sempit/font besar. Hanya kategori yang MEMANG ada yang ditampilkan.
@Composable
private fun StatsLegend(hasOk: Boolean, hasSkipped: Boolean, hasFail: Boolean, hasAvg: Boolean, hasManual: Boolean = false) {
    val errorColor = MaterialTheme.colorScheme.error
    val items = mutableListOf<Pair<Color, String>>()
    if (hasOk) items.add(successGreen to "Berhasil")
    if (hasSkipped) items.add(skippedAmber to "Dilewati")
    if (hasFail) items.add(errorColor to "Gagal")
    val text = buildAnnotatedString {
        items.forEachIndexed { i, (color, label) ->
            if (i > 0) append("   ")
            withStyle(SpanStyle(color = color)) { append("●") }
            append(" $label")
        }
        if (hasAvg) {
            if (items.isNotEmpty()) append("   ")
            append("- - - Rata-rata")
        }
        if (hasManual) {
            if (items.isNotEmpty() || hasAvg) append("   ")
            append("• titik = dipicu manual")
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall)
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
