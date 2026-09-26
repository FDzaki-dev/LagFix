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

## v11
- Tombol "Jalankan fstrim sekarang" sekarang minta konfirmasi dulu (dialog "Jalankan"/"Batal") sebelum benar-benar memicu operasi TRIM.
- Setelah fstrim selesai dijalankan, muncul notifikasi (toast) hasilnya (berhasil/gagal) — tak perlu scroll ke Riwayat untuk tahu.
- Tiap kontrol di tab Pengaturan (jadwal otomatis, interval, charging/idle, tema) sekarang menampilkan toast konfirmasi singkat tiap kali diubah.

## v12
- Fix: toast konfirmasi tidak lagi terasa delay saat mengganti beberapa pengaturan (tema/interval/dll) secara berurutan cepat — toast baru langsung menggantikan toast lama, tidak menumpuk antrean.
- Card "Tautan" (unduh rilis, kode sumber, lapor masalah, tentang aplikasi) dipindah dari tab Utama ke tab Pengaturan (paling bawah), supaya tab Utama lebih ringkas.

## v13
- Kalau Shizuku belum siap saat jadwal otomatis jalan, sekarang dicoba ulang otomatis (bukan nunggu jadwal penuh berikutnya yang bisa berhari-hari).
- Pesan error "Cek pembaruan"/"Update sekarang" sekarang lebih jelas untuk kasus tanpa koneksi internet dan rate-limit GitHub (bukan pesan teknis mentah).
- Kalau unduhan update APK putus di tengah jalan, file rusaknya otomatis dibersihkan.
- Riwayat sekarang membedakan entri "dilewati" (Shizuku belum siap, warna kuning) dari entri "gagal" beneran (warna merah).

## v19
- Baru: widget home screen — tampilkan status fstrim terakhir + tombol "Jalankan Sekarang" langsung dari layar utama, tanpa buka app.
- Baru: tile Quick Settings "LagFix" — tap untuk trigger fstrim manual dari panel Quick Settings (swipe dari atas layar).

## v20
- Tombol widget & tile sekarang kasih tahu hasilnya (notifikasi toast: berhasil/gagal/Shizuku belum siap) — sebelumnya tidak ada tanda apa-apa setelah ditekan.
- Teks status widget saat proses diganti "Sedang memproses…" (sebelumnya "Menjadwalkan…" yang membingungkan, bisa dikira fitur jadwal otomatis).
- Tampilan widget dirapikan: kartu bersudut membulat + tombol "Jalankan Sekarang" warna biru (sebelumnya kotak polos + tombol abu-abu sistem).

## v21
- Widget, tile Quick Settings, dan tampilan di dalam app sekarang saling sinkron otomatis — begitu fstrim selesai dijalankan dari mana pun (widget/tile/app), yang lain langsung ikut update. Sebelumnya widget bisa nyangkut di teks "Sedang memproses…" sampai lama.

## v22
- Baru: tombol "Izinkan berjalan tanpa batas" di tab Pengaturan (khusus HP yang baterainya masih dioptimasi sistem) — biar jadwal otomatis fstrim lebih diandalkan, terutama di HP dengan ROM yang agresif mematikan aplikasi latar belakang (mis. XOS, MIUI, ColorOS). Tombol otomatis hilang begitu sudah diizinkan.
