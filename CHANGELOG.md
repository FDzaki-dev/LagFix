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

## v5
- CI: nama file APK di GitHub Release sekarang unik per build (`LagFix_build-<n>_release/debug.apk`), bukan `app-release.apk` generik — mencegah unduhan bertumpuk dgn nama duplikat (`app-release (1).apk` dst) di browser/HP saat unduh beberapa rilis.

## v6
- Fix regresi: tombol pembaruan sebelumnya melempar ke browser & APK menumpuk di folder Download. Sekarang unduh ke cache privat app + langsung buka Package Installer (FileProvider) — tanpa browser, tanpa file menumpuk (sisa unduhan lama otomatis dihapus tiap update baru).
- Tambah izin `REQUEST_INSTALL_PACKAGES`; prompt "izinkan pasang dari sumber tak dikenal" akan muncul sekali di awal (standar keamanan Android, tak bisa dilewati tanpa root).

## v8
- Label versi di UI sekarang ikut nomor build CI (`v1.0.<run_number>`), bukan statis "1.0.0" tiap rilis. Build lokal/dev tampil "1.0.0-dev".
- Riwayat: tiap baris sekarang punya indikator visual (titik + teks berwarna) hijau untuk OK, merah untuk FAIL — bukan teks polos semua.

## v9
- Tambah dialog "Tentang" (tombol baru di card Tautan) — ringkasan aplikasi: deskripsi singkat, versi terpasang, package id, dan tautan source/developer, jadi info app tidak lagi cuma tersebar di label versi + card Tautan.

## v10
- Tambah tab "Pengaturan" (nav-bar bawah, 2 tab: Utama/Pengaturan) — jadwal otomatis, interval, dan opsi "hanya saat charging/idle" sekarang di tab tersendiri, bukan nebeng di layar utama.
- Tampilan berubah total: palet warna kustom "calm" bergaya Cupertino/iOS (bukan Material You dinamis lagi) + sudut kartu lebih membulat. Dark mode sekarang navy-charcoal lembut, bukan hitam pekat.
- Baru: pemilih tema manual di tab Pengaturan — "Ikuti sistem" / "Terang" / "Gelap".
