# LagFix (fstrim)
Utilitas Android non-root untuk memicu dan menjadwalkan `fstrim` (TRIM pada UFS/eMMC) dengan interval kustom.

## Cara kerja
Menjalankan `sm fstrim` (fallback `sm idle-maint run`) sebagai shell UID lewat **Shizuku**. Penjadwalan: WorkManager (6 jam – 7 hari; opsi hanya saat charging / idle).

## Syarat
- Android 8.0+ (API 26)
- Shizuku aktif (Wireless debugging) + izin diberikan ke LagFix
- Shizuku non-root mati setelah reboot → job terjadwal dilewati (tercatat di Riwayat) sampai Shizuku dijalankan lagi

## Build
CI: GitHub Actions (`.github/workflows/build.yml`). Lokal: `gradle assembleDebug` (Gradle 8.9, JDK 17). Signing release via env: `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` + `release.keystore` di root.

## Crash log
`Download/LagFix/LagFix_crash_*.txt`

## Tautan di app
Kartu "Tautan" di layar utama membuka rilis terbaru, source, dan lapor masalah di `github.com/FDzaki-dev/LagFix`.

## Pathway CI
- **Build sukses** → GitHub Release otomatis (tag `build-<run_number>`), APK terlampir, selalu jadi `/releases/latest`.
- **Build gagal** → log build diunggah sebagai artifact Actions, nama file `LagFix_build_fail_log_<run_number>.txt`.
