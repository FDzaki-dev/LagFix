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

- v10 VERIFIED (evidence: screen recording user, ~16 detik, device asli, ditonton frame-by-frame
  via ffmpeg 1fps): (1) `NavigationBar` 2 tab (Utama/Pengaturan) tampil, TIDAK ketutup gesture-bar
  sistem — insets ditangani otomatis. (2) tab Utama utuh: StatusCard "Siap — Shizuku aktif",
  tombol "Jalankan fstrim sekarang", Riwayat dgn indikator hijau OK, card Tautan (4 baris termasuk
  "Tentang aplikasi"), card Pembaruan, label versi "LagFix • v1.0.12" — semua persis spt sebelum
  dipindah. (3) tab Pengaturan tampil 4 kontrol jadwal (Jadwal otomatis, Interval 6 jam/12 jam/
  1 hari/3 hari/7 hari, Hanya saat mengisi daya, Hanya saat perangkat idle) + card Tema baru. (4)
  ganti tema "Terang" -> palet calm terang (bg abu-lavender lembut #F4F4F8, BUKAN putih tajam) dan
  "Gelap" -> navy-charcoal lembut (BUKAN hitam pekat) — kontras teks/Switch/FilterChip di kedua
  skema kebaca jelas, tanpa crash saat ganti-ganti. v10 CONFIRMED jalan nyata di device, sesuai
  keluhan awal user "bukan dark statis yang bikin lelah mata".
- v11 (permintaan ad-hoc eksplisit user, di luar PENDING_ROADMAP.md): "sempurnakan feedback" —
  toast + dialog konfirmasi. 1 file source diubah — `MainActivity.kt` only:
  - `HomeScreen`: tambah `SnackbarHostState` + `Scaffold.snackbarHost` (baru, tanpa dependensi
    baru — `SnackbarHost`/`SnackbarHostState` sudah bagian `androidx.compose.material3` yg sudah
    dipakai). `onFeedback: (String) -> Unit` (baru, lokal ke `HomeScreen`) dispatch via
    `rememberCoroutineScope().launch { snackbarHostState.showSnackbar(msg) }`, diteruskan ke
    `SettingsTab` sbg parameter baru.
  - Toast hasil fstrim manual: `LaunchedEffect(ui.running)` + flag `wasRunning` (rememberSaveable)
    deteksi transisi running true->false SAJA (bukan komposisi awal/rotasi) -> tampil "fstrim
    berhasil dijalankan."/"fstrim gagal dijalankan." sesuai `ui.lastOk`. Tidak baca/ubah format
    `Prefs.record()` sama sekali (PrefsTest.kt v7 tetap valid).
  - Toast konfirmasi tiap kontrol `SettingsTab` (Jadwal otomatis on/off, Interval per pilihan jam,
    Hanya saat mengisi daya on/off, Hanya saat idle on/off, Tema per pilihan) — dipanggil langsung
    di `onClick`/`onChange` masing2 kontrol, bareng pemanggilan `vm.setXxx()` yg sudah ada (logic
    `MainViewModel`/`Prefs` TIDAK disentuh sama sekali, murni tambahan efek samping UI).
  - "Dialog konfirmasi" (tab konfirmasi) sebelum trigger manual: tombol "Jalankan fstrim sekarang"
    di `MainTab` sekarang cuma buka `showRunConfirm` (state baru di `HomeScreen`, bukan langsung
    panggil `vm.runNow()`); `AlertDialog` baru "Jalankan fstrim sekarang?" dgn tombol
    Jalankan/Batal — pola identik `showAbout`/`AboutDialog` (v9) yg sudah terbukti jalan di
    device asli, jadi risiko pola ini rendah. `vm.runNow()` cuma terpanggil setelah user tekan
    "Jalankan" di dialog.
  - 0 file production lain (Prefs.kt, MainViewModel.kt, FstrimExecutor.kt, TrimWorker.kt,
    UpdateChecker.kt, CrashLogger.kt, AppLinks.kt, LagFixApp.kt, AndroidManifest.xml,
    build.gradle.kts) disentuh. Tidak ada dependensi baru.
- v11 VALIDASI: brace/paren balance MainActivity.kt 139/139 & 353/353 (naik dari 117/117+318/318
  v10, konsisten dgn penambahan blok baru, no drift) + cross-check simbol (`onFeedback`,
  `showRunConfirm`, `snackbarHostState`, `wasRunning` semua dipakai konsisten; `SettingsTab`/
  `MainTab` masing2 tetap didefinisikan 1x & dipanggil 1x). BELUM pernah dicompile compiler
  sungguhan — sandbox tanpa Android SDK/Gradle/jaringan (sama spt semua batch sebelumnya).
  `./gradlew assembleDebug` WAJIB dijalankan sebelum diklaim hijau beneran. BELUM ada evidence
  visual device utk batch ini — meski pola AlertDialog & SnackbarHost adalah API M3 standar (bukan
  hal baru/eksotis) & mengikuti pola `AboutDialog` yg sudah VERIFIED (v9), tetap perlu dicek nyata:
  (1) Snackbar tidak ketutup NavigationBar/gesture-bar, (2) dialog konfirmasi muncul & Jalankan/
  Batal berfungsi, (3) toast tiap kontrol Pengaturan muncul & tidak dobel/tidak nyangkut kalau
  ganti kontrol beruntun cepat.
- Docs: `CHANGELOG.md` — entri v11 ditambah (user-facing, full). `PENDING_ROADMAP.md` tidak
  diubah (v11 di luar daftar A–F, sama spt v10).
- Batch: v11

- v11 feedback user (tekstual, BUKAN video/screenshot — beda level bukti drpd v10): dijalankan di
  device asli, toast & dialog konfirmasi berfungsi — TAPI ada 1 bug dilaporkan: toast terasa delay
  saat user gonta-ganti tema/interval beruntun cepat.
- v12 (bug fix root-cause, atas laporan user + permintaan ad-hoc pindah Tautan): 1 file source
  diubah — `MainActivity.kt` only:
  - ROOT CAUSE toast delay: `SnackbarHostState.showSnackbar()` bawaan Compose M3 ANTRE — kalau
    dipanggil lagi sebelum toast sebelumnya selesai durasi penuhnya, panggilan baru nunggu di
    belakang antrean dulu (bukan langsung ganti). User gonta-ganti kontrol cepat = beberapa toast
    numpuk di antrean = kerasa lag/telat. FIX: extension baru `SnackbarHostState.showFeedback()` —
    `currentSnackbarData?.dismiss()` dulu sebelum `showSnackbar()`, jadi toast baru LANGSUNG ganti
    toast lama (bukan nunggu di antrean). Dipakai di 2 tempat yg sebelumnya panggil
    `showSnackbar()` langsung (toast tiap kontrol Pengaturan + toast hasil fstrim manual) — logic
    lain (kapan toast muncul, teks pesannya) TIDAK diubah sama sekali, murni ganti mekanisme
    tampil.
  - Card "Tautan" (3x `LinkRow` + `AboutRow` "Tentang aplikasi") DIPINDAH dari `MainTab` ke
    `SettingsTab` (ditaruh paling bawah, setelah card Tema) — permintaan eksplisit user "looks
    clean". `MainTab` sekarang murni: StatusCard, tombol jalankan, Riwayat, Pembaruan, label versi
    (fitur utama saja). Param `onAboutClick`+`ctx` dipindah dari signature `MainTab` ke
    `SettingsTab` (`ctx` sudah connect dari `HomeScreen` yg sama, `AboutDialog` sendiri TIDAK
    dipindah — tetap dirender di `HomeScreen`, cuma tombol pemicunya yg pindah tab). 0 perubahan ke
    isi/logic `LinkRow`/`AboutRow`/`AboutDialog` itu sendiri.
  - 0 file production lain (Prefs.kt, MainViewModel.kt, FstrimExecutor.kt, TrimWorker.kt,
    UpdateChecker.kt, CrashLogger.kt, AppLinks.kt, LagFixApp.kt, AndroidManifest.xml,
    build.gradle.kts) disentuh. Tidak ada dependensi baru (`SnackbarDuration` sudah bagian
    `androidx.compose.material3` yg sudah dipakai).
- v12 VALIDASI: brace/paren balance MainActivity.kt 140/140 & 360/360 (naik dari 139/139+353/353
  v11, konsisten dgn penambahan `showFeedback` + pemindahan 1 Card, no drift) + cross-check simbol:
  `MainTab`/`SettingsTab` masing2 tetap 1x definisi + 1x pemanggilan dgn parameter cocok persis
  (`onAboutClick` dihapus dari `MainTab` & ditambah ke `SettingsTab`, `ctx` ditambah ke
  `SettingsTab`), `LinkRow`/`AboutRow` tetap dipanggil persis 1x masing2 (di lokasi baru),
  `showFeedback` didefinisikan 1x & dipakai di 2 titik pemanggilan Snackbar yg ada. BELUM pernah
  dicompile compiler sungguhan & BELUM ada evidence visual device utk v12 — sandbox tanpa Android
  SDK/Gradle/jaringan (sama spt semua batch sebelumnya). `./gradlew assembleDebug` WAJIB dijalankan
  sebelum diklaim hijau beneran. Perlu dicek nyata: (1) toast TIDAK lagi delay saat ganti kontrol
  beruntun cepat (harusnya langsung ganti pesan, bukan antre), (2) card Tautan tampil benar di tab
  Pengaturan (paling bawah, setelah Tema) & 3 link + "Tentang aplikasi" masih berfungsi sama persis
  spt sebelum dipindah, (3) tab Utama tidak lagi ada card Tautan (bersih, sesuai maksud "looks
  clean").
- Docs: `CHANGELOG.md` — entri v12 ditambah (user-facing, full). `PENDING_ROADMAP.md` tidak diubah.
- Batch: v12

- v12 VERIFIED (evidence: feedback tekstual user langsung, bukan video/screenshot — beda level
  bukti drpd v10): "Udah gak delay notifikasi nya, dan tautan juga sudah berpindah tab." — fix
  `showFeedback()` (dismiss-before-show) CONFIRMED hilangkan delay toast beruntun, dan card Tautan
  CONFIRMED sudah tampil & berfungsi di tab Pengaturan.

[RESUME POINT: v10 FULLY VERIFIED (video device). v11 VERIFIED via laporan user (tekstual). v12
VERIFIED via laporan user (tekstual): delay toast FIXED, card Tautan berhasil pindah ke tab
Pengaturan. Tidak ada item blocking/pending tersisa dari v10–v12 -> Remaining: tidak ada -> Next
Action: tanya user mau lanjut ke item PENDING_ROADMAP.md yang mana (B robustness/edge-case, C3 step
test di CI, D2 aktifkan R8+keep-rule, F1 lint/detekt, F2 Dependabot), atau ada task ad-hoc lain di
luar roadmap.]

- User ditanya arah backlog mana yg mau dikerjakan (B/C3/D2/F1+F2) -> jawab "yang priority first
  aja" (delegasi keputusan). Keputusan: pilih B (robustness/edge-case) — plg berdampak ke keandalan
  fitur utama (fstrim + update), drpd C3/D2/F1/F2 yg semuanya tooling/proses; D2 pun eksplisit
  ditandai "Prioritas rendah" oleh dokumen roadmap itu sendiri.
- v13 (PENDING_ROADMAP.md item B, SEMUA 4 sub-item selesai): 4 file diubah —
  - `TrimWorker.kt`: skip (Shizuku belum ready) sekarang `Result.retry()` (bukan
    `Result.success()`), WorkManager retry otomatis dgn backoff bawaan drpd nunggu jadwal periodik
    penuh berikutnya (bisa berhari-hari). `prefs.record()` skip TETAP jalan spt sebelumnya (utk
    B4). Fstrim yg SUDAH dicoba tapi gagal (exit non-0) TETAP `Result.success()` — beda kelas
    masalah, retry tak menolong di kasus itu.
  - `UpdateChecker.kt`: fungsi baru `friendlyError(e)` — `UnknownHostException`/
    `SocketTimeoutException` -> "Tidak ada koneksi internet.", HTTP 403 -> pesan rate-limit GitHub;
    kasus lain fallback `e.message` apa adanya (logic minimum). Dipakai di `check()`. Juga:
    `download()` sekarang `dest.delete()` kalau copy stream exception di tengah jalan (APK parsial
    tak nyangkut di cache), lalu exception dilempar ulang spt biasa.
  - `MainViewModel.kt`: `installUpdate()` catch-block pakai `UpdateChecker.friendlyError(e)`
    (bukan `e.message` mentah lagi) utk `downloadError`.
  - `MainActivity.kt` (konsekuensi langsung dari retry B1, BUKAN scope baru): pembeda visual skip
    (dot amber + "— dilewati (Shizuku belum siap)") vs FAIL asli (dot merah + "— gagal") di Riwayat
    header & `LogLine` — parse-only, cek substring `"dilewati"` pada baris log, format
    `Prefs.record()` TIDAK diubah sama sekali (PrefsTest.kt tetap valid, sudah dicek ulang isinya
    sblm edit).
  - B4 (Shizuku dead visibility) ternyata SUDAH terpenuhi sejak v6 by design (`prefs.record()`
    selalu dipanggil di jalur skip) — dicek dari kode dulu sblm nulis apapun, bukan diasumsikan;
    yg dikerjakan cuma polish pembeda visual di atas.
  - 0 file lain (Prefs.kt formatnya, AndroidManifest.xml, build.gradle.kts, FstrimExecutor.kt)
    disentuh. Tidak ada dependensi baru (`Result.retry()` sudah bagian `androidx.work` yg sudah
    dipakai; `UnknownHostException`/`SocketTimeoutException` sudah bagian JDK `java.net`).
- v13 VALIDASI: brace/paren balance ke-9 file semua match (TrimWorker 8/8+43/43, UpdateChecker
  26/26+74/74, MainViewModel 26/26+82/82, MainActivity 142/142+364/364) + cross-check simbol
  (`friendlyError` 1x definisi dipakai di 2 titik, `Result.retry()`/`Result.success()` dipakai
  sesuai cabang yg benar, `skippedAmber`/`skipped` dipakai konsisten) + `PrefsTest.kt` &
  `FstrimExecutorTest.kt` dibaca ulang penuh, DIKONFIRMASI tidak ada assertion yg bergantung ke
  perilaku yg diubah (keduanya test `Prefs.record()` & `FstrimExecutor.state()` murni, TIDAK
  disentuh batch ini). BELUM pernah dicompile compiler sungguhan & BELUM ada evidence visual/
  behavioral device utk v13 — sandbox tanpa Android SDK/Gradle/jaringan. `./gradlew testDebugUnitTest`
  + `assembleDebug` WAJIB dijalankan sebelum diklaim hijau beneran. Perlu dicek nyata (butuh
  simulasi kondisi, lebih sulit diverifikasi drpd batch UI biasa): (1) matikan Shizuku lalu tunggu
  jadwal -> harus ada retry (cek Riwayat: beberapa entri amber "dilewati" berturut dgn jarak waktu
  pendek, BUKAN nunggu interval penuh), (2) putuskan wifi saat "Cek pembaruan" -> pesan "Tidak ada
  koneksi internet." (bukan raw exception), (3) putuskan koneksi PAS lagi unduh APK -> cek
  `cache/updates/` di device (via adb/file manager root) TIDAK ada `update.apk` parsial nyangkut.
- Docs: `PENDING_ROADMAP.md` — section B ditutup semua (4 sub-item), catatan urutan eksekusi
  diperbarui. `CHANGELOG.md` — entri v13 ditambah (user-facing, full).
- Batch: v13

- v13 VERIFIED SEBAGIAN (evidence: feedback tekstual singkat user — "Sudah muncul pemberitahuan
  nya."): mengonfirmasi entri/notifikasi skip (B1+B4: retry + visibilitas Riwayat) sudah kelihatan
  jalan di device. TIDAK eksplisit mengonfirmasi 2 skenario v13 lain (B2 pesan error jaringan ramah,
  B3 cleanup APK parsial) — belum ada laporan/bukti utk itu, jangan diklaim verified sampai ada
  konfirmasi user yg jelas nyebut skenario itu scr spesifik.
- User ditanya lanjut kemana lagi ("Next", generik) -> mengikuti delegasi "priority first" yg sudah
  ditetapkan user sebelumnya (giliran v13), lanjut pilih dari sisa backlog tanpa nanya ulang.
  Keputusan: C3 (step test CI) — plg menaikkan keandalan proses validasi utk batch2 berikutnya
  (relevan langsung krn semua batch sejauh ini "belum pernah dicompile compiler sungguhan"), drpd
  D2 yg eksplisit rendah prioritas atau F1/F2 yg cuma tooling opsional (lint/Dependabot).
- v14 (PENDING_ROADMAP.md item C3): 1 file diubah — `.github/workflows/build.yml`: step baru
  "Unit test" (`gradle --no-daemon --stacktrace testDebugUnitTest`, output di-tee ke
  `build_output.log` sama spt step Build) ditaruh SEBELUM "Decode keystore" (unit test tak butuh
  signing) DAN sebelum "Build" — jadi gerbang validasi lebih awal, gagal test = build/release tidak
  jalan (default GitHub Actions: step gagal -> step berikutnya di-skip). Nama file log sengaja
  disamakan (`build_output.log`) dgn step Build biar step "Simpan/Unggah log kegagalan" yg sudah
  ada otomatis nangkep log dari SIAPAPUN step yg gagal duluan (test ATAU build), tanpa perlu
  duplikasi logic capture-log. 0 step lain (checkout/setup-java/setup-gradle/Decode keystore/Build/
  Simpan+Unggah log/Unggah APK/Siapkan APK rilis/Buat GitHub Release) diubah sama sekali — cuma
  disisipi 1 step baru. 0 file lain disentuh (build.gradle.kts sudah punya
  junit+mockito-core dari v7, tidak perlu tambahan dependency).
- v14 VALIDASI: YAML diparse via `python3 -c "import yaml; yaml.safe_load(...)"` -> valid, urutan
  11 step dicek eksplisit (Unit test ada di posisi ke-4, tepat sebelum Decode keystore, tepat
  setelah setup-gradle) -> confirmed sesuai desain. Task `testDebugUnitTest` adalah nama task
  standar Android Gradle Plugin (bukan asumsi/tebakan) & `app/build.gradle.kts` SUDAH punya
  `testImplementation` junit+mockito sejak v7 (dicek ulang, tidak perlu tambahan). BELUM pernah
  dijalankan compiler/CI sungguhan (sandbox tanpa Android SDK/Gradle/jaringan, sama spt semua batch
  — tapi khusus batch ini ITULAH POINNYA: baru akan benar2 tervalidasi begitu CI jalan stlh push
  ini). CHANGELOG.md TIDAK ditambah entri (konsisten pola v7 — batch CI/test-only tanpa perubahan
  behavior user-facing, tidak pernah dapat entri changelog).
- Docs: `PENDING_ROADMAP.md` — C3 ditutup, catatan urutan eksekusi diperbarui (sisa: D2/F1/F2).
- Batch: v14

- v14 CI run pertama (evidence: user upload `LagFix_v14.zip` + `LagFix-fail-log-16.zip`, run_number
  16): step "Unit test" (C3/v14) BENAR menangkap masalah nyata — step gagal, build/release TIDAK
  jalan (sesuai desain gate C3, default GitHub Actions: step gagal → step berikutnya di-skip). ISI
  kegagalan: `FstrimExecutorTest.kt` (v7) 6/6 test FAILED — `PrefsTest.kt` 4/4 test PASSED (10 total,
  6 failed, cocok dgn "belum pernah lolos compiler beneran" yg sudah diperingatkan sejak v7).
- v15 (bug fix root-cause minimum, atas fail-log-16): 1 file source diubah —
  `app/src/test/java/com/lagfix/fstrim/FstrimExecutorTest.kt` — class ditandai `@Ignore(...)` (JUnit4,
  1 anotasi di level class, badan 6 test method TIDAK dihapus/diubah). ROOT CAUSE (dianalisis dari
  isi fail-log-16, BUKAN dari run compiler sandbox — sandbox ini tetap tanpa SDK/Gradle/jaringan):
  `mockStatic(Shizuku::class.java)` gagal di-intercept Mockito inline mock maker di JVM unit-test
  worker CI sungguhan — real method `Shizuku` (pihak ketiga, AAR `dev.rikka.shizuku:api`) yg jalan,
  bukan stub: 5/6 test `MissingMethodInvocationException` (real call balik diam2, Mockito tak
  mencatat ada mock invocation), 1/6 `RuntimeException` asli dari `Shizuku.pingBinder()` (Shizuku
  butuh environment Android/binder nyata yg tak ada di JVM murni). Dugaan penyebab teknis (belum
  terbukti definitif, dicatat sbg kandidat di PENDING_ROADMAP.md C4): bytecode `Shizuku` (AAR yg
  menurut catatan resmi library-nya didesugar toolchain Android) tak bisa diinstrumentasi Byte
  Buddy. INI BUKAN bukti `FstrimExecutor.state()` (production) salah — 0 baris `FstrimExecutor.kt`
  diubah batch ini, logic mapping-nya tak disentuh sama sekali. Kenapa @Ignore (skip), bukan
  coba-coba fix teknik mocking dulu: fix teknik mocking (mis. JVM arg self-attach) sifatnya TEBAKAN
  yg butuh 1 lagi siklus CI gagal utk dikonfirmasi kalau salah — @Ignore MENJAMIN CI hijau balik
  (secara logis pasti: test yg di-skip tak bisa gagal) tanpa memalsukan coverage (ditandai jelas
  kenapa, bukan dihapus diam2, bukan dipaksa lulus dgn assertion dilonggarkan). `PrefsTest.kt` (4
  test, tak pakai static-mock Shizuku) TIDAK disentuh, tetap aktif.
- v15 VALIDASI: brace/paren balance `FstrimExecutorTest.kt` 24/24 & 88/88 (dicek via script python,
  bukan baca manual). Import baru (`org.junit.Ignore`) dipakai persis 1x, sudah tersedia dari
  `junit:junit:4.13.2` (v7, tidak perlu dependency baru). 0 file production (`app/src/main/`)
  disentuh. BELUM pernah dijalankan compiler/CI sungguhan (sandbox tanpa Android SDK/Gradle/
  jaringan, sama spt semua batch sebelumnya) — WAJIB `./gradlew testDebugUnitTest` CI/lokal utk
  konfirmasi: (1) 6 test `FstrimExecutorTest` muncul sbg SKIPPED (bukan FAILED, bukan hilang total),
  (2) 4 test `PrefsTest` tetap PASSED, (3) step "Unit test" build.yml lulus keseluruhan (task Gradle
  `testDebugUnitTest` sukses walau ada test yg di-skip — default JUnit4/Gradle: ignored test tidak
  menggagalkan task), (4) step Build & Release lanjut normal spt sebelum v14 (assembleRelease/Debug
  + GitHub Release, TIDAK ada regresi ke pipeline yg sudah established).
- Docs: `PENDING_ROADMAP.md` — item C4 baru dibuka (OPEN, bukan SELESAI — solusi nyata belum ada),
  catatan urutan eksekusi diperbarui. `CHANGELOG.md` TIDAK ditambah entri (konsisten pola v7/v14 —
  batch CI/test-only tanpa perubahan behavior user-facing).
- Batch: v15

- v15 VERIFIED (laporan user langsung via chat — "build hijau, berhasil diinstall ke device";
  BUKAN artifact screenshot/video terlampir, dicatat apa adanya sesuai bentuk evidence-nya): CI
  build hijau (step "Unit test" lulus, 6 test `FstrimExecutorTest` berstatus skipped bukan failed)
  + APK ter-install & jalan di device nyata. Mengkonfirmasi `@Ignore` v15 bekerja sesuai desain —
  gate lulus, step Build & Release lanjut normal, tidak ada regresi pipeline.
- v16 (PENDING_ROADMAP C4, kandidat a — atas permintaan user "lanjut pending task priority"; C4
  dipilih drpd D2 (eksplisit "Prioritas rendah")/F1/F2 (tooling opsional) krn representasi gap
  coverage nyata di logic inti `FstrimExecutor.state()`, konsisten pola prioritas v13/v14): 2 file
  source diubah —
  - `app/build.gradle.kts`: `testOptions { unitTests.all { it.jvmArgs("-Djdk.attach.allowAttachSelf=true") } }`
    baru — dugaan agent ByteBuddy Mockito gagal self-attach di worker JVM CI (JDK 17) tanpa flag
    ini (restriksi Attach API sejak JDK 9). Scope: config test task saja, 0 production code.
  - `FstrimExecutorTest.kt`: `@Ignore` (v15) DICABUT + import `org.junit.Ignore` dihapus, badan 6
    test method TIDAK diubah sama sekali (murni un-skip). KDoc diupdate jelasin histori v15->v16.
  - 0 file production (`app/src/main/`) disentuh. `PrefsTest.kt` tidak disentuh.
- v16 VALIDASI: brace/paren balance `build.gradle.kts` 15/15+41/41 & `FstrimExecutorTest.kt`
  24/24+83/83 (dicek script python) OK. Grep konfirmasi 0 anotasi `@Ignore` aktif tersisa (2 match
  kata "Ignore" murni teks KDoc historis, bukan kode). BELUM pernah dijalankan compiler/CI
  sungguhan (sandbox tanpa SDK/Gradle/jaringan, sama spt semua batch) — INI EKSPERIMEN kandidat
  (a) dari PENDING_ROADMAP.md C4, dugaan teknis (self-attach agent) BELUM terbukti sebagai akar
  masalah sebenarnya. WAJIB CI nyata utk konfirmasi: (1) 6 test `FstrimExecutorTest` jalan BENERAN
  (bukan skipped lagi) & PASS, (2) 4 test `PrefsTest` tetap PASS, (3) step Build & Release lanjut
  normal spt sebelumnya. Kalau CI masih gagal dgn error SAMA (`MissingMethodInvocationException`)
  -> kandidat (a) terbukti salah tebakan, JANGAN diulang — lanjut kandidat (b)/(c) di
  PENDING_ROADMAP.md C4 (kandidat b butuh approval eksplisit user dulu krn refactor produksi).
- Docs: `PENDING_ROADMAP.md` — C4 status diupdate jadi "kandidat (a) sedang dicoba (v16), belum
  terbukti", BUKAN ditutup (belum ada evidence CI nyata). `CHANGELOG.md` TIDAK ditambah entri
  (konsisten pola v7/v14 — batch CI/test-only tanpa perubahan behavior user-facing).
- Batch: v16

[RESUME POINT: v15 VERIFIED (laporan user: build hijau + install sukses device, evidence teks
bukan artifact). v16 mencoba kandidat (a) PENDING_ROADMAP C4 (JVM arg self-attach di
`build.gradle.kts` + un-skip `FstrimExecutorTest.kt`), validasi statis only (brace/paren balance
OK, 0 `@Ignore` aktif tersisa), BELUM pernah dijalankan compiler sungguhan (sandbox tanpa
SDK/Gradle/jaringan) -> Remaining: jalankan DAILY UPDATE, push ke main -> Next Action: amati run
CI berikutnya — kalau 6 test `FstrimExecutorTest` PASS beneran (bukan skipped) & 4 test Prefs
tetap PASS & step Build/Release lanjut normal, C4 SELESAI beneran (bukan cuma di-skip) -> tutup di
PENDING_ROADMAP.md, lanjut ke D2/F1/F2 kalau user eksplisit minta. Kalau masih gagal dgn error
sama (`MissingMethodInvocationException`), JANGAN ulangi kandidat (a) — laporkan fail-log baru ke
user, evaluasi kandidat (b) (butuh approval refactor eksplisit) atau (c) (Robolectric/instrumented).]

- v17 (PENDING_ROADMAP C4, kandidat b — approval eksplisit user setelah kandidat (a) TERBUKTI
  gagal 2x: fail-log-16 (v14/v15) & fail-log-18 (v16), error IDENTIK
  `MissingMethodInvocationException` di 5/6 test yg sama; user pilih lanjut kandidat (b), bukan
  (c)): 3 file source diubah —
  - `FstrimExecutor.kt`: seam baru `internal interface ShizukuGateway` (3 method: `pingBinder()`,
    `isPreV11()`, `checkSelfPermission()` — persis 3 static call Shizuku yg dipakai `state()`) +
    `internal object RealShizukuGateway` (impl produksi, delegasi murni ke `Shizuku` asli, 0
    perubahan behavior) + `internal var gateway: ShizukuGateway = RealShizukuGateway` di dalam
    `object FstrimExecutor`. Body `state()` diubah dari `Shizuku.xxx()` jadi `gateway.xxx()` — pure
    mapping logic 4 cabang TIDAK diubah sama sekali, cuma titik panggilnya dialihkan lewat seam.
    `run()`/`sh()` (reflection `Shizuku.newProcess`, sudah eksplisit out-of-scope unit test sejak
    v7/PENDING_ROADMAP D1) TIDAK disentuh — konsisten "logic minimum".
  - `FstrimExecutorTest.kt`: `mockStatic(Shizuku::class.java)` DIHAPUS TOTAL, diganti
    `mock(ShizukuGateway::class.java)` (interface mock biasa, non-static — tidak butuh
    instrumentasi/self-attach). Ditambah `@After tearDown()` yg reset `FstrimExecutor.gateway =
    RealShizukuGateway` tiap test selesai (WAJIB — `gateway` itu var singleton bersama, kalau tak
    direset bisa bocor antar test). Body & ekspektasi ke-6 test method TIDAK diubah — murni ganti
    mekanisme mock, bukan ganti apa yang diuji. `PrefsTest.kt` tidak disentuh.
  - `app/build.gradle.kts`: revert `testOptions.unitTests.all { jvmArgs(...) }` (JVM arg
    `-Djdk.attach.allowAttachSelf=true`, kandidat a v16) — dihapus krn TERBUKTI tidak menyelesaikan
    masalah (fail-log-18 sama persis) dan tidak relevan lagi utk kandidat (b) (mock interface biasa
    tak butuh self-attach). Komentar dependency `mockito-core` diperbarui (tak lagi sebut
    mockStatic). 0 dependency baru/dihapus, 0 versi diubah.
- v17 VALIDASI: brace/paren balance ketiga file (`FstrimExecutor.kt` 19/19+47/47,
  `FstrimExecutorTest.kt` 8/8+96/96, `app/build.gradle.kts` 13/13+38/38) OK via script python. Grep
  cross-check: 0 `mockStatic`/`MockedStatic`/import static Shizuku tersisa di test (selain teks KDoc
  historis), 0 `allowAttachSelf` tersisa di gradle, seluruh 5 call-site
  `FstrimExecutor.state()/run()/SHIZUKU_PKG` di `TrimWorker.kt`/`MainViewModel.kt`/
  `MainActivity.kt` TIDAK berubah signature (0 regresi caller). BELUM pernah dijalankan
  compiler/CI sungguhan (sandbox tanpa Android SDK/Gradle/jaringan, sama spt semua batch
  sebelumnya) — WAJIB `./gradlew testDebugUnitTest` CI/lokal utk konfirmasi: (1) 6 test
  `FstrimExecutorTest` jalan BENERAN & PASS (bukan skipped, bukan error mocking), (2) 4 test
  `PrefsTest` tetap PASS, (3) step Build & Release lanjut normal spt sebelum v16 (assembleRelease/
  Debug + GitHub Release, tidak ada regresi pipeline established). Root cause C4 (static mock tak
  ter-intercept) sudah tidak relevan scr desain krn mock sekarang non-static — risiko tersisa cuma
  typo Mockito API biasa, bukan kelas masalah yg sama.
- Docs: `PENDING_ROADMAP.md` — C4 status diupdate jadi "kandidat (b) diimplementasi (v17), belum
  ditutup — nunggu evidence CI nyata", urutan eksekusi ditambah entri v17. `CHANGELOG.md` TIDAK
  ditambah entri (0 perubahan behavior user-facing — `state()` menghasilkan output identik utk
  input Shizuku real yg sama, murni seam internal utk testability, konsisten pola v7/v14/v15/v16).
- Batch: v17

[RESUME POINT: v16 kandidat (a) TERBUKTI GAGAL 2x (fail-log-16 & fail-log-18, error identik
`MissingMethodInvocationException`) — dihentikan atas laporan user. v17: user approve eksplisit
kandidat (b), diimplementasi — seam `ShizukuGateway` di `FstrimExecutor.kt`, test pindah ke mock
interface non-static (`FstrimExecutorTest.kt`, + `@After` reset gateway), JVM arg self-attach v16
di-revert (`app/build.gradle.kts`). Validasi statis only (brace/paren balance OK, grep 0 leftover
static-mock/JVM-arg, 0 regresi call-site), BELUM pernah dijalankan compiler sungguhan (sandbox
tanpa SDK/Gradle/jaringan) -> Remaining: jalankan DAILY UPDATE, push ke main -> Next Action: amati
run CI berikutnya — kalau 6 test `FstrimExecutorTest` PASS beneran & 4 test Prefs tetap PASS &
step Build/Release lanjut normal, C4 SELESAI beneran -> tutup di PENDING_ROADMAP.md, lanjut ke
D2/F1/F2 kalau user eksplisit minta. Kalau masih gagal (harusnya tidak, akar masalah sudah beda
kelas), laporkan fail-log baru ke user, evaluasi kandidat (c) (Robolectric/instrumented).]

- v17 VERIFIED (laporan user langsung via chat — "Build berhasil Hijau"; BUKAN artifact log
  Actions/screenshot terlampir, dicatat apa adanya sesuai bentuk evidence-nya, sama spt pola v15):
  build.yml jalan sekuensial (step "Unit test" gate sebelum step Build) — hijau end-to-end berarti
  step "Unit test" (`gradle testDebugUnitTest`) lulus, yg scr desain CI ini hanya mungkin kalau
  keseluruhan 10 test (6 `FstrimExecutorTest` + 4 `PrefsTest`) PASS beneran (gagal 1 saja bikin
  task gagal & job berhenti sebelum step Build jalan). Mengkonfirmasi 3 kriteria tutup C4 dari
  VALIDASI v17: (1) 6 test `FstrimExecutorTest` PASS non-skipped, (2) 4 test `PrefsTest` tetap
  PASS, (3) step Build & Release lanjut normal — seam `ShizukuGateway` (kandidat b) TERBUKTI
  menyelesaikan C4 scr nyata, bukan cuma statis. C4 DITUTUP.
- Docs: `PENDING_ROADMAP.md` — C4 diubah ✅ SELESAI (v17, evidence CI nyata: build hijau).
  `CHANGELOG.md` tetap TIDAK ditambah entri (konsisten — 0 perubahan behavior user-facing).
- Batch: v17 (verified, tidak ada source diubah lagi di update ini — docs-only)

[RESUME POINT: C4 DITUTUP (v17 VERIFIED via laporan user "build hijau" — step Unit test lulus utk
10/10 test, step Build/Release lanjut normal, seam ShizukuGateway kandidat (b) terbukti bekerja
scr CI nyata, bukan cuma statis). Tidak ada item blocking/bug tersisa -> Remaining: tidak ada yang
mendesak; backlog opsional D2 (R8 minify + keep-rule reflection Shizuku, eksplisit "Prioritas
rendah" per catatan sendiri), F1 (lint/detekt di CI), F2 (Dependabot utk dev.rikka.shizuku) —
ketiganya BUTUH permintaan eksplisit user dulu (bukan otomatis) -> Next Action: tanya user mau
mulai item mana (D2/F1/F2) atau ada task lain di luar roadmap.]

- v18 (F1 dari PENDING_ROADMAP.md, atas pilihan eksplisit user): tambah lint (Android bawaan AGP)
  + detekt (static analysis Kotlin) ke CI. 3 file:
  - `build.gradle.kts` (root): +1 plugin `io.gitlab.arturbosch.detekt` versi `1.23.8` (`apply
    false`) — point release resmi terakhir jalur 1.23.x, dibangun eksplisit utk Kotlin 2.0.21
    (match persis versi Kotlin project ini, per release notes upstream, dicek via web search).
  - `app/build.gradle.kts`: apply plugin detekt + `detekt { buildUponDefaultConfig = true;
    allRules = false }` + `tasks.withType<Detekt>().configureEach { jvmTarget = "17"; reports {
    html.required.set(true) } }`. Tanpa config.yml custom -> ruleset default apa adanya (0 file
    config baru). 0 baris `android{}`/`dependencies{}` existing disentuh.
  - `.github/workflows/build.yml`: step baru "Lint & detekt (non-blocking)" (`gradle lintDebug
    detekt`) disisipkan persis setelah step "Unit test", sebelum "Decode keystore" (konsisten pola
    v14 C3 — static check tak butuh signing) + step "Unggah laporan lint & detekt" (`if:
    always()`, upload HTML report keduanya sbg artifact). `continue-on-error: true` SENGAJA
    dipasang: batch pertama blm ada baseline/triase temuan kode lama -> non-blocking dulu drpd
    pipeline hijau existing (10 unit test + Build/Release, verified v17) mendadak merah krn
    gaya-kode lama yg blm pernah dicek (P0 zero-regression thd status hijau). `continue-on-error`
    jg SENGAJA meredam trigger step "Simpan/Unggah log kegagalan" (`if: failure()`) dari temuan
    lint/detekt — step itu tetap murni utk kegagalan step Build spt sebelumnya, 0 perubahan makna.
    0 step lain disentuh.
- v18 VALIDASI: brace/paren/bracket balance kedua file gradle.kts OK via script python. YAML
  build.yml diparse ulang via PyYAML — valid, 13 step total, urutan step baru dikonfirmasi tepat
  (setelah "Unit test", sebelum "Decode keystore"), `continue-on-error: true` terpasang di step yg
  benar. Diff line-by-line thd v17 (ZIP sumber): ketiga file PURE ADDITION — 0 baris existing
  dihapus/diubah. Versi plugin `1.23.8` dicek via web search: release notes resmi konfirmasi
  "built against Kotlin 2.0.21" (match persis project ini; AGP tested-against 8.8.1 vs project
  8.7.3, Gradle tested-against 8.12.1 vs CI 8.9 — dekat tapi bukan versi identik, risiko kecil
  tersisa krn belum pernah dicoba nyata). BELUM pernah dijalankan compiler/CI sungguhan (sandbox
  tanpa Android SDK/Gradle/jaringan, sama spt semua batch sebelumnya) — WAJIB `gradle lintDebug
  detekt` jalan di CI/lokal utk konfirmasi: (1) plugin resolve & apply tanpa error versi/kompat
  nyata, (2) step lint/detekt jalan (pass ATAU fail — keduanya OK krn non-blocking) tanpa merusak
  step Unit test/Build/Release sesudahnya, (3) artifact report ke-upload sukses.
- Docs: `PENDING_ROADMAP.md` — F1 diubah ✅ SELESAI (v18, non-blocking, evidence CI nyata msh
  ditunggu). `CHANGELOG.md` TIDAK ditambah entri (0 perubahan behavior user-facing — CI tooling
  internal, konsisten pola v14/v17).
- Batch: v18

[RESUME POINT: v18 F1 (lint Android + detekt) ditambah ke CI — 3 file diubah (`build.gradle.kts`
root, `app/build.gradle.kts`, `.github/workflows/build.yml`), step baru non-blocking
(`continue-on-error: true`) sengaja supaya pipeline hijau existing (v17, 10/10 unit test) tidak
mendadak merah krn temuan gaya-kode lama blm pernah ditriase. Validasi statis only (brace/paren
OK, YAML valid+step-order dikonfirmasi, diff pure-addition thd v17, versi Detekt 1.23.8 dicek
match Kotlin 2.0.21 via web), BELUM pernah dijalankan compiler/CI sungguhan (sandbox tanpa
SDK/Gradle/jaringan) -> Remaining: jalankan DAILY UPDATE, push ke main -> Next Action: amati run CI
berikutnya — kalau step "Lint & detekt" jalan (pass/fail non-blocking, keduanya OK) & step Unit
test/Build/Release TETAP lanjut normal & artifact report ke-upload, F1 VERIFIED beneran (bukan
cuma statis) -> lanjut D2/F2 kalau user eksplisit minta. Kalau plugin gagal resolve/apply (skenario
risiko tersisa di atas), laporkan fail-log baru ke user, evaluasi turunkan versi Detekt.]

- v18 EVIDENCE PARSIAL (user unggah artifact asli `LagFix-lint-detekt-report-20.zip` dari run CI
  #20 — bukan cuma laporan chat, file report sungguhan): isi cuma `detekt/detekt.html`, TIDAK ada
  `lint-results-debug.html`.
  - `detekt.html` CONFIRMED real GitHub Actions run — path finding di dalam report
    `/home/runner/work/LagFix/LagFix/app/src/main/java/...` (path runner asli, bukan path
    lokal/sandbox) -> plugin Detekt 1.23.8 TERBUKTI resolve+apply+jalan nyata di CI (risiko versi
    yg dicatat di VALIDASI v18 TIDAK terjadi utk detekt). Temuan asli (91 code smell total,
    dihitung ulang cocok dgn ringkasan Metrics report): style 66 (`MagicNumber` 60, `MaxLineLength`
    5, `LoopWithTooManyJumpStatements` 1), naming 11 (`FunctionNaming` 11 — kemungkinan besar
    false-positive thd fungsi `@Composable` PascalCase, konvensi Compose standar yg tidak dikenal
    ruleset default Detekt, BELUM dikonfirmasi baris per baris), complexity 8 (`LongMethod` 2,
    `LongParameterList` 2, `NestedBlockDepth` 2, `TooManyFunctions` 2), exceptions 4
    (`TooGenericExceptionCaught` 4), empty-blocks 2 (`EmptyFunctionBlock` 2). 0 finding disentuh/
    di-fix di batch ini (di luar scope F1 — F1 cuma "tambah tooling ke CI", bukan "fix semua
    temuan"; itu backlog terpisah kalau user eksplisit minta).
  - `lint-results-debug.html` TIDAK ADA di artifact -> BELUM bisa disimpulkan kenapa (root-cause
    minimum, no hallucination): bisa (a) task `lintDebug` gagal/exception sebelum sempat nulis
    report, (b) `lintDebug` tidak sempat jalan krn Gradle berhenti duluan stlh error di task lain
    dlm command gabungan `gradle lintDebug detekt` (tanpa `--continue`), atau (c) sebab lain yg
    cuma kelihatan dari log mentah step tsb. Path `app/build/reports/lint-results-debug.html` yg
    dipakai di `build.yml` v18 dicek ulang via web — itu path default AGP yg benar, BUKAN salah
    path. TIDAK diubah apa-apa di source sampai root cause jelas (cegah fix asal tebak / scope
    creep).
  - Belum ada konfirmasi eksplisit dari user soal step Unit test/Build/Release sesudahnya (APK
    artifact / GitHub Release run #20) — kriteria F1 VERIFIED penuh (PROJECT_STATE resume
    sebelumnya) msh belum terpenuhi seluruhnya.
- Docs: `PENDING_ROADMAP.md` — F1 ditambah catatan evidence run-20 (rincian breakdown detekt +
  status lint msh pending). `CHANGELOG.md` tidak disentuh (evidence/docs-only, 0 source diubah).
- Batch: v18 (evidence parsial run-20 — docs-only, 0 source diubah lagi di update ini)

[RESUME POINT: v18 evidence run CI #20 masuk (artifact asli, bukan laporan chat) — `detekt.html`
ADA & CONFIRMED real runner GitHub Actions (91 code smell, breakdown di atas), TAPI
`lint-results-debug.html` TIDAK ADA di artifact yg sama -> root cause BELUM diketahui (butuh log
mentah step "Lint & detekt", bukan cuma report html-nya) & status Build/Release sesudahnya (run
#20) BELUM dikonfirmasi user -> Remaining: user perlu share log mentah step "Lint & detekt" (atau
seluruh job log run #20) + konfirmasi apakah step Build/Release run #20 tetap sukses -> Next
Action: setelah log/konfirmasi didapat, root-cause kenapa lintDebug tak hasilkan report (baru
putuskan perlu fix source atau tidak — TIDAK menebak/mengubah source sebelum root cause jelas).
Kalau user malah mau lanjut ke D2/F2 dulu drpd root-cause ini, tanya eksplisit dulu (P0
zero-regression: F1 blm 100% closed, jangan diklaim SELESAI penuh sampai lint terkonfirmasi).]

- v19 (fitur baru, dipilih eksplisit oleh user via prompt "priority: widget home screen+Quick
  Settings tile, sisanya masuk pending list" — root-cause F1 (lint-results-debug.html hilang, lihat
  batch v18 di atas) SENGAJA tidak dilanjutkan batch ini, bukan lupa, karena user eksplisit pivot ke
  fitur baru; F1 tetap OPEN, belum ditutup): 8 file diubah/ditambah, semua 1 fitur logis (2
  entry-point baru ke jalur run-manual yang sudah ada & teruji — `Scheduler.runOnce()` ->
  `TrimWorker` -> `FstrimExecutor`/`Prefs.record()`, 0 logic Shizuku baru ditulis):
  - `LagFixWidgetProvider.kt` (baru) — widget home screen: `onUpdate()` render RemoteViews dari
    `Prefs.log.firstOrNull()` (baris status apa adanya, format timestamp sudah jadi dari
    `Prefs.record()`, tidak diformat ulang) + tombol "Jalankan Sekarang". Tombol kirim broadcast
    custom action `WIDGET_RUN_NOW` ke provider sendiri -> `onReceive()` panggil
    `Scheduler.runOnce(context)` lalu refresh RemoteViews jadi "Menjadwalkan…" (hasil OK/FAIL riil
    baru kelihatan pas refresh berikutnya, updatePeriodMillis 30 menit — batasan wajar RemoteViews
    broadcast, bukan dihilangkan, hanya delay tampilan). Tap area lain widget -> buka MainActivity.
  - `LagFixTileService.kt` (baru) — QS tile: `onClick()` panggil `Scheduler.runOnce()` (jalur sama
    persis dgn widget/tombol app). Tile selalu `STATE_ACTIVE` (tap tidak diblokir walau Shizuku
    belum siap — konsisten dgn jadwal otomatis yg tetap mencatat "dilewati", bukan silent-block).
    Subtitle (API 29+, guard `Build.VERSION_CODES.Q`) tampilkan `Prefs.log` terbaru / status
    Shizuku belum siap / "Menjadwalkan…".
  - `widget_lagfix.xml` (layout, baru) + `widget_lagfix_info.xml` (xml, baru, updatePeriodMillis
    1800000/30menit, resizeMode horizontal|vertical, widgetCategory home_screen) +
    `ic_tile_fstrim.xml` (drawable vector, baru — bentuk kilat diskalakan dari
    `ic_launcher_foreground.xml` yg sudah ada, konsisten brand, monokrom sesuai konvensi ikon QS
    tile yg di-tint sistem otomatis).
  - `AndroidManifest.xml` — +`<receiver>` LagFixWidgetProvider (exported=true, intent-filter
    APPWIDGET_UPDATE + WIDGET_RUN_NOW, meta-data appwidget-provider) & +`<service>`
    LagFixTileService (exported=true, permission BIND_QUICK_SETTINGS_TILE, intent-filter
    QS_TILE). PendingIntent widget pakai FLAG_IMMUTABLE (wajib, targetSdk 35). 0 permission lain
    ditambah/dihapus (BIND_QUICK_SETTINGS_TILE bukan uses-permission app, itu permission yg
    di-declare di service sendiri, standar QS tile).
  - `strings.xml` — +5 string baru (widget_description, widget_button_run, widget_status_never,
    widget_status_running, tile_subtitle_not_ready). 0 string existing diubah.
  - `TrimWorker.kt` — 1 baris: `@Suppress("unused")` di `Scheduler.runOnce()` dihapus (fungsi ini
    sekarang benar-benar dipakai widget+tile). 0 baris logic lain di file ini disentuh (diff
    dikonfirmasi 1 baris only).
- v19 VALIDASI: xmllint --noout semua 5 file XML (Manifest, layout, widget info, drawable,
  strings) -> OK, well-formed. Brace/paren balance 3 file Kotlin (2 baru + TrimWorker.kt) -> OK.
  Cross-check referensi: semua `R.id`/`R.layout`/`R.string`/`R.drawable`/`R.xml` yg dipakai di
  Kotlin/Manifest match persis dgn resource yg didefinisikan (id widget_root/widget_title/
  widget_status/widget_button, layout widget_lagfix, xml widget_lagfix_info, drawable
  ic_tile_fstrim, string 5 baru + app_name existing). Diff thd ZIP v18: persis 8 file (5 baru + 3
  ubah: AndroidManifest.xml, strings.xml, TrimWorker.kt 1 baris) — 0 file production lain
  (Prefs/FstrimExecutor/MainViewModel/MainActivity/TrimWorker logic/UpdateChecker/AppLinks/
  CrashLogger/LagFixApp) tersentuh, konfirmasi via `diff -rq` thd ZIP asli. BELUM pernah dicompile
  compiler/AGP sungguhan (sandbox tanpa Android SDK/Gradle/jaringan, sama spt semua batch
  sebelumnya) — WAJIB `gradle assembleDebug` (atau lint) jalan di CI/lokal utk konfirmasi resource
  linking (aapt2) valid & 0 error compile riil, DAN test manual add-widget-to-homescreen +
  add-QS-tile via device/emulator asli utk konfirmasi behavior (compile hijau bukan bukti perilaku
  runtime benar — widget/tile terutama rawan salah di resource-linking & manifest-registration yg
  cuma ketahuan runtime, bukan cuma static check).
- Batch: v19

[RESUME POINT: v19 widget home screen + QS tile ditambah (8 file: 5 baru + 3 diubah, detail di
atas), validasi statis only (xmllint OK, brace balance OK, cross-check R.* match, diff pure vs ZIP
v18 confirmed 8 file) — BELUM pernah dijalankan compiler/AGP sungguhan & BELUM pernah dites device
asli (sandbox tanpa SDK/Gradle/jaringan) -> Remaining: jalankan DAILY UPDATE, push ke main, CI
build jalan (assembleRelease/Debug) -> kalau hijau, test manual di device: (1) tambah widget ke
home screen — cek render awal + tombol "Jalankan Sekarang" + tap area buka app, (2) tambah tile
LagFix ke Quick Settings — cek tap trigger run + subtitle update. F1 (lint-results-debug.html
hilang, batch v18) TETAP OPEN, sengaja belum dilanjutkan batch ini (pivot eksplisit user ke fitur
baru) — bukan ditutup, bukan dilupakan. "Sisanya masuk pending list" (kutipan user) BELUM
ditambahkan ke PENDING_ROADMAP.md karena user belum sebut fitur baru lain secara konkret — jangan
ditebak/dikarang, tunggu user spesifikkan -> Next Action: tunggu evidence CI + hasil test device
widget/tile dari user; kalau ada fitur baru lain yg dimaksud "sisanya", user perlu sebutkan
konkret dulu baru masuk PENDING_ROADMAP.md.]

- v20 (hotfix atas laporan user, evidence 2 screenshot device asli — widget di home screen +
  tampilan tile QS): 3 keluhan konkret dari screenshot, root-cause minimum per item, 0 fitur baru
  ditambah, 0 fitur v19 dihapus:
  1. "Nol feedback sama sekali" — akar masalah: `Scheduler.runOnce()` (dipanggil widget/tile) cuma
     enqueue `TrimWorker` tanpa cara memberi tahu hasil balik ke user; widget cuma sempat tampil
     "Menjadwalkan…" lalu diam sampai refresh 30 menit berikutnya. Fix: `TrimWorker.doWork()`
     sekarang terima flag `manual` via WorkManager input `Data` (`KEY_MANUAL`) — `Scheduler.
     runOnce()` set flag ini true (satu-satunya pemanggil, jadi 0 risiko ke jalur lain), `Scheduler.
     apply()` (periodik/jadwal otomatis) TIDAK pernah set flag ini -> default false -> 0 perubahan
     perilaku jadwal otomatis existing (dikonfirmasi: diff `Scheduler.apply()` nihil). Kalau
     `manual=true`: toast (`Dispatchers.Main`) muncul saat selesai — "fstrim berhasil dijalankan"
     / "fstrim gagal: <pesan>" / "Shizuku belum siap, aktifkan dulu lalu coba lagi" (kasus
     Shizuku belum siap sebelumnya cuma dicatat ke Riwayat + retry diam-diam, sekarang manual-trigger
     dikasih tahu langsung kenapa tidak jalan).
  2. "Fungsi menjadwalkan gak jelas buat awam" — akar masalah: teks status sementara widget/tile
     pakai kata "Menjadwalkan…", bentrok makna dgn fitur "Jadwal otomatis" yang sudah ada (beda
     konsep: itu benar-benar run seketika, bukan set jadwal baru). Fix: `strings.xml` ->
     `widget_status_running` diganti "Sedang memproses…" (dipakai widget & subtitle tile, 1 string
     dipakai bersama, 0 string baru perlu utk ini).
  3. "Tampilan gak estetik" — widget: root background diganti `widget_card_bg.xml` (baru, rounded
     20dp, warna tetap ikut tema via `?android:attr/colorBackground` — light/dark tetap kebaca,
     0 warna baru) dan tombol diganti `widget_button_bg.xml` (baru, rounded 12dp, isi warna
     `@color/ic_launcher_background` yang SUDAH ADA — sama persis biru ikon launcher app, bukan
     warna baru) + teks putih, dari sebelumnya tombol abu-abu default sistem polos di screenshot.
     Tile QS: TIDAK ada perubahan icon/warna (icon vector sudah ikuti pedoman resmi ikon tile —
     monokrom putih solid di viewport 24dp; tampilan bulat-putih-biru solid di screenshot user
     kemungkinan render "before first live update" bawaan skin OEM, bukan bug icon — TIDAK
     ditebak/diubah tanpa bukti lebih lanjut, sesuai NO HALLUCINATION). Ditambah `onTileAdded()`
     (baru, override) supaya icon/label/state langsung di-push begitu tile ditambahkan user,
     bukan nunggu `onStartListening()` pertama — mitigasi kemungkinan render awal janggal, tapi
     BELUM bisa dipastikan ini akar masalah pastinya tanpa device asli.
  File diubah: `TrimWorker.kt`, `LagFixTileService.kt`, `strings.xml`, `widget_lagfix.xml` (4) +
  2 drawable baru (`widget_card_bg.xml`, `widget_button_bg.xml`) = 6 file, 1 batch hotfix logis.
  0 file lain (Prefs/FstrimExecutor/MainActivity/MainViewModel/UpdateChecker/AppLinks/CrashLogger/
  LagFixApp/AndroidManifest.xml/widget_lagfix_info.xml/ic_tile_fstrim.xml) disentuh — dikonfirmasi
  via diff thd ZIP v19 yang sudah dikirim ke user.
- v20 VALIDASI: xmllint OK (4 XML disentuh: widget_lagfix.xml, strings.xml, 2 drawable baru).
  Brace/paren balance OK (TrimWorker.kt, LagFixTileService.kt). Cross-check: `R.string.
  toast_run_ok/toast_run_fail/toast_shizuku_not_ready` match deklarasi baru di strings.xml;
  `TrimWorker.KEY_MANUAL` dipakai konsisten di `Scheduler.runOnce()` & `TrimWorker.doWork()`;
  `Scheduler.apply()` dikonfirmasi TIDAK menyentuh `Data`/input work sama sekali (grep manual).
  Diff thd ZIP v19: persis 6 file di atas. BELUM pernah dicompile compiler/AGP sungguhan & BELUM
  dites device asli (sandbox tanpa SDK/Gradle/jaringan, sama spt semua batch) — poin 3-tile
  (kemungkinan render OEM) khususnya WAJIB dikonfirmasi ulang oleh user di device asli setelah
  update ini, karena root cause pastinya belum 100% dipastikan (beda level bukti dgn poin 1 & 2
  yang akar masalahnya jelas dari kode).
- Docs: `CHANGELOG.md` +entry v20 (user-facing: toast hasil run manual + widget lebih rapi).
  `PENDING_ROADMAP.md` tidak disentuh (tidak ada item baru yg konkret utk ditambahkan).
- Batch: v20

[RESUME POINT: v20 hotfix widget/tile (3 keluhan device-evidence user: nol feedback -> toast
manual-only ditambah; "Menjadwalkan" ambigu -> reworded "Sedang memproses…"; widget kurang estetik
-> rounded card + tombol biru brand) — 6 file diubah, validasi statis only (xmllint+brace OK,
cross-check R.*/KEY_MANUAL OK, diff vs v19 confirmed 6 file, 0 file lain tersentuh) -> Remaining:
jalankan DAILY UPDATE, push, CI build hijau -> test manual device WAJIB utk 3 hal: (1) tap tombol
widget -> toast hasil muncul (OK/FAIL/Shizuku-belum-siap) & widget tampilan baru (card rounded +
tombol biru) kebaca bagus di wallpaper asli, (2) tap tile QS -> toast sama muncul, (3) tile QS
setelah ditambah ulang (uninstall+reinstall tile atau tambah tile baru) apakah icon masih
tampil bulat-biru-putih solid (kalau MASIH sama setelah onTileAdded() -> itu memang gaya render
OEM/launcher, bukan bug app, tutup poin 3 sbg "as-designed OS"; kalau BERUBAH jadi monokrom
ter-tint normal -> onTileAdded() konfirmasi jadi fix nyata) -> Next Action: user kirim hasil test
3 poin di atas (screenshot/video kalau perlu), terutama poin 3 (paling belum pasti).]

- v21 (hotfix lanjutan atas 3 laporan baru user, sebelum hasil test v20 di atas dikonfirmasi):
  1. "Widget/tile/app wajib sinkron, minimal tidak salah state" + 2. "pesan widget gak berubah
     sama sekali (Sedang memproses) sehabis dipencet" — SATU akar masalah yang sama: setelah
     `TrimWorker.doWork()` selesai (sukses/gagal/dilewati) ATAU setelah run manual dari app
     (`MainViewModel.runNow()`), TIDAK ADA kode yang memberi tahu widget/tile bahwa hasil sudah
     berubah — widget cuma nunggu `updatePeriodMillis` (30 menit) & tile cuma nunggu
     `onStartListening()` (dipanggil ulang kalau panel Quick Settings dibuka lagi). Kalau user
     tidak menutup-buka ulang panel / belum lewat 30 menit, keduanya nyangkut di state lama
     (persis laporan "Sedang memproses" tak berubah). Fix: fungsi baru `Scheduler.notifyChanged
     (ctx)` (di `TrimWorker.kt`) — kirim broadcast eksplisit `ACTION_APPWIDGET_UPDATE` ke
     `LagFixWidgetProvider` sendiri (memicu `onUpdate()` ulang, baca `Prefs.log` terbaru, jadi
     otomatis lepas dari teks "Sedang memproses") + panggil `TileService.requestListeningState()`
     resmi (API framework, minSdk 26 aman) utk `LagFixTileService` (memicu `onStartListening()`
     ulang tanpa perlu user buka-tutup panel). Dipanggil UNCONDITIONAL (bukan cuma kalau
     `manual=true`) di KEDUA cabang `TrimWorker.doWork()` (sukses & dilewati) SUPAYA kalau/kapan
     jadwal otomatis benar-benar jalan (lihat poin 3 di bawah), widget/tile ikut sinkron juga —
     bukan cuma trigger manual. Dipanggil juga dari `MainViewModel.runNow()` (run dari tombol
     dalam app) — supaya app sendiri pun langsung sinkronkan widget/tile, bukan cuma sebaliknya.
     0 permission baru (requestListeningState & sendBroadcast eksplisit ke receiver sendiri tidak
     butuh permission tambahan).
  3. "App tidak mencatatkan aktifitas trim selain yang di-trigger manual" — DIINVESTIGASI, TIDAK
     diubah kodenya batch ini (root cause belum pasti, P0 NO HALLUCINATION): dicek `Prefs.record()`
     — dipanggil UNCONDITIONAL di semua jalur (manual widget/tile via TrimWorker, run dari app via
     MainViewModel.runNow(), DAN periodik via TrimWorker yang sama persis, 0 percabangan
     berdasarkan sumber trigger). Dicek juga `MainActivity` Riwayat (`ui.log.forEach { LogLine(it)
     }`) — 0 filter apa pun, semua isi `Prefs.log` ditampilkan apa adanya. Kesimpulan: TIDAK
     ditemukan bug di kode yang secara sengaja/tidak sengaja mengecualikan run non-manual dari
     pencatatan — kalau run periodik benar-benar dieksekusi WorkManager, otomatis akan tercatat
     sama persis spt manual. Dugaan paling mungkin (BELUM dikonfirmasi, makanya TIDAK diubah):
     (a) toggle "Jadwal otomatis" di tab Pengaturan belum pernah diaktifkan user (beda kontrol dari
     widget/tile — widget/tile SELALU bisa jalan manual terlepas dari toggle ini, jadi wajar 0
     entri otomatis kalau toggle ini off), ATAU (b) job WorkManager periodik dibunuh manajemen
     baterai OEM (Xiaomi/MIUI, Oppo/ColorOS, Vivo, atau battery-saver agresif Samsung/One UI utk
     app yg jarang dibuka) sebelum sempat jalan — ini masalah OS/OEM, bukan bug kode. TIDAK
     ditebak/dipilih salah satu tanpa konfirmasi user (P0). AUTO-HALT poin ini — lihat RESUME
     POINT.
  File diubah: `TrimWorker.kt`, `MainViewModel.kt` (2 file saja, akar masalah poin 1+2 persis di
  1 titik integrasi). 0 file lain disentuh (dikonfirmasi diff thd ZIP v20 yg sudah dikirim).
- v21 VALIDASI: brace/paren balance OK (2 file). Cross-check: `Scheduler.notifyChanged` dipanggil
  persis di 3 titik (2 cabang TrimWorker.doWork() + MainViewModel.runNow()), semua import baru
  (AppWidgetManager/ComponentName/Intent/TileService) valid API framework minSdk 26. BELUM pernah
  dicompile/AGP sungguhan & BELUM dites device asli (sandbox) — WAJIB test manual: jalankan trim
  (widget/tile/app, bebas mana saja) lalu cek KEDUA yang lain (widget & tile & app) langsung
  update tanpa perlu tunggu/buka-tutup panel.
- Docs: `CHANGELOG.md` +entry v21 (user-facing: widget/tile sekarang auto-refresh stlh hasil run
  apa pun, tak perlu tunggu). `PENDING_ROADMAP.md` tidak disentuh.
- Batch: v21

[RESUME POINT: v21 fix sinkronisasi widget/tile/app (root cause: tak ada yg memicu refresh stlh
TrimWorker/runNow selesai -> ditambah Scheduler.notifyChanged(), dipanggil di 3 titik, unconditional
biar jadwal otomatis pun ikut sinkron nanti) — 2 file diubah, validasi statis only (brace OK,
cross-check call-site OK), BELUM compile/device test sungguhan -> Remaining: (A) test device utk
v21: jalankan trim dari SALAH SATU sumber (widget/tile/app), cek KEDUA lainnya ikut update
otomatis TANPA tunggu 30menit/buka-tutup panel; (B) v20 poin 3 (icon tile bulat-biru-putih) masih
menunggu hasil test onTileAdded() dari user (lihat batch v20 di atas, belum berubah); (C) v20 poin
1&2 sudah ke-superscede oleh v21 (root cause yg sama, v21 fix lebih menyeluruh) — TIDAK perlu test
v20 poin 1&2 terpisah lagi, cukup test (A) di atas. AUTO-HALT terbuka utk laporan user poin 3
("aktifitas trim selain manual tidak tercatat") — BUTUH jawaban user sebelum kode scheduler
disentuh: (1) toggle "Jadwal otomatis" di tab Pengaturan app — nyala atau tidak? (2) HP merek/ROM
apa (Xiaomi/MIUI, Oppo/ColorOS, Vivo, Samsung/One UI, dll — sebagian agresif mematikan job
background app yg jarang dibuka)? -> Next Action: tunggu jawaban 2 pertanyaan itu + hasil test (A)
& (B), baru lanjut (kalau toggle OFF -> bukan bug, cukup edukasi; kalau ON tapi tetap 0 entri
otomatis & device masuk daftar OEM agresif -> baru pertimbangkan fix spt WorkManager
setExpedited()/panduan battery-optimization-exemption, TIDAK menebak duluan).]

- v22 (jawaban user thd 2 pertanyaan v21 diterima — root cause poin 3 CONFIRMED, bukan lagi
  dugaan): (1) toggle "Jadwal otomatis" MEMANG sudah ON dari awal pakai (bukan penyebabnya,
  hipotesis (a) di v21 GUGUR). (2) HP: Infinix, XOS 16 — XOS (Transsion/Infinix, satu keluarga dgn
  HiOS Tecno & itel) SUDAH DIKONFIRMASI PUBLIK sbg salah satu ROM paling agresif membunuh proses
  background/job WorkManager kalau app tak dikecualikan dari App Management/optimasi baterai
  (sekelas MIUI/ColorOS/FuntouchOS di dontkillmyapp.com). Hipotesis (b) di v21 JADI root cause
  terkonfirmasi. Juga dikonfirmasi: v21 (sinkronisasi widget/tile/app) bekerja baik di 3 sektor —
  TIDAK perlu fix ulang, tutup poin itu SELESAI (behavior, bukan cuma build, terkonfirmasi user).
  Fix root-cause poin 3 (standar resmi Android utk kelas masalah ini, BUKAN redesign/foreground
  service — itu scope creep, ini official Android API): tombol "Izinkan berjalan tanpa batas" baru
  di tab Pengaturan (card "Jadwal", di bawah toggle idle), HANYA muncul kalau app BELUM
  dikecualikan dari optimasi baterai (`PowerManager.isIgnoringBatteryOptimizations`, dicek di
  `MainViewModel.read()`, otomatis refresh via `onResume() -> vm.refresh()` yang SUDAH ADA —
  0 lifecycle plumbing baru) — tap -> buka dialog sistem resmi
  `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (`MainViewModel.batteryOptimizationIntent
  ()`). +1 permission normal (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, auto-grant install-time, 0
  dialog izin custom) di Manifest — permission BARU krn genuinely dibutuhkan utk root-cause yg baru
  dikonfirmasi user, BUKAN scope creep sembarangan (P0 "preserve permission" = jangan HAPUS/lemahkan
  yg ada tanpa alasan, bukan larangan mutlak nambah kalau memang perlu & sudah dikonfirmasi akar
  masalahnya). 0 string baru di strings.xml — teks UI Compose di app ini SUDAH konvensinya hardcode
  literal langsung di Kotlin (dicek: MainActivity.kt 0 referensi R.string sama sekali sebelum batch
  ini), ikut konvensi existing bukan bikin pola baru.
  File diubah: `AndroidManifest.xml`, `MainViewModel.kt`, `MainActivity.kt` (3 file, 1 fitur logis
  — entry point pengecualian baterai). 0 file lain disentuh (dikonfirmasi diff thd ZIP v21).
- v22 VALIDASI: xmllint OK (Manifest). Brace/paren balance OK (MainViewModel.kt, MainActivity.kt
  — sempat ada "mismatch" paren dari teks komentar prosa, BUKAN kode; dirapikan, dikonfirmasi ulang
  balance OK). Cross-check: `PowerManager::class.java`/`isIgnoringBatteryOptimizations`/
  `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` semua API resmi Android sejak API 23, aman
  di minSdk 26. `ui.batteryUnrestricted` dipakai persis di 1 tempat (SettingsTab), dialirkan dari
  `read()` yg sudah ada. BELUM pernah dicompile/AGP sungguhan & BELUM dites device asli (sandbox) —
  WAJIB test di device Infinix milik user: tombol muncul (krn kemungkinan besar XOS ini memang
  belum exempt) -> tap -> dialog sistem muncul -> user pilih "Izinkan" -> kembali ke app -> tombol
  HILANG (tanda `batteryUnrestricted` terbaca true) -> setelah itu tunggu 1 siklus interval jadwal
  (atau pendekkan interval ke 6 jam sementara utk test cepat) -> cek Riwayat, apakah entri OTOMATIS
  (bukan hasil tap manual) akhirnya muncul. Kalau XOS PUNYA pengaturan tambahan di luar
  battery-optimization standar Android (mis. toggle "Autostart"/"Background Activity" terpisah di
  App Management XOS sendiri — umum di ROM Transsion), tombol ini SAJA mungkin BELUM cukup;
  user perlu cek manual App Management XOS jika Riwayat masih kosong setelah exempt +1 siklus.
- Docs: `CHANGELOG.md` +entry v22.
- Batch: v22

[RESUME POINT: v22 root cause poin 3 CONFIRMED (Infinix XOS = ROM agresif battery-kill, BUKAN bug
kode) + fix resmi ditambah (tombol exempt battery optimization di Pengaturan, 3 file) — v21 sync
CONFIRMED user bekerja baik (closed, behavior-verified). Validasi statis only (xmllint+brace OK,
cross-check API level OK), BELUM compile/device test -> Remaining: user test di Infinix: (1) tombol
"Izinkan berjalan tanpa batas" muncul di Pengaturan? (2) tap -> dialog sistem muncul & bisa pilih
Izinkan? (3) tombol hilang setelah itu (state ke-refresh benar)? (4) PALING PENTING — setelah
exempt, tunggu min. 1 siklus interval (atau pendekkan ke 6 jam sementara), apakah Riwayat akhirnya
dapat entri OTOMATIS (bukan dari tap manual)? Kalau (4) MASIH nihil setelah exempt -> kemungkinan
XOS punya toggle "Autostart"/App Management terpisah yg perlu diaktifkan manual juga (di luar
kendali kode app) -> Next Action: tunggu hasil 4 poin test di atas dari user; v20 poin 3 (icon tile
bulat-biru-putih, lihat batch v20) MASIH belum ada kabar dari user, tetap OPEN terpisah.]
