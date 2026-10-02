# Changelog

## v84
- Pengaturan > Info & diagnostik > Log diagnostik: tombol baru "Ambil logcat sistem". Menyimpan 1 file di Documents/LagFix berisi: alasan proses LagFix terakhir dimatikan Android (Android 11+, tanpa Shizuku), jejak fstrim yang dijalankan sistem (mis. saat boot), serta baris logcat terkait LagFix (siklus layanan persisten, proses dimatikan, FGS dihentikan). Bagian logcat butuh Shizuku siap; tanpa Shizuku, file tetap dibuat dengan bagian alasan proses mati saja. File hanya dibuat saat tombol ditekan (tidak otomatis).
- Kartu "Paksa trim saat reboot": ditambah catatan bahwa trim oleh sistem tidak masuk Riwayat (karena dijalankan Android sendiri), beserta petunjuk mencari jejaknya lewat tombol di atas.

## v83
- Pengaturan: kartu baru "Paksa trim saat reboot". Mengatur setting sistem Android `fstrim_mandatory_interval` ke 1 ms lewat Shizuku supaya Android menjalankan fstrim sendiri saat boot (cara kerja dari aplikasi mFSTRIM; efeknya BELUM diverifikasi di HP ini). Nilai sistem saat ini dibaca dan ditampilkan lebih dulu (tidak menimpa diam-diam), hasil perubahan dibuktikan dengan membaca ulang, dan tombol "Reset ke default Android" menghapus nilainya (nilai ini tetap ada di sistem walau LagFix di-uninstall). Shizuku hanya dibutuhkan untuk membaca/mengubah. Jadwal otomatis tidak berubah.

## v82
- Aksesibilitas (TalkBack): setiap saklar di Pengaturan kini terbaca lengkap dengan namanya dan seluruh barisnya bisa diketuk (minimal 48dp); grafik Statistik punya satu ringkasan yang dibaca TalkBack; judul kartu bisa dilompati sebagai heading; lipatan mengumumkan status terbuka/tertutup; simbol hias (panah, titik, centang) tidak lagi dibacakan. Tampilan hampir tidak berubah (baris saklar sedikit lebih tinggi).

## v81
- Layar saat aplikasi baru dibuka (termasuk splash bawaan Android 12 ke atas) dikunci ke warna gelap midnight yang sama dengan latar aplikasi, supaya tidak ada kilatan warna lain sebelum tampilan utama muncul. Tidak ada perubahan fitur.

## v80
- Riwayat dan Statistik dipoles: garis dasar grafik dibuat lebih terang agar jelas terlihat, garis putus-putus rata-rata kini tetap terlihat saat melintasi batang, batang bersudut membulat, dan ukuran garis mengikuti kepadatan layar sehingga tidak terlalu tipis di HP beresolusi tinggi. Baris Riwayat yang dilewati kini berlabel SKIP (sebelumnya FAIL) agar sesuai dengan legenda Statistik. Data dan format riwayat tidak berubah.

## v79
- Tab Pengaturan dirapikan: 5 kartu menjadi 3 bagian (Jadwal, Keandalan latar belakang, Info & diagnostik). Panduan agar jadwal tetap jalan kini digabung ke Keandalan latar belakang dan Log diagnostik digabung ke Info & diagnostik; keduanya bisa dilipat (tertutup secara bawaan) dengan tombol di dalam kartu. Isi, teks, dan semua tombol tidak berubah.

## v78
- Notifikasi "Layanan latar depan persisten" kini dipulihkan otomatis saat jadwal interval berjalan: di awal dan akhir tiap run, bila toggle aktif tetapi notifikasi tidak tampil (hilang atau diturunkan sistem), notifikasi dimunculkan lagi. Tidak ada timer, alarm, atau file log tambahan. Ini upaya mitigasi, bukan jaminan: ROM tetap bisa mencabutnya lagi atau mematikan proses.

## v77
- Pindah tab dengan geser (swipe): geser ke kiri membuka Pengaturan, geser ke kanan kembali ke Utama, tanpa harus menekan ikon di bawah. Animasi ganti tab sama seperti saat menekan ikon. Menggeser baris chip interval tetap menggulir chip, bukan pindah tab.
- Semua jam kini memakai format 12 jam (AM/PM), mis. "02/10 07:05 PM": Riwayat, sumbu waktu grafik Statistik, ringkasan terakhir dijalankan, dan widget (widget memakai "2/10 7:05 PM" agar baris status tidak terpotong). Riwayat lama tetap terbaca. Nama file log (mis. LagFix_diag_..._073121.txt) tetap 24 jam karena itu nama file, bukan tampilan jam.

