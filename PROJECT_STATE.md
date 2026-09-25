[BRANDING_NAME: LagFix]
[TERMUX_ROOT: LagFix]

# PROJECT_STATE
- App: LagFix (fstrim) — pemicu + penjadwal fstrim non-root (via Shizuku), setara mFSTRIM.
- Package: com.lagfix.fstrim | minSdk 26 | compile/target 35 | Kotlin 2.0.21 | AGP 8.7.3 | Compose M3
- Alur: Prefs (SharedPreferences) -> Scheduler (WorkManager periodic) -> TrimWorker -> FstrimExecutor (Shizuku shell: `sm fstrim`, fallback `sm idle-maint run`)
- UI: MainActivity + MainViewModel (state bertahan rotasi)
- Safety: CrashLogger -> Download/LagFix via MediaStore (API 29+)
- CI: .github/workflows/build.yml; secret KEYSTORE_BASE64/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD; tanpa secret -> debug APK
- Keputusan: referensi Aplikasi_mFSTRIM.md (Runtime.exec + WRITE_SECURE_SETTINGS) tidak dipakai — UID aplikasi tidak berhak menjalankan `sm fstrim`; diganti Shizuku (UID shell).
- v2: card "Tautan" (AppLinks.kt) di MainActivity — rilis terbaru/source/lapor masalah; versionName ditampilkan via PackageManager (tanpa BuildConfig).
- v2: CI (build.yml) — sukses -> GitHub Release otomatis (softprops/action-gh-release@v2, tag `build-<run_number>`, `make_latest: true`, APK terlampir). Gagal -> log diunggah sebagai artifact, nama file wajib diakhiri run_number: `LagFix_build_fail_log_<run_number>.txt`. Job perlu `permissions: contents: write`.
- v4: fitur "Pembaruan" in-app (`UpdateChecker.kt`, baru) — GET GitHub `releases/latest` + GET raw `CHANGELOG.md` (branch main), pakai `org.json`/`HttpURLConnection` bawaan Android (tanpa dependensi baru).
- v4: compare versi before/after RIIL — `versionCode` APK = `GITHUB_RUN_NUMBER` (env otomatis Actions, via `app/build.gradle.kts`), dibandingkan angka hasil parse `tag_name` rilis (`build-<n>`). Lokal/dev tanpa env ini tetap `versionCode=1` (fallback, non-breaking).
- v4: UI — card "Pembaruan" (`MainActivity.kt`) di bawah card "Tautan": tombol cek, state UpToDate/Available/Error, dialog changelog scrollable, tombol unduh (asset `.apk` rilis kalau ada, else halaman rilis).
- v4: `MainViewModel.kt` — `checkUpdate()` (Dispatchers.IO) + `updateChecking`/`updateResult` di `UiState`; `refresh()`/`runNow()` dipatch agar tidak me-reset state update tsb tiap `onResume()`/selesai fstrim (fix regresi laten yg muncul dari state baru ini).
- v4: `AndroidManifest.xml` +izin `INTERNET` (wajib utk network call di atas; tidak ada izin lain ditambah).
- v4 VERIFIED (evidence screenshot user): CI sukses nyata di Actions — rilis `build-4` "LagFix build 4" terbentuk, `make_latest: true` OK, asset APK terlampir (6.51 MB). softprops/action-gh-release@v2 + glob path APK CONFIRMED jalan di real Actions (bukan asumsi lagi).
- v5: fix `build.yml` — asset APK rilis sebelumnya bernama generik `app-release.apk`/`app-debug.apk` (sama persis tiap build) -> browser tabrakan nama saat unduh rilis berturut-turut (`app-release (1).apk` dst menumpuk) & tidak informatif. Step baru "Siapkan APK rilis" copy APK ke `dist/LagFix_build-<run_number>_<release|debug>.apk` sebelum diunggah; step Release skrg pakai `files: dist/*.apk` (bukan glob output Gradle langsung). Artifact Actions biasa ("Unggah APK (artifact)") tidak diubah (di luar keluhan/scope — itu bukan tautan unduh publik berulang, adanya di tab Actions per-run).
- v5: tidak mengubah `UpdateChecker.kt` — logic pilih asset APK cuma cek suffix `.apk` (`endsWith(".apk")`), jadi tetap cocok dgn nama baru, tidak regresi.
- v6 (fix regresi dilaporkan user, evidence video+APK): tombol "Unduh" v4 pakai `openUrl()` -> `ACTION_VIEW` ke URL asset -> dibuka BROWSER, APK jatuh ke folder Download publik & menumpuk tiap update. Diganti install in-app asli:
  - `UpdateChecker.download()` (baru) — unduh APK ke `context.cacheDir/updates/` (privat app, BUKAN Download publik), hapus sisa unduhan lama dulu tiap mulai unduh baru -> tak pernah menumpuk.
  - `AndroidManifest.xml` +izin `REQUEST_INSTALL_PACKAGES` +provider `androidx.core.content.FileProvider` (authority `${applicationId}.fileprovider`) +resource baru `res/xml/file_paths.xml` (expose `cache/updates/` doang).
  - `MainViewModel.installUpdate(url)` — download (Dispatchers.IO) -> `FileProvider.getUriForFile()` -> `Intent.ACTION_VIEW` tipe `application/vnd.android.package-archive` ke Package Installer langsung (bukan browser), state `downloading`/`downloadError` baru di UiState (dipatch juga ke `refresh()`/`runNow()` spy tak ke-reset, pola sama spt `updateChecking`/`updateResult`).
  - `MainActivity` — tombol "Unduh" jadi "Update sekarang" -> panggil `vm::installUpdate` langsung (bukan `openUrl`); tampil "Memasang…" saat proses & pesan error kalau gagal.
  - Batasan OS (bukan bug): dialog konfirmasi sistem Package Installer ("Izinkan pasang dari LagFix?" sekali di awal, lalu "Pasang pembaruan ini?" tiap kali) TIDAK BISA dihilangkan tanpa root — itu proteksi Android bawaan utk app non-Play-Store, bukan browser lagi & tidak ninggalin file di Download.
