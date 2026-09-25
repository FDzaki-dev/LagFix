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
3. `build.yml` saat ini cuma assembleRelease/Debug, belum ada step `test` — kalau C1 dikerjakan,
   tambah step test di CI sebagai validasi tambahan (bukan pengganti verifikasi behavior nyata).

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
1. Belum ada lint/static-analysis (ktlint/detekt) di `build.yml`.
2. Belum ada dependency-update check otomatis (mis. Dependabot) utk `dev.rikka.shizuku`.

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

## Eksplisit DI LUAR SCOPE
Tidak ada rencana ganti arsitektur, ganti dependency utama (Shizuku/WorkManager/Compose), migrasi
modul, atau redesign UI besar. Semua di atas incremental & non-breaking per item.
