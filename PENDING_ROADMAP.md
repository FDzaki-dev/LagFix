[BRANDING_NAME: LagFix]
[TERMUX_ROOT: LagFix]

# PENDING_ROADMAP — Rencana Penyempurnaan (basis: source v6 nyata + APK build-6)

Dasar dokumen: dibaca langsung dari LagFix_v6.zip (9 file Kotlin, 756 baris total) +
LagFix_build-6_release.apk yang sudah dikompilasi. Tidak ada source yang diubah di batch ini —
dokumen ini PLANNING ONLY. Tiap item butuh approval eksplisit sebelum dieksekusi (no scope creep).

## A. BLOCKING — status setelah evidence video real-device
1. ✅ VERIFIED (evidence video): prompt "unknown sources" muncul sekali, FileProvider->Package
   Installer kebuka, install sukses, app kebuka ulang tanpa crash, state persist.
2. ✅ VERIFIED (evidence video): versi terpasang naik build 6 -> build 7 sesuai versionCode CI.
3. ✅ DITUTUP via pembacaan kode (`UpdateChecker.download()`): `deleteRecursively()` + nama file
   tetap `update.apk` -> menumpuk arsitektural tidak mungkin. Verified via source, bukan runtime
   device-test — beda level bukti, tapi cukup kuat. Cek ukuran Cache di Setelan > Aplikasi tetap
   opsional kalau user mau extra-sure, tidak blocking apapun.
Build hijau ≠ behavior terverifikasi (P0) — poin 1&2 verified via device, poin 3 verified via source.

## B. Robustness / edge case (logic minimum, non-breaking) — SEMUA SELESAI v13
1. ✅ SELESAI (v13, keputusan: perlu retry, bukan cukup as-is): `TrimWorker` sekarang
   `Result.retry()` (bukan `Result.success()`) kalau Shizuku belum ready setelah poll 5 detik —
   WorkManager otomatis coba lagi dgn backoff bawaan, tak perlu nunggu jadwal periodik penuh
   berikutnya. Skip TETAP dicatat ke Riwayat (`prefs.record()`) spt sebelumnya. TIDAK retry kalau
   fstrim SUDAH dicoba tapi gagal (exit code non-0) — beda kelas masalah, retry tak menolong. File:
   `TrimWorker.kt` only.
2. ✅ SELESAI (v13): `UpdateChecker.friendlyError()` (baru) memetakan `UnknownHostException`/
   `SocketTimeoutException` -> "Tidak ada koneksi internet." dan HTTP 403 -> pesan rate-limit,
   dipakai di `check()` (langsung) & `MainViewModel.installUpdate()` (pemanggilan eksplisit) —
   sebelumnya keduanya tampilkan `e.message` mentah ke user. Kasus lain tetap fallback ke
   `e.message` apa adanya (logic minimum, tidak coba tangani semua jenis exception). File:
   `UpdateChecker.kt`, `MainViewModel.kt`.
3. ✅ SELESAI (v13): `UpdateChecker.download()` sekarang hapus `update.apk` parsial kalau copy
   stream gagal di tengah jalan (exception ditangkap, `dest.delete()`, lalu dilempar ulang) —
   sebelumnya file rusak bisa nyangkut di cache sampai percobaan unduh berikutnya. File:
   `UpdateChecker.kt` only.
4. ✅ SELESAI (v13, verifikasi + polish): dicek dari kode — skip SUDAH tercatat ke Riwayat sejak
   v6 (bukan silent skip beneran, `prefs.record()` selalu dipanggil di jalur ini). Yang ditambah:
   pembeda visual skip (dot amber, teks "— dilewati (Shizuku belum siap)") vs FAIL asli (dot merah,
   "— gagal") di `MainActivity.kt` (Riwayat header + `LogLine`), supaya beberapa entri skip
   berturut-turut (efek samping retry poin B1) tidak disalahartikan sbg fstrim gagal berulang kali.
   Parse-only, format `Prefs.record()` TIDAK diubah (PrefsTest.kt tetap valid).

## C. Testing (gap nyata — dicek langsung: tidak ada app/src/test atau app/src/androidTest sama sekali)
1. ✅ SELESAI (v7): Unit test `Prefs.record()` (rotasi log maks 30 baris, format timestamp
   `dd/MM HH:mm`) dan `FstrimExecutor.state()` (mapping 4 status Shizuku) — logic murni, tidak
   butuh device fisik. File: `PrefsTest.kt`, `FstrimExecutorTest.kt`. BELUM pernah dicompile
   compiler sungguhan (sandbox); wajib `./gradlew testDebugUnitTest` di CI/lokal utk verifikasi run.
