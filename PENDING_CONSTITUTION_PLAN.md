[BRANDING_NAME: LagFix]
[TERMUX_ROOT: LagFix]

# PENDING_CONSTITUTION_PLAN — Rencana tertanam berbasis Konstitusi v3.5 (LOCKED)

**Status v102: Q1, Q4, Q5 SELESAI (docs-only, 0 source).** Q2 + Q3a menunggu uji/data dari user (APK `build-84`); Q3b, Q6, Q7 butuh perintah user. Tabel §4 memuat status terkini.

Dasar: dibaca langsung dari ZIP `LagFix-main.zip` (HEAD = v99; `.cursorrules`, `PROJECT_STATE.md`, `CHANGELOG.md`,
`PENDING_*.md`, `build.yml`, `app/build.gradle.kts`, manifest, 19 berkas Kotlin `app/src/main`). **PLANNING ONLY — 0 source,
0 dependency, 0 izin manifest diubah di batch v100.** Nomor baris = ZIP v99. Sandbox tanpa Gradle/SDK/device -> semua temuan
dari baca/grep (level bukti: SUMBER), BELUM dikompilasi, BELUM diuji device. Tiap item antrean butuh perintah eksplisit user.

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

## 2. Audit Android Vital Guards (grep source v99)
| Guard | Bukti | Status |
|-------|-------|--------|
| Crash handling | `Thread.setDefaultUncaughtExceptionHandler` 1x (`CrashLogger.kt:48`); tulis ke `Documents/LagFix` via `MediaStore.Files` + siklus `IS_PENDING` (`:118-135`). `ErrorBoundary` khusus: 0 (Compose tak punya bawaan; `runCatching` 42x di sumber) | Handler global OK |
| UI state & lifecycle | `rememberSaveable` 16x, `ViewModel` dipakai; `selectedTab` `rememberSaveable` (`MainActivity.kt:157`) | OK (dari sumber) |
| Compose performance | `LaunchedEffect` 5x; transisi tab = `graphicsLayer` (0 rekomposisi/frame, M4); `derivedStateOf` 0, `DisposableEffect` 0 | Tak ada pelanggaran terbukti; profil rekomposisi BELUM diukur |
| UI truncation | `enableEdgeToEdge` (`:120`), `Scaffold` (`:207`), `imePadding` (`:267`), `verticalScroll` 4x; `systemBarsPadding`/`WindowInsets` eksplisit 0; `LazyColumn` 0 | Indikasi OK; font scale 1.3/2.0 BELUM DEVICE-VERIFIED (M9/P5) |
| Thread safety | `Dispatchers.IO` 20x; `runBlocking` 0; `Thread.sleep` 0; `UpdateChecker` mendokumentasikan "WAJIB dari Dispatchers.IO" | OK |
| Battery & background | `AlarmManager` 0; jadwal = WorkManager. `while (true)` 2x BUKAN watchdog: `LogcatSnapshot.kt:110` (`copyCapped`, berhenti di EOF/batas byte) dan `MainActivity.kt:299` (pemblokir sentuhan, hanya selama `transitioning`, suspend). Flow/`StateFlow` 0 di sumber -> `collectAsStateWithLifecycle` tidak relevan | OK; FGS persisten = lihat K1 |
| Security | Grep `api_key/secret/passw/bearer/ghp_/AIza` di `app/src` = 0 temuan; `BuildConfig` 0 pemakaian; keystore via GitHub Secrets (`build.yml` step Decode keystore) | OK |
| Streaming & OOM | `UpdateChecker.kt:126` `inputStream.use { FileOutputStream(dest).use { input.copyTo(output) } }` di IO; `readBytes` 0; dump logcat dialirkan dgn batas (`copyCapped`). `UpdateChecker.kt:138` `readText()` utk JSON rilis/CHANGELOG (teks kecil, tak dibatasi: risiko rendah, dicatat) | Memenuhi maksud; Okio literal = K2 |

Temuan jam 12 jam (permintaan user v77): tampil di app memakai `formatClock12`/`formatStamp12h` (`TimeFormat.kt`) dan `formatTime` `hh:mm a` (`MainActivity.kt:1384`); format SIMPAN `dd/MM HH:mm` (`Prefs.kt:64`) sengaja 24 jam (baris lama tetap terbaca). Sisa 24 jam: `LogcatSnapshot.kt:80` (`yyyy-MM-dd HH:mm:ss`, stempel isi berkas diagnostik) -> K6.

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

