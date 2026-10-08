# Changelog

## v142
- Tambah opsi tema ke-2 "Neumorphism" (hanya mode gelap) di Pengaturan > Tampilan; tema Glassmorphism tetap default dan tampil sama seperti v141. Tema baru memakai burgundy sebagai warna utama dengan sage yang tenang sebagai pendamping dan champagne sebagai aksen tipis. Kartu, tombol, chip, dan switch timbul dari latar yang nadanya beda, dengan dua lapis bayangan gelap, tepi berkilau di kiri-atas, serta sumur cekung untuk lencana status, trek switch, chip terpilih, dan tombol yang sedang ditekan. Judul memakai huruf kaligrafi TeX Gyre Chorus dan isi memakai Caladea (font disertakan di aplikasi, lisensi di `docs/licenses/`). Pilihan tema tersimpan dan langsung berlaku.

## v141
- Uji v140 di Infinix X6850 menunjukkan penutupan layar saat meninggalkan app tidak pernah tercatat ketika Recents dibuka dari dalam app, sehingga kartu LagFix masih bisa digeser pembersih bawaan HP dan prosesnya mati. Kini penutupan juga dipicu saat layar app berhenti terlihat (selain saat tombol Home ditekan), dengan pengaman yang sama; tambahan: tidak menutup saat layar HP dimatikan atau saat layar diputar. Catatan diagnostik `recentsReadBack` kini memuat jumlah panggilan per pemicu, panggilan terakhir, dan teks galat mentah bila ada. Belum diuji di perangkat.

## v140
- Saat layanan latar depan persisten aktif, meninggalkan app (tombol Home atau membuka Recents dari dalam app) kini menutup layar app dan menghapus tasknya, supaya tidak ada kartu LagFix yang bisa digeser pembersih bawaan HP. Efeknya: saat dibuka lagi, app mulai dari tab awal. Penutupan ditahan saat ada proses berjalan (fstrim manual, cek atau unduh update, boot-trim, snapshot logcat), saat app membuka layar lain (setelan, izin, installer), dan di mode multi-window. Ada pemutus otomatis: bila proses mati dalam 10 detik setelah penutupan, fitur ini mati sendiri. Catatan diagnostik `recentsReadBack` kini memuat jumlah dan keputusan terakhirnya. Belum diuji di perangkat.

## v139
- Tidak ada perubahan pada aplikasi. Uji v138 di Infinix X6850 berhasil: berkas `kill_context` kini terbit untuk kematian swipe-up clean. Log menunjukkan LagFix mati sekitar 1 detik setelah dibuka ke Recents langsung dari dalam app (tanpa lewat Home); penyembunyian dari Recents (v133) belum melindungi jalur itu. Keputusan cara menutup celah ini menunggu persetujuan pengguna.

## v138
- Pencatat penyebab kematian proses (`kill_context`) kini juga memeriksa kematian yang dicatat sistem sebagai "OTHER", tidak hanya "SIGNALED". Sebabnya: di Infinix X6850 kematian karena swipe-up clean (pembersih bawaan HP) dicatat sebagai OTHER, sehingga berkas ini tidak pernah terbit di HP itu. Berkas diagnostik 7 Okt malam juga menunjukkan (satu sampel) swipe-up clean masih mematikan LagFix walau app sudah disembunyikan dari Recents (v133).
- Tidak ada perubahan tampilan. Belum diuji di perangkat.

## v137
- Tidak ada perubahan pada aplikasi. Perapian dokumen proyek: riwayat batch v94 sampai v136 dipindah dari `PROJECT_STATE.md` ke `docs/archive/PROJECT_STATE_v94-v136.md` (isi identik, tidak ada yang dibuang) sehingga `PROJECT_STATE.md` susut dari sekitar 297 KB menjadi sekitar 14 KB. Catatan lanjutan untuk sesi berikutnya dibawa ke entri v137.