- Repo: https://github.com/FDzaki-dev/LagFix — AppLinks.GITHUB_OWNER = "FDzaki-dev" (sudah terisi, bukan placeholder lagi).
- v7 (C1 dari PENDING_ROADMAP.md): unit test JVM murni (tanpa device) —
  `app/src/test/.../PrefsTest.kt` (rotasi log maks 30 baris terbaru-di-depan, format stamp
  `dd/MM HH:mm`, status OK/FAIL, newline diganti spasi + truncate 120 char) dan
  `app/src/test/.../FstrimExecutorTest.kt` (5 mapping status Shizuku: NOT_INSTALLED/NOT_RUNNING
  x2 jalur/READY/NEED_PERMISSION + 1 fallback saat `pingBinder()` throw exception).
  `Prefs` diuji pakai fake `SharedPreferences` in-memory (bukan Mockito) biar semantik put/apply
  beneran asli. `Shizuku` di-mock via `Mockito.mockStatic` (`org.mockito:mockito-core:5.23.0`,
  inline mock maker default di 5.x, testImplementation only). `FstrimExecutor.run()` (blocking
  shell exec reflection) SENGAJA tidak diuji — butuh Shizuku binder nyata, tetap D1 di roadmap.
  File diubah: `app/build.gradle.kts` (+2 testImplementation) + 2 file test baru — 0 file
  `app/src/main/` (production) disentuh.
- v7 VALIDASI: brace/paren balance OK + cross-check simbol (SHIZUKU_PKG, TrimResult, Prefs ctor)
  cocok dgn source asli. BELUM pernah dicompile compiler sungguhan — sandbox tanpa Android
  SDK/Gradle/jaringan. `./gradlew testDebugUnitTest` WAJIB dijalankan di CI/lokal sebelum diklaim
  hijau beneran (build.yml belum ada step test — di luar scope C1, backlog terpisah C3).
- Batch: v7