## 4. Antrean batch (urut risiko rendah -> tinggi; SEMUA butuh perintah user)
| ID | Isi | File source target | Gate | Status |
|----|-----|--------------------|------|--------|
| Q1 | DAILY UPDATE v100 lalu baca hasil CI v99: detekt harus 1 -> 0 (`MaxLineLength` `BoundedShellTest.kt:9`); APK rilis + artifact debug terbit. Merah -> upload `LagFix-fail-log-<run>.zip`, baca `LagFix_build_fail_log_<run>.txt` + `lint_detekt/detekt/detekt.txt`, perbaiki HANYA baris yang dilaporkan | 0 (atau baris dilaporkan) | CI hijau penuh | SELESAI v102 (bukti: rilis `build-84`, commit `e031afa`, lihat entri v102 `PROJECT_STATE.md`) |
| Q2 | Uji device v98/v99: Pengaturan > Log diagnostik > "Ambil logcat sistem" selesai/tidak + lama + isi snackbar; penanda "[dihentikan: melewati batas waktu]"/"(dilewati: anggaran waktu...)" = bagian lambat; Riwayat & daftar log tampil 5 teratas + "Tampilkan semua (N)" | 0 | Laporan user | MENUNGGU USER |
| Q3a | Notifikasi persisten (BUG_TARGET RESUME v97): uji data TANPA kode: buka app, HOME (jangan swipe), ketuk QS tile, buka app, "Ambil logcat sistem" SEGERA; baca `LIFECYCLE ensureShowing ... shown=`, `onStartCommand ... startForeground=`, `Service.startForeground() not allowed due to bg restriction`, `am_foreground_service_start/stop`, `isBackgroundRestricted` | 0 | Bukti log | MENUNGGU USER |
| Q3b | Kode notifikasi, urut risiko: (i) cabut/ganti baris v96 `ensureShowing` di `onResume` (`MainActivity.kt:134`) bila tak berguna; (ii) kartu UI dari `ActivityManager.isBackgroundRestricted`; (iii) log `persistent_service` jujur (cek `isForegroundNotificationShown` SETELAH `startForeground()`) = sentuh `PersistentTrimService.kt` (DO-NOT-TOUCH); (iv) tombol Shizuku `cmd appops set com.lagfix.fstrim RUN_ANY_IN_BACKGROUND allow` (fitur baru, ubah setelan sistem, jelaskan dampak baterai) | (i) `MainActivity.kt`; (ii) `MainActivity.kt`+`strings.xml`+komposabel baru; (iii) `PersistentTrimService.kt`; (iv) 3-5 file | Tanpa watchdog/alarm/loop; tile 3x berturut-turut konsisten; 0 fungsi member baru di `MainViewModel` (TEPAT 10) & `LogcatSnapshot` (TEPAT 10) | Tertahan Q3a |
| Q4 | Housekeeping docs: arsipkan RESUME POINT/entri `PROJECT_STATE.md` v25-v9x ke `docs/archive/` (preseden v28: `PROJECT_STATE_v2-v24.md`), pindahkan boilerplate aturan kode yang diulang tiap RESUME POINT ke 1 berkas dan rujuk namanya | `PROJECT_STATE.md`, `docs/archive/*` (VIP) | `[RESUME POINT]` tetap baris terakhir & mandiri; 0 info hilang | SELESAI v101 (archive `docs/archive/PROJECT_STATE_v25-v93.md` byte-identik; pemindahan boilerplate aturan RP DITAHAN: RP harus mandiri utk cold start) |
| Q5 | Sinkron `.cursorrules` ke Konstitusi v3.5 (drift, lihat §6) | `.cursorrules` (VIP) | Selaras preferensi user; 0 aturan hilang | SELESAI v101 (`.cursorrules` 1,6 KB -> ±4,4 KB, selaras v3.5) |
| Q6 | Antrean lama: pemangkasan FIFO 50 berkas di `Documents/LagFix` termasuk `.zip`; menyatukan 2 baca-ulang logcat penuh di ringkasan (grep fstrim & grep app membaca ±56 MB lagi); H2(2) preset interval; H2 tambahan #1 lisensi OSS; D2 (R8/minify, berisiko); F2 (Dependabot) | per item | per item | Antrean |
| Q7 | M9 DoP P1-P9 DEVICE-VERIFIED (TalkBack, font scale 1.3/2.0, layar kecil/landscape/gesture-nav, kontras) | sesuai temuan (<=5) | Screenshot user | Antrean |

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
- **K2 Okio literal vs "JANGAN tambah dependency":** `java.io` `use{}` + `copyTo` di IO sudah memenuhi maksud (tanpa muat byte penuh). Okio = dependency baru -> hanya bila user memutuskan.
- **K3 Gerbang lint/detekt:** konstitusi "non-blocking saat evaluasi, wajib hijau sebelum commit" vs repo BLOCKING di CI. Dipertahankan BLOCKING (keputusan user v88; opsi PRAGMATIS ditolak).
- **K4 `collectAsStateWithLifecycle`:** sumber 0 Flow; `lifecycle-runtime-compose` bukan dependency (hanya `lifecycle-viewmodel-ktx:2.8.7`). Bila kelak ada Flow di UI -> butuh dependency baru + persetujuan.
- **K5 Sinkron `.cursorrules` (Q5):** DILAKUKAN v101 (perintah "Mulai kerjakan!!").
- **K6 Jam 24 jam di berkas diagnostik (`LogcatSnapshot.kt:80`):** dibiarkan 24 jam agar sejajar dengan jam logcat/`dumpsys`; ubah ke 12 jam hanya bila user menghendaki (menyentuh format isi berkas + tes).

## 8. Di luar scope
Ganti arsitektur/dependency utama (Shizuku/WorkManager/Compose), migrasi modul, redesign UI big-bang, jalur revive berbasis watchdog/alarm.
