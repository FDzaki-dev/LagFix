# LagFix (fstrim)
Utilitas Android non-root untuk memicu dan menjadwalkan `fstrim` (TRIM pada UFS/eMMC) dengan interval kustom.

## Cara kerja
Menjalankan `sm fstrim` (fallback `sm idle-maint run`) sebagai shell UID lewat **Shizuku**. Penjadwalan: WorkManager (preset 6 jam – 7 hari atau interval kustom isi sendiri mulai 15 menit; toggle opsional "Interval radikal" membuka interval kustom mulai 1 menit lewat rantai WorkManager sekali-jalan, tanpa jaminan waktu tepat saat Doze).

## Paksa trim saat reboot (v83)
Kartu di tab Pengaturan menulis setting sistem `fstrim_mandatory_interval` = 1 ms lewat Shizuku (`settings put global`), supaya Android sendiri memaksa fstrim saat boot (cara kerja ala mFSTRIM; efeknya belum diverifikasi di perangkat). Nilai dibaca dulu dan dibaca ulang setelah ditulis; tombol Reset menghapus kuncinya (`settings delete global`) karena nilainya bertahan walau app di-uninstall.

## Snapshot logcat (v84, diperluas v86)
Tombol "Ambil logcat sistem" (Pengaturan > Info & diagnostik > Log diagnostik) menyimpan 2 berkas di `Documents/LagFix`: (1) `LagFix_diag_logcat_*.txt` = ringkasan yang bisa dibaca di dalam aplikasi (alasan proses mati `ApplicationExitInfo` 30 terbaru, status sisi-aplikasi, status sistem: jobscheduler/WorkManager, notifikasi, servis, appops, standby bucket, baterai, Doze, `last-fstrim` dengan presisi detik, plus tampilan logcat terfilter); (2) `LagFix_diag_logcatraw_*.zip` = dump mentah logcat lengkap tanpa filter (main+system+events+crash) yang dialirkan ke zip. Berkas .zip tidak tampil di daftar log aplikasi; ambil dari file manager. Setelah dump, buffer logcat diperbesar ke 16M (sementara, kembali normal saat reboot) agar pengambilan berikutnya mencakup puluhan menit. Bagian logcat/sistem butuh Shizuku siap.

## Syarat
- Android 8.0+ (API 26)
- Shizuku aktif (Wireless debugging) + izin diberikan ke LagFix
- Shizuku non-root mati setelah reboot → job terjadwal dilewati (tercatat di Riwayat) sampai Shizuku dijalankan lagi

## Build
CI: GitHub Actions (`.github/workflows/build.yml`). Lokal: `gradle assembleDebug` (Gradle 8.9, JDK 17). Signing release via env: `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` + `release.keystore` di root.

## Crash log
`Documents/LagFix/LagFix_crash_*.txt` (sebelum v51: `Download/LagFix/`)

## Tautan di app
Kartu "Tautan" di layar utama membuka rilis terbaru, source, dan lapor masalah di `github.com/FDzaki-dev/LagFix`.

## Pembaruan di app
Kartu "Pembaruan" mengecek rilis terbaru (`releases/latest`) + `CHANGELOG.md` mentah dari branch main. Menampilkan versi terpasang vs tersedia (build number = run_number CI) dan changelog dalam dialog scrollable. Tombol "Update sekarang" mengunduh APK ke cache privat app lalu langsung membuka Package Installer (FileProvider) — bukan lewat browser, dan tidak menyimpan file ke folder Download publik (sisa unduhan lama dihapus tiap unduh baru). Butuh izin `INTERNET` + `REQUEST_INSTALL_PACKAGES`; Android akan minta izin "unknown sources" sekali di awal (proteksi OS bawaan, tidak bisa dilewati tanpa root).

## Catatan teknis — reflection Shizuku (D1, tech debt)
`FstrimExecutor.sh()` memanggil `Shizuku.newProcess(String[], String[], String)` — method
`private` sejak Shizuku API 13 — lewat reflection (`getDeclaredMethod` + `isAccessible = true`).
Ini titik rapuh utama aplikasi: kalau versi `dev.rikka.shizuku` dinaikkan dan nama/urutan/tipe
parameter method itu berubah, kegagalannya **diam-diam di runtime**, bukan compile error. Versi
saat ini sudah di-pin (`dev.rikka.shizuku:api` & `:provider` di `13.1.5`) — **wajib
re-verifikasi manual reflection ini** (buka source `Shizuku` versi baru, cocokkan signature
`newProcess`) tiap kali mau menaikkan versi Shizuku, sebelum rilis.

## Pathway CI
- **Build sukses** → GitHub Release otomatis (tag `build-<run_number>`), APK terlampir sbg `LagFix_build-<run_number>_release-atau-debug.apk` (nama unik per build, bukan `app-release.apk` generik — hindari tabrakan nama saat unduh rilis berturut-turut), selalu jadi `/releases/latest`.
- **Build sukses, laporan** → artifact Actions `LagFix-lint-detekt-<run_number>` (v106): folder `lint_detekt/` (log mentah `lint_output.log` & `detekt_output.log`, laporan detekt, file `lint-results*`) selain artifact APK.
- **Build gagal** → SATU artifact Actions `LagFix-fail-log-<run_number>` (v92): file `LagFix_build_fail_log_<run_number>.txt` + folder `lint_detekt/` (laporan detekt, file `lint-results*`, log mentah). Cukup satu unduhan.
- **Lint & detekt (blocking, v88)** → temuan lint (warning dihitung error) atau detekt menggagalkan build sebelum APK dibuat. Rincian: artifact `LagFix-fail-log-<run_number>` di atas (log kegagalan memuat bagian `lint_output.log`, `detekt_output.log`, `lint-results-debug.txt`; folder `lint_detekt/` memuat laporan lengkapnya). Saat build gagal tidak ada artifact lint/detekt terpisah (v92); saat build sukses laporannya terunggah terpisah (v106, lihat di bawah).
