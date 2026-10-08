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
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v61: dark-only -> ikon status/navigasi bar SELALU terang, apa pun mode sistem (default auto
        // memakai ikon gelap saat sistem terang -> tak terbaca di latar midnight).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            LagFixTheme { HomeScreen(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        syncExcludeFromRecents(applicationContext) // v133: task bisa lahir baru (notifikasi/tile) -> terapkan ulang
        // v123: catcher dibaca SEBELUM `ensureShowing` di bawah (keadaan notifikasi sebelum re-assert).
        ProcessCatcher.capture(applicationContext, ProcessCatcher.SOURCE_ACTIVITY, allowCold = true)
        // v96: app terlihat = start FGS DIIZINKAN OS walau app dibatasi di background (snapshot v95: bg
        // restriction menolak start dari proses background). Idempoten: no-op bila toggle OFF / notif tampil.
        PersistentTrimService.ensureShowing(this, "onResume")
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // v140: celah excludeFromRecents (task di depan tetap tampil di Recents) -> hapus task saat user pergi.
        // Aturan & pengaman di LeaveGuard (RecentsExclusion.kt); di sini hanya pekerjaan ViewModel yg ikut mati.
        LeaveGuard.onUserLeave(this, vm.ui.running || vm.ui.downloading || vm.ui.updateChecking || vm.bootTrim.busy)
    }
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
    val haptic = LocalHapticFeedback.current // v71 (M4): tick ringan saat konfirmasi run
    val versionName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
    }
    var showAbout by rememberSaveable { mutableStateOf(false) } // v9 (E2)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // v10: 0=Utama, 1=Pengaturan; v108: IntState
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

    // v76 (fix fade tab, rekaman layar ke-2): v75 mengganti tab SAAT alpha 0 -> komposisi tab baru yang
    // berat jatuh di tengah fade -> layar kosong 83-200 ms (8 dari 8 pindah tab di rekaman) di antara
    // fade-out & fade-in. Kini tab tujuan DIKOMPOSISI DULU (alpha 0) selagi tab lama masih tampak penuh
    // & diam; frame berat itu lewat DULU (2x withFrameNanos), BARU fade dimulai: lama 1->0 (TAB_OUT_MS),
    // lalu baru 0->1 (TAB_IN_MS) -> fade murni di fase gambar (graphicsLayer), 0 rekomposisi/frame, tanpa
    // layar kosong. Tab lama dibuang dari komposisi setelah alpha 0. Tiap tab pakai `key(tab)` sendiri
    // (scroll per tab, mulai dari atas; komposisi tab tak diulang saat tab lain dibuang). Selama transisi
    // (~300 ms) 2 tab terkomposisi & sentuhan diblokir (`transitioning`) agar tak menekan widget tak
    // terlihat. Ketuk bolak-balik cepat: efek restart, alpha lanjut dari nilai terkini.
    val tabAlphas = remember {
        listOf(Animatable(if (selectedTab == 0) 1f else 0f), Animatable(if (selectedTab == 1) 1f else 0f))
    }
    val tabComposed = remember { mutableStateListOf(selectedTab == 0, selectedTab == 1) }
    var transitioning by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTab) {
        val target = selectedTab
        val other = 1 - target
        val needsFade = tabAlphas[target].value < 1f || tabAlphas[other].value > 0f
        if (needsFade) {
            transitioning = true
            if (!tabComposed[target]) {
                tabComposed[target] = true // tab tujuan masuk komposisi, alpha 0 (tak terlihat)
                withFrameNanos { } // frame komposisi/layout berat jatuh di sini, tab lama masih utuh
                withFrameNanos { } // pastikan frame itu selesai sebelum animasi mulai
            }
            tabAlphas[other].animateTo(0f, tween(durationMillis = LagMotion.TAB_OUT_MS, easing = FastOutLinearInEasing))
            tabComposed[other] = false
            tabAlphas[target].animateTo(1f, tween(durationMillis = LagMotion.TAB_IN_MS, easing = FastOutSlowInEasing))
            transitioning = false
        }
    }

    Scaffold(
        containerColor = Color.Transparent, // v61: backdrop berglow dari LagFixTheme tampil di belakang
        topBar = { TopAppBar(title = { Text("LagFix (fstrim)") }, colors = glassTopBarColors()) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = GlassNavContainer, modifier = Modifier.glassTopEdge()) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    // v62 (M2): label "Utama" sudah dibaca TalkBack
                    icon = { Icon(painterResource(R.drawable.ic_nav_home), contentDescription = null) },
                    label = { Text("Utama") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(painterResource(R.drawable.ic_nav_settings), contentDescription = null) }, // v62 (M2)
                    label = { Text("Pengaturan") }
                )
            }
        }
    ) { pad ->
        // v76: lihat efek `tabAlphas` di atas. Isi tiap tab memakai `tab` dari loop (BUKAN selectedTab)
        // agar tab yang sedang memudar tetap menampilkan dirinya sendiri. Callback/state/argumen tab
        // identik v73.
        // v77: swipe horizontal lintas tab (geser kiri = Pengaturan, geser kanan = Utama). Jalur transisi
        // SAMA dgn tap ikon nav (cukup ubah `selectedTab` -> efek v76), 0 komponen baru (bukan Pager).
        // Gestur yg sudah dikonsumsi anak (chip interval `horizontalScroll`, kolom teks) tak memicu pindah
        // tab; diabaikan selama `transitioning`. Ambang 72dp: cukup sengaja agar tak bentrok dgn gulir.
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .pointerInput(Unit) {
                    val threshold = 72.dp.toPx()
                    var dragged = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragCancel = { dragged = 0f },
                        onDragEnd = {
                            if (!transitioning) {
                                if (dragged <= -threshold && selectedTab == 0) {
                                    selectedTab = 1
                                } else if (dragged >= threshold && selectedTab == 1) {
                                    selectedTab = 0
                                }
                            }
                            dragged = 0f
                        },
                        onHorizontalDrag = { _, amount -> dragged += amount }
                    )
                }
        ) {
            for (tab in 0..1) {
                if (tabComposed[tab]) {
                    key(tab) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = tabAlphas[tab].value }
                                .imePadding()
                                .verticalScroll(rememberScrollState())
                                .padding(LagSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(LagSpacing.md)
                        ) {
                            if (tab == 0) {
                                MainTab(
                                    ui = ui,
                                    ctx = ctx,
                                    versionName = versionName,
                                    onRunNow = { showRunConfirm = true }, // v11: minta konfirmasi dulu
                                    onGrant = ::requestShizukuPermission,
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
                    }
                }
            }
            if (transitioning) { // blokir sentuhan selama transisi (~300 ms): tab tujuan belum terlihat
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    }
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
                    TextButton(onClick = {
                        showRunConfirm = false
                        // v71 (M4): tick ringan; ikut pengaturan sentuhan sistem
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.runNow()
                    }) { Text("Jalankan") }
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

// v98: batas tampil daftar (laporan user: Riwayat & daftar log terlalu panjang ke bawah). Data TIDAK dihapus/
// diubah: Riwayat tetap maks 30 baris (Prefs) & daftar log tetap maks 50 file FIFO terbaru (CrashLogger.listLogs);
// yang dibatasi hanya berapa baris yang langsung terlihat, sisanya lewat tombol "Tampilkan semua".
private const val PREVIEW_COUNT = 5

// v98 (laporan user: label "Mengambil…" tak kunjung selesai): pagar UI. Bila snapshot melewati batas ini, tombol
// dilepas & user diberi tahu; pekerjaan di latar belakang dibiarkan selesai sendiri (tak bisa dibatalkan paksa).
private const val SNAPSHOT_UI_TIMEOUT_MS = 240_000L

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
    // v68 (M3): status Shizuku + tombol run digabung jadi hero (HomeHero.kt). Callback, kondisi
    // enabled (READY && !running), dan semua teks identik v67; progress hanya saat running.
    HeroStatusCard(
        state = ui.shizuku,
        running = ui.running,
        onGrant = onGrant,
        onOpen = { openShizuku(ctx, ui.shizuku) },
        onRunNow = onRunNow
    )

    GlassCard(Modifier.fillMaxWidth(), animateSize = true) { // v71 (M4): tinggi kartu halus saat isi berubah
        Column(Modifier.padding(LagSpacing.lg), verticalArrangement = Arrangement.spacedBy(LagSpacing.xs)) {
            CardTitle("Riwayat")
            if (ui.lastRunMs == 0L) {
                HistoryEmptyState() // v68 (M3): copy lama "Belum pernah dijalankan" + petunjuk aksi
            } else {
                Text(
                    formatTime(ui.lastRunMs) + when {
                        ui.log.firstOrNull()?.contains("dilewati") == true -> " — dilewati (Shizuku belum siap)"
                        ui.lastOk -> " — berhasil"
                        else -> " — gagal"
                    }
                )
            }
            // v38: pemicu run terakhir (Manual/Otomatis) dari baris log terbaru. Baris lama tanpa
            // token -> label null -> tidak ditampilkan (bukan menebak).
            val lastTrigger = ui.log.firstOrNull()?.let { parseLogLine(it)?.trigger }.orEmpty()
            val lastTriggerLabel = if (ui.lastRunMs != 0L) triggerLabel(lastTrigger) else null
            if (lastTriggerLabel != null) {
                Text("Dipicu oleh: $lastTriggerLabel", style = MaterialTheme.typography.bodySmall)
            }
            var showAllRuns by rememberSaveable { mutableStateOf(false) } // v98
            (if (showAllRuns) ui.log else ui.log.take(PREVIEW_COUNT)).forEach { LogLine(it) }
            if (ui.log.size > PREVIEW_COUNT) {
                TextButton(onClick = { showAllRuns = !showAllRuns }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAllRuns) "Ringkas" else "Tampilkan semua (${ui.log.size})")
                }
            }
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

private const val INTERVAL_MIN_PER_DAY = 1440L
private const val INTERVAL_MIN_PER_HOUR = 60L

// v44: menit -> teks manusiawi ("15 menit", "1 jam 30 menit", "3 hari"). Murni format, 0 side-effect.
internal fun formatInterval(minutes: Long): String {
    val d = minutes / INTERVAL_MIN_PER_DAY
    val h = (minutes % INTERVAL_MIN_PER_DAY) / INTERVAL_MIN_PER_HOUR
    val m = minutes % INTERVAL_MIN_PER_HOUR
    val parts = buildList {
        if (d > 0) add("$d hari")
        if (h > 0) add("$h jam")
        if (m > 0) add("$m menit")
    }
    return parts.joinToString(" ").ifEmpty { "0 menit" }
}

// v10: tab "Pengaturan" — jadwal otomatis + interval (dipindah dari Utama, sama
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
    // v79 (M5): bagian yang jarang dipakai dilipat; state tahan rotasi. Default tertutup.
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var logOpen by rememberSaveable { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(LagSpacing.lg), verticalArrangement = Arrangement.spacedBy(LagSpacing.sm)) {
            CardTitle("Jadwal")
            ToggleRow("Jadwal otomatis", ui.enabled) {
                vm.setEnabled(it)
                onFeedback(if (it) "Jadwal otomatis diaktifkan." else "Jadwal otomatis dinonaktifkan.")
            }
            Text("Interval")
            // v44 (permintaan user: interval kustom diisi sendiri, biar tak kelamaan nunggu saat
            // uji): chip preset TETAP (nilai jam x 60 -> menit), ditambah kolom angka menit.
            // Batas bawah 15 menit = batas periodik WorkManager (di bawah itu WorkManager diam-diam
            // menaikkannya, jadi ditolak eksplisit di sini). Teks input rememberSaveable -> tahan rotasi.
            // v118: toggle "Interval radikal" (bebas on/off, tanpa batasan) menurunkan batas bawah ke 1 menit.
            val focusManager = LocalFocusManager.current
            val presets = listOf(6L to "6 jam", 12L to "12 jam", 24L to "1 hari", 72L to "3 hari", 168L to "7 hari")
            var customText by rememberSaveable {
                mutableStateOf(
                    if (presets.any { it.first * 60L == ui.intervalMinutes }) "" else ui.intervalMinutes.toString()
                )
            }
            val customValue = customText.toLongOrNull()
            val minInterval = Scheduler.minIntervalMinutes(ui.radicalInterval)
            val customValid = customValue != null && customValue >= minInterval
            val applyCustom: () -> Unit = {
                if (customValue != null && customValid) {
                    vm.setInterval(customValue)
                    onFeedback("Interval diubah ke ${formatInterval(customValue)}.")
                    focusManager.clearFocus()
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(LagSpacing.sm)
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
                horizontalArrangement = Arrangement.spacedBy(LagSpacing.sm),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.weight(1f),
                    // v64: label dipersingkat + 1 baris (sebelumnya membungkus 2 baris & merusak notch outline
                    // di kolom setengah lebar).
                    label = { Text("Kustom (menit)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    isError = customText.isNotEmpty() && !customValid,
                    supportingText = {
                        Text(
                            if (customText.isNotEmpty() && !customValid) {
                                if (ui.radicalInterval) {
                                    "Minimal $minInterval menit."
                                } else {
                                    "Minimal $minInterval menit (batas WorkManager)."
                                }
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
            // v118 (permintaan user): mode interval radikal (< 15 menit), bebas dinyalakan/dimatikan kapan saja
            // tanpa batasan/konfirmasi. Mati -> interval < 15 menit otomatis dinaikkan ke 15 (lihat setInterval).
            ToggleRow("Interval radikal (< 15 menit)", ui.radicalInterval) { on ->
                vm.setInterval(ui.intervalMinutes, on)
                if (!on && customValue != null && customValue < Scheduler.MIN_INTERVAL_MINUTES) customText = ""
                onFeedback(if (on) "Interval radikal aktif." else "Interval radikal nonaktif.")
            }
            if (ui.radicalInterval) {
                // v119 (permintaan user): chip preset cepat 1/5/10 menit, tampil hanya saat mode radikal ON.
                Row(horizontalArrangement = Arrangement.spacedBy(LagSpacing.sm)) {
                    listOf(1L, 5L, 10L).forEach { m ->
                        FilterChip(
                            selected = ui.intervalMinutes == m,
                            onClick = {
                                customText = ""
                                vm.setInterval(m)
                                onFeedback("Interval diubah ke ${formatInterval(m)}.")
                            },
                            label = { Text("$m menit") }
                        )
                    }
                }
                Text(
                    "Boros baterai. Saat layar mati/Doze, Android bisa menunda jadwal; tak dijamin tepat waktu.",
                    style = MaterialTheme.typography.bodySmall
                )
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
                    Text("✓", Modifier.decorative())
                }
            } else {
                Text(
                    "Jadwal otomatis bisa meleset di HP ini kalau baterai masih dioptimasi sistem.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = { runCatching { ctx.startExternal(batteryOptimizationIntent(ctx)) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Izinkan berjalan tanpa batas", Modifier.weight(1f))
                    Text("↗", Modifier.decorative())
                }
            }
            // v111 (kandidat (ii)): baris di atas hanya mengecek optimasi baterai; pembatasan latar belakang
            // (`isBackgroundRestricted`) dicek & ditampilkan terpisah supaya ✓ di atas tak jadi sinyal aman palsu.
            if (ui.backgroundRestricted) BackgroundRestrictedNotice(ctx)
        }
    }

    // v83 (H2(1), perintah user): atur setting sistem `fstrim_mandatory_interval` lewat Shizuku
    // supaya Android memaksa fstrim saat boot. Nilai sistem DIBACA dulu & ditampilkan (tak menimpa
    // diam-diam), hasil tulis dibuktikan baca-ulang, dan ada Reset (nilai bertahan stlh uninstall).
    // Tombol vertikal (fillMaxWidth) spy tak terpotong di font scale besar. 0 perubahan Jadwal.
    val bootTrim = vm.bootTrim
    val shizukuReady = ui.shizuku == ShizukuState.READY
    LaunchedEffect(ui.shizuku) { vm.refreshBootTrim() }
    GlassCard(Modifier.fillMaxWidth(), animateSize = true) {
        Column(Modifier.padding(LagSpacing.lg), verticalArrangement = Arrangement.spacedBy(LagSpacing.sm)) {
            CardTitle("Paksa trim saat reboot")
            Text(
                "Mengatur batas \"paksa fstrim\" bawaan Android (setting sistem " +
                    "${BootTrimSetting.KEY}) ke 1 ms, supaya Android sendiri menjalankan fstrim saat boot, " +
                    "tanpa menunggu Shizuku hidup. Cara kerja ini dari laporan pengguna mFSTRIM dan " +
                    "BELUM diverifikasi di HP ini. Shizuku hanya dibutuhkan saat mengubah. Nilainya tersimpan " +
                    "di sistem dan tetap ada walau LagFix di-uninstall: tekan Reset untuk mengembalikan. " +
                    "Trim oleh sistem ini TIDAK masuk Riwayat; jejaknya bisa dicari lewat " +
                        "\"Ambil logcat sistem\" di Info & diagnostik.",
                style = MaterialTheme.typography.bodySmall
            )
            val reading = bootTrim.reading
            Text(
                when {
                    !shizukuReady -> "Nilai sistem: tak bisa dibaca, Shizuku belum siap."
                    reading == null -> "Nilai sistem: membaca…"
                    else -> "Nilai sistem sekarang: ${describeBootTrim(reading)}"
                }
            )
            bootTrim.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            val alreadyOn = reading == BootTrimReading.Value(BootTrimSetting.EVERY_REBOOT_MS)
            val canChange = shizukuReady && !bootTrim.busy && reading != null
            Button(
                onClick = { vm.changeBootTrim(true) },
                enabled = canChange && !alreadyOn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (reading is BootTrimReading.Value) {
                        "Timpa dengan 1 ms (tiap reboot)"
                    } else {
                        "Aktifkan (tiap reboot)"
                    }
                )
            }
            TextButton(
                onClick = { vm.changeBootTrim(false) },
                enabled = canChange && reading != BootTrimReading.Unset,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Reset ke default Android") }
        }
    }

    // v49 (milestone "Kartu panduan jadwal", dipilih user; menggantikan paragraf teks "Autostart"
    // v24 yg dulu di kartu Jadwal — DIPINDAH ke sini & dilengkapi, bukan diduplikasi).
    // Dasar bukti: logcat v41 (XOS `TranManualCleanMgr` SIGKILL saat swipe-Recents + tolak restart
    // servis) — cuma poin 1 yg TERBUKTI di HP ini. Poin 2 (Kunci Recents) & 3 (Autostart) = praktik
    // umum ROM, BELUM diverifikasi di HP ini -> teksnya sengaja berhati-hati, tanpa klaim pasti.
    // Tombol hanya membuka Info Aplikasi (intent standar Android, 0 tebakan nama activity per ROM
    // -> tetap patuh alasan v24: tak ada intent Autostart khusus merek).
    // v27 (fitur opsional, HANYA utk user yg sudah coba opsi baterai/Autostart di atas & masih
    // bermasalah — konsultasi eksplisit dgn user sebelum dibuat, lihat riwayat v25/v26, dikerjakan v27). 0 logic
    // fstrim baru — cuma menjaga proses tetap hidup, jadwal periodik tetap lewat WorkManager
    // (Scheduler.apply(), 0 diubah).
    GlassCard(Modifier.fillMaxWidth(), animateSize = true) {
        Column(Modifier.padding(LagSpacing.lg), verticalArrangement = Arrangement.spacedBy(LagSpacing.sm)) {
            CardTitle("Keandalan latar belakang")
            // v79 (M5): kartu "Agar jadwal tetap jalan" (v49) digabung ke sini sbg panduan yang bisa dilipat.
            FoldHeader("Panduan agar jadwal tetap jalan", guideOpen) { guideOpen = !guideOpen }
            if (guideOpen) {
                Text(
                    "Jadwal otomatis jalan di latar belakang, jadi HP tidak boleh mematikan LagFix. " +
                        "Yang paling berpengaruh:",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• Jangan geser LagFix dari daftar aplikasi terbaru (Recents). Di HP seperti " +
                        "Infinix/Tecno (XOS), menggesernya langsung mematikan proses LagFix. " +
                        "Untuk keluar cukup tekan Home.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• Kunci LagFix di Recents kalau HP punya opsinya (biasanya tahan kartu aplikasi, " +
                        "lalu pilih Kunci). Ini melindungi dari \"Bersihkan semua\"; menggeser satu " +
                        "kartu tetap bisa mematikannya di sebagian HP.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• Izinkan \"Autostart\" / \"Latar belakang\" di App Management atau Phone " +
                        "Master/Security App bawaan HP. Nama menu beda-beda tiap merek & versi.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Tidak ada jaminan 100%: sistem HP tetap bisa mematikan aplikasi kapan saja.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        runCatching {
                            ctx.startExternal(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", ctx.packageName, null)
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Buka Info Aplikasi LagFix", Modifier.weight(1f))
                    Text("↗", Modifier.decorative())
                }
                // v105 (perintah user, konfigurasi_bypass_restricted_os.md bagian 3): menggantikan aturan
                // lama v24/v49 "tak ada intent khusus merek" HANYA untuk Xiaomi/Samsung/Huawei (VendorSettings.kt).
                TextButton(
                    onClick = { openVendorBatterySettings(ctx) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Buka pengaturan Autostart / baterai merek HP", Modifier.weight(1f))
                    Text("↗", Modifier.decorative())
                }
            }
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
                    LeaveGuard.markExternal() // v140: dialog izin = Activity lain di depan; jangan hapus task
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.setPersistentService(turnOn)
                }
                onFeedback(if (turnOn) "Layanan latar depan diaktifkan." else "Layanan latar depan dimatikan.")
            }
            Text(
                "Opsional — memaksa proses LagFix tetap hidup di HP yang agresif mematikan " +
                    "aplikasi latar belakang, dengan notifikasi permanen yang tak bisa disembunyikan " +
                    "selama aktif. Coba dulu opsi baterai & Autostart di atas sebelum ini. Selama aktif, " +
                    "LagFix juga tidak tampil di daftar aplikasi terbaru (Recents); " +
                    "buka lewat ikon, widget, atau tile. Meninggalkan aplikasi juga menutup layarnya " +
                    "(tab dan dialog kembali ke awal saat dibuka lagi).",
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
    // v79 (M5): Log Diagnostik (dilipat; daftar log baru dimuat saat dibuka) + Tautan jadi 1 kartu "Info & diagnostik".
    // v12: tautan dipindah dari tab Utama biar tab Utama cuma isi fitur utama (status/aksi/riwayat/pembaruan).
    GlassCard(Modifier.fillMaxWidth(), animateSize = true) {
        Column(Modifier.padding(LagSpacing.lg), verticalArrangement = Arrangement.spacedBy(LagSpacing.xs)) {
            CardTitle("Info & diagnostik")
            FoldHeader("Log diagnostik", logOpen) { logOpen = !logOpen }
            if (logOpen) LogReaderCard(ctx = ctx, onFeedback = onFeedback)
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
    var capturing by remember { mutableStateOf(false) } // v84: snapshot logcat
    var logs by remember { mutableStateOf<List<CrashLogger.LogFile>>(emptyList()) }
    var showAllLogs by rememberSaveable { mutableStateOf(false) } // v98: daftar diringkas ke PREVIEW_COUNT
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

    // v79 (M5): dirender di dalam kartu "Info & diagnostik" -> tanpa GlassCard/judul sendiri.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LagSpacing.sm)) {
        Text(
            "Baca langsung dari dalam aplikasi — tidak bergantung file manager/folder Documents " +
                "yang mungkin tidak menampilkan file baru di sebagian HP.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(LagSpacing.sm)) {
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
                                val writeFailReason = result.exceptionOrNull()?.message ?: "error tidak diketahui"
                                onFeedback("Tes tulis GAGAL: $writeFailReason")
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
        // v84 (perintah user: perluas catch logcat): 1 file ringkasan bukti tanpa adb/PC.
        TextButton(
            onClick = {
                if (!capturing) {
                    capturing = true
                    scope.launch {
                        // v98: pekerjaan berat di IO, tapi UI hanya menunggu sampai SNAPSHOT_UI_TIMEOUT_MS supaya
                        // label tak bisa macet selamanya (scope = Main, jadi state di bawah aman diubah langsung).
                        val job = async(Dispatchers.IO) {
                            LeaveGuard.beginWork() // v140: selama snapshot jalan, leave TIDAK menghapus task
                            try {
                                LogcatSnapshot.capture(ctx)
                            } finally {
                                LeaveGuard.endWork()
                            }
                        }
                        val result = withTimeoutOrNull(SNAPSHOT_UI_TIMEOUT_MS) { job.await() }
                        capturing = false
                        if (result == null) {
                            onFeedback(
                                "Snapshot belum selesai setelah ${SNAPSHOT_UI_TIMEOUT_MS / 1000} dtk; masih " +
                                    "berjalan di latar belakang. Cek Documents/LagFix beberapa saat lagi."
                            )
                        } else {
                            result.onSuccess {
                                onFeedback(it.feedback())
                                load()
                            }.onFailure { onFeedback("Snapshot GAGAL: ${it.message ?: "error tidak diketahui"}") }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (capturing) "Mengambil… (bisa memakan waktu)" else "Ambil logcat sistem", Modifier.weight(1f)) }
        Text(
            "Menyimpan 2 file di Documents/LagFix: ringkasan (.txt, bisa dibaca di sini) dan dump mentah " +
                "logcat LENGKAP tanpa filter (.zip, tidak tampil di daftar ini — kirim file ini bila ringkasan " +
                "kurang). Ringkasan memuat status sistem (notifikasi, servis, jadwal, baterai) dan alasan proses " +
                "mati. Setelah dump, buffer logcat diperbesar sementara (kembali normal saat reboot) agar " +
                "pengambilan berikutnya mencakup lebih lama — jadi tekan sekali SEBELUM menguji, lalu sekali lagi " +
                "sesudahnya. Bagian logcat butuh Shizuku siap.",
            style = MaterialTheme.typography.bodySmall
        )
        when {
            loadError != null -> Text(
                "Error: $loadError",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            !loading && logs.isEmpty() -> Text(
                "Belum ada file log ditemukan (baik di Documents/LagFix, Download/LagFix lama, " +
                    "maupun cadangan internal app).",
                style = MaterialTheme.typography.bodySmall
            )
        }
        (if (showAllLogs) logs else logs.take(PREVIEW_COUNT)).forEach { log ->
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
        if (logs.size > PREVIEW_COUNT) { // v98
            TextButton(onClick = { showAllLogs = !showAllLogs }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showAllLogs) "Ringkas" else "Tampilkan semua (${logs.size})")
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
        Text("↗", Modifier.decorative())
    }
}

private fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startExternal(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

// v9 (E2): beda dari LinkRow ("↗" = buka browser) — "›" krn ini buka dialog in-app, bukan tautan.
@Composable
private fun AboutRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text("›", Modifier.decorative())
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
                    Text("↗", Modifier.decorative())
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
private val lateRegex = Regex("""\stelat=(\d+)m$""") // v120: token akhir dari Prefs.record
private const val LOG_TAIL_GROUP = 4 // v121 (detekt MagicNumber): group regex ke-4 `(.*)` = sisa baris

// v77: `stamp` = jam 12 jam (AM/PM) utk TAMPIL (konversi dari format simpan 24 jam di TimeFormat.kt).
// v120: `late` = menit terlambat (0 = tepat waktu / baris lama tanpa token); token dibuang dari `rest`.
internal data class ParsedLog(
    val stamp: String,
    val ok: Boolean,
    val trigger: String,
    val rest: String,
    val late: Long = 0L
) {
    val skipped: Boolean get() = !ok && rest.contains("dilewati")
}

internal fun parseLogLine(line: String): ParsedLog? {
    val g = logLineRegex.find(line)?.groupValues ?: return null
    val late = lateRegex.find(g[LOG_TAIL_GROUP])
    return ParsedLog(
        stamp = formatStamp12h(g[1]),
        ok = g[2] == "OK",
        trigger = g[3],
        rest = if (late == null) g[4] else g[4].substring(0, late.range.first),
        late = late?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    )
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
    val statusColors = LocalStatusColors.current
    val tint = when {
        parsed.ok -> statusColors.success
        parsed.skipped -> statusColors.warning
        else -> MaterialTheme.colorScheme.error
    }
    // v80 (M6): baris "dilewati" (amber) sebelumnya berlabel FAIL padahal legenda Statistik
    // membedakan Dilewati vs Gagal.
    val status = when {
        parsed.ok -> "OK"
        parsed.skipped -> "SKIP"
        else -> "FAIL"
    }
    // v38: tampilkan pemicu run (Manual/Otomatis) kalau ada; baris lama tanpa token -> tanpa tag.
    val triggerTag = if (parsed.trigger.isNotEmpty()) " (${parsed.trigger})" else ""
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("●", modifier = Modifier.decorative(), color = tint, style = MaterialTheme.typography.bodySmall)
        Column {
            Text(
                "${parsed.stamp} $status$triggerTag ${parsed.rest}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = tint
            )
            // v120 (permintaan user): penanda terlambat (mis. ditunda Doze); amber = warna peringatan Riwayat.
            if (parsed.late > 0L) {
                Text(
                    "Terlambat ${formatInterval(parsed.late)} dari jadwal",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = statusColors.warning
                )
            }
        }
    }
}

// v37 (fitur user-facing, permintaan user "grafik statistik ... bikin project hidup, gak teknis
// banget") + v38 (feedback user: batang grafik "kurang informatif utk user awam"): kartu "Statistik"
// di tab Utama. PARSE-ONLY di sisi UI (reuse parseLogLine/LocalStatusColors), 0 data baru.
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

private const val DURATION_MS_PER_TENTH = 100L
private const val DURATION_HALF_TENTH_MS = 50L
private const val DURATION_TENTHS_PER_SECOND = 10L

// v38: "820ms" tak bermakna bagi user awam -> tampilkan dalam detik (1 desimal, koma gaya
// Indonesia). Di bawah 0,1 detik ditulis "< 0,1 detik" supaya tak jadi "0,0 detik" yg membingungkan.
private fun formatDuration(ms: Long): String {
    if (ms < DURATION_MS_PER_TENTH) return "< 0,1 detik"
    val tenths = (ms + DURATION_HALF_TENTH_MS) / DURATION_MS_PER_TENTH
    return "${tenths / DURATION_TENTHS_PER_SECOND},${tenths % DURATION_TENTHS_PER_SECOND} detik"
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

    GlassCard(Modifier.fillMaxWidth(), animateSize = true) { // v71 (M4): tinggi kartu halus saat isi berubah
        Column(Modifier.padding(LagSpacing.lg), verticalArrangement = Arrangement.spacedBy(LagSpacing.sm)) {
            CardTitle("Statistik")
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
    val successColor = LocalStatusColors.current.success
    val warningColor = LocalStatusColors.current.warning
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val avgLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val haloColor = MaterialTheme.colorScheme.surface // v80 (M6): halo gelap di bawah garis rata-rata
    val hasManualMarker = stats.any { it.trigger == TriggerSource.MANUAL }
    // v82 (M9): Canvas tak punya semantik -> TalkBack dulu membaca sumbu terpotong-potong / diam.
    // Satu ringkasan menggantikan semuanya.
    val chartDescription = buildString {
        append("Grafik lama tiap proses: ${stats.size} proses, ")
        append(if (stats.size > 1) "dari ${stats.first().stamp} sampai ${stats.last().stamp}" else stats.first().stamp)
        append(". Terlama ${formatDuration(rawMax)}")
        if (avgOkMs > 0L) append(", rata-rata proses berhasil ${formatDuration(avgOkMs)}")
        append(".")
    }
    Column(
        Modifier.clearAndSetSemantics { contentDescription = chartDescription },
        verticalArrangement = Arrangement.spacedBy(LagSpacing.xs)
    ) {
        Row(
            Modifier.fillMaxWidth().height(LagChart.HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(LagSpacing.sm)
        ) {
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
                // v80 (M6): semua ukuran dari token LagChart (dp -> px), bukan px literal; alpha garis dasar 0.4->0.6
                // (kontras 2.8 -> 4.7, lihat Design.kt); garis rata-rata diberi halo gelap agar tetap
                // terlihat di atas batang.
                val barGapPx = LagChart.BAR_GAP.toPx()
                val n = stats.size.coerceAtLeast(1)
                val barWidth = ((size.width - barGapPx * (n - 1)) / n).coerceAtLeast(1f)
                val baselinePx = LagChart.BASELINE_STROKE.toPx()
                val minBarPx = LagChart.MIN_BAR_HEIGHT.toPx()
                val radiusPx = minOf(LagChart.BAR_RADIUS.toPx(), barWidth / 2f)
                val markerRadiusPx = LagChart.MARKER_RADIUS.toPx()
                drawLine(
                    color = axisColor.copy(alpha = LagChart.BASELINE_ALPHA),
                    start = Offset(0f, size.height - baselinePx / 2f),
                    end = Offset(size.width, size.height - baselinePx / 2f),
                    strokeWidth = baselinePx
                )
                stats.forEachIndexed { i, s ->
                    val tint = when {
                        s.ok -> successColor
                        s.skipped -> warningColor
                        else -> errorColor
                    }
                    // Tinggi minimum kecil biar durasi 0 (dilewati) tetap kelihatan sbg bar tipis,
                    // bukan hilang total — murni visual, 0 pengaruh ke data asli.
                    val ratio = (s.durationMs.toFloat() / maxDuration).coerceIn(0f, 1f)
                    val barHeight = (size.height * ratio).coerceAtLeast(minBarPx)
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(i * (barWidth + barGapPx), size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(radiusPx)
                    )
                    if (hasManualMarker && s.trigger == TriggerSource.MANUAL) {
                        drawCircle(
                            color = axisColor,
                            radius = markerRadiusPx,
                            center = Offset(
                                i * (barWidth + barGapPx) + barWidth / 2f,
                                (size.height - barHeight - LagChart.MARKER_OFFSET.toPx()).coerceAtLeast(markerRadiusPx)
                            )
                        )
                    }
                }
                // Garis putus-putus = rata-rata proses yang berhasil: acuan cepat "di atas/di bawah biasanya".
                if (avgOkMs > 0L) {
                    val y = size.height - (size.height * (avgOkMs.toFloat() / maxDuration)).coerceIn(0f, size.height)
                    val dash = PathEffect.dashPathEffect(
                        floatArrayOf(LagChart.AVG_DASH.toPx(), LagChart.AVG_DASH_GAP.toPx()), 0f
                    )
                    drawLine(
                        color = haloColor.copy(alpha = LagChart.AVG_HALO_ALPHA),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = LagChart.AVG_HALO_STROKE.toPx(),
                        pathEffect = dash
                    )
                    drawLine(
                        color = avgLineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = LagChart.AVG_STROKE.toPx(),
                        pathEffect = dash
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
private fun StatsLegend(
    hasOk: Boolean,
    hasSkipped: Boolean,
    hasFail: Boolean,
    hasAvg: Boolean,
    hasManual: Boolean = false
) {
    val errorColor = MaterialTheme.colorScheme.error
    val statusColors = LocalStatusColors.current
    val items = mutableListOf<Pair<Color, String>>()
    if (hasOk) items.add(statusColors.success to "Berhasil")
    if (hasSkipped) items.add(statusColors.warning to "Dilewati")
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

    GlassCard(Modifier.fillMaxWidth(), animateSize = true) { // v71 (M4): tinggi kartu halus saat isi berubah
        Column(Modifier.padding(LagSpacing.lg), verticalArrangement = Arrangement.spacedBy(LagSpacing.sm)) {
            CardTitle("Pembaruan")

            when (result) {
                null -> Text(if (checking) "Memeriksa pembaruan…" else "Ketuk untuk memeriksa versi terbaru di GitHub.")
                is UpdateResult.UpToDate -> Text("Sudah versi terbaru (build ${result.installedBuild}).")
                is UpdateResult.Error -> Text("Gagal memeriksa pembaruan: ${result.message}")
                is UpdateResult.Available -> {
                    Text("Versi terpasang: build ${result.installedBuild}")
                    Text("Versi tersedia: build ${result.info.latestBuild} (${result.info.latestName})")
                    // v65: aksi disusun simetris — semua tombol selebar kartu & setinggi sama (sebelumnya
                    // TextButton + Button berdampingan: teks Update membungkus 2 baris, tinggi tak sama,
                    // TextButton menjorok ke dalam). Hierarki: Update = primer (filled), changelog = tonal.
                    Button(
                        onClick = { onInstall(result.info.downloadUrl) },
                        enabled = !downloading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (downloading) "Memasang…" else "Update sekarang") }
                    FilledTonalButton(
                        onClick = { showChangelog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Lihat changelog") }
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
                val checkLabel = if (result == null) "Cek pembaruan" else "Cek ulang"
                if (result is UpdateResult.Available) {
                    // v65: saat ada update, "Update sekarang" jadi satu-satunya aksi primer -> cek ulang = tonal.
                    FilledTonalButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text(checkLabel) }
                } else {
                    Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text(checkLabel) }
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
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // v82 (M9): seluruh baris = SATU target Switch (`toggleable`): TalkBack membaca label + status "aktif/nonaktif"
    // (sebelumnya Switch terbaca tanpa label), target sentuh >= 48dp (dulu hanya Switch ~32dp).
    // Callback `onChange` sama.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

// v82 (M9): judul kartu = heading (TalkBack bisa lompat antar judul).
@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

// v82 (M9): baris lipat (Pengaturan) — glif ▾/▴ dekoratif (dulu dibaca "segitiga hitam"),
// status dibaca "Terbuka/Tertutup".
@Composable
private fun FoldHeader(label: String, expanded: Boolean, onToggle: () -> Unit) {
    TextButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Terbuka" else "Tertutup" }
    ) {
        Text(label, Modifier.weight(1f))
        Text(if (expanded) "▴" else "▾", Modifier.decorative())
    }
}

// v82 (M9): glif hias (↗ › ✓ ● ▾) disembunyikan dari TalkBack; maknanya sudah ada di teks sebelahnya.
private fun Modifier.decorative(): Modifier = clearAndSetSemantics { }

private fun openShizuku(ctx: Context, state: ShizukuState) {
    val intent = if (state == ShizukuState.NOT_INSTALLED) {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
    } else {
        ctx.packageManager.getLaunchIntentForPackage(FstrimExecutor.SHIZUKU_PKG)
    }
    runCatching { intent?.let { ctx.startExternal(it) } }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date(ms)) // v77: 12 jam (AM/PM)

// v111 (kandidat (ii), perintah user): peringatan bila `ActivityManager.isBackgroundRestricted` = true. Status ini
// DIBACA ulang tiap `vm.refresh()` (onResume), jadi hilang sendiri setelah user mengubahnya di Info Aplikasi.
// Teks sengaja tak menjanjikan hasil: pembatasan ini baru TERBUKTI ada di log, bukan satu-satunya
// penyebab notifikasi hilang.
@Composable
private fun BackgroundRestrictedNotice(ctx: Context) {
    Text(
        "Android membatasi LagFix berjalan di latar belakang. Ini TERPISAH dari status baterai di atas, dan " +
            "bisa membuat notifikasi layanan persisten gagal tampil atau hilang. Ubah lewat Info Aplikasi > Baterai.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
    TextButton(
        onClick = {
            runCatching {
                val appUri = Uri.fromParts("package", ctx.packageName, null)
                ctx.startExternal(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri))
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Buka Info Aplikasi LagFix", Modifier.weight(1f))
        Text("↗", Modifier.decorative())
    }
}