## v135
- Kode keluar (exit) perintah yang dijalankan lewat Shizuku kini terbaca. Di berkas diagnostik 7 Okt, seksi `logcatShizuku`, `logcatInfo`, dan "Jejak fstrim" selalu menulis `exit=null` walau perintah selesai (perintah lokal tetap `exit=0`), sehingga tak bisa dibedakan dari proses yang tak sempat selesai. Penyebab yang diduga: `exitValue()` proses Shizuku melempar jenis galat yang tak ditangani `waitFor(timeout)` bawaan; kini `exitValue()` dipoll sampai 2 detik. Seharusnya ikut memperbaiki percobaan perbesar buffer logcat (v132), yang sebelumnya tak pernah dianggap sukses karena exit tak terbaca sehingga diulang di tiap pembukaan app.
- Tidak ada perubahan tampilan. Ditambah satu tes unit. Belum diuji di perangkat.

## v134
- Ringkasan snapshot logcat dan berkas diagnostik `process_catch` kini memuat status Recents: jumlah task LagFix dan flag `baseIntent`-nya dibaca balik langsung dari sistem, sehingga terlihat apakah penyembunyian dari Recents (v133) benar-benar terpasang. Sebabnya: pada dump 7 Okt, logcat di HP uji tidak memuat satu pun baris milik app (hanya 11 tag sistem), jadi baris log `RecentsExclusion` tidak bisa dijadikan bukti.
- Tidak ada perubahan perilaku app. Ditambah tiga tes unit. Belum diuji di perangkat.

## v133
- Selama toggle "Layanan latar depan persisten" menyala, LagFix kini disembunyikan dari daftar aplikasi terbaru (Recents). Sebabnya: log mentah menunjukkan pada 3 dari 3 kematian yang tertangkap, menggeser LagFix dari Recents memicu pembunuhan proses oleh modul OEM `TranManualCleanMgr`, lalu notifikasi persisten dibatalkan dan servis tidak dipulihkan. Tanpa kartu di Recents, tidak ada yang bisa digeser. Toggle dimatikan, LagFix kembali tampil di Recents. Penyembunyian diterapkan saat toggle diubah dan setiap kali app dibuka, jadi tetap berlaku walau task baru lahir dari notifikasi, tile, atau widget. Buka LagFix lewat ikon, widget, tile, atau ketuk notifikasi. Teks bantu di bawah toggle ikut diperbarui.
- Efektivitasnya di HP uji belum terbukti: pembersih OEM lain masih bisa membunuh lewat jalur berbeda. Hasil tiap penerapan dicatat apa adanya di logcat (tag `RecentsExclusion`). Ditambah dua tes unit. Belum diuji di perangkat.

## v132
- App kini memperbesar buffer logcat sistem ke 16M secara otomatis (perlu Shizuku aktif), tidak lagi menunggu tombol "Ambil logcat sistem" ditekan. Sebabnya: pada berkas 6 Okt baris logcat yang dibutuhkan pencatat kill untuk dua kematian proses (pid 31286 dan 9172) tidak ada lagi saat dibaca, dan pada snapshot v84 buffer bawaan hanya mencakup sekitar 30 detik. Penyebab pasti hilangnya baris itu belum terbukti. Perbesar dijalankan sekali per proses, sesudah app selesai membaca logcat untuk berkas catcher; perintahnya sama dengan tombol snapshot dan bersifat sementara (kembali normal saat HP dihidupkan ulang). Hasilnya dicatat apa adanya di logcat (tag `LogBuffer`).
- Tidak ada perubahan tampilan. Ditambah empat tes unit. Belum diuji di perangkat.

## v130
- Berkas `process_catch` kini punya dua bagian baru. `logcatInfo` (perlu Shizuku aktif) mencetak apa adanya ukuran buffer logcat (`logcat -g`), dua baris paling tua yang masih ada di buffer, dan dua baris pertama sejak awal jendela waktu, tanpa saringan nama app. Gunanya membedakan apakah buffer log sudah tertimpa atau pembacaan berdasarkan waktu meleset, karena pada HP uji bagian `logcatShizuku` hanya memuat 16 baris yang mulai 3,4 detik setelah proses lahir. `appVersion` mencetak nama dan kode versi APK yang membuat berkas, supaya jelas versi mana yang diuji.
- Tidak ada perubahan perilaku lain. Ditambah empat tes unit. Belum diuji di perangkat.