## v76
- Perpindahan tab: layar tidak lagi kosong sesaat di antara fade-out dan fade-in. Tab tujuan disiapkan lebih dulu selagi tab lama masih tampil, baru kemudian tab lama memudar dan tab baru muncul. Sentuhan ditahan selama transisi (sekitar 0,3 detik). Hanya animasi ganti tab yang berubah.

## v75
- Perpindahan tab diperbaiki berdasarkan rekaman layar: sebelumnya layar sempat membeku sesaat lalu tab baru muncul tiba-tiba tanpa fade. Sekarang tab lama memudar dulu, baru tab baru disiapkan saat layar tak terlihat berubah, lalu memudar masuk. Hanya animasi ganti tab yang berubah.

## v74
- Perpindahan tab Utama/Pengaturan diperhalus: tab lama memudar dulu, lalu tab baru muncul (total 300 ms), jadi tidak ada lagi isi dua tab yang bertumpuk di tengah transisi. Hanya animasi ganti tab yang berubah.

## v73
- Perbaikan build: v71 dan v72 gagal dikompilasi sehingga tidak ada APK. Fitur M4 (tab memudar, kartu menyesuaikan tinggi, getaran konfirmasi) tetap sama; hanya cara progress "Menjalankan…" memudar yang diganti.

## v71
- Perpindahan tab Utama/Pengaturan kini memudar halus (200 ms). Tiap tab punya posisi gulir sendiri dan selalu mulai dari atas.
- Kartu Status, Riwayat, Statistik, dan Pembaruan menyesuaikan tinggi dengan halus saat isinya berubah (mis. baris Riwayat baru muncul setelah run). Progress "Menjalankan…" muncul dan hilang dengan fade.
- Tombol "Jalankan" di dialog konfirmasi memberi getaran ringan (mengikuti pengaturan sentuhan sistem; perangkat Android 8.0 tidak bergetar). Tidak ada animasi yang berputar terus-menerus.

## v70
- Ikon Quick Settings tile kini ikut desain baru: chip memori flash dengan kilau di tengah (sebelumnya masih petir lama). Ikon kecil notifikasi "Layanan latar depan persisten" memakai gambar yang sama, jadi ikut berganti. Perilaku tile tidak berubah.

## v69
- Widget: tampilan disamakan dengan gaya utama aplikasi — kartu kaca gelap midnight dengan cahaya biru/cyan/violet dan tepi bercahaya, ikon aplikasi + judul lavender-putih di bagian atas, teks status lembut, dan tombol "Jalankan Sekarang" berbentuk pil periwinkle (sebelumnya kartu mengikuti tema sistem/launcher dan tombol biru datar). Pratinjau di pemilih widget kini menampilkan isi, bukan kartu kosong. Isi status, interval, dan fungsi tombol tidak berubah.
- Ikon aplikasi dirombak total: petir putih di atas biru polos diganti chip memori flash dengan kilau "bersih" di tengah (menggambarkan trim/pembersihan penyimpanan), berlatar midnight berpendar yang selaras dengan UI. Mendukung ikon bertema Android 13+. Ikon tile Panel Cepat tidak berubah.

## v68
- Tab Utama: status Shizuku dan tombol "Jalankan fstrim sekarang" kini satu kartu utama (hero) — lencana bulat berikon (centang mint saat siap, peringatan amber saat belum), judul & penjelasan di tengah, tombol aksi selebar kartu. Saat fstrim berjalan muncul bilah progres di atas tombol. Kartu Riwayat yang masih kosong kini menampilkan ikon dan petunjuk ("Ketuk tombol Jalankan di atas, atau nyalakan Jadwal otomatis di tab Pengaturan."). Teks status, kondisi tombol aktif/nonaktif, dan fungsi tidak berubah.