[RESUME POINT: v7 unit test ditulis (PrefsTest.kt + FstrimExecutorTest.kt), validasi statis (brace+referensi simbol) only, BELUM pernah dijalankan compiler sungguhan (sandbox tanpa SDK/Gradle/jaringan) -> Remaining: jalankan DAILY UPDATE, push, CI build jalan seperti biasa (assembleRelease/Debug) — unit test BELUM otomatis kejalankan di CI karena build.yml belum ada step `test` (C3, backlog terpisah, belum diminta) -> Next Action: push ke main, kalau ada typo Mockito API bakal ketahuan pas lokal run `gradle testDebugUnitTest` atau kalau user minta tambah step test ke CI]
- Docs: `PENDING_ROADMAP.md` dibuat (planning-only, 0 source file diubah) — rencana penyempurnaan A(blocking)/B(edge case)/C(testing)/D(tech debt)/E(UX minor)/F(CI), digrounded dari inspeksi langsung source v6 (tanpa app/src/test sama sekali).
- v6 VERIFIED (evidence: screen recording user, ~24 detik, device asli, ditonton frame-by-frame): alur "Update sekarang" end-to-end sukses nyata —
  (1) prompt sistem "Instal aplikasi tidak dikenal" muncul PERSIS SEKALI di awal (dialog + tombol Setelan) -> user toggle "Izinkan dari sumber ini" ON di halaman Settings yang scoped ke LagFix (1.0.0) — bukan izin global.
  (2) balik ke app, dialog Package Installer "Ingin mengupdate aplikasi ini?" muncul otomatis (FileProvider URI kebaca, installer kebuka tanpa lewat browser) -> tombol Update -> progress "Menginstal..." -> "Aplikasi terinstal." -> Buka.
  (3) app kebuka ulang TANPA crash, Riwayat (SharedPreferences) persist utuh lintas update, versi terpasang naik build 6 -> build 7 (CI run baru otomatis dari commit docs batch sebelumnya — run_number naik walau source app tak berubah, sesuai desain `versionCode = GITHUB_RUN_NUMBER`).
- Known SISA — DITUTUP via pembacaan kode langsung (`UpdateChecker.download()`): folder `cache/updates/` di-`deleteRecursively()` TOTAL lalu `mkdirs()` tiap kali `download()` dipanggil, dan file tujuan selalu nama tetap `update.apk` (bukan unik per build). Konsekuensi: menumpuk 2+ file ARSITEKTURAL TIDAK MUNGKIN selama kode ini yang jalan (bahkan kalau delete gagal, write berikutnya overwrite nama yang sama). Status: verified via source, BUKAN via runtime device-test — beda level bukti, dicatat sebagai valid tapi bukan pengganti kalau suatu saat mau extra-sure lewat cek ukuran Cache di Setelan > Aplikasi (opsional, tidak blocking).

[RESUME POINT: v6 alur install-update FULLY VERIFIED — poin 1&2 via evidence video device asli, poin 3 via pembacaan source (`deleteRecursively()`+nama file tetap) -> Remaining: tidak ada lagi item blocking di batch v6; roadmap v7 (PENDING_ROADMAP.md: unit test Prefs+FstrimExecutor.state) siap dimulai kapan saja -> Next Action: tanya user mau mulai v7 sekarang atau ada task lain]

- v8 (D3+E1 dari PENDING_ROADMAP.md, atas pilihan eksplisit user): 2 file source diubah —
  - `app/build.gradle.kts` (D3): `versionName` sekarang ikut `GITHUB_RUN_NUMBER` (`"1.0.<n>"`),
    bukan statis `"1.0.0"`. Lokal/dev tanpa env ini -> `"1.0.0-dev"` (fallback non-breaking,
    berbeda jelas dari build CI). `versionCode` tidak diubah (tetap `GITHUB_RUN_NUMBER` fallback 1).
  - `MainActivity.kt` (E1): baris Riwayat sekarang lewat `LogLine()` baru — parse regex
    `^(dd/MM HH:mm) (OK|FAIL) (.*)$` lalu render titik+teks warna (hijau `0xFF2E7D32` utk OK,
    `MaterialTheme.colorScheme.error` utk FAIL). Fallback ke `Text()` polos kalau baris tak cocok
    pola (baris lama/format tak dikenal tetap tampil, tidak crash). Format baris dari
    `Prefs.record()` TIDAK diubah — `PrefsTest.kt` (v7) tetap valid tanpa modifikasi.
  - 0 file production logic lain disentuh (Prefs.kt, FstrimExecutor.kt, MainViewModel.kt,
    TrimWorker.kt, UpdateChecker.kt tidak disentuh). `versionName` dicek grep: cuma dipakai
    utk display (`MainActivity.kt` baris label versi), tidak dipakai logic compare update
    (itu pakai `versionCode`/`longVersionCode`, tidak disentuh) -> non-breaking confirmed.