## v129
- Perbaikan pencatat kill (`kill_context`). Pada berkas 6 Okt, dua dari tiga kematian proses tidak memuat baris yang menunjukkan siapa pembunuhnya, karena app menyimpan 150 baris PERTAMA dari jendela 25 detik dan jatah itu habis sebelum waktu kematian (satu kematian berhenti 10 detik sebelum mati, satu lagi 0,6 detik sebelumnya). Kini app membaca sampai 3000 baris dari logcat dan menyimpan baris yang paling dekat dengan waktu kematian: 100 baris terakhir sampai waktu mati ditambah 50 baris sesudahnya. Isi baris tidak diubah. Baris judul tiap kematian kini juga mencantumkan jumlah baris yang cocok (`matched`) di samping jumlah yang disimpan (`lines`), jadi terlihat bila ada yang terpotong.
- Tidak ada perubahan perilaku lain. Ditambah tiga tes unit dan satu tes diperbarui (batas baris perintah 300 menjadi 3000). Belum diuji di perangkat.

## v128
- Berkas `process_catch` kini punya bagian `logcatShizuku` (perlu Shizuku aktif): baris logcat sistem yang menyebut LagFix atau pid proses itu, mulai satu detik sebelum proses lahir (maksimal 150 baris, disalin apa adanya). Gunanya melihat siapa yang meminta notifikasi layanan persisten tampil dan kapan. Bagian `ownLogcat` kini mencoba lagi tanpa filter pid bila percobaan pertama kosong (di HP uji percobaan pertama mengembalikan 0 baris). Tidak ada perubahan perilaku lain. Ditambah empat tes unit. Belum diuji di perangkat.

## v127
- Berkas `process_catch` kini punya bagian baru `ownLogcat`: baris logcat milik app sendiri yang bertanda `LIFECYCLE`, disalin apa adanya dengan urutan waktu aslinya (maksimal 200 baris terbaru), ditambah satu baris angka mentah di depannya. Dengan begitu terbaca komponen mana yang memulai notifikasi layanan persisten dan apa yang melahirkan proses, tanpa perlu menekan "Ambil logcat sistem" dan tanpa Shizuku. Widget kini juga menulis satu baris logcat tiap menerima broadcast. Tidak ada perubahan perilaku lain. Ditambah lima tes unit. Belum diuji di perangkat.

## v125
- Berkas `process_catch` kini mencantumkan pid, nama proses, dan stempel waktu start pada tiap baris riwayat start proses, ditambah satu baris jangkar waktu. Dengan begitu tiap baris bisa dicocokkan dengan proses tertentu, tidak lagi ambigu. Tidak ada perubahan perilaku lain. Ditambah tiga tes unit. Belum diuji di perangkat.

## v124
- Pencatat baru untuk mencari pelaku yang mematikan app. Setiap kali app menyimpan berkas keadaan prosesnya (`process_catch`), app juga memeriksa kematian proses terbaru yang disebabkan sinyal SIGKILL (maksimal 3, hanya yang belum pernah diperiksa dan masih dalam 12 jam terakhir). Untuk tiap kematian, app menyalin dari logcat sistem (lewat Shizuku) baris yang menyebut nama app, pid, atau kata kunci kill dalam rentang 20 detik sebelum sampai 5 detik sesudah waktu kematian, lalu menyimpannya di `LagFix_diag_kill_context_*.txt` di Documents/LagFix.
- Perlu Shizuku aktif. Kill yang barisnya sudah tertimpa di buffer logcat tidak bisa dibongkar; tombol "Ambil logcat sistem" memperbesar buffer sementara sampai perangkat dihidupkan ulang. Ditambah enam tes unit. Belum diuji di perangkat.