## v67
- Quick Settings tile dialihfungsikan: ketuk tile sekarang langsung menyalakan notifikasi "Layanan latar depan persisten" saat itu juga (label tile: "Layanan persisten"). Tile tidak lagi menjalankan fstrim — fstrim manual tetap lewat tombol di app dan widget. Tile hanya memicu servis; saklar di Pengaturan tidak berubah. Kalau Android menolak menyalakan servis dari tile, muncul pesan singkat (aplikasi tidak crash).

## v66
- Kartu status Shizuku (tab Utama): kini ada ikon status di samping judul — centang hijau-mint saat "Siap", segitiga peringatan amber saat Shizuku belum terpasang / tidak aktif / butuh izin. Ikon punya label aksesibilitas (TalkBack: "Status: baik" / "Status: perlu tindakan"). Teks, tombol, dan fungsi tidak berubah.

## v65
- Kartu Pembaruan (tab Utama): tombol aksi disusun simetris. Saat ada versi baru, "Update sekarang" (utama), "Lihat changelog", dan "Cek ulang" kini selebar kartu dan setinggi sama (sebelumnya berdampingan dengan ukuran berbeda: "Update sekarang" membungkus 2 baris dan "Lihat changelog" menjorok ke dalam). Fungsi update dan cek versi tidak berubah.

## v64
- Pengaturan > Jadwal: label kolom interval kustom dipersingkat menjadi "Kustom (menit)" dan dipaksa satu baris, sehingga tidak lagi membungkus dan merusak bingkai kolom. Fungsi interval tidak berubah.

## v62
- Tampilan: ikon bilah navigasi bawah (Utama, Pengaturan) kini ikon vektor bergaya garis-solid yang ikut warna tema (sebelumnya emoji berwarna yang tidak serasi dengan gaya kaca). Tidak ada perubahan fungsi, jadwal fstrim, widget, tile, maupun layanan latar depan.

## v61
- Tampilan: aplikasi kini HANYA mode gelap dan seluruh tema, tipografi, serta bentuk memakai gaya Glassmorphism & Glow. Latar midnight blue berpendar lembut (biru, cyan, violet), kartu berupa panel kaca translusen dengan tepi bercahaya, bilah atas/bawah transparan, sudut lebih membulat, dan judul berbobot tebal dengan cahaya halus. Warna teks lavender-putih (bukan putih murni) agar nyaman di mata. Kartu \"Tema\" (Ikuti sistem/Terang/Gelap) dihapus dari Pengaturan; ikon bar status & navigasi selalu terang apa pun mode sistem. Tidak ada perubahan fungsi, jadwal fstrim, widget, tile, maupun layanan latar depan.

## v60
- Tampilan: palet warna dirapikan. Status Riwayat & Statistik (berhasil / dilewati / gagal) kini terbaca jelas di tema gelap (sebelumnya terlalu redup), batas kolom/chip/switch lebih terlihat, dan warna kartu, bilah bawah, serta notifikasi singkat (snackbar) kini seragam dengan palet calm. Di tema terang, kartu berwarna putih. Judul kartu sedikit lebih tebal. Tidak ada perubahan fungsi, jadwal fstrim, widget, tile, maupun layanan latar depan.

## v57
- Dicabut: layanan Aksesibilitas "LagFix (penjaga proses)" dan tombol "Buka Pengaturan Aksesibilitas" (v55). Uji logcat di HP ini menunjukkan sistem tidak menyambungkan ulang layanan itu setelah LagFix digeser dari Recents, jadi tidak mempercepat kembalinya notifikasi permanen. Kalau layanan sudah kamu aktifkan, matikan manual di Setelan > Aksesibilitas (otomatis hilang dari daftar setelah update). Tidak ada perubahan pada jadwal fstrim, widget, tile, maupun layanan latar depan persisten.

## v55
- Pengaturan > Keandalan latar belakang: tombol baru "Buka Pengaturan Aksesibilitas" untuk mengaktifkan layanan "LagFix (penjaga proses)". Layanan ini kosong (tidak membaca layar maupun ketikan); gunanya membantu sistem menghidupkan lagi proses LagFix lebih cepat setelah dimatikan (mis. digeser dari Recents), sehingga notifikasi permanen bisa kembali tanpa menunggu interval jadwal. Harus diaktifkan sendiri di Setelan Aksesibilitas; di Android 13+ untuk aplikasi di luar Play Store mungkin perlu "Izinkan pengaturan terbatas" lebih dulu. Belum ada jaminan di semua HP.

