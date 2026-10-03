[BRANDING_NAME: LagFix]
[TERMUX_ROOT: LagFix]

# PENDING_CONSTITUTION_PLAN — Rencana tertanam berbasis Konstitusi v3.5 (LOCKED)

**Status v103 (perintah user: "Yang bisa diperhatikan via lintdebug detekt aja lah"): jalur pengamat guard = `lintDebug` + `detekt` SAJA (§0).** Q1, Q4, Q5 SELESAI. Q2, Q3a, Q7 DILEPAS dari gerbang (tak teramati lint/detekt; bukti device dari user = opsional, bukan syarat). Q3b ditahan. v104: Q9 dikerjakan (3 berkas, kode; menunggu CI). v107: CI #87/#88 hijau (Q9/Q10 terbukti). v108: Q8 + Q11 dikerjakan (perintah user "lanjutkan progress yg bisa dgn modal lintdebug/detekt only"). v109: CI #90 hijau -> Q8/Q11 terbukti; TIDAK ADA lagi item antrean yang teramati lint/detekt. Tabel §4 memuat status terkini.

Dasar: dibaca langsung dari ZIP `LagFix-main.zip` (HEAD = v99; `.cursorrules`, `PROJECT_STATE.md`, `CHANGELOG.md`,
`PENDING_*.md`, `build.yml`, `app/build.gradle.kts`, manifest, 19 berkas Kotlin `app/src/main`). **PLANNING ONLY — 0 source,
0 dependency, 0 izin manifest diubah di batch v100.** Nomor baris = ZIP v99. Sandbox tanpa Gradle/SDK/device -> semua temuan
dari baca/grep (level bukti: SUMBER), BELUM dikompilasi, BELUM diuji device. Tiap item antrean butuh perintah eksplisit user.

## 0. Jalur pengamat guard = lintDebug + detekt (keputusan user v103)
- Satu-satunya pengawas otomatis guard: `lintDebug` (AGP, `warningsAsErrors`, `app/lint.xml`) + `detekt` 1.23.8 (`buildUponDefaultConfig = true`, `allRules = false`, `maxIssues` 0, `config/detekt/detekt.yml`), keduanya BLOCKING di CI (gerbang `build.yml`, K3). Selaras konstitusi: aturan difokuskan pada potensi bug yang bisa diamati agen.
- Konsekuensi: (1) guard TIDAK dinyatakan "OK/terverifikasi" tanpa keluaran alat; temuan grep = informasi, bukan bukti. (2) Item yang tak teramati kedua alat TIDAK jadi gerbang dan tak dikejar (lihat §2, §4, §7). (3) Perubahan aturan/konfigurasi lint-detekt = batch tersendiri atas perintah user (CI BLOCKING: salah konfigurasi = build merah). (4) Bukti HP dari user tetap diterima bila diberikan, tapi bukan syarat.
- Bukti dasar: CI v99 hijau (rilis `build-84`, commit `e031afa`, entri v102 `PROJECT_STATE.md`) = 0 temuan lint & detekt pada konfigurasi sekarang.

