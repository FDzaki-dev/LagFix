# Changelog

## v1 — 1.0.0
- Initial scaffold: Compose M3 UI, integrasi Shizuku, penjadwal WorkManager, CrashLogger (MediaStore), CI GitHub Actions.

## v2
- UI: card "Tautan" (rilis terbaru, source, lapor masalah) + label versi aplikasi.
- CI: build sukses → GitHub Release otomatis dengan APK terlampir (`/releases/latest` selalu terbaru). Build gagal → log diunggah, nama file diakhiri nomor run (`LagFix_build_fail_log_<run_number>.txt`).

## v4
- Fitur pembaruan dalam-app: card "Pembaruan" mengecek rilis GitHub terbaru + menampilkan changelog (dari `CHANGELOG.md`).
- Compare versi before/after riil: versi terpasang (build number = versionCode) vs versi tersedia (run_number dari tag rilis CI).
- Tombol unduh langsung ke APK rilis (atau halaman rilis kalau asset tak ditemukan).