## v123
- Pencatat gabungan untuk semua kondisi notifikasi persisten yang masih belum jelas. Saat panel Quick Settings dibuka, app dibuka, atau jadwal berjalan, app mencatat 1 baris di logcat. Berkas `LagFix_diag_process_catch_*.txt` di Documents/LagFix disimpan hanya pada dua keadaan: (1) proses app baru lahir saat panel QS dibuka atau app dibuka (sekali per proses), atau (2) proses app masih hidup tetapi notifikasi persisten tidak ada padahal toggle menyala (paling sering sekali per 30 menit).
- Isi berkas adalah data mentah tanpa tafsiran: notifikasi aktif segera dan 1,5 detik kemudian, prioritas proses, status pembatasan latar belakang, daftar layanan app, lima riwayat start proses (Android 15 ke atas), dan lima riwayat kematian proses (Android 11 ke atas). Pencatat hanya merekam, tidak memulai atau menghentikan layanan. Ditambah empat tes unit. Belum diuji di perangkat.

## v121
- Perbaikan pemeriksaan kode (detekt) dari build v120: satu angka literal di pembaca baris Riwayat diganti konstanta bernama. Tidak ada perubahan perilaku.
- Pencatat baru untuk kejadian "panel Quick Settings dibuka". Tiap panel dibuka, app menulis 1 baris di logcat. Bila saat itu proses app baru saja lahir (umur kurang dari 10 detik), app juga menyimpan 1 berkas `LagFix_diag_tile_listening_*.txt` di Documents/LagFix, maksimal 1 berkas per proses. Isinya data mentah: pid, umur proses, status toggle layanan persisten, dan dua dump notifikasi aktif (segera dan 1,5 detik kemudian). Pencatat ini hanya merekam, tidak memulai atau menghentikan layanan. Dipakai untuk membuktikan apakah notifikasi persisten muncul lagi karena panel dibuka. Ditambah dua tes unit untuk format isi berkas. Belum diuji di perangkat.

## v120
- Riwayat kini menandai run Otomatis yang terlambat. Bila jarak ke run Otomatis sebelumnya melebihi interval (toleransi 5 menit atau seperlima interval, mana yang lebih besar), baris itu diberi tulisan amber "Terlambat N menit dari jadwal". Berguna saat Android menunda jadwal ketika Doze: run baru tercatat saat HP dinyalakan, dan sekarang keterlambatannya terlihat. Jam di Riwayat tetap waktu run yang sebenarnya. Run Manual tidak ditandai, dan baris lama tetap terbaca. Ditambah tiga tes unit untuk perhitungannya.

## v119
- Saat toggle "Interval radikal" aktif, muncul tiga chip preset cepat: 1 menit, 5 menit, 10 menit. Satu ketukan langsung menerapkan interval itu, jadi tidak perlu mengetik di kolom Kustom. Chip tersembunyi saat toggle mati. Tidak ada perubahan lain.

## v118
- Pengaturan > Jadwal: tambah toggle "Interval radikal (< 15 menit)". Saat aktif, kolom Kustom (menit) menerima nilai mulai 1 menit; interval di bawah 15 menit dijalankan lewat rantai WorkManager sekali-jalan yang menjadwalkan dirinya sendiri setelah tiap run (periodik WorkManager mentok 15 menit). Toggle bebas dinyalakan/dimatikan kapan saja tanpa batasan atau konfirmasi. Saat dimatikan, interval di bawah 15 menit otomatis dinaikkan ke 15 menit dan jadwal kembali periodik biasa. Android tetap bisa menunda jadwal saat Doze, jadi waktu tidak dijamin tepat. Belum diuji di perangkat.

## v116
- Notifikasi layanan persisten kini memakai importance TINGGI (di HP uji terbaca 3, sebelumnya kode meminta MIN). Android tidak mengizinkan app menaikkan importance channel yang sudah ada, jadi dibuat channel baru `lagfix_keep_alive_high` dan channel lama `lagfix_keep_alive` dihapus saat layanan dibuat. Suara, getar, dan lampu dimatikan di level channel (tetap senyap) dan notifikasi tetap tersembunyi di layar kunci. Deskripsi channel disesuaikan (tidak lagi "diminimalkan"). Konsekuensi: setelan channel lama yang pernah diubah di Setelan HP hilang; HP mungkin menampilkan banner pop-up (belum diuji di perangkat). Belum dibuktikan oleh CI dan belum diuji di perangkat.

## v114
- Tidak ada perubahan perilaku. Perbaikan satu temuan detekt dari build v113 (angka literal untuk format heksadesimal di dump log notifikasi diganti konstanta bernama). Isi log tetap sama.