## 1. Peta Konstitusi v3.5 -> LagFix
| Aturan | Penerapan di repo | Status |
|--------|-------------------|--------|
| P0 STABILITY+ZERO-REGRESSION > USER INTENT + ZIP > PROJECT_STATE > GUARDS > OUTPUT | ZIP terbaru = kebenaran; Git = mirror (DAILY UPDATE: simpan lokal -> reset ke `FETCH_HEAD` -> ganti isi dgn ZIP -> rollback otomatis bila file <70% / manifest hilang) | Berlaku |
| Cold Start | `[BRANDING_NAME: LagFix]`, `[TERMUX_ROOT: LagFix]` di baris 1-2 `PROJECT_STATE.md`; `[RESUME POINT]` = baris terakhir | Berlaku |
| ESTAFET SEEDING `[FITUR/BUG] -> [STATUS] -> [LANGKAH]` | RESUME POINT v95-v99 sudah memakai skema ini; v100 melanjutkan | Berlaku |
| Descending Changelog | `CHANGELOG.md` v99 di baris 3 (terbaru di atas) | Terpenuhi |
| NO SCOPE CREEP, maks 3-5 file source | VIP (`PROJECT_STATE.md`, `README.md`, `CHANGELOG.md`, `.cursorrules`, `PENDING_*.md`) dikecualikan | Berlaku |
| NO STRUCTURAL REWRITE | `MainActivity.kt` (70 KB) TIDAK dipecah/ditulis ulang; komposabel baru -> berkas baru | Berlaku |
| BUG ISOLATION | RESUME POINT wajib menyebut fungsi/baris cacat (preseden v95-v99) | Berlaku |
| EXTERNAL REFERENCING | `PROJECT_STATE.md` 326.984 B (75 baris RESUME POINT = 104.847 B) -> v101: riwayat v25-v93 dipindah ke berkas arsip, `PROJECT_STATE.md` ±45 KB | Terpenuhi v101 (Q4) |
| Proactive Archiving | `PENDING_*.md`/histori diarsipkan ke `docs/archive/` HANYA bila task menyentuh housekeeping docs; v100 TIDAK (0 arsip); v101 = task housekeeping (Q4): hanya riwayat `PROJECT_STATE.md`, `PENDING_*.md` TIDAK diarsipkan | Dipakai v101 |
| AUTO-HALT | token <20%, error-loop buntu 3 iterasi, ancaman OOM -> simpan state -> docs -> repack ZIP -> handoff | Berlaku |
| STRICT LINT & DETEKT | CI: step Lint (`build.yml:44`) + Detekt (`:50`) lalu Gerbang BLOCKING (`:71`); detekt `maxIssues` 0 | Lebih ketat dari konstitusi (lihat K3) |
| Termux IMMUTABLE (`||` literal, commit 1 baris terkuotasi, no force push) | Dipakai apa adanya; `[Deskripsi Perubahan]` = berkas/komponen aktual | Berlaku |

## 2. Peta guard -> pengamat (lintDebug / detekt)
Bukti tools: dokumentasi detekt (GlobalCoroutineUsage default tidak aktif; InjectDispatcher default aktif tapi "Requires Type Resolution"; SleepInsteadOfDelay default aktif; rule bertanda type-resolution DILEWATI saat type resolution mati, catatan rilis detekt 1.22) + `build.yml` (CI menjalankan `gradle detekt` biasa = tanpa type resolution) + grep sumber v99 (angka). Rule lint di luar yang tertulis di `app/lint.xml` TIDAK diverifikasi di CI repo ini.