2. Checklist manual/instrumented untuk alur Shizuku (grant/revoke permission via
   `requestPermission()`) dan alur install-update (FileProvider + Package Installer).
3. ✅ SELESAI (v14): `build.yml` sekarang punya step "Unit test" (`gradle testDebugUnitTest`)
   sebelum step Build — jalan duluan, gerbang validasi tambahan sebelum assembleRelease/Debug
   (bukan pengganti verifikasi behavior nyata, cuma nangkep compile-error/regresi test lebih awal
   drpd nunggu build APK penuh). Ditaruh sebelum "Decode keystore" (test tak butuh signing), 0
   step lain (keystore/build/release) disentuh sama sekali.
4. ✅ SELESAI (v17, evidence CI nyata — laporan user "build hijau") — histori: v15 men-`@Ignore`
   6 test `FstrimExecutorTest.kt` (v7) krn FAILED nyata di CI (fail-log-16):
   `mockStatic(Shizuku::class.java)` tidak berhasil di-intercept Mockito inline mock maker di JVM
   unit-test worker sungguhan. v16 coba kandidat (a) — JVM arg `-Djdk.attach.allowAttachSelf=true`
   — TERBUKTI GAGAL (fail-log-18, error IDENTIK), dihentikan. v17 (approval eksplisit user):
   kandidat (b) — seam/interface `ShizukuGateway` di sekitar 3 pemanggilan Shizuku dlm
   `FstrimExecutor.kt` (`pingBinder`/`isPreV11`/`checkSelfPermission`), production pakai
   `RealShizukuGateway` (delegasi murni, 0 perubahan behavior), test pakai
   `mock(ShizukuGateway::class.java)` non-static + `@After` reset. JVM arg kandidat (a) di-revert.
   Build hijau (step Unit test lulus, gate sebelum step Build) mengkonfirmasi 10/10 test PASS
   beneran & step Build/Release lanjut normal — C4 TERTUTUP scr nyata, bukan cuma statis.
   `PrefsTest.kt` (4 test) tetap PASS, tidak terdampak. File v17: `FstrimExecutor.kt` +
   `FstrimExecutorTest.kt` + `app/build.gradle.kts` (+ dokumen ini).

## D. Technical debt (dicatat sebagai risiko, backlog — bukan refactor sekarang)
1. ✅ SELESAI (v9): `FstrimExecutor.sh()` panggil `Shizuku.newProcess` (method private) via
   reflection — titik rapuh utama, gagalnya diam-diam di runtime kalau versi Shizuku naik &
   signature berubah. Mitigasi (sesuai scope item ini — bukan refactor): dicatat di `README.md`
   ("Catatan teknis — reflection Shizuku") + wajib re-verifikasi manual tiap upgrade versi
   Shizuku. 0 file source diubah (docs-only, sesuai keputusan "bukan refactor sekarang").
2. `isMinifyEnabled = false` permanen di release (sengaja, karena poin D1) — APK release lebih
   besar dari perlu. Kalau nanti mau aktifkan R8: wajib keep-rule spesifik hanya utk method yang
   direflect, BUKAN blanket keep-rule (sesuai guard R8 di .cursorrules). Prioritas rendah.
3. ✅ SELESAI (v8): `versionName` sekarang ikut `GITHUB_RUN_NUMBER` (`"1.0.<n>"`, lokal/dev
   `"1.0.0-dev"`) — label versi di UI tak lagi statis "1.0.0" tiap rilis. File: `app/build.gradle.kts`
   only (defaultConfig). Belum dicompile compiler sungguhan (sandbox tanpa SDK/Gradle).

## E. UX minor (opsional, backlog — butuh approval eksplisit)
1. ✅ SELESAI (v8): Riwayat sekarang punya indikator visual OK/FAIL (titik + teks warna
   hijau/merah) per baris, bukan teks polos lagi. Parse-only di UI (`MainActivity.kt`,
   `LogLine()`) — format baris dari `Prefs.record()` tidak diubah (PrefsTest.kt tetap valid).
   Fallback teks polos kalau baris tak cocok pola (non-breaking). Belum dicompile compiler
   sungguhan (sandbox tanpa SDK/Gradle).
2. ✅ SELESAI (v9), VERIFIED (evidence video device asli): Info app dikonsolidasi lewat dialog "Tentang" (baru) — dipicu tombol baru
   (`AboutRow`, "›") di card Tautan. Isi dialog: tagline app, versi terpasang, package id, tombol
   ke source/developer. File diubah: `MainActivity.kt` only (tambah `AboutRow`+`AboutDialog`+state
   `showAbout`; `versionName` dipindah ke atas `HomeScreen` biar dipakai bareng label footer & dialog
   — nilai/perilaku label footer TIDAK berubah). 0 file production logic lain (Prefs/FstrimExecutor/
   MainViewModel/TrimWorker/UpdateChecker) disentuh.