## v51
- Log crash dan diagnostik kini disimpan di folder Documents/LagFix (sebelumnya Download/LagFix), jadi folder Download tidak lagi terisi file log. Kartu "Log Diagnostik" di Pengaturan membaca kedua lokasi. File lama di Download/LagFix tidak dipindah otomatis; hapus manual bila tak diperlukan.

## v49
- Pengaturan: kartu baru "Agar jadwal tetap jalan" berisi panduan singkat (jangan geser LagFix dari Recents, kunci di Recents bila ada, izinkan Autostart/Latar belakang) plus tombol ke Info Aplikasi. Paragraf Autostart lama di kartu Jadwal dipindah ke kartu ini.
- Layanan latar depan persisten kini mencatat siklus hidupnya ke logcat (kata kunci LIFECYCLE) untuk membantu diagnosis saat dimatikan sistem. Tidak ada perubahan perilaku.

## v48
- Dihapus juga: batasan "baterai tidak rendah" pada jadwal otomatis. Sekarang jadwal murni mengikuti interval, tanpa syarat apa pun (sebelumnya masih ditunda saat baterai sangat rendah).
- Jalur penyegaran satu kali dari v47 dicabut. Setelah memasang v48, matikan lalu nyalakan lagi "Jadwal otomatis" (atau tekan "Terapkan" pada interval) satu kali supaya jadwal lama yang masih membawa syarat baterai digantikan jadwal baru.

## v47
- Dihapus: opsi "Hanya saat mengisi daya" dan "Hanya saat perangkat idle" di Pengaturan > Jadwal, berikut keterangan "Menunggu: ..." di kartu Riwayat, tab Pengaturan, dan widget (v46). Jadwal kini murni mengikuti interval yang kamu atur, plus tombol manual.
- Pembaruan ini otomatis menyegarkan jadwal lama satu kali saat aplikasi dibuka, supaya syarat mengisi daya/idle versi lama benar-benar hilang (bukan cuma tombolnya).
- Catatan: jadwal otomatis tetap ditunda kalau baterai sedang sangat rendah (pengaman lama, tidak termasuk opsi yang dihapus).

## v46
- Jadwal otomatis: saat toggle "Hanya saat mengisi daya" / "Hanya saat perangkat idle" aktif dan jadwal sedang ditahan, sekarang tertulis jelas "Menunggu: mengisi daya" / "menunggu: perangkat idle" — di kartu Riwayat (tab Utama), tab Pengaturan, dan baris kedua widget.
- Kalau syaratnya sudah terpenuhi, tab Pengaturan menegaskan "Syarat terpenuhi — jadwal akan jalan sesuai interval." supaya jelas fiturnya memang bekerja, bukan diam tanpa keterangan.

## v45
- Widget layar utama: baris kedua kini menampilkan interval jadwal yang aktif (mis. "Tiap 15 menit"), dan baris status menyebut apakah proses terakhir dari jadwal Otomatis atau dijalankan Manual.
- Kartu "Statistik" di app: ringkasan kini merinci berapa dari proses terakhir yang Otomatis vs Manual, dan grafik menandai batang hasil "Manual" dengan titik kecil di atasnya (kalau ada campuran keduanya) supaya jelas kedua jenis pemicu sama-sama terhitung.

## v44
- Pengaturan > Jadwal: interval kustom. Isi sendiri angka dalam menit (minimal 15 menit) lalu tekan "Terapkan" atau tombol selesai di keyboard; pilihan cepat 6 jam – 7 hari tetap ada.
- Teks di bawah kolom menampilkan interval yang sedang aktif (mis. "1 jam 30 menit"). Pilihan interval lama tetap terbawa saat pembaruan.
- Catatan: 15 menit adalah batas terkecil penjadwal Android (WorkManager), jadi nilai lebih kecil ditolak. Opsi "Hanya saat mengisi daya"/"idle" tetap berlaku dan bisa menunda jalannya jadwal.

## v40
- Perbaikan notifikasi "Layanan latar depan persisten": layanan sekarang otomatis dinyalakan lagi setiap aplikasi dibuka kembali setelah dimatikan sistem (mis. digeser dari Recents), sehingga notifikasinya muncul lagi.
- Notifikasi layanan kini ditampilkan langsung, tidak lagi tertunda beberapa detik di Android 12+ (sebelumnya bisa keburu terbunuh sebelum sempat muncul).
- Catatan: di HP dengan sistem yang agresif (mis. Infinix XOS), aplikasi tetap bisa dimatikan saat digeser dari Recents; notifikasi akan kembali begitu aplikasi dibuka lagi. Tidak ada jaminan 100%.

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