| Guard (konstitusi) | Teramati lint/detekt? | Keadaan di repo | Tindakan |
|---|---|---|---|
| Crash handling (`setDefaultUncaughtExceptionHandler`, log MediaStore) | TIDAK (tak ada rule) | handler ada di `CrashLogger.kt` (hasil grep, bukan hasil alat) | Dipegang saat menulis kode; tanpa klaim verifikasi |
| State tahan rotasi (`rememberSaveable`/ViewModel) | Sebagian: lint Compose (mis. state tak di-`remember`) bila rule-nya ikut library; TIDAK diverifikasi | 0 temuan lint di CI v99 | Tak dikejar lebih jauh |
| Recomposition / side-effect | Sebagian (lint Compose); sama seperti baris di atas | 0 temuan lint di CI v99 | Tak dikejar lebih jauh |
| UI terpotong (WindowInsets, scroll) | Hampir tidak (lint tak mengukur layar nyata) | `enableEdgeToEdge`, `Scaffold`, `imePadding`, `verticalScroll` ada (grep) | Pemeriksaan visual = Q7, DILEPAS dari gerbang |
| Thread safety (I/O di IO, tak blok Main) | Hampir tidak: `SleepInsteadOfDelay` aktif default (tipe resolusi tidak diverifikasi); `InjectDispatcher` butuh type resolution -> DILEWATI CI, dan MENOLAK `Dispatchers.IO` langsung (20 pemakaian, bertentangan dgn konstitusi) -> JANGAN diaktifkan | `runBlocking` 0, `Thread.sleep` 0, `GlobalScope` 0 (grep) | Tak ada rule baru di batch ini; kandidat Q8 |
| Baterai/background (tanpa watchdog, WorkManager) | Sebagian: lint bawaan (izin alarm/wake lock, `BatteryLife` diizinkan 1 berkas di `lint.xml`); tak ada deteksi "watchdog" | `AlarmManager` 0; 2 `while (true)` bukan watchdog (grep) | FGS persisten = K1 (dibekukan) |
| Security (tanpa hardcode secret) | TIDAK (lint/detekt bukan pemindai secret) | 0 pola secret di `app/src` (grep); keystore via GitHub Secrets | Di luar jalur; tanpa klaim verifikasi |
| Streaming/OOM (Okio, `use{}`) | TIDAK | `UpdateChecker.kt` memakai `use{}`+`copyTo` di IO (grep) | Okio literal (K2) GUGUR dari antrean |
| Lint/detekt bersih | YA (inti) | CI v99 hijau; supresi tertulis: 12 `@Suppress` (mayoritas `TooGenericExceptionCaught`, `DEPRECATION`, `UNCHECKED_CAST`), 0 `@SuppressLint`, pengecualian per-berkas di `lint.xml` | Pertahankan; supresi baru wajib beralasan tertulis |