## v113
- Isi file log layanan persisten (`diag_persistent_service`) kini data mentah saja, tanpa kalimat penjelasan atau kesimpulan dari aplikasi. Yang ditulis: status izin notifikasi, importance channel, exception `startForeground()` apa adanya, lalu dua dump mentah daftar notifikasi aktif milik LagFix (id, tag, channel, flags dalam heksadesimal, waktu posting), satu segera dan satu setelah jeda 1,5 detik. Baris logcat `LIFECYCLE` juga memakai format mentah yang sama. Tidak ada perubahan perilaku notifikasi. Belum diuji di HP.

## v112
- Log diagnostik layanan persisten diperbaiki: pada v111 file `diag_persistent_service` memeriksa notifikasi terlalu cepat dan mencatat "TIDAK terlihat" walau notifikasi sebenarnya tampil. Sekarang file ditulis setelah pengecekan ulang 1,5 detik dan memuat kedua hasil (cek segera dan setelah jeda); hasil akhir mengikuti pengecekan terakhir. Bila HP mematikan proses sebelum 1,5 detik, file tidak sempat tertulis (catatan di logcat tetap ada). Tidak ada perubahan perilaku notifikasi. Belum diuji di HP.

## v111
- Pengaturan > kartu Jadwal kini menampilkan peringatan merah bila Android membatasi LagFix berjalan di latar belakang. Status ini terpisah dari tanda centang "Baterai: berjalan tanpa batas" dan bisa membuat notifikasi layanan persisten gagal tampil atau hilang. Tersedia tombol langsung ke Info Aplikasi LagFix; peringatan hilang sendiri setelah pembatasan dicabut dan aplikasi dibuka lagi. Log layanan persisten juga dibuat jujur: tidak lagi mencatat "SUKSES" hanya karena `startForeground()` kembali tanpa error. Yang dicatat sekarang apakah notifikasi benar-benar terlihat (dicek segera, lalu sekali lagi 1,5 detik kemudian di logcat). Belum diuji di HP dan belum menjamin notifikasi tidak hilang lagi. Tanpa Firebase, tanpa dependency atau izin baru.

## v110
- Tidak ada perubahan pada aplikasi. Ditambahkan pemeriksaan pembaruan otomatis lewat Dependabot (`.github/dependabot.yml`): sebulan sekali GitHub membuka pull request bila ada versi baru library atau GitHub Actions. Hanya pemberitahuan: tidak menjalankan build dan tidak mengubah kode utama sampai pull request itu digabung manual. Pembaruan `dev.rikka.shizuku` perlu diverifikasi ulang dulu karena aplikasi memanggil bagian privat Shizuku lewat reflection. Belum terbukti GitHub sudah memprosesnya.

## v109
- Tidak ada perubahan pada aplikasi. Hasil CI v108 terkonfirmasi hijau: tes, lint, detekt, dan build lolos. Laporan lint kini 0 error dan 0 warning, dan temuan informasi `AutoboxingStateCreation` sudah hilang (sisa 6 item informasi yang memang sengaja dibiarkan: versi target Android dan 5 versi library yang lebih baru). Laporan detekt 0 temuan. Larangan impor `runBlocking` dan `GlobalScope` diterima konfigurasi tanpa error, tetapi belum teruji menangkap pelanggaran karena memang tidak ada yang melanggar. Perilaku di perangkat belum diuji. Seluruh antrean yang bisa diamati lint dan detekt kini selesai.

## v108
- Tidak ada perubahan perilaku yang terlihat. Pengawasan kode diperkuat lewat detekt: impor `runBlocking` dan `GlobalScope` kini ditolak (saat ini tidak ada yang memakainya). Penyimpan nomor tab aktif di layar utama memakai state khusus angka (`mutableIntStateOf`), sehingga satu temuan informasi lint (`AutoboxingStateCreation`) seharusnya hilang dari laporan. Belum dibuktikan oleh CI dan belum diuji di perangkat (tab aktif harus tetap bertahan saat layar diputar).