- v8 VALIDASI: brace/paren balance OK (MainActivity.kt 82/82, 199/199; build.gradle.kts 13/13,
  36/36) + cross-check simbol (`LogLine`, `logLineRegex`, `successGreen`, `ghRunNumber`) semua
  resolve konsisten. BELUM pernah dicompile compiler sungguhan — sandbox tanpa Android
  SDK/Gradle/jaringan (sama seperti v7). `./gradlew assembleDebug` + `testDebugUnitTest` WAJIB
  dijalankan di CI/lokal sebelum diklaim hijau beneran.
- Docs: `PENDING_ROADMAP.md` — item D3 & E1 ditandai ✅ SELESAI (v8); `CHANGELOG.md` — entri v8
  ditambah (append-only, v7 sengaja tidak ada entri CHANGELOG krn dev-only/no user-facing change,
  konsisten pola v1-v6 yang cuma catat perubahan user-facing).
- Batch: v8

[RESUME POINT: v8 selesai (D3 versionName dinamis + E1 indikator OK/FAIL Riwayat), validasi statis
(brace+referensi simbol) only, BELUM pernah dijalankan compiler sungguhan (sandbox tanpa
SDK/Gradle/jaringan) -> Remaining: jalankan DAILY UPDATE, push, CI build jalan seperti biasa
(assembleRelease/Debug) — v7 unit test JUGA belum pernah tervalidasi run CI sungguhan (masih
menumpuk dari batch sebelumnya, C3 belum diminta jadi build.yml belum ada step test) -> Next
Action: push ke main (assembleDebug/Release + testDebugUnitTest lokal disarankan dulu kalau
Termux ada JDK+SDK; kalau tidak, cukup andalkan CI), verifikasi visual Riwayat OK/FAIL & label versi
`v1.0.<n>` di device asli setelah install build baru — PENDING_ROADMAP.md sisa: B (butuh evidence
real dulu), C3/D1/D2/E2/E3/F1/F2 (backlog, tunggu user eksplisit minta)]

- v9 (D1+E2 dari PENDING_ROADMAP.md, atas pilihan eksplisit user): 1 file source diubah —
  - `MainActivity.kt` (E2): dialog baru "Tentang" (`AboutDialog`) konsolidasi info app (tagline,
    versi, package id, tombol source/developer) — dipicu row baru `AboutRow` ("›", beda dari
    `LinkRow` "↗" krn buka dialog in-app bukan browser) di card "Tautan". `versionName` (sudah ada
    sejak v2) dipindah dari bawah `HomeScreen` ke atas biar dipakai bareng label footer lama +
    dialog baru — nilai & tampilan label footer TIDAK berubah (murni hoist, no-op secara output).
  - `README.md` (D1, docs-only): tambah bagian "Catatan teknis — reflection Shizuku" — dokumentasi
    risiko `FstrimExecutor.sh()` reflect method private `Shizuku.newProcess` (bisa gagal diam-diam
    kalau versi Shizuku naik & signature berubah) + kewajiban re-verifikasi manual tiap upgrade.
    Sesuai keputusan item D1 sendiri di roadmap ("bukan refactor sekarang") — 0 source diubah utk
    poin ini, FstrimExecutor.kt TIDAK disentuh.
  - 0 file production logic lain (Prefs.kt, FstrimExecutor.kt, MainViewModel.kt, TrimWorker.kt,
    UpdateChecker.kt, build.gradle.kts) disentuh. Tautan/footer/nav lama tidak dihapus/diubah.
