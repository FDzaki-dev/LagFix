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