## v107
- Tidak ada perubahan pada aplikasi. Hasil CI v105 dan v106 terkonfirmasi hijau (tes, lint, detekt, build rilis dan debug lolos). Artifact baru `LagFix-lint-detekt-<nomor build>` terbukti terbit dan berisi laporan lengkap: lint melaporkan 0 error dan 0 warning, detekt melaporkan 0 temuan (berkas teksnya kosong karena memang tidak ada temuan; ringkasan metrik ada di `detekt.md`). Perilaku di perangkat belum diuji.

## v106
- Tidak ada perubahan pada aplikasi. Saat build hijau, GitHub Actions kini juga mengunggah laporan lint dan detekt (log mentah, laporan detekt, dan file hasil lint) sebagai artifact tersendiri bernama `LagFix-lint-detekt-<nomor build>`, selain dua artifact APK. Sebelumnya laporan itu hanya terunggah saat build gagal (di dalam `LagFix-fail-log-<nomor build>`, yang tidak berubah). Saat tidak ada temuan, isi laporan memang ringkas.

## v105
- Notifikasi layanan persisten kini memakai saluran berprioritas minimum dan disembunyikan dari layar kunci (sesuai dokumen konfigurasi kedua dari user). Di Pengaturan, kartu Keandalan latar belakang punya tombol baru yang membuka halaman Autostart atau baterai milik Xiaomi, Samsung, atau Huawei; merek lain membuka daftar pengecualian optimasi baterai Android. Bagian alarm 60 detik pada dokumen itu sengaja tidak diterapkan karena melanggar aturan baterai proyek. Perilaku di perangkat belum diuji.

## v104
- Notifikasi layanan latar depan persisten disesuaikan dengan dokumen konfigurasi standar yang diberikan: notifikasi tidak hilang saat diketuk, berkategori layanan, berprioritas rendah, tidak bunyi atau getar ulang saat diperbarui; saluran notifikasi punya nama dan deskripsi yang jelas di Setelan; di Android 14 ke atas tipe layanan diberikan eksplisit. Bagian yang khusus kebijakan Google Play sengaja tidak diterapkan. Perilaku di perangkat belum diuji, dan ini belum terbukti memperbaiki masalah notifikasi yang hilang.

## v103
- Tidak ada perubahan pada aplikasi. Pengawasan kualitas kode kini dipusatkan di lintDebug dan detekt saja: dokumen rencana dan `.cursorrules` disesuaikan, pemeriksaan yang tidak bisa diamati kedua alat itu tidak lagi jadi syarat, dan satu kandidat penguatan (larangan impor `runBlocking` dan `GlobalScope` lewat detekt) dicatat tanpa diaktifkan.

## v102
- Tidak ada perubahan pada aplikasi. Hasil CI v99 terkonfirmasi: rilis `build-84` terbit untuk commit v99, artinya tes, lint, dan detekt lolos dan APK berhasil dibuat (temuan detekt terakhir dari v98 sudah tertutup). Perilaku di perangkat belum diuji.

## v101
- Tidak ada perubahan pada aplikasi. Perapian dokumen proyek: riwayat batch v25 sampai v93 dipindah dari `PROJECT_STATE.md` ke `docs/archive/PROJECT_STATE_v25-v93.md` (isi identik, tidak ada yang dibuang) sehingga `PROJECT_STATE.md` susut dari sekitar 327 KB menjadi sekitar 45 KB, dan aturan kerja di `.cursorrules` disamakan dengan konstitusi pengembangan v3.5.

## v100
- Tidak ada perubahan pada aplikasi. Ditambahkan dokumen rencana `PENDING_CONSTITUTION_PLAN.md` berbasis konstitusi pengembangan yang berlaku: peta aturan ke proyek, audit pagar Android (crash, lifecycle, thread, baterai, keamanan, OOM) dari pembacaan kode, antrean batch berikutnya (cek CI v99, uji perangkat snapshot logcat, penyelidikan notifikasi persisten, perapian dokumen), dan daftar keputusan terbuka.

## v99
- Tidak ada perubahan perilaku aplikasi. Hasil CI v98: build, tes (termasuk tes batas waktu perintah shell baru), dan lint hijau; detekt menemukan 1 baris dokumentasi terlalu panjang pada berkas tes, kini dibungkus menjadi dua baris tanpa mengubah isi tes.