- v9 VALIDASI: brace/paren balance MainActivity.kt 97/97, 238/238 (before edit juga balance,
  no drift) + cross-check simbol (`AboutRow`, `AboutDialog`, `showAbout`, `versionName` dipakai
  konsisten, tiap definisi dipanggil persis sekali). `Modifier.weight()` di `AboutDialog` resolve
  sama seperti `LinkRow`/`ToggleRow` existing (member RowScope, tanpa import tambahan) — tidak ada
  import baru ditambah. BELUM pernah dicompile compiler sungguhan — sandbox tanpa Android
  SDK/Gradle/jaringan (sama seperti v7/v8). `./gradlew assembleDebug` WAJIB dijalankan di CI/lokal
  sebelum diklaim hijau beneran; verifikasi visual dialog "Tentang" (buka/tutup, tombol source)
  butuh device/emulator asli — belum ada evidence device utk v9.
- Docs: `PENDING_ROADMAP.md` — item D.1 & E.2 ditandai ✅ SELESAI (v9); `CHANGELOG.md` — entri v9
  ditambah (append-only, cuma catat E2 yg user-facing; D1 docs-only/dev-only tidak dientri,
  konsisten pola v7).
- Batch: v9

[RESUME POINT: v9 selesai (D1 catatan risiko reflection Shizuku di README + E2 dialog "Tentang"
konsolidasi info app di MainActivity.kt), validasi statis (brace+referensi simbol) only, BELUM
pernah dijalankan compiler sungguhan (sandbox tanpa SDK/Gradle/jaringan) -> Remaining: jalankan
DAILY UPDATE, push, CI build jalan seperti biasa (assembleRelease/Debug) — v7 unit test (C1) &
v9 dialog "Tentang" (E2) SAMA-SAMA belum pernah tervalidasi run/tampil nyata (CI belum ada step
test/C3; dialog belum ada evidence device) -> Next Action: push ke main, lalu verifikasi visual di
device asli setelah install build baru: buka card Tautan -> tombol "Tentang aplikasi" -> cek
dialog tampil benar (tagline+versi+package+tombol source) & bisa ditutup; PENDING_ROADMAP.md sisa:
B (butuh evidence real dulu), C3/D2/E3/F1/F2 (backlog, tunggu user eksplisit minta)]

- v9 VERIFIED (evidence: screen recording user, ~9 detik, device asli, ditonton frame-by-frame):
  (1) card "Tautan" tampil row baru "Tentang aplikasi" dgn chevron "›" (bukan "↗" spt link lain) —
  sesuai desain `AboutRow`.
  (2) tap row -> dialog "Tentang LagFix" muncul persis sesuai `AboutDialog`: tagline "Pemicu &
  penjadwal fstrim non-root (setara mFSTRIM), via Shizuku.", "Versi: 1.0.11", "Paket:
  com.lagfix.fstrim", tombol "Source & developer" + tombol "Tutup" — build CI nyata (versionName
  format run-number, bukan "1.0.0-dev" lokal) sudah terinstal di device, D3(v8) ikut re-konfirmasi.
  (3) tap "Source & developer" -> browser device kebuka ke `github.com/FDzaki-dev/LagFix` (repo
  benar, bukan salah tautan) -> listing repo nyata: 9 tags, file `app/`, `README.md`,
  `CHANGELOG.md`, `PENDING_ROADMAP.md`, `PROJECT_STATE.md` berstatus "2 minutes ago" (persis 5 file
  yg diubah batch v9), sisanya (`.github/workflows`, `.cursorrules`, `.gitignore`,
  `build.gradle.kts`) tetap "33 minutes ago" (tidak tersentuh) — DAILY UPDATE + push v9 CONFIRMED
  jalan bersih, tanpa file nyasar/tanpa regresi scope.
  Catatan minor (non-blocking, di luar scope verifikasi ini): versionName run_number=11 vs repo
  cuma nunjuk 9 tags — kemungkinan ada run yg skip pembuatan release tag di antaranya; belum
  ditelusuri, tidak mempengaruhi apapun yg diverifikasi barusan.
  Belum kelihatan on-camera: tap "Tutup" (dismiss dialog) — tidak blocking, `onDismiss` sama
  persis pola `showChangelog` (v4) yg sudah lama terbukti jalan.