3. ✅ SELESAI (v10, di luar urutan — permintaan ad-hoc user, bukan dipilih dari daftar ini):
   Toggle dark/light manual sekarang ada (malah lebih lengkap: picker "Ikuti sistem/Terang/Gelap"),
   plus palet warna kustom "calm" (bukan Material You dinamis lama). Detail di PROJECT_STATE.md
   batch v10.

## F. Release/CI hardening (opsional, backlog)
1. ✅ SELESAI (v18, non-blocking): `build.yml` sekarang punya step "Lint & detekt" (Android Lint
   bawaan AGP + `io.gitlab.arturbosch.detekt` 1.23.8) setelah step "Unit test". `continue-on-error:
   true` — batch pertama, blm ada baseline/triase temuan lama, jadi tidak (belum) menggagalkan
   pipeline. Report HTML diunggah sbg artifact. Evidence run CI #20 (artifact asli): `detekt.html`
   CONFIRMED real runner GitHub Actions (91 code smell — rincian di PROJECT_STATE.md batch v18).
   `lint-results-debug.html` TIDAK ada di artifact yg sama — root cause belum diketahui, butuh log
   mentah step tsb + konfirmasi status Build/Release run #20 sebelum F1 dianggap VERIFIED penuh.
   v52: akar masalah `lint-results-debug.html` hilang TETAP belum diketahui (log mentah step lint tak
   pernah diunggah). `build.yml` kini memisah step Lint/Detekt & mengunggah log mentah + semua file
   `lint-results*` + listing; F1 baru VERIFIED penuh setelah artifact run berikutnya dibaca.
   v54: ✅ F1 VERIFIED penuh — artifact `LagFix-lint-detekt-report-47` dibaca: `lint-results-debug.html`
   (+ .txt/.xml) ADA di `app/build/reports/` & ikut terunggah; lint `BUILD SUCCESSFUL` (0 error, 15
   warning). Detekt step gagal `MaxIssuesReached` (127 weighted issue; sengaja non-blocking) tapi
   laporan tetap tertulis. Penyebab HTML hilang di run #20 TIDAK terbaca dari artifact ini (cuma
   terbukti: dgn step lint terpisah + log mentah, laporan kini muncul). Temuan detekt (MagicNumber 84,
   FunctionNaming 15, MaxLineLength 10, sisanya <=4/rule; 91 di MainActivity.kt) = backlog triase,
   BUKAN bagian F1.
   v58 (triase detekt, config-only): `config/detekt/detekt.yml` (baru) + `config.setFrom(...)` di
   `app/build.gradle.kts` — `FunctionNaming` & `MagicNumber` dgn `ignoreAnnotated: ['Composable']`.
   FunctionNaming 15/15 temuan terverifikasi dari source = fungsi @Composable PascalCase (false-positive).
   Efek nyata ke jumlah temuan BELUM terbukti sampai artifact detekt run berikutnya dibaca. 0 kode app diubah.
   v88 (perintah user: "lintDebug/detekt tolong diperketat; gapapa build merah diawal"): lint & detekt
   kini BLOCKING. `build.yml`: step Lint/Detekt tetap jalan keduanya (continue-on-error + `id`), lalu step
   "Gerbang lint & detekt (BLOCKING)" exit 1 bila salah satu `failure` -> Build/Release terlewati;
   "Simpan log kegagalan" kini menggabung `build_output.log` + `lint_output.log` + `detekt_output.log` +
   `lint-results-debug.txt`. `app/build.gradle.kts`: `lint { abortOnError = true; warningsAsErrors = true }`.
   Referensi sebelum diperketat: lint 0 error/15 warning (v54), detekt 127 weighted issue (v54; sesudah
   config v58 & kode v55-v87 belum terukur). Merah di awal DIHARAPKAN; triase temuan = pekerjaan lanjutan.
   v89 (triase log CI #73): hasil nyata setelah blocking = lint 17 error (+1 informational), detekt 149
   weighted issue (MagicNumber 83, MaxLineLength ~41, TooGenericExceptionCaught 10, LongMethod 7,
   CyclomaticComplexMethod 4, LongParameterList 3, TooManyFunctions 3, NestedBlockDepth 2, EmptyFunctionBlock 2,
   LoopWithTooManyJumpStatements 1). Unit test HIJAU. Lint v89: `app/lint.xml` (baru; `lintConfig` eksplisit) +
   manifest `dataExtractionRules` + `mipmap-anydpi-v26` -> `mipmap-anydpi`. Detekt v89: hanya override struktural
   Compose/test di `config/detekt/detekt.yml`. SISA detekt (pekerjaan batch berikut): MagicNumber, MaxLineLength,
   TooGenericExceptionCaught, NestedBlockDepth, LoopWithTooManyJumpStatements, LongMethod `capture`,
   LongParameterList `RawOutcome`, TooManyFunctions (LogcatSnapshot, MainViewModel).
2. Belum ada dependency-update check otomatis (mis. Dependabot) utk `dev.rikka.shizuku`.

## G. UI/UX Premium (milestone, permintaan eksplisit user v59)
1. Dokumen utama: `PENDING_UIUX_MILESTONE.md` (DoP P1-P9, audit baseline v58, kontras warna, fase M0-M9, keputusan D1-D3).
   M0 (dokumen) SELESAI v59, docs-only. M1 IMPLEMENTED v60 (`Design.kt` baru + `MainActivity.kt`; belum dikompilasi/diuji device).
   M2-M9 PLANNED (update: M2 v62, M3 v68, M4 v71 IMPLEMENTED — lihat `PENDING_UIUX_MILESTONE.md`; sisa M5-M9): tiap fase = 1 batch, visual-only, butuh "lanjut M<n>" eksplisit.
   Temuan nyata: kontras `successGreen`/`skippedAmber` gagal 4.5:1 di tema gelap (2.83/3.42) -> diperbaiki di M1 (v60, per-tema).

## H. ANTREAN (atas permintaan user v71) — butuh approval eksplisit sebelum dikerjakan
**H1. Notifikasi persisten tidak tampil / tidak ter-trigger saat app MASIH HIDUP (belum di-swipe).** Status: BELUM DIREPRODUKSI di device, penyebab belum terbukti. Fungsi terkait: `LagFixTileService.onClick` -> `PersistentTrimService.start()` -> `onStartCommand` (`startForeground(NOTIF_ID=42, ...)`).
- Panduan diagnosis (urut, JANGAN blind-debug): (1) bedakan kasus: (a) notifikasi MASIH terlihat di shade lalu tile diketuk = tak ada yang berubah (idempoten, by design v67); (b) notifikasi TIDAK terlihat (mis. digeser user; Android 13+ memperbolehkan notifikasi FGS di-dismiss) + app belum di-swipe + tile diketuk tak memunculkan apa pun = kasus gagal yang sebenarnya. (2) Untuk (b) ambil logcat tag `PersistentTrimService` kata kunci `LIFECYCLE` + tag `LagFixTileService`: cek `start() -> startForegroundService() dipanggil`, lalu `onStartCommand ... startForeground=ok`, atau warning "ditolak OS" (-> toast tile "ditolak"). (3) Bila `onStartCommand` jalan tapi notifikasi tak tampil: OS/OEM tidak memposting ulang notifikasi FGS yang di-dismiss (klaim pihak ketiga bahwa `startForeground` ulang memostingnya lagi BELUM terbukti di device ini).
- Kandidat perbaikan (semua menyentuh DO-NOT-TOUCH `PersistentTrimService` kecuali opsi 3): (1) flag hidup/mati companion (`onCreate`/`onDestroy`) agar tile bisa memberi umpan balik "sudah aktif" saat servis hidup; (2) `setDeleteIntent` pada notifikasi -> servis memposting ulang saat di-dismiss (hati-hati: jangan membuat loop; tanpa watchdog/alarm, sesuai Battery guard); (3) tile-only: cek `NotificationManager.activeNotifications` (hardcode id 42 + channel `lagfix_keep_alive` = duplikasi konstanta private) lalu toast/subtitle.
- Gate: tile diketuk 3x berturut-turut tanpa swipe -> perilaku konsisten; notifikasi di-dismiss lalu tile diketuk -> tampil lagi; 0 loop; 0 watchdog.

**H2. Acuan upstream mFSTRIM (ZIP `mFSTRIM-build-015eb39`, diunggah user v71; arahan: "jadikan acuan improvisasi base code").** Arahan belum berisi daftar perubahan konkret -> temuan di bawah dicatat sebagai kandidat; TIDAK ada kode LagFix yang diubah karena ZIP ini.
- Temuan upstream (dibaca langsung dari source): package `com.draco.mfstrim`, minSdk 23/targetSdk 31, UI = XML `PreferenceFragmentCompat`. Satu-satunya fungsi: `ListPreference` yang menulis `Settings.Global.fstrim_mandatory_interval` lewat izin `WRITE_SECURE_SETTINGS` (diberikan `adb shell pm grant` atau `su -c pm grant`). Pilihan: unset / 0 (tak pernah) / 1 (tiap reboot) / 1 hari / 2 hari / 3 hari (default) / 1 minggu / 1 bulan. Layar `PermissionActivity` mengunci user + salin perintah ADB.
- Cara kerja (sumber: commit AOSP `4f868ed` + thread XDA mFSTRIM, bukan diuji di sini): setting itu = batas "paksa fstrim" saat boot bila fstrim belum jalan dalam interval; fstrim dikerjakan Android, bukan app; baru dicek saat reboot; nilainya bertahan setelah app di-uninstall; ada user yang mengonfirmasi `last-fstrim` berubah saat boot; satu laporan "tidak jalan di MIUI" kemudian ditarik pelapornya (tak terbukti); perintah `adb shell settings put global fstrim_mandatory_interval 1` setara.
- LagFix saat ini: 0 pemakaian setting ini (grep `mandatory|WRITE_SECURE|Settings.Global` = 0); mekanisme = `sm fstrim` (fallback `sm idle-maint run`) via Shizuku + penjadwalan WorkManager.
- Kandidat adopsi (BELUM dikerjakan): (1) opsi "Paksa trim saat reboot (sistem)" di Pengaturan: via Shizuku `settings put global fstrim_mandatory_interval <ms>` (tanpa izin manifest baru — INFERENSI: shell UID boleh menulis setting ini, sesuai laporan XDA) + tombol reset `settings delete global fstrim_mandatory_interval` (WAJIB, karena nilai bertahan setelah uninstall) + tampilkan nilai sekarang (`settings get global`); (2) preset interval upstream sebagai pilihan; (3) validasi per-OEM/Android 14+ di device sebelum diklaim bekerja. Menyentuh `FstrimExecutor` (DO-NOT-TOUCH) atau helper baru + `Prefs` (DO-NOT-TOUCH) -> butuh approval. **STATUS v83: H2(1) DIKERJAKAN atas perintah user** (helper baru `BootTrimSetting.kt`; `FstrimExecutor.sh` hanya private->internal; `Prefs` TIDAK disentuh — sumber kebenaran = nilai sistem hasil baca-ulang; kartu di `SettingsTab`). Belum terbukti kompilasi/efek boot di device. H2(2) preset interval upstream & H2 tambahan #1-#6 masih antrean (butuh perintah).
- Sengaja TIDAK diadopsi: XML Preferences/AppCompat, plugin OSS licenses, layar izin terkunci, leanback/TV, polling izin tiap 100 ms (melanggar Battery guard), minSdk/targetSdk lama.
- **H2 tambahan (v72, user: "gak temuan improvisasi lain selain paksa trim saat reboot??")** — upstream hanya ~150 baris Kotlin & 1 fitur, jadi yang layak dicontoh sedikit. Urut prioritas (semua BELUM dikerjakan, butuh approval):
  1. **Lisensi open source/atribusi (prioritas tertinggi).** Upstream punya menu "Licenses". LagFix: 0 lisensi/atribusi — tak ada file LICENSE di repo, tak ada di README, tak ada di `AboutDialog` (hanya versi, paket, tautan source). Pustaka yang ikut ter-bundle: Shizuku API+provider, AndroidX/Compose, WorkManager (kebanyakan Apache-2.0; lisensi persis tiap pustaka BELUM diverifikasi di sini; ini bukan nasihat hukum, tapi lisensi semacam ini lazim mensyaratkan salinan lisensi/notice ikut didistribusikan). Rencana tanpa dependency baru: baris "Lisensi open source" di `AboutDialog` -> dialog scrollable berisi daftar pustaka + nama & teks lisensi (statis di `strings.xml`/asset), BUKAN `play-services-oss-licenses` (upstream; menambah dependency Play Services). File: `MainActivity.kt` (`AboutDialog`), `strings.xml`. LISENSI LagFix sendiri = keputusan user (jangan ditebak). Catatan: upstream = BSD-2-Clause (c) 2022 Tyler Nijmeh; kode LagFix independen (Shizuku+Compose), tapi bila kelak MENYALIN kode upstream (bukan sekadar ide) wajib menyertakan notice-nya.
  2. **Alasan utama adopsi H2(1) = lepas dari Shizuku saat boot.** Setelah izin `WRITE_SECURE_SETTINGS` diberikan sekali (via Shizuku `pm grant` dari app, 1 ketukan, atau ADB), app/sistem tak butuh Shizuku hidup lagi: yang mengerjakan fstrim saat boot adalah Android. Ini menutup kelemahan nyata LagFix: Shizuku non-root mati setelah reboot -> `TrimWorker` menunggu <=5 s lalu gagal. Satu keluarga dengan H2(1), tapi motivasinya terpisah.
  3. **Baca-balik nilai sistem sebagai sumber kebenaran.** UI upstream selalu membaca `Settings.Global` aktual (bukan salinan lokal). Bila H2(1) diadopsi: baca nilai sekarang SEBELUM menulis, tampilkan ke user, dan jangan menimpa diam-diam — user yang juga memasang mFSTRIM akan punya dua app menulis setting yang sama.
  4. **Petunjuk "default sistem 3 hari" di kartu interval** (rendah). Upstream melabeli 3 hari (259200000 ms) sebagai default (kecocokan dgn konstanta AOSP belum diverifikasi di sini). LagFix menawarkan preset jam + kustom menit (v44, boleh sangat sering). Kandidat: 1 baris teks bantu netral di kartu Jadwal. JANGAN menulis klaim manfaat/keausan sebagai fakta sebelum diverifikasi.
  5. **R8 / shrinkResources** (sudah ada di backlog D2, bukan temuan baru): upstream release `minifyEnabled true` + `shrinkResources true` dengan 0 aturan kustom; LagFix `isMinifyEnabled = false` (komentar: reflection Shizuku). Upstream TIDAK memakai Shizuku -> bukan bukti aman; jangan diubah tanpa uji device.
  6. **TV/non-touch** (opsional, rendah): upstream mendeklarasikan `uses-feature touchscreen required=false` + leanback launcher + banner; LagFix tidak. Efek di TV box belum diverifikasi; pekerjaan besar (fokus D-pad di Compose) -> hanya bila user menargetkan TV box.
  - **Sengaja TIDAK dicontoh (anti-pola upstream):** polling izin tanpa batas tiap 100 ms (LagFix: loop terbatas 5 s/500 ms); layar izin yang mengunci (`onBackPressed` kosong); `Settings.Global.putString` tanpa try/catch (`SecurityException` bila izin dicabut); `su -c` otomatis di init ViewModel tanpa interaksi (memicu prompt root) dan `ProcessBuilder` yang tak ditunggu/dibaca hasilnya.

## Urutan eksekusi disarankan
- v6: compile OK + A FULLY CLOSED (A1/A2 via device evidence, A3 via source analysis).
- v7 (SELESAI): C1 unit test Prefs+FstrimExecutor.state — 3 file diubah (build.gradle.kts + 2
  test baru), 0 file production. Belum dicompile beneran (sandbox) — perlu run CI/lokal.
- v8 (SELESAI, atas permintaan eksplisit user): D3 (versionName dinamis) + E1 (indikator OK/FAIL
  Riwayat) — 2 file source (`app/build.gradle.kts`, `MainActivity.kt`), 0 file production logic
  lain disentuh. Item B tetap TIDAK dieksekusi (belum ada evidence real jadi masalah).
- v9 (SELESAI, atas permintaan eksplisit user): D1 (catatan risiko reflection Shizuku di README,
  docs-only) + E2 (dialog "Tentang" konsolidasi info app) — 1 file source (`MainActivity.kt`) +
  README.md (VIP doc). 0 file production logic disentuh.
- Backlog bebas urutan (C3 CI test step, D2, F1–F2): hanya kalau user eksplisit minta.
- v13 (SELESAI, atas permintaan eksplisit user "priority first" — B dipilih krn plg berdampak ke
  keandalan fitur utama, drpd C3/D2/F1/F2 yg semuanya tooling/proses): B1-B4 semua selesai (lihat
  detail di atas). 4 file: `TrimWorker.kt`, `UpdateChecker.kt`, `MainViewModel.kt`,
  `MainActivity.kt`. Sisa backlog: C3, D2, F1, F2 (D2 eksplisit "Prioritas rendah" per catatan
  sendiri di atas).
- v14 (SELESAI, lanjutan "priority first" — C3 dipilih krn langsung menaikkan keandalan proses
  validasi batch2 berikutnya, drpd D2 yg eksplisit rendah prioritas atau F1/F2 yg cuma tooling
  opsional): C3 selesai (lihat detail di atas). 1 file: `.github/workflows/build.yml`. Sisa
  backlog: D2 ("Prioritas rendah"), F1, F2.
- v15 (bug fix atas laporan user, evidence CI nyata fail-log-16 — bukan dari daftar
  prioritas/roadmap normal): C4 baru dibuka & langsung ditangani (skip test, bukan tutup
  permanen) — lihat detail C4 di atas. 1 file: `FstrimExecutorTest.kt`. Sisa backlog: C4
  (butuh solusi nyata, belum tertutup), D2, F1, F2.
- v16 (lanjutan "priority first" — C4 dipilih drpd D2 (eksplisit rendah)/F1/F2 (tooling opsional)
  krn representasi gap coverage nyata di logic inti): kandidat (a) PENDING_ROADMAP C4 dicoba — JVM
  arg self-attach + un-skip test. 2 file: `app/build.gradle.kts`, `FstrimExecutorTest.kt`. BELUM
  ada evidence CI nyata, C4 TETAP OPEN sampai konfirmasi run berikutnya. Sisa backlog kalau C4
  belum tuntas: kandidat (b)/(c); kalau C4 tuntas: D2, F1, F2.
- v17 (atas laporan user: kandidat (a) TERBUKTI gagal 2x — fail-log-16 & fail-log-18, error
  identik — + approval eksplisit user utk kandidat (b)): refactor seam/interface `ShizukuGateway`
  diimplementasi, JVM arg kandidat (a) di-revert. 3 file: `FstrimExecutor.kt`,
  `FstrimExecutorTest.kt`, `app/build.gradle.kts`. VERIFIED (evidence CI nyata: laporan user "build
  hijau" — step Unit test lulus utk 10/10 test, step Build/Release lanjut normal). C4 SELESAI &
  DITUTUP. Sisa backlog: D2 ("Prioritas rendah"), F1, F2 — ketiganya opsional, butuh permintaan
  eksplisit user.
- v18 (atas pilihan eksplisit user, dari 3 opsional D2/F1/F2 setelah C4 ditutup): F1 dipilih —
  lint (Android bawaan AGP) + detekt 1.23.8 ditambah ke `build.yml`, non-blocking
  (`continue-on-error: true`) krn blm ada baseline/triase temuan kode lama. 3 file:
  `build.gradle.kts` (root), `app/build.gradle.kts`, `.github/workflows/build.yml`. Validasi statis
  only (brace/paren OK, YAML valid, diff pure-addition thd v17) — blm ada evidence CI nyata. Sisa
  backlog: D2 ("Prioritas rendah"), F2.

- v59 (atas permintaan eksplisit user "planning + embedded doc milestone UI UX premium"): G dibuka, docs-only.
  File: `PENDING_UIUX_MILESTONE.md` (baru), bagian ini. 0 source diubah. Sisa backlog: G/M1-M9 (butuh perintah user), D2, F2.
- v60 (atas perintah user "Lanjut M1"): G/M1 diimplementasi — `Design.kt` (baru), `MainActivity.kt`; detail di `PENDING_UIUX_MILESTONE.md` bagian 9. Sisa backlog: G/M2-M9 (butuh perintah user), D2, F2.
- v61 (permintaan user): arah visual DARK ONLY + Glassmorphism & Glow — `Design.kt`, `MainActivity.kt`, `themes.xml`; detail & checklist device di `PENDING_UIUX_MILESTONE.md` bagian 10. M2-M9 tetap butuh perintah user; kartu Tema & `ThemeMode` sisa (debt kecil, tak dipakai) boleh dibersihkan atas perintah.
- v62 (user "Lanjut??" + screenshot device v61): G/M2a — ikon nav emoji -> vector drawable (`ic_nav_home.xml`, `ic_nav_settings.xml`, `MainActivity.kt`). Temuan device v61 dicatat di `PENDING_UIUX_MILESTONE.md` bagian 11: teks tebal & chip terpotong = BUKAN bug (klarifikasi user v63); label OutlinedTextField interval membungkus -> diperbaiki v64 (label "Kustom (menit)", 1 baris; `MainActivity.kt`; user melaporkan tak membungkus lagi). v65: `UpdateCard` tombol aksi simetris (`MainActivity.kt`, `PENDING_UIUX_MILESTONE.md` bagian 12). v66: G/M2b — ikon status Shizuku di `StatusCard` (`ic_status_ok.xml`, `ic_status_warning.xml`, `strings.xml`, `MainActivity.kt`; `PENDING_UIUX_MILESTONE.md` bagian 13). Sisa: M3-M9 (butuh perintah user).
- v67 (permintaan user): QS tile dialihfungsikan jadi pemicu `PersistentTrimService.start()` (bukan `Scheduler.runOnce()`) — `LagFixTileService.kt`, `strings.xml`, `AndroidManifest.xml` (label tile). Di luar jalur milestone UI/UX.
- v68 (user "Lanjutkan milestone!!"; tile v67 dikonfirmasi berhasil di device): G/M3 — hero status Shizuku + aksi run + progress + empty state Riwayat (`HomeHero.kt` baru, `ic_empty_history.xml` baru, `strings.xml`, `MainActivity.kt`; `PENDING_UIUX_MILESTONE.md` bagian 14). Sisa: M4-M9 (butuh perintah user).
- v69 (user: widget wajib selaras gaya utama + ikon app dirombak total): visual-only — `widget_lagfix.xml`, `widget_card_bg.xml`, `widget_button_bg.xml` (widget dark glass + glow, tombol pil periwinkle) & `ic_launcher_foreground.xml` (chip memori flash + kilau), `ic_launcher_backdrop.xml` baru, `ic_launcher_monochrome.xml` baru, `mipmap-anydpi-v26/ic_launcher.xml` (+`<monochrome>`); `PENDING_UIUX_MILESTONE.md` bagian 15. Di luar M-seri. Sisa: M4-M9 (butuh perintah user).
- v70 (user: ikon QS tile belum ikut revisi ikon): `ic_tile_fstrim.xml` bentuk baru chip+kilau 24dp (juga ikon kecil notifikasi persisten). Pertanyaan "tile hanya bereaksi setelah app di-swipe" dijawab analisis kode (PROJECT_STATE v70), 0 kode diubah. Sisa: M4-M9 (butuh perintah user).

## Eksplisit DI LUAR SCOPE
Tidak ada rencana ganti arsitektur, ganti dependency utama (Shizuku/WorkManager/Compose), migrasi
modul, atau redesign UI big-bang. Pengecualian (v59, permintaan eksplisit user): peningkatan UI/UX bertahap di bagian G,
visual-only per fase. Semua di atas incremental & non-breaking per item.
- v71 (user "Lanjutkan milestone!!" + ZIP referensi mFSTRIM): G/M4 motion & haptic — `Design.kt` (`LagMotion`, `GlassCard(animateSize)`), `HomeHero.kt` (fade progress, hero animateSize), `MainActivity.kt` (Crossfade tab, kartu animateSize, haptic konfirmasi); `PENDING_UIUX_MILESTONE.md` bagian 17. Antrean baru: bagian H (H1 notifikasi persisten saat app hidup + panduan; H2 temuan mFSTRIM + kandidat). Sisa: M5-M9 (butuh perintah user).
- v72 (user: "gak temuan improvisasi lain selain paksa trim saat reboot??"): docs-only — `PENDING_ROADMAP.md` bagian H2 diperluas (6 temuan berurut prioritas + anti-pola yang tak dicontoh); 0 source diubah. Sisa: M5-M9, H1, H2 (butuh perintah user).
- v73 (user: log CI gagal run 58 + "Fix it immediately!!"): FIX kompilasi `HomeHero.kt` (AnimatedVisibility di dalam Box -> animateFloatAsState + alpha); 1 file source, 0 perubahan perilaku dibanding rancangan M4; detail `PENDING_UIUX_MILESTONE.md` bagian 17. Sisa: M5-M9, H1, H2 (butuh perintah user).
- v83 (user: pilih H2 "paksa trim saat reboot"): H2(1) — kartu "Paksa trim saat reboot" di Pengaturan; 4 file source + 1 tes baru (`BootTrimSetting.kt`, `FstrimExecutor.kt` 1 modifier, `MainViewModel.kt`, `MainActivity.kt`, `BootTrimSettingTest.kt`). Sisa: H1, H2(2)+tambahan, D2, F2 (butuh perintah user).
- v84 (user: "lanjut perluas catch logcat aplikasi untuk meneruskan progress yang stagnant!!"): tombol "Ambil logcat sistem" (snapshot bukti ke Documents/LagFix) — menyasar investigasi mandek: notifikasi persisten (LIFECYCLE `ensureShowing`, v78) + bukti trim saat reboot (H2, v83). 3 file source + 1 tes (`LogcatSnapshot.kt` baru, `CrashLogger.kt` +`writeDiagnostic`, `MainActivity.kt`, `LogcatSnapshotTest.kt` baru). Sisa: pencatatan trim-sistem ke Riwayat (butuh bukti dulu + approval `Prefs`), H1, H2(2)+tambahan, D2, F2 (butuh perintah user).
- v85 (user: "Lanjutkan progress!!" + snapshot logcat v84): analisis snapshot (H2 terbukti kuat via `last-fstrim`; logcat tak memuat baris boot krn buffer ~34 dtk) + FIX filter `LogcatSnapshot.kt` (nama paket ikut cocok); 2 file source (`LogcatSnapshot.kt`, `LogcatSnapshotTest.kt`). Sisa: kandidat catat trim-sistem ke Riwayat, H2(2), lisensi OSS, H1, D2, F2 (butuh perintah user).
- v86 (user: "Biasakan untuk bikin logcat nya itu totalitas, jangan setengah-setengah dan yang ketangkap malah terhalang bug!!"): snapshot logcat TOTAL — dump mentah .zip streaming + status sistem/sisi-app + buffer 16M; 3 file source (`LogcatSnapshot.kt`, `LogcatSnapshotTest.kt`, `MainActivity.kt` teks/umpan balik) + fix tes v85. Sisa: kandidat catat trim-sistem ke Riwayat, H2(2), lisensi OSS, H1, D2, F2 (butuh perintah user).