## v98
- Tombol "Ambil logcat sistem" tidak lagi bisa macet selamanya di "Mengambil…": tiap perintah sistem pada ringkasan kini dibatasi waktu (40 detik per perintah, 150 detik total), tombol dilepas otomatis oleh pagar waktu di layar (4 menit), dan ketukan kedua saat snapshot pertama masih berjalan ditolak dengan pesan jelas. Dump mentah (.zip) tetap diambil lebih dulu dan tidak dibatasi.
- Riwayat dan daftar log diagnostik kini hanya menampilkan 5 entri terbaru, sisanya lewat tombol "Tampilkan semua" / "Ringkas". Batas penyimpanan tidak berubah: Riwayat maksimal 30 baris dan daftar log maksimal 50 file terbaru (yang terlama jatuh lebih dulu).

## v97
- Tidak ada perubahan pada aplikasi. Pembetulan catatan v96: pengamatan di HP menunjukkan notifikasi layanan persisten tetap tidak muncul selama aplikasi masih hidup atau terlihat di Recents, walau dipicu dari luar (mis. QS tile); notifikasi hanya muncul bila aplikasi di-swipe/dimatikan lalu ada pemicu eksternal. Karena itu pemasangan ulang saat aplikasi dibuka (v96) belum terbukti membantu, dan penyebab pastinya masih diselidiki.

## v96
- Notifikasi layanan persisten kini dipasang ulang otomatis setiap aplikasi dibuka (bila toggle layanan persisten aktif dan notifikasinya sedang tidak tampil). Penyebabnya: HP membatasi LagFix di latar belakang sehingga Android menolak memunculkan notifikasi itu dari proses yang lahir di latar belakang (mis. setelah pasang pembaruan atau lewat widget), tetapi mengizinkannya saat aplikasi terlihat. Ini tidak menjamin notifikasi bertahan selamanya di latar belakang.

## v94
- Tidak ada perubahan perilaku aplikasi. Hasil CI v93: build, tes, dan lint hijau, dan detekt turun dari 14 ke 2 temuan. Batch penutup perapian detekt: pemecahan teks stempel jam pada `formatStamp12h` kini memakai tiga grup hasil pencocokan (tanggal, jam, menit) tanpa grup teks penuh sehingga tidak melewati batas jumlah elemen destructuring, dan satu baris panjang pada tes pemotongan log dibungkus menjadi beberapa baris tanpa mengubah angka maupun pemeriksaan.

## v93
- Tidak ada perubahan perilaku aplikasi. Hasil CI v92: build, tes, dan lint hijau, dan detekt turun dari 42 ke 14 temuan. Pengumpulan laporan lint dan detekt ke satu artifact log kegagalan terbukti berjalan (satu unduhan). Batch keempat perapian detekt: angka literal pada pencatat crash, daftar log, dan model tampilan diganti konstanta bernama atau konstanta versi Android, baris panjang dipecah, penanganan galat di batas shell dan unduhan diberi alasan tertulis, dan fungsi di model tampilan utama dikurangi dengan memindahkan fungsi yang tidak menyentuh state ke luar kelas. Fungsi pengatur tema yang tidak lagi dipanggil dari mana pun dihapus.

## v92
- Tidak ada perubahan perilaku aplikasi. Hasil CI v91: build dan tes hijau, lint 0 error, dan detekt turun dari 70 ke 42 temuan. Batch ketiga perapian detekt: angka literal pada cek update, pengaturan paksa trim saat reboot, penyimpanan riwayat, dan worker jadwal diganti konstanta bernama; fungsi cek update dan unduh APK dipecah agar tidak bersarang terlalu dalam; penanganan galat di batas jaringan dan shell diberi alasan tertulis.
- Di CI, laporan lint dan detekt kini digabung ke dalam satu artifact log kegagalan (`LagFix-fail-log-<nomor>`), sehingga saat build gagal cukup satu kali unduh. Artifact terpisah `LagFix-lint-detekt-report-<nomor>` dihapus.