[RESUME POINT: v9 FULLY VERIFIED — dialog "Tentang" (E2) tampil & isi benar di device asli, tautan
"Source & developer" benar, push v9 ke GitHub CONFIRMED bersih (persis 5 file berubah, 0 file
nyasar) -> Remaining: tidak ada lagi item blocking di batch v9; D1 (README note) tak butuh
verifikasi device (docs-only). Backlog PENDING_ROADMAP.md: B (robustness, butuh evidence masalah
nyata dulu), C3 (step test di CI), D2 (aktifkan R8 + keep-rule spesifik), E3 (toggle dark/light
manual), F1 (lint/detekt), F2 (Dependabot) — semua nunggu user eksplisit pilih -> Next Action:
tanya user mau lanjut item mana, atau ada task lain di luar roadmap.]

- v10 (di luar urutan PENDING_ROADMAP.md — permintaan ad-hoc eksplisit user, bukan dari daftar
  pilihan yg ditawarkan/C3-D2-E3-F1-F2): tab "Pengaturan" baru + tema kustom. 3 file source diubah:
  - `MainActivity.kt`: `HomeScreen` sekarang punya `Scaffold.bottomBar` (`NavigationBar` 2 tab,
    tanpa dependensi icon-pack baru — pakai emoji "🏠"/"⚙️" spt gaya app ini yg sudah pakai
    karakter polos "●"/"›"/"↗"). Konten lama dipecah jadi 2 composable baru tanpa ubah
    logic/callback sama sekali (murni pindah lokasi): `MainTab` (StatusCard, tombol jalankan,
    Riwayat, Tautan+Tentang, Pembaruan, label versi — SEMUA fitur utama tetap di sini) dan
    `SettingsTab` (Jadwal otomatis+interval+charging/idle — 4 kontrol yg dulu nebeng di layar utama
    tanpa judul, sekarang di tab tersendiri + BARU: picker tema "Ikuti sistem/Terang/Gelap").
    Palet warna lama (`dynamicDarkColorScheme`/`dynamicLightColorScheme` Material You + fallback
    `darkColorScheme()`/`lightColorScheme()` kosong) DIGANTI TOTAL palet kustom "calm"
    terinspirasi Cupertino/iOS (`calmLightScheme`/`calmDarkScheme`) — dark mode SENGAJA navy-charcoal
    lembut (#1C1E27) bukan hitam pekat, sesuai permintaan eksplisit user "bukan dark statis yang
    bikin lelah mata". Tambah `calmShapes` (corner radius M3 default dinaikkan ~+4dp) utk kesan
    kartu lebih membulat ala Cupertino — visual-only, tidak ubah struktur Card/Column apapun.
    LINGKUP "Cupertino style" DIBATASI ke palet warna + corner radius (bukan rebuild komponen jadi
    segmented-control/SF-style penuh) — keputusan scoping, di luar itu blm dikerjakan.
  - `Prefs.kt`: `enum class ThemeMode {SYSTEM,LIGHT,DARK}` (baru) + persist `themeMode` (SharedPreferences
    String, default SYSTEM = perilaku lama, non-breaking utk user existing/upgrade). `record()`/`log`/
    field lain TIDAK disentuh (PrefsTest.kt v7 tetap valid tanpa modifikasi).
  - `MainViewModel.kt`: `UiState.themeMode` (baru, default SYSTEM) dibaca fresh tiap `read()` sama
    spt field prefs lain (TIDAK butuh copy-preservation khusus spt `updateChecking`/dst, krn bukan
    state transient in-memory) + `setThemeMode()` baru (langsung update `ui`, TIDAK panggil
    `reschedule()`/Scheduler krn tema tak berhubungan dgn WorkManager — beda dgn `setEnabled`/dst).
  - 0 file production lain (FstrimExecutor.kt, TrimWorker.kt, UpdateChecker.kt, CrashLogger.kt,
    AppLinks.kt, LagFixApp.kt, AndroidManifest.xml, build.gradle.kts) disentuh. Tidak ada dependensi
    baru ditambah (NavigationBar/Shapes/RoundedCornerShape semua sudah tersedia dari
    androidx.compose.material3/foundation yg sudah ada).
  - Efek samping (di luar scope diminta tapi relevan dicatat): PENDING_ROADMAP.md E.3 ("tidak ada
    toggle dark/light manual") jadi TERTUTUP oleh fitur ini (malah lebih lengkap — ada picker
    3-arah + palet kustom, bukan cuma toggle 2-arah).
- v10 VALIDASI: brace/paren balance MainActivity.kt 117/117 & 318/318, Prefs.kt 11/11 & 63/63,
  MainViewModel.kt 26/26 & 81/81 — semua balance, no drift. Cross-check simbol: `ThemeMode`/
  `themeMode`/`MainTab`/`SettingsTab`/`calmLightScheme`/`calmDarkScheme`/`calmShapes` dipakai
  konsisten lintas 3 file, tiap definisi dipanggil sesuai jumlah yg diharapkan (MainTab 1x,
  SettingsTab 1x). Grep konfirmasi tidak ada file lain (TrimWorker.kt dst) yg referensi composable
  yg dipindah/diubah — perubahan terisolasi ke 3 file yg disebut. BELUM pernah dicompile compiler
  sungguhan — sandbox tanpa Android SDK/Gradle/jaringan (sama spt semua batch sebelumnya).
  `./gradlew assembleDebug` WAJIB dijalankan sebelum diklaim hijau beneran. BELUM diverifikasi
  visual sama sekali di device asli — ini restrukturisasi UI TERBESAR sejak v1 (nav-bar 2 tab +
  ganti total color scheme), risiko regresi visual/tata-letak lebih tinggi drpd batch2 sebelumnya
  meski logic/callback per kontrol individual tidak diubah. Perlu extra-hati2 cek: (1) NavigationBar
  tidak ketutup gesture-bar sistem (enableEdgeToEdge aktif — M3 NavigationBar seharusnya auto-handle
  insets, tapi belum dibuktikan di device asli), (2) kontras warna calmDarkScheme/calmLightScheme
  scr real (terutama teks di atas Card/Surface), (3) FilterChip tema & interval tetap kebaca jelas
  di kedua skema warna baru.
- Docs: `PENDING_ROADMAP.md` — E.3 ditandai ✅ SELESAI (v10, di luar urutan, superseded oleh fitur
  tab Pengaturan+tema); `CHANGELOG.md` — entri v10 ditambah (user-facing, full).
- Batch: v10

[RESUME POINT: v10 (tab "Pengaturan" + tema calm Cupertino-style) RE-AUDIT independen sesi ini —
0 file diubah (ZIP yang diberikan user SUDAH berisi implementasi v10 lengkap; dikonfirmasi ulang
dari nol, bukan cuma percaya klaim dokumen ini): brace/paren balance ke-9 file Kotlin semua match
(MainActivity 117/117+318/318, Prefs 11/11+63/63, MainViewModel 26/26+81/81, dst), cross-check
simbol ThemeMode/SettingsTab/MainTab/calmLightScheme/calmDarkScheme/calmShapes/setThemeMode semua
konsisten & dipanggil sesuai desain, compose-bom 2024.10.01 kompatibel (Shapes ctor/NavigationBar/
NavigationBarItem/FilterChip semua tersedia di versi ini), themes.xml native (Theme.LagFix, splash
pre-Compose) tidak disentuh & tidak konflik dgn color scheme Compose baru. Status TETAP: belum
pernah dicompile compiler sungguhan & belum ada evidence visual device sama sekali (sandbox tanpa
SDK/Gradle/jaringan) -> Remaining: jalankan DAILY UPDATE di Termux, push ke main, biarkan CI build
(assembleRelease/Debug) jalan -> Next Action: install APK hasil CI di device asli, verifikasi
visual: (1) tab bar bawah muncul & tak ketutup gesture-bar, (2) tab "Utama" = konten lama
(StatusCard/tombol jalankan/Riwayat/Tautan/Tentang/Pembaruan/versi) minus 4 kontrol jadwal, (3) tab
"Pengaturan" = 4 kontrol jadwal (fungsi sama persis) + card Tema baru, (4) ganti 3 pilihan tema
tanpa crash & dark mode terasa calm/navy-charcoal (bukan hitam pekat) sesuai keluhan awal user;
rekam evidence (video/screenshot) spt pola v6/v9 sebelumnya. PENDING_ROADMAP.md sisa: B, C3, D2,
F1, F2 (backlog, tunggu user eksplisit minta)]