Kandidat penguatan yang BISA diamati tanpa type resolution (DIKERJAKAN v108, lihat Q8; CI #90 hijau): `ForbiddenImport` untuk `kotlinx.coroutines.runBlocking` dan `kotlinx.coroutines.GlobalScope` (0 import sekarang -> tidak membuat CI merah dari kode yang ada; butuh daftar `imports` di `detekt.yml`).

## 3. Rantai validasi -> pipeline nyata
| Tahap konstitusi | Alat di repo | Catatan |
|------------------|--------------|---------|
| Syntax | `xmllint` (XML), delta `{}` `()` (Kotlin) di sandbox | Satu-satunya yang bisa dijalankan di sandbox |
| Build | CI step Build (`build.yml:83`) | Hanya CI |
| Affected behavior | Unit test (`build.yml:22`) + checklist device dari user | Test = logika murni; UI/OEM wajib device |
| Regression | P9: jadwal on/off+interval, run manual, widget, tile, toggle persisten, rotasi, cek update | Checklist user |
| Package integrity | ZIP datar `LagFix_v<Batch>.zip` (tanpa folder pembungkus, termasuk `.github/`, `.cursorrules`, `.gitignore`, `docs/archive/.gitkeep`); rollback DAILY UPDATE | Dicek tiap batch |
| Docs | CHANGELOG (descending) + PROJECT_STATE (+RESUME POINT) + PENDING terkait | Wajib tiap batch |

Pre-commit hook `./gradlew detekt lintDebug`: repo tidak punya `gradlew`/`gradle/` dan Termux tanpa Android SDK -> penegakan = CI (lebih ketat: BLOCKING, bukan evaluasi). Jangan kembali ke non-blocking/baseline (dilarang di RESUME POINT v95+).

## 4. Antrean batch (urut risiko rendah -> tinggi; tiap item butuh perintah user; gerbang = lintDebug + detekt, lihat §0)
| ID | Isi | File source target | Gate | Status |
|----|-----|--------------------|------|--------|
| Q1 | DAILY UPDATE v100 lalu baca hasil CI v99: detekt harus 1 -> 0 (`MaxLineLength` `BoundedShellTest.kt:9`); APK rilis + artifact debug terbit. Merah -> upload `LagFix-fail-log-<run>.zip`, baca `LagFix_build_fail_log_<run>.txt` + `lint_detekt/detekt/detekt.txt`, perbaiki HANYA baris yang dilaporkan | 0 (atau baris dilaporkan) | CI hijau penuh | SELESAI v102 (bukti: rilis `build-84`, commit `e031afa`, lihat entri v102 `PROJECT_STATE.md`) |
| Q2 | Uji device v98/v99: Pengaturan > Log diagnostik > "Ambil logcat sistem" selesai/tidak + lama + isi snackbar; penanda "[dihentikan: melewati batas waktu]"/"(dilewati: anggaran waktu...)" = bagian lambat; Riwayat & daftar log tampil 5 teratas + "Tampilkan semua (N)" | 0 | Laporan user | DILEPAS v103 (hanya teramati di HP; bukan gerbang; hasil dari user diterima bila ada) |
| Q3a | Notifikasi persisten (BUG_TARGET RESUME v97): uji data TANPA kode: buka app, HOME (jangan swipe), ketuk QS tile, buka app, "Ambil logcat sistem" SEGERA; baca `LIFECYCLE ensureShowing ... shown=`, `onStartCommand ... startForeground=`, `Service.startForeground() not allowed due to bg restriction`, `am_foreground_service_start/stop`, `isBackgroundRestricted` | 0 | Bukti log | DILEPAS v103 (tak teramati lint/detekt; opsional dari user) |
| Q3b | Kode notifikasi, urut risiko: (i) cabut/ganti baris v96 `ensureShowing` di `onResume` (`MainActivity.kt:134`) bila tak berguna; (ii) kartu UI dari `ActivityManager.isBackgroundRestricted`; (iii) log `persistent_service` jujur (cek `isForegroundNotificationShown` SETELAH `startForeground()`) = sentuh `PersistentTrimService.kt` (DO-NOT-TOUCH); (iv) tombol Shizuku `cmd appops set com.lagfix.fstrim RUN_ANY_IN_BACKGROUND allow` (fitur baru, ubah setelan sistem, jelaskan dampak baterai) | (i) `MainActivity.kt`; (ii) `MainActivity.kt`+`strings.xml`+komposabel baru; (iii) `PersistentTrimService.kt`; (iv) 3-5 file | Tanpa watchdog/alarm/loop; tile 3x berturut-turut konsisten; 0 fungsi member baru di `MainViewModel` (TEPAT 10) & `LogcatSnapshot` (TEPAT 10) | DITAHAN v103 (tak teramati lint/detekt; BUG_TARGET v97 TETAP terbuka; 0 perubahan kode tanpa perintah eksplisit user) |
| Q4 | Housekeeping docs: arsipkan RESUME POINT/entri `PROJECT_STATE.md` v25-v9x ke `docs/archive/` (preseden v28: `PROJECT_STATE_v2-v24.md`), pindahkan boilerplate aturan kode yang diulang tiap RESUME POINT ke 1 berkas dan rujuk namanya | `PROJECT_STATE.md`, `docs/archive/*` (VIP) | `[RESUME POINT]` tetap baris terakhir & mandiri; 0 info hilang | SELESAI v101 (archive `docs/archive/PROJECT_STATE_v25-v93.md` byte-identik; pemindahan boilerplate aturan RP DITAHAN: RP harus mandiri utk cold start) |
| Q5 | Sinkron `.cursorrules` ke Konstitusi v3.5 (drift, lihat §6) | `.cursorrules` (VIP) | Selaras preferensi user; 0 aturan hilang | SELESAI v101 (`.cursorrules` 1,6 KB -> ±4,4 KB, selaras v3.5) |
| Q6 | Antrean lama: pemangkasan FIFO 50 berkas di `Documents/LagFix` termasuk `.zip`; menyatukan 2 baca-ulang logcat penuh di ringkasan (grep fstrim & grep app membaca ±56 MB lagi); H2(2) preset interval; H2 tambahan #1 lisensi OSS; D2 (R8/minify, berisiko); F2 (Dependabot) | per item | per item | Antrean |
| Q7 | M9 DoP P1-P9 DEVICE-VERIFIED (TalkBack, font scale 1.3/2.0, layar kecil/landscape/gesture-nav, kontras) | sesuai temuan (<=5) | Screenshot user | DILEPAS v103 (kecuali ada temuan lint; pemeriksaan visual bukan gerbang) |
| Q8 | Perkuat pengamat (kandidat): `ForbiddenImport` (`runBlocking`, `GlobalScope`) di `config/detekt/detekt.yml`; tanpa `InjectDispatcher` | `config/detekt/detekt.yml` (konfigurasi) | CI hijau; 0 temuan baru (grep: 0 import) | SELESAI v108 di konfigurasi (`ForbiddenImport` aktif, `imports` = `runBlocking` + `GlobalScope`; grep 0 import di `app/src` main+test); CI TERBUKTI hijau (run #90): `detekt` BUILD SUCCESSFUL, `Findings (0)`; konfigurasi diterima tanpa error. BELUM terbukti aturan itu MENEMBAK (0 impor terlarang di kode, jadi tak ada yang bisa ditandai) |
| Q9 | Terapkan item `docs/konfigurasi_notifikasi_persistent.md` yang belum ada, KECUALI kebijakan Google Play (perintah user v104) | `PersistentTrimService.kt`, `strings.xml`, `AndroidManifest.xml` | lintDebug + detekt hijau di CI | SELESAI v104 di kode; CI TERBUKTI hijau lewat run #87 (v105 memuat kode v104; test, assembleRelease, assembleDebug, detekt, lintDebug lolos per tangkapan layar user) & #88 (laporan: lint 0 error 0 warning, detekt 0 temuan); perilaku di device BELUM diuji |
| Q10 | Terapkan `docs/konfigurasi_bypass_restricted_os.md` (perintah user v105): bagian 1 channel MIN + VISIBILITY_SECRET, bagian 3 tautan vendor. Bagian 2 (`setAlarmClock` tiap 60 dtk) DITOLAK: melanggar P0/Vital Guard Baterai & K1 | `PersistentTrimService.kt`, `VendorSettings.kt` (baru), `MainActivity.kt`, `strings.xml` | lintDebug + detekt hijau di CI | Bagian 1 & 3 SELESAI v105 di kode, CI TERBUKTI hijau (run #87, #88; lihat Q9); perilaku di device BELUM diuji; bagian 2 TIDAK dikerjakan (butuh amandemen eksplisit aturan) |
| Q11 | Temuan informational lint run #88 yang bisa dikerjakan di kode: `AutoboxingStateCreation` `MainActivity.kt:157` (`rememberSaveable { mutableStateOf(0) }` -> `mutableIntStateOf`). Item lain (`OldTargetApi`, 5x `GradleDependency`) = informational SENGAJA (`lint.xml` v89; backlog upgrade/F2) | `MainActivity.kt` (1 baris) | lintDebug + detekt hijau; state tab TETAP bertahan rotasi (Vital Guard UI State) | SELESAI v108 di kode (`MainActivity.kt`: +1 import `mutableIntStateOf`, baris 158 `mutableIntStateOf(0)`); CI TERBUKTI hijau (run #90): item `AutoboxingStateCreation` HILANG dari `lint-results-debug.txt` (0 kemunculan; sisa 6 Information by design). Dasar: runtime Compose menyediakan state-int yang bisa disimpan `rememberSaveable` (`ParcelableSnapshotMutableIntState`; dari pengetahuan, BELUM diverifikasi kompilasi/device di sandbox). Rotasi layar di HP (tab aktif tetap bertahan) = opsional dari user, BELUM diuji |

## 5. Protokol tiap batch (ringkas)
1. Cold start dari `[RESUME POINT]`; sebut ZIP basis. 2. Kunci scope: daftar <=3-5 file source + VIP. 3. Bug: tulis fungsi/baris
cacat (isolasi). 4. Edit minimum; DO-NOT-TOUCH (`PersistentTrimService`, `LagFixApp`, `BootReceiver`, `ic_tile_fstrim.xml`, `CrashLogger`/
`FstrimExecutor` di luar baris yang dilaporkan, `Prefs`, `Scheduler`/`TrimWorker`, `UpdateChecker`, signing/CI release) hanya atas
perintah. 5. Validasi sandbox (syntax) + jujur menyatakan belum dikompilasi/diuji. 6. Docs: `CHANGELOG.md` (+entri di atas),
`PROJECT_STATE.md` (+entri +RESUME POINT skema ESTAFET), `PENDING_*` terkait. 7. ZIP datar `LagFix_v<Batch>.zip`. 8. Skrip
Termux DAILY UPDATE dengan `[Deskripsi Perubahan]` deterministik. 9. AUTO-HALT bila kondisi §1 terpenuhi.

## 6. Drift `.cursorrules` terhadap Konstitusi v3.5 — DITUTUP v101 (Q5); daftar di bawah = kondisi SEBELUM v101
- Prioritas: `.cursorrules` "P1 USER INTENT & ZIP/SOURCE" vs v3.5 "P0: STABILITY + ZERO-REGRESSION > USER INTENT + ZIP/SOURCE".
- Output chat: `.cursorrules` "ringkasan <=5 baris (blok kode)" vs v3.5 ringkasan bullet DILARANG dibungkus backticks; urutan ringkasan -> ZIP -> skrip Termux.
- RESUME POINT: `.cursorrules` tanpa skema vs v3.5 `[FITUR/BUG] -> [STATUS] -> [LANGKAH]`.
- Changelog: `.cursorrules` "append-only" vs v3.5 descending (terbaru di atas; `CHANGELOG.md` sudah descending).
- Dokumen VIP: `.cursorrules` hanya `PROJECT_STATE.md`, `README.md`, `CHANGELOG.md` vs v3.5 + `.cursorrules` + `PENDING_*.md`.
- Belum ada di `.cursorrules`: AUTO-HALT terukur, NO STRUCTURAL REWRITE, BUG ISOLATION, EXTERNAL REFERENCING, Proactive Archiving, STRICT LINT & DETEKT, Android Vital Guards rinci, kotak Termux IMMUTABLE.

## 7. Konflik / keputusan terbuka (default = status quo, 0 risiko)
- **K1 FGS persisten vs "foreground service abadi dilarang":** `PersistentTrimService` ada atas permintaan user (v25-v27), opsional, default OFF (`Prefs.kt:46-48` `persistentService` default `false`; manifest `foregroundServiceType="specialUse"`). USER INTENT > GUARDS -> dipertahankan, DIBEKUKAN: tak diperluas, tanpa watchdog/AlarmManager/loop revive.
- **K2 Okio literal vs "JANGAN tambah dependency":** GUGUR v103 (tak teramati lint/detekt; `java.io` `use{}`+`copyTo` di IO dipertahankan). Okio hanya bila user memutuskan.
- **K3 Gerbang lint/detekt:** konstitusi "non-blocking saat evaluasi, wajib hijau sebelum commit" vs repo BLOCKING di CI. Dipertahankan BLOCKING (keputusan user v88; opsi PRAGMATIS ditolak). Jadi gerbang tunggal pengamat guard (§0).
- **K4 `collectAsStateWithLifecycle`:** GUGUR v103 (tak teramati lint/detekt; sumber 0 Flow di UI).
- **K5 Sinkron `.cursorrules` (Q5):** DILAKUKAN v101 (perintah "Mulai kerjakan!!").
- **K6 Jam 24 jam di berkas diagnostik (`LogcatSnapshot.kt:80`):** dibiarkan 24 jam agar sejajar dengan jam logcat/`dumpsys`; ubah ke 12 jam hanya bila user menghendaki (menyentuh format isi berkas + tes).

## 8. Di luar scope
Ganti arsitektur/dependency utama (Shizuku/WorkManager/Compose), migrasi modul, redesign UI big-bang, jalur revive berbasis watchdog/alarm.