## v25
- Warna icon tile Quick Settings diganti ke warna brand (biru) — percobaan lanjutan supaya lebih kelihatan di beberapa HP yang sebelumnya menampilkannya polos/putih.

## v23
- Tab Pengaturan sekarang menampilkan tanda centang "Baterai: berjalan tanpa batas ✓" setelah izin diberikan — sebelumnya tombolnya cuma hilang tanpa keterangan apa-apa.
- Tampilan status di widget dirapikan jadi lebih mudah dibaca: "Bersih ✓ · tanggal jam" / "Gagal ⚠ · tanggal jam" / "Shizuku belum siap · tanggal jam" — sebelumnya menampilkan baris log teknis mentah. Detail lengkap (durasi, exit code, dll) tetap bisa dilihat di Riwayat dalam aplikasi.

## v26
- Fix akar masalah icon tile Quick Settings yang tak pernah kelihatan mati/idle (selalu tampil menyala) — ternyata bukan soal warna icon (sudah dicoba v25), tapi status tile yang dulu selalu di-set "menyala" walau sedang tidak memproses apa pun. Sekarang icon benar-benar meredup saat idle dan menyala hanya saat sedang memproses. Tap tile tetap selalu berfungsi seperti biasa.

## v27
- Baru (opsional, nonaktif secara default): toggle "Layanan latar depan persisten" di tab Pengaturan — buat HP yang sangat agresif mematikan aplikasi latar belakang (mis. Infinix XOS) dan sudah dicoba opsi baterai/Autostart tapi jadwal otomatis masih belum jalan sendiri. Kalau diaktifkan, muncul notifikasi permanen (tak bisa disembunyikan selama aktif) yang menjaga aplikasi tetap hidup, dan otomatis menyala lagi setelah HP di-restart.

## v30
- Fix: catatan crash/diagnostik (folder Download/LagFix/) sebelumnya bisa gagal total tanpa jejak apa pun — foldernya tidak pernah muncul. Sekarang penulisan file dijamin diselesaikan dengan benar sesuai mekanisme resmi Android, dan kalau tetap gagal karena kondisi HP tertentu, catatannya otomatis disimpan sebagai cadangan di penyimpanan internal aplikasi supaya tidak pernah hilang tanpa jejak.

## v38
- Baru: Riwayat sekarang menunjukkan proses fstrim dipicu oleh apa — Manual (dijalankan sendiri lewat tombol, widget, atau tile) atau Otomatis (jadwal interval). Baris ringkasan di atas Riwayat juga menuliskannya dalam kalimat.
- Grafik Statistik lebih mudah dibaca: lama proses ditulis dalam detik, ada angka acuan di sisi grafik, tanggal awal dan akhir, garis putus-putus untuk rata-rata, dan keterangan warna batang.
- Catatan: riwayat lama (sebelum v38) tidak punya info pemicu, jadi tampil tanpa keterangan itu.

## v37
- Baru: kartu "Statistik" di tab Utama, di bawah Riwayat — ringkasan jumlah berhasil/dilewati/gagal, rata-rata durasi, dan grafik batang kecil yang menunjukkan durasi tiap proses fstrim dari waktu ke waktu (warna hijau/kuning/merah sesuai hasilnya).

## v35
- Percobaan perbaikan notifikasi "Layanan latar depan persisten" yang belum pernah tampil: layanan sekarang langsung menampilkan notifikasinya di baris kode paling pertama begitu dimulai (sebelumnya ada 2 pengecekan kecil yang jalan duluan). Belum tentu ini akar masalah sepenuhnya — investigasi masih berlanjut menunggu hasil test dari perangkat Anda.

## v31
- Baru: card "Log Diagnostik" di tab Pengaturan — baca catatan crash/diagnostik langsung di dalam aplikasi (tidak perlu buka file manager atau folder Download sama sekali). Tap salah satu untuk lihat isinya lengkap, plus tombol "Salin" untuk menyalin teksnya langsung dari aplikasi.