## v91
- Tidak ada perubahan perilaku aplikasi. Hasil CI v90: build dan tes hijau, lint 0 error, dan detekt turun dari 134 ke 70 temuan. Batch kedua perapian detekt: angka literal pada format interval dan durasi diganti konstanta bernama, baris panjang di layar utama dan komponen desain dipecah, dan satu pesan galat tes tulis dipisah ke variabel tanpa mengubah teksnya.

## v90
- Tidak ada perubahan perilaku aplikasi. Pemeriksaan lint dibereskan dengan menambahkan aturan backup untuk Android 11 ke bawah (hasilnya tetap tanpa backup). Batch pertama perapian detekt pada kode snapshot logcat: angka literal diganti konstanta bernama, baris panjang dipecah, penangkapan galat di batas shell/I-O diberi alasan tertulis, dan fungsi `capture` dipecah menjadi bagian-bagian lebih kecil dengan isi dan urutan keluaran yang sama.

## v89
- Tidak ada perubahan perilaku aplikasi. Pemeriksaan lint dibereskan: ikon peluncur dipindah ke folder `mipmap-anydpi`, aturan ekstraksi data Android 12+ ditambahkan (hasilnya tetap tanpa backup), dan pengecualian lint yang disengaja kini tercatat beralasan di `app/lint.xml`. Konfigurasi detekt disesuaikan untuk fungsi Compose.

## v88
- Tidak ada perubahan pada aplikasi. Pemeriksaan kualitas kode otomatis (lint dan detekt) di CI kini wajib lolos sebelum APK dibuat dan dirilis; temuannya menggagalkan build dan rinciannya ikut masuk ke log kegagalan.

## v87
- Tidak ada perubahan pada aplikasi. Build v85 gagal di CI karena kode tes tidak terkompilasi (konstanta pola filter belum dikualifikasi nama object); perbaikannya sudah ada di v86 dan rilis ini memastikan perbaikan tersebut ikut dipublikasikan.

## v86
- Snapshot logcat dibuat totalitas, bukan sekadar ringkasan terfilter. Tombol "Ambil logcat sistem" kini menyimpan 2 berkas di Documents/LagFix: ringkasan (.txt) dan dump mentah logcat lengkap tanpa filter (.zip, main+system+events+crash) yang dialirkan langsung ke zip sehingga tidak ada filter yang bisa menyembunyikan bukti dan tidak memenuhi memori. Kegagalan salah satu bagian tidak lagi menggagalkan bagian lain, dan kegagalan dump mentah selalu dilaporkan.
- Ringkasan kini memuat status saat ini yang tidak ikut berputar seperti logcat: status sisi-aplikasi (toggle persisten, notifikasi aktif, servis berjalan, pengecualian baterai, standby bucket), jadwal WorkManager (jobscheduler), notifikasi, servis, appops, baterai, Doze, dan waktu `last-fstrim` dengan presisi detik. Catatan proses mati diperluas dari 10 ke 30.
- Setelah dump, buffer logcat diperbesar ke 16M (sementara, kembali normal saat reboot). Sebelumnya buffer hanya menyimpan sekitar 30 detik terakhir sehingga kejadian beberapa menit lalu sudah hilang. Tekan tombol sekali sebelum menguji dan sekali lagi sesudahnya.
- Perbaikan tes: konstanta pola filter di tes v85 belum dikualifikasi nama object sehingga tes tidak akan terkompilasi.

## v85
- Tombol "Ambil logcat sistem" diperbaiki: bagian "Jejak fstrim oleh sistem" dan "Logcat terkait LagFix" sebelumnya terisi ratusan baris derau karena kata "fstrim"/"lagfix" juga cocok dengan nama paket aplikasi sendiri (com.lagfix.fstrim), sehingga baris yang dicari tergeser. Kini baris yang menyebut nama paket dibuang dari bagian fstrim, dan bagian LagFix hanya memuat baris peristiwa (lifecycle servis persisten, start proses, kill, FGS stop, job). Header snapshot kini mencantumkan zona waktu aplikasi supaya jam `dumpsys mount` (UTC) mudah dibandingkan dengan jam lokal.

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
