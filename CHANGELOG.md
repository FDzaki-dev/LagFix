# Changelog

## v189
- Perintah user: "Fokus perburuan cacat yang lain!!". Unggahan: `LagFix_diag_persistent_service_20261121_173516.txt`. Laporan CI v187/v188 belum ada.
- Dump logcat mentah ("Ambil logcat sistem") kini berbatas waktu 120 detik. Sebelumnya dibaca tanpa batas: `logcat` yang macet menahan snapshot dan kunci "sedang berjalan" selamanya, sehingga tombol mengambang dan ketukan berikutnya ditolak. Kini pengawas menghentikan proses pada batas; ringkasan memuat `Galat shell: Waktu habis 120000 ms (dump dihentikan)` dan jumlah byte logcat yang sudah tertulis dicatat jujur.
- Kartu "Paksa trim saat reboot" dan baris `fstrim_mandatory_interval` di snapshot: perintah `settings get/put/delete` kini berbatas waktu 10 detik (sebelumnya tanpa batas; Shizuku macet membuat kartu sibuk selamanya dan snapshot ikut menggantung). Timeout ditampilkan sebagai "waktu habis" (data mentah); penulisan tetap dibuktikan lewat baca ulang.
- Tes: `BoundedShellTest` +2 tes (`ProcessWatchdog`: proses macet dihentikan pada batas dan `timedOut`; `cancel()` sebelum batas tidak menyentuh proses). Target run berikutnya: 215 tes lulus (213 pada v188 belum terbukti).
- Data baru dicatat untuk BUG_TARGET v147 (tidak ada perubahan kode): start dari latar pada 17:35 gagal lagi; saat start `importance=125` lalu 6 detik kemudian `importance=300` dan `foreground=false`, `isBackgroundRestricted=true`.
- Belum dijalankan di CI dan belum diuji di perangkat.

## v188
- Perintah user: "Fokus ke hal lain yang bisa diperbaiki langsung!!" (di luar notifikasi persisten). Unggahan: `LagFix_diag_persistent_service_20261121_124201.txt` dan `LagFix_diag_process_catch_20261121_124437.txt`. Laporan CI v187 belum ada.
- Pemasangan senyap (Shizuku): pengaliran APK ke stdin `pm` kini berbatas waktu. Sebelumnya `writeAll` berjalan sebelum batas waktu `BoundedShell` dimulai, sehingga bila `pm` tidak membaca stdin penulisan memblokir tanpa batas dan "Update sekarang" menggantung tanpa jatuh ke installer sistem. Kini APK dialirkan di thread daemon dan ditunggu maksimal 120 detik; lewat batas proses dihentikan dan hasilnya galat pengaliran (`feed=IOException: Pengaliran APK melewati ... ms`), sehingga pemasangan jatuh ke installer sistem seperti kegagalan lain. Galat non-IO di thread dibungkus agar tidak menjadi uncaught exception. Perilaku normal tidak berubah. Belum teramati di perangkat; semantik `destroy()` pada pipa Shizuku belum terbukti (hanya memengaruhi pembersihan thread).
- Tes: `SilentInstallerTest` +4 tes (Process palsu): APK terkirim utuh dan stdin ditutup; stdin macet kembali pada batas waktu dan proses dihentikan; `IOException` dikembalikan apa adanya; galat non-IO dibungkus. Target run berikutnya: 213 tes lulus (209 pada v187 belum terbukti).
- Data baru dicatat untuk BUG_TARGET v147 (tidak ada perubahan kode): saat app di depan notifikasi foreground service asli tampil (`flags=0x2006a`, `foreground=true`) walau `isBackgroundRestricted=true`; pada start dingin, notifikasi tampil bersamaan dengan `startForeground` kedua (dari `onResume`), bukan yang pertama.
- Belum dijalankan di CI dan belum diuji di perangkat.

## v187
- Unggahan user: `LagFix_diag_persistent_service_20261121_123223.txt` + `LagFix_v186.zip` (tanpa teks). Laporan CI run 38 (v186) belum ada; kompilasi v186 terbukti tak langsung (baris `lateState` muncul di diag).
- Diag notifikasi persisten (X6855, 12:32:23): kejadian kedua berturut-turut dengan pola sama. Start dari latar, `startForeground` tanpa exception, id 42 belum ada 1,5 detik sesudahnya, lalu ada sesudah `notify()` (flags `0x2000a`, tanpa flag foreground service). Baris `lateState`: umur proses 3422 ms, `importance` 300, servis `foreground=false`. `isBackgroundRestricted = true`. Sebab belum terbukti; ketahanan notifikasi hasil repost dalam jangka panjang belum terbukti.
- Temuan audit statis (belum teramati di perangkat): (1) `shown` mensyaratkan flag foreground service, jadi notifikasi hasil repost selalu tercatat `shown=false` dan tidak bisa dibedakan dari notifikasi yang absen. (2) `repostAndRead` berjalan di coroutine IO yang lepas dari lifecycle servis: bila `stop()`/`onDestroy()` terjadi dalam jeda 1,5 detik sebelum `notify()`, notifikasi diposting sesudah pembatalan dan tertinggal.
- `PersistentTrimService`: flag `destroyed` (diset di awal `onDestroy`); `repostAndRead` tidak memanggil `notify()` bila servis sudah destroyed dan membatalkan id 42 bila destroyed terlihat sesudah `notify()`. `ensureShowing` membaca `activeNotifications` sekali dan mencatat `present=<true|false|null>` (id 42 ada tanpa syarat flag; `null` = pembacaan gagal).
- `CatchJournal` + `ProcessCatcher`: log `catch` dan kunci jurnal `observations` kini memuat `shown=` dan `present=`. Baris jurnal lama (tanpa `present`) tetap terbaca dan tidak dilipat dengan baris baru.
- Tes: `CatchJournalTest` disesuaikan + 4 tes murni (`formatNotifState`, `notificationPresent`). Target run berikutnya: 209 tes lulus (205 pada v186 belum terbukti).
- Belum dijalankan di CI dan belum diuji lama di perangkat. BUG_TARGET v147 tetap terbuka sampai `process_catch` jangka panjang tiba.

## v186
- Unggahan user: `LagFix_diag_persistent_service_20261121_122444.txt` (tanpa teks). Laporan CI run 37 (v185) belum ada; kompilasi v185 terbukti tak langsung (baris `repost` muncul di diag).
- Diag notifikasi persisten (X6855, 12:24:44): start dari latar, `startForeground` tanpa exception, notifikasi id 42 belum ada 1,5 detik sesudahnya. Percobaan v185 bekerja pada kejadian ini: `notify()` tanpa exception dan 1,5 detik sesudahnya id 42 ada (flags `0x2000a`, tanpa flag foreground service). Belum terbukti notifikasi itu bertahan lama di latar.
- `PersistentTrimService.stop()`: sesudah menghentikan servis, notifikasi id 42 dibatalkan eksplisit. Temuan audit statis atas repost v185 (belum teramati di perangkat): notifikasi hasil `notify()` dari instans servis yang sudah mati tidak dibatalkan `onDestroy()`, jadi bisa tertinggal sesudah toggle dimatikan.
- Diag `persistent_service`: baris baru `lateState` di akhir berkas (umur proses, `importance`, flag `foreground` servis menurut `getRunningServices`; `null` bila tak terbaca). Importance sampel awal berbeda antar diag (125 pada 12:14, 300 pada 12:24) untuk gejala yang sama; baris ini membaca keadaan sesudah jeda.
- Tes: `FormatActiveNotificationsTest` +2 tes murni untuk `formatLateState`. Target run berikutnya: 205 tes lulus (203 pada v185 belum terbukti).
- Belum dijalankan di CI dan belum diuji lama di perangkat. BUG_TARGET v147 tetap terbuka sampai diag/`process_catch` jangka panjang tiba.

## v185
- Unggahan user: `LagFix_v184.zip` + `LagFix_diag_persistent_service_20261121_121434.txt` (tanpa teks). Laporan CI run 36 (v184) belum ada: kompilasi v184 terbukti tak langsung (baris konteks baru muncul di diag), tes/detekt/lint belum terverifikasi.
- Diag notifikasi persisten (X6855, 12:14:34): `startForeground` tidak melempar exception, importance proses 125 (foreground service tanpa Activity di atas, artinya start dari latar), `isBackgroundRestricted = true`, dan notifikasi id 42 tetap belum ada 1,5 detik sesudahnya. Pola sama dengan diag 11:52. Koreksi catatan v184: setelah `startForeground`, proses di latar terbaca 125, bukan 300/400.
- `PersistentTrimService`: percobaan perbaikan. Bila 1,5 detik sesudah `startForeground` notifikasi id 42 belum ada, notifikasi yang sama diposting lewat `notify()` (id sama, jadi menggantikan). Hasilnya dicatat mentah (exception `notify` apa adanya dan dump `activeNotifications` sesudah repost) di file diag atau logcat. `onDestroy` membatalkan notifikasi hasil repost. Urutan `startForeground`, channel, dan isi notifikasi tidak berubah.
- Tes: `FormatActiveNotificationsTest` +4 tes murni untuk keputusan repost. Target run berikutnya: 203 tes lulus.
- Belum dijalankan di CI dan belum terbukti di perangkat; belum boleh disebut sembuh. BUG_TARGET v147 tetap terbuka sampai diag berikutnya memuat baris `repost`.

## v184
- Unggahan user: `LagFix_laporan-untuk-claude_run35.zip` + `LagFix_diag_persistent_service_20261121_120020.txt` (tanpa teks). Laporan run 35 untuk v183: semua hijau (assembleDebug exit 0, 197 tes lulus, detekt 0, detekt typed 0, SpotBugs 0, ArchUnit 7 aturan 0 pelanggaran, lint hanya hint compose-bom).
- Diag notifikasi persisten (X6855, 12:00:20): notifikasi aplikasi diizinkan, channel importance 4 (HIGH), `startForeground` tidak melempar exception, tetapi notifikasi belum ada 1,5 detik sesudahnya. Berkas itu belum mencatat pemicu start dan keadaan proses.
- `PersistentTrimService`: log diag `persistent_service` kini memuat satu blok konteks start (data mentah): pid, umur proses, `startId`/`flags`/`intentNull`, `importance` proses, dan `isBackgroundRestricted`. Hanya pencatatan sesudah `startForeground()`; alur servis dan notifikasi tidak berubah. Diag berikutnya bisa membedakan start dari latar vs depan.
- Tes: `FormatActiveNotificationsTest` +2 tes murni untuk format baru. Target run berikutnya: 199 tes lulus.
- Belum dijalankan di CI. BUG_TARGET v147 (notifikasi persisten di latar) tetap terbuka.

## v183
- Unggahan user: `LagFix_laporan-untuk-claude_run34.zip` + `LagFix_diag_process_catch_20261121_115212.txt` (tanpa teks). Laporan run 34 untuk v182: semua hijau (assembleDebug exit 0, 196 tes lulus, detekt 0, detekt typed 0, SpotBugs 0, ArchUnit 7 aturan 0 pelanggaran, lint hanya hint compose-bom).
- `ArchitectureTest`: kelas alur pembaruan (`UpdateHttp`, `UpdateWebFallback`, `ReleaseRef`, `SilentInstaller`, `SilentInstallResult`) kini terdaftar di dua daftar aturan arsitektur (bebas Compose dan bebas kelas UI) sehingga ikut diperiksa; sebelumnya luput. Tes baru `coreLists_coverUpdateFlowClasses` menjaga pendaftaran itu. Target run berikutnya: 197 tes lulus. Perilaku aplikasi tidak berubah.
- Diag notifikasi persisten (X6855): stempel `startHistory` kini terbaca (perbaikan v147 terbukti di perangkat). Notifikasi baru muncul setelah `startForeground` kedua dari `onResume`; selama aplikasi di latar tetap `shown=false` dan `isBackgroundRestricted=true`. Sebab pastinya belum terbukti, `PersistentTrimService` tidak diubah. BUG_TARGET v147 tetap terbuka.
- Belum dijalankan di CI.

## v182
- Unggahan user: `LagFix_laporan-untuk-claude_run33.zip` (tanpa teks). Laporan run 33 untuk v181: semua hijau (assembleDebug exit 0, 193 tes lulus, detekt 0, detekt typed 0, SpotBugs 0, ArchUnit 7 aturan 0 pelanggaran, lint hanya hint compose-bom).
- Audit statis alur pasang senyap (`SilentInstaller`): bila `pm install` menolak sebelum membaca stdin, penulisan APK gagal (EPIPE) dan dulu hanya "IOException: Broken pipe" yang tercatat; alasan penolakan dari `pm` hilang. Kini galat pengaliran dilaporkan, proses tetap dibaca, dan log memuat keluaran mentah `pm` ditambah baris `feed=`. Pemasangan yang pengaliran APK-nya gagal tidak pernah dianggap sukses; tetap jatuh ke installer sistem seperti sebelumnya.
- Teks log/detail untuk jalur normal (tanpa galat pengaliran) sama persis dengan v181.
- Tes: `SilentInstallerTest` +3 tes murni (format detail, baris `feed=`, penilaian sukses). Target run berikutnya: 196 tes lulus.
- Belum dijalankan di CI. Temuan terbuka (tak diubah, perlu bukti perangkat): pengaliran APK ke stdin `pm` belum punya batas waktu. BUG_TARGET v147 (notifikasi persisten) tetap terbuka.

## v181
- Unggahan user: `LagFix_v180.zip` + `LagFix_laporan-untuk-claude_run32.zip` (tanpa teks). Laporan run 32: build debug v180 terkompilasi (`assembleDebug` exit 0), 190 tes lulus, ArchUnit 7 aturan 0 pelanggaran, SpotBugs 0; sisanya gagal di detekt (1 temuan), detekt typed (5 temuan), dan lint (1 error).
- `SilentInstaller.kt`: `install` kini punya 2 `return` (batas detekt 2; sebelumnya 3). Dua pengaman awal digabung: Shizuku harus siap, lalu paket APK harus sama dengan paket aplikasi; pemeriksaan paket dipindah ke fungsi murni baru `packageMismatch` (urutan tetap: paket APK hanya dibaca bila Shizuku siap).
- Typed `NullableToStringCall` x4 (`SilentInstaller.kt` 3 tempat, `UpdateChecker.kt:161`): nilai nullable di templat string kini diberi `?: "null"` eksplisit. Teks log dan teks detail hasil sama persis dengan sebelumnya (templat nullable mencetak "null"); yang berubah hanya penanganannya jadi eksplisit.
- Lint `NewerVersionAvailable`: `com.squareup.okio:okio` 3.18.1 -> 3.18.2 di `app/build.gradle.kts` (versi dari pesan lint CI). Hint `compose-bom` 2026.09.00 -> 2026.11.01 tidak disentuh (disengaja; BOM baru hanya setelah cek visual).
- Tes: `SilentInstallerTest` +3 tes murni untuk `packageMismatch` (paket sama, paket lain, paket tak terbaca). Target run berikutnya: 193 tes lulus, detekt 0, detekt typed 0, lint hanya hint compose-bom.
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint). Perilaku runtime tidak berubah; BUG_TARGET v147 (notifikasi persisten) tetap terbuka dan tak disentuh.

## v180
- Perintah user: "Perbaiki dan terapkan OkHttp+Okio" (unggahan: `LagFix_v179.zip`, `LagFix-fail-log-152.zip`, `LagFix_diag_process_catch_20261121_062959.txt`).
- Bukti (log build gagal run 152): `:app:compileDebugKotlin` gagal di `UpdateWebFallback.kt:38:72` ("Missing '}'"), `:78:1` ("Unclosed comment"), dan 3 "Unresolved reference" (`latestTag`, `apkHref`, `pageText`) yang hanya akibat. Akar: KDoc `apkHref` (v178) memuat pola berkas APK yang berisi garis miring lalu bintang; Kotlin mendukung komentar bersarang, jadi urutan itu membuka komentar baru yang tak pernah tertutup dan sisa berkas ikut menjadi komentar. Build v178 dan v179 tidak pernah terkompilasi di CI (run 29 = v177 adalah yang terakhir hijau).
- Perbaikan: KDoc `apkHref` diganti kata-kata tanpa urutan itu. Pemindaian seluruh `app/` dan `config/` menemukan tidak ada komentar bersarang atau tak tertutup lain.
- OkHttp+Okio: `UpdateHttp.kt` (baru) menjadi satu-satunya pintu jaringan pembaruan, menggantikan `HttpURLConnection`. Klien malas: cek rilis sambung 6 + baca 8 dtk (nilai v174), jalur cadangan tanpa mengikuti pengalihan, unduh 15 + 15 dtk (nilai v171); ketiganya berbagi pool koneksi. Pesan galat "HTTP <kode>" identik, jadi deteksi rate limit 403/429 dan pemetaan `friendlyError` tak berubah.
- Unduh APK: `ResponseBody.source()` dialirkan ke berkas lewat `sink().buffer().use {}`; tiap putaran memindahkan maksimal 8 KiB lalu `emitCompleteSegments()`, jadi memori tidak menampung seluruh APK dan `bytes()` tidak dipakai. `Accept-Encoding: identity` tetap, supaya Content-Length = ukuran APK dan bilah progres akurat.
- Teks kecil (JSON rilis, CHANGELOG, halaman daftar aset) dibaca lewat aliran Okio dengan batas 4 MiB; lebih dari itu menjadi galat, bukan dimuat utuh.
- `SilentInstaller.kt`: APK dialirkan ke stdin `pm install` lewat Okio (`writeAll`), menggantikan `buffered()` + `copyTo`; perilaku sama.
- `app/build.gradle.kts`: `com.squareup.okhttp3:okhttp:5.5.1` (rilis stabil terbaru, 22 Okt 2026) dan `com.squareup.okio:okio:3.18.1` (versi yang ditarik OkHttp 5.5.x).
- Tes: `DownloadProgressTest` dialihkan ke Okio (`Buffer`/`Source`, asersi lama sama) + 1 tes baru (maks 8 KiB per putaran, urutan byte terjaga); `UpdateHttpTest` (baru, 6 tes: kode 2xx dan galat 199/300/403/429, pemetaan rate limit, teks UTF-8, batas teks, header permintaan, header unduhan).
- Berkas diag `LagFix_diag_process_catch_*` diterima tetapi tidak diproses di batch ini; BUG_TARGET v147 (notifikasi persisten) tetap terbuka dan tak disentuh.
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint); v178 dan v179 juga belum pernah terkompilasi, jadi run berikutnya bisa memunculkan galat kompilasi lanjutan di berkas v178/v179. Target run berikutnya: 190 tes lulus dan semua alat tetap 0 temuan.

## v179
- Perintah user: "Adaptasi maksimal ke Lagfix!!" dengan berkas `update/` dari SONIX (`GitHubReleaseChecker`, `UpdateDownloader`, `UpdateManager`).
- Diadopsi: pasang senyap lewat Shizuku. Bila Shizuku siap dan APK memang paket aplikasi ini, "Update sekarang" memasang APK dengan `pm install -r` di proses Shizuku, tanpa dialog installer; APK dialirkan lewat stdin secara buffered. Bila Shizuku belum siap atau pemasangan gagal, dipakai installer sistem seperti sebelumnya, dan keluaran mentah kegagalan dicatat ke log (tag `SilentInstaller`).
- Diadopsi: keadaan "Memasang" di UI. Bilah di kartu Pembaruan berganti judul menjadi "Memasang pembaruan…" dengan bilah tak tentu selama pemasangan senyap berjalan.
- `SilentInstaller.kt` (baru), `MainViewModel.kt` (properti `installing`, `installUpdate` mencoba pasang senyap dulu), `UpdateProgressBar.kt`, `MainActivity.kt` (parameter `installing`), `SilentInstallerTest` (3 tes baru).
- Tidak diadopsi: OkHttp/Okio (dependensi baru, tak bisa diverifikasi di sini), catatan rilis dari field `body` (workflow rilis LagFix tidak mengisinya), singleton `UpdateManager` (keadaan update sudah di `MainViewModel`), token GitHub (jalur web cadangan v178 sudah menangani rate limit).
- Batas yang diketahui: pemasangan sukses membuat sistem mematikan proses aplikasi, sehingga aplikasi tertutup tanpa dialog "Buka" dan perlu dibuka ulang manual (dugaan dari log SONIX, belum terbukti di LagFix).
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint); laporan CI v178 juga belum dikirim. Target run berikutnya: 183 tes lulus dan semua alat tetap 0 temuan. Setelah memasang: uji "Update sekarang" dengan Shizuku aktif, lalu kirim Log diagnostik.

## v178
- Laporan user (tangkapan layar tanpa teks, terpasang v1.0.149): kartu Pembaruan menampilkan "Gagal memeriksa pembaruan: Terlalu banyak permintaan ke GitHub (rate limit), coba lagi nanti." Laporan CI run 29 (commit v177): semua alat lulus, 175 tes lulus, detekt polos dan typed 0 temuan, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, lint hanya 1 petunjuk (compose-bom, disengaja).
- Akar: pengecekan hanya memakai API GitHub tanpa token (60 permintaan per jam per alamat IP; IP seluler dipakai bersama), sehingga mudah mendapat HTTP 403.
- `UpdateWebFallback.kt` (baru): jalur cadangan tanpa API. `github.com/FDzaki-dev/LagFix/releases/latest` dialihkan ke `/releases/tag/<tag>`, lalu halaman daftar aset rilis dibaca untuk menemukan tautan APK.
- `UpdateChecker.kt`: API tetap jalur utama; hanya bila API menjawab HTTP 403 atau 429, pengecekan jatuh ke jalur cadangan. Galat 429 kini juga dipetakan ke pesan rate limit.
- `MainActivity.kt`: error di kartu Pembaruan jadi dua tingkat: "Gagal memeriksa pembaruan" (judul kecil) dan sebabnya (teks kecil), keduanya berwarna error; dulu satu kalimat panjang yang tampak lebih besar dari judul kartu.
- `UpdateWebFallbackTest`: 5 tes baru (urai tag dari Location, urai tautan APK, pemetaan 403/429).
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint). Target run berikutnya: 180 tes lulus dan semua alat tetap 0 temuan. Asumsi belum terbukti di perangkat: format halaman daftar aset GitHub.

## v177
- Laporan user: "Ripple effect yang bertumpuk-tumpuk dengan desain pil tidak lu fix?!!" (merujuk rekaman layar Pengaturan yang disertai "Kurang finishing!!"; v176 tidak menyentuh ripple).
- Akar (dari kode, belum terbukti di perangkat): pil header lipat v175 digambar lewat latar di luar tombol, sehingga ada dua lapis pil dengan tinggi berbeda (latar luar setinggi area sentuh minimum, permukaan tombol beserta ripple di dalamnya).
- `Design.kt`: `foldBar()` dihapus, diganti `foldBarColors()` yang memberi tombol warna wadah (onSurface alpha 8%) sehingga latar pil adalah wadah tombol itu sendiri.
- `MainActivity.kt`: `FoldHeader` memakai `foldBarColors()` tanpa latar luar; keterangan status Terbuka/Tertutup dan aksi tidak berubah. Elemen lain (sakelar, baris tautan, bilah navigasi) tidak disentuh.
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint); laporan CI v175 dan v176 juga belum dikirim. Target run berikutnya: 175 tes lulus dan semua alat tetap 0 temuan.

## v176
- Laporan user: "Kurang finishing!!" (rekaman layar Pengaturan dan diag frame; tanpa laporan CI baru, jadi v175 belum terbukti di CI).
- Bukti rekaman: lima chip interval ditaruh satu baris yang bisa digeser, sehingga "3 hari" terpotong di tepi kartu dan "7 hari" tidak terlihat. Keterangan kolom Kustom ("Interval aktif: 15 menit") dan judul sakelar ("Interval radikal (< 15 menit)") membungkus dengan kata "menit" sendirian di baris kedua. Nama berkas log terputus di tengah angka. Teks baris tautan menjorok sekitar 12 dp dari judul kartu dan garis sekat.
- `MainActivity.kt`: chip interval jadi baris berisi 3 chip berlebar sama (baris terakhir diisi pengisi), semua preset terlihat tanpa menggeser; aksi chip tidak berubah. Keterangan kolom Kustom jadi "Aktif: ...". Label sakelar jadi "Interval radikal" (rentang 1-14 menit sudah ada di keterangannya).
- `MainActivity.kt`: teks baris tautan dan aksi (Unduh rilis terbaru, Lihat kode sumber, Laporkan masalah, Tentang aplikasi, tautan Info Aplikasi/Autostart/baterai, tombol Muat ulang/Tes tulis log/Ambil logcat sistem, baris berkas log) kini rata dengan judul kartu dan garis sekat; area sentuh tetap selebar baris.
- `MainActivity.kt`: baris berkas log jadi dua baris: nama (putus hanya sesudah garis bawah) dan sumber di baris kecil berwarna redup.
- Metrik frame tema Glass (120 Hz): median 15,0 ms, p99 25,3 ms, rata-rata GPU 8,77 ms per frame; hambatan GPU masih ada dan tidak disentuh batch ini.
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint). Target run berikutnya: 175 tes lulus dan semua alat tetap 0 temuan.

## v175
- Laporan user: "Masih nyaru dan kurang sekat pemisah!!" (rekaman layar Pengaturan, diag frame, laporan CI run 27). Laporan CI run 27 (commit v174): semua alat lulus. 175 tes lulus (target v174 tercapai: 169 + 6), detekt polos dan typed 0 temuan, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, peringatan kompilator 0, deprecated 0, lint hanya 1 petunjuk (compose-bom, disengaja).
- Bukti rekaman: di Pengaturan, baris Baterai, peringatan merah, tombol tautan, header lipat beserta isinya, sakelar, dan daftar tautan Info & diagnostik menyatu dalam satu kartu tanpa garis pemisah. Header lipat berupa teks biru tanpa latar. Tepi bawah kartu kaca nyaris tak tampak (border alpha 5%).
- `Design.kt`: `SectionDivider` baru (garis 1 dp, warna isi alpha 18%, ikut kedua tema) dan `Modifier.foldBar` (latar pil tipis alpha 8%). Border kartu kaca bagian tengah/bawah dinaikkan dari 12%/5% ke 20%/14%; tepi atas tetap.
- `MainActivity.kt`: 11 garis pemisah: kartu Jadwal (sebelum Interval, sebelum mode radikal), Keandalan latar belakang (sebelum panduan, sebelum sakelar layanan persisten), Paksa trim saat reboot (sebelum nilai sistem + aksi), Info & diagnostik (di bawah judul dan di antara tiap baris), Pembaruan (di antara status versi dan bagian unduhan/aksi). `FoldHeader` memakai `foldBar` sehingga header lipat tampak sebagai bilah.
- Metrik frame tema Glass (120 Hz): p99 26,9 ms (sebelumnya 49,6 ms), median 14,9 ms, rata-rata GPU 8,78 ms per frame; hambatan GPU masih ada dan belum ditangani.
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint). Target run berikutnya: 175 tes lulus dan semua alat tetap 0 temuan.

## v174
- Laporan user: "Periksa pembaruan aja lama banget. Hierarki, penanda juga masih kurang jelas!!" (rekaman layar, diag frame, laporan CI run 26). Laporan CI run 26 (commit v173): semua alat lulus. 169 tes lulus, detekt polos dan typed 0 temuan, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, peringatan kompilator 0, deprecated 0, lint hanya 1 petunjuk (compose-bom, disengaja).
- Bukti rekaman: spinner "Memeriksa pembaruan…" berputar minimal 41 detik; "Menghubungkan ke GitHub…" sekitar 10 detik per percobaan; dua kali muncul "Tidak ada koneksi internet" padahal Wi-Fi aktif, percobaan ketiga berhasil. Metrik frame tema Glass (120 Hz): 1110 dari 1114 frame melewati anggaran 8,33 ms, rata-rata GPU 8,66 ms per frame (hambatan GPU, belum ditangani).
- `UpdateChecker.kt`: changelog tidak lagi diambil di dalam `check` (dulu permintaan kedua berurutan sebelum hasil tampil); fungsi baru `fetchChangelog`. Batas sambung/baca API 10/10 dtk jadi 6/8 dtk. `friendlyError`: batas waktu kini "GitHub tidak merespons (waktu habis), coba lagi." dan gagal tersambung "Tidak bisa terhubung ke GitHub, coba lagi."; "Tidak ada koneksi internet." hanya untuk host tak terjangkau. Durasi tiap tahap (cek, changelog, sambung/selesai unduh) dan pengecualian mentah dicatat ke logcat dengan tag `UpdateChecker`.
- `MainViewModel.kt`: `checkUpdate` menampilkan hasil dulu, lalu changelog menyusul dan hanya dipasang bila hasil masih objek yang sama. `installUpdate` mencatat pengecualian mentah.
- `MainActivity.kt`: kartu Pembaruan: nama rilis tidak diulang ("build 147 (LagFix build 147)" jadi "build 147"); galat unduhan tepat di bawah status versi dengan label "Gagal memperbarui" (dulu terselip di antara tombol); "Cek ulang" jadi tombol teks saat hasil ada, jadi tiga tingkat jelas (Update terisi, Lihat changelog tonal, Cek ulang teks). Legenda grafik: spasi di dalam satu butir diganti NBSP sehingga "•" tidak lagi terpisah dari "titik = dipicu manual" saat baris turun.
- `Design.kt`: pilihan tema di Pengaturan > Tampilan diberi centang ✓ pada tema terpilih.
- `UpdateCheckerErrorTest`: 6 tes baru (pemetaan galat jaringan, rate limit, galat tanpa pesan, kontrak salinan changelog).
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint). Target run berikutnya: 175 tes lulus dan semua alat tetap 0 temuan. Setelah memasang: tekan "Cek pembaruan", lalu kirim Log diagnostik (baris tag `UpdateChecker`) untuk membuktikan tahap yang lambat.

## v173
- Laporan CI run 25 (commit v172): semua alat lulus. 169 tes lulus, detekt polos dan typed 0 temuan, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, peringatan kompilator 0, deprecated 0, lint hanya 1 petunjuk (compose-bom 2026.09.00 ke 2026.11.01, disengaja). Pelipatan penjelasan kartu Info & diagnostik (v172) lolos kompilasi, tes, detekt, SpotBugs, dan lint; tampilannya di perangkat belum dinilai.
- Hierarki kartu Statistik: baris ringkasan hasil ("Dari N proses terakhir: ... berhasil · ... gagal") kini gaya `titleSmall` sebagai baris utama kartu; dulu `bodySmall` sama kecil dengan catatan rata-rata di bawahnya. Teks dan logika tidak berubah.

## v172
- Laporan CI run 24 (commit v171): semua alat lulus. 169 tes lulus (target v171 tercapai: 160 + 9), detekt polos dan typed 0 temuan, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, peringatan kompilator 0, deprecated 0, lint hanya 1 petunjuk (compose-bom 2026.09.00 ke 2026.11.01, disengaja). Bilah progres unduhan dan penataan hierarki v171 lolos kompilasi, tes, detekt, SpotBugs, dan lint; tampilannya di perangkat belum dinilai.
- Hierarki kartu Info & diagnostik (lanjutan keluhan UI/UX v171): pembaca log dulu menyelipkan 3 paragraf penjelasan di antara tombol (tombol, paragraf, tombol, paragraf). Kini tombol Muat ulang, Tes tulis log, dan Ambil logcat sistem berkumpul di atas, dan ketiga paragraf dilipat di bawah "Penjelasan tombol" (kalimatnya tidak diubah). Logika dan callback tidak berubah.

## v171
- Laporan user (tanpa laporan CI baru; basis ZIP `LagFix_v170.zip`, CI run 23 hijau untuk v170): (1) unduhan pembaruan aplikasi tidak punya bilah progres; (2) tampilan keseluruhan dinilai kurang profesional dan hierarkinya membingungkan.
- Bilah progres unduhan: kartu Pembaruan kini menampilkan bilah, persen, dan "x MB dari y MB" selama mengunduh (bilah tak tentu bila server tak memberi ukuran, "Menghubungkan ke GitHub…" sebelum ada data). Tombol berubah "Mengunduh…" (dulu "Memasang…"), dan Cek ulang dinonaktifkan saat mengunduh. `UpdateChecker.download` menerima callback progres (opsional), laporan dibatasi sekitar 100 kali per unduhan, dan header `Accept-Encoding: identity` dikirim supaya ukuran akurat. Kemajuan disimpan di state terpisah `MainViewModel.downloadProgress` dan dibaca hanya oleh bilah, jadi layar tidak direkomposisi tiap laporan.
- Hierarki tampilan: tiap kartu punya judul + keterangan satu baris; judul bilah atas mengikuti layar aktif ("LagFix (fstrim)" / "Pengaturan"). Utama: hero hanya punya satu aksi primer (belum siap = aksi status, siap = Jalankan), judul "Siap dijalankan", dan baris ringkasan jadwal otomatis; Riwayat menampilkan hasil terakhir berwarna status lalu daftar "Proses terbaru"; Cek pembaruan selalu tombol sekunder. Pengaturan diurutkan Jadwal, Keandalan latar belakang (kini memuat status baterai yang dipindah dari Jadwal), Tampilan, Paksa trim saat reboot, Info & diagnostik; sakelar punya keterangan di bawah label; penjelasan panjang dilipat (kalimatnya tidak diubah). Logika, callback, dan state tidak berubah.
- `DownloadProgressTest`: 9 tes baru (persen/pecahan, format MB, jarak laporan, salinan berlapor, galat baca diteruskan).

## v170
- Laporan CI run 22 (commit v169): semua alat lulus. 158 tes lulus (target v169 tercapai), detekt polos dan typed 0 temuan, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, peringatan kompilator 0, deprecated 0, lint hanya 1 petunjuk (compose-bom 2026.09.00 ke 2026.11.01, disengaja). Pembanding stempel logcat yang sadar pergantian tahun (v169) lolos kompilasi, tes, detekt, SpotBugs, dan lint; efeknya hanya pada log diagnostik dan belum diamati di perangkat.
- Audit kode (tanpa bukti perangkat baru): `LogcatSnapshot` dibaca penuh. Proses dump mentah logcat (`captureRaw`, tombol "Ambil logcat sistem") dibuat langsung lewat Shizuku, tidak lewat `BoundedShell`. Hanya aliran keluarannya yang ditutup; aliran masukan dan galat tidak pernah ditutup, dan proses tidak dihentikan di jalur normal maupun saat penulisan zip gagal, sehingga satu pegangan pipa dan proses jauh Shizuku tertinggal per penekanan tombol. Cacat yang sama dengan `FstrimExecutor.sh` (v165) dan `BoundedShell.run` (v167); dampaknya di perangkat masih dugaan. Ini juga mengoreksi catatan v167 yang menyebut semua proses sudah dibereskan.
- `LogcatSnapshot.kt`: `captureRaw` kini selalu membereskan proses di blok `finally`, sesudah kode keluar dibaca. `BoundedShell.kt`: `release` dijadikan `internal` (isi tidak berubah). Hasil snapshot, isi zip, dan pesan galat tetap sama.
- `BoundedShellReleaseTest`: 2 tes baru untuk `release` yang dipanggil langsung: menutup kedua aliran dan menghentikan proses, serta galat `destroy` ditelan dan aman dipanggil dua kali.
- Audit selesai tanpa perubahan: `copyCapped`, pemformat, `capture`, `writeRawZip`, `section`, `appSideState`, `exitReasons`. Catatan kandidat (belum dikerjakan): `capture` membaca `BootTrimSetting.read()` tanpa batas waktu; bila menggantung, snapshot berikutnya ditolak sampai proses app mati.
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint). Target run berikutnya: 160 tes lulus dan semua alat tetap 0 temuan.

## v169
- Laporan CI run 21 (commit v168): semua alat lulus. 154 tes lulus (target v168 tercapai), detekt polos dan typed 0 temuan, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, peringatan kompilator 0, deprecated 0, lint hanya 1 petunjuk (compose-bom 2026.09.00 ke 2026.11.01, disengaja). Penangkapan penolakan start layanan saat boot (`BootReceiver.startAfterBoot`) lolos kompilasi, tes, detekt, SpotBugs, dan lint; efeknya di perangkat belum diamati.
- Audit kode (tanpa bukti perangkat baru), mengerjakan kandidat dari v168: stempel logcat `MM-dd HH:mm:ss.SSS` tidak memuat tahun, tetapi `KillCatcher.inWindow` dan `selectNearDeath` membandingkannya sebagai teks polos. Jendela waktu yang melintasi 31 Des ke 1 Jan (awal jendela lebih besar dari akhir) tidak mencocokkan satu baris pun, sehingga berkas diagnostik kill dan seksi logcat Shizuku kosong (sekitar 25 detik per tahun). Bila kematian proses jatuh sesudah pergantian tahun, baris sebelum pergantian juga salah dihitung sebagai sesudah kematian. Kegagalan ini belum teramati di perangkat dan hanya memengaruhi log diagnostik.
- `KillCatcher.kt`: fungsi baru `compareStamps` mengurutkan stempel dengan sadar pergantian tahun (stempel yang lebih kecil dari awal jendela dihitung sesudah pergantian). `inWindow` dan `selectNearDeath` memakainya; `selectNearDeath` kini menerima awal jendela (`from`). `shizukuLogcatItems` ikut sembuh karena memakai `inWindow`. Untuk jendela biasa (tanpa pergantian tahun) hasilnya identik dengan sebelumnya.
- `KillCatcherTest`: 3 pemanggilan lama diberi argumen awal jendela (asersi tidak berubah) dan 4 tes baru ditambahkan: `inWindow` lintas pergantian tahun, `selectNearDeath` dengan kematian sebelum dan sesudah pergantian, serta `shizukuLogcatItems` lintas pergantian.
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint). Logika tes baru diperiksa dengan emulasi di sandbox (gagal pada logika lama, lulus pada logika baru). Target run berikutnya: 158 tes lulus dan semua alat tetap 0 temuan.

## v168
- Laporan CI run 20 (commit v167): semua alat lulus. 150 tes lulus (target v167 tercapai), detekt polos dan typed 0 temuan, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, peringatan kompilator 0, deprecated 0, lint hanya 1 petunjuk (compose-bom 2026.09.00 ke 2026.11.01, disengaja). Pembersihan proses `BoundedShell.run` v167 lolos kompilasi, tes, detekt, SpotBugs, dan lint; efeknya di perangkat belum diamati.
- Audit kode (tanpa bukti perangkat baru): `BootReceiver.onReceive` memanggil `PersistentTrimService.start()` tanpa penangkap pengecualian. Di Android 12 ke atas OS bisa menolak start layanan latar depan dari proses latar (snapshot v95: pembatasan latar menolak), dan tiga pemanggil lain (`startIfEnabled`, `ensureShowing`, tile) sudah menangkap penolakan itu. Saat boot, `LagFixApp.startIfEnabled` menangkapnya, tetapi `BootReceiver` yang berjalan di proses dan kondisi yang sama melemparnya, sehingga proses jatuh saat boot. Kegagalan ini belum teramati di perangkat; perbaikannya defensif.
- `BootReceiver.kt`: fungsi baru `startAfterBoot` menjalankan start hanya bila toggle aktif dan mengembalikan kegagalan apa pun apa adanya (tidak dilempar). Bila ditolak, dicatat satu baris logcat mentah (kata kunci `LIFECYCLE`, pengecualian apa adanya, plus stacktrace). Jalur sukses identik dengan sebelumnya; dua start per boot (`BootReceiver` dan `LagFixApp`) sengaja tidak diubah.
- Tes baru `BootReceiverStartTest` (4 tes, tanpa Android): toggle mati tidak memulai apa pun, toggle hidup memulai tepat sekali, pengecualian yang ditolak dikembalikan sama persis dan tidak dilempar, `SecurityException` juga tertangkap.
- Audit selesai tanpa perubahan: `ProcessCatcher` (seluruh isi coroutine IO sudah dibungkus `runCatching`), `CatchJournal`, `LogBuffer`, `RecentsExclusion`/`LeaveGuard` (pasangan `beginWork`/`endWork` seimbang), `UpdateChecker`, widget, tile, dan `PersistentTrimService` (bug notifikasi persisten tetap terbuka dan tidak disentuh).
- Catatan kandidat (belum dikerjakan, dampak sangat kecil): `KillCatcher.inWindow`/`selectNearDeath` dan `shizukuLogcatItems` membandingkan stempel logcat `MM-dd HH:mm:ss.SSS` sebagai teks, sehingga jendela yang melintasi 31 Des ke 1 Jan tidak mencocokkan satu baris pun (sekitar 25 detik per tahun, hanya log diagnostik).
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint). Target run berikutnya: 154 tes lulus dan semua alat tetap 0 temuan.

## v167
- Laporan CI run 19 (commit v166): semua alat lulus. 146 tes lulus (target v166 tercapai), detekt polos dan typed 0 temuan, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, peringatan kompilator 0, deprecated 0, lint hanya 1 petunjuk (compose-bom 2026.09.00 ke 2026.11.01, disengaja). Perbaikan `refresh()`/`runNow()` v166 lolos kompilasi dan tes; efeknya di perangkat belum diamati.
- Audit kode (tanpa bukti perangkat baru): `BoundedShell.run` hanya memanggil `destroy()` saat batas waktu terlewati. Pada jalur normal proses tidak dihentikan dan aliran masukan/galatnya tidak ditutup, sehingga satu pegangan pipa (dan, lewat Shizuku, satu proses jauh) tertinggal pada setiap pemanggilan. `BoundedShell` dipakai `ProcessCatcher`/`OwnLogcat` (tiap capture), `LogcatSnapshot`, `KillCatcher`, dan `LogBuffer`. Ini cacat yang sama dengan `FstrimExecutor.sh` pada v165. Dugaan (belum terbukti di perangkat): proses jauh Shizuku menahan pegangan sampai `destroy()` atau proses aplikasi mati.
- `BoundedShell.kt`: `run` kini membungkus pekerjaannya dengan `try/finally` yang memanggil fungsi privat `release` (tutup masukan, tutup galat, `destroy()`). Kode keluar tetap dibaca lebih dulu, hasil dan exception asli tidak berubah, galat saat bersih-bersih ditelan. Dengan ini semua titik pembuatan `Process` di aplikasi (`FstrimExecutor.sh`, `LogcatSnapshot.shizukuProcess`, `OwnLogcat`) selalu dibereskan.
- Tes baru `BoundedShellReleaseTest` (4 tes, proses tiruan tanpa Shizuku): jalur normal membereskan proses setelah kode keluar dibaca, jalur batas waktu menghentikan pembaca dan membereskan proses, `destroy()` yang gagal tidak mengubah hasil, dan exception dari `start` tetap diteruskan.
- Audit `LagFixTileService.captureListening` selesai tanpa perubahan: `CrashLogger.logDiagnostic` membungkus tulisnya dengan `runCatching`, jadi coroutine lepas di sana tidak bisa menjatuhkan aplikasi.
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint). Target run berikutnya: 150 tes lulus dan semua alat tetap 0 temuan.

## v166
- Laporan CI run 18 (commit v165): semua alat lulus. 141 tes lulus (target v165 tercapai), detekt polos dan typed 0 temuan, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, peringatan kompilator 0, deprecated 0, lint hanya 1 petunjuk (compose-bom 2026.09.00 ke 2026.11.01, disengaja). Pembersihan proses Shizuku v165 lolos kompilasi dan tes; efeknya di perangkat belum diamati.
- Audit kode (tanpa bukti perangkat baru): `MainViewModel.refresh()` membaca status "sedang berjalan" sebelum pembacaan lambat (binder Shizuku dan PackageManager) lalu menulisnya kembali sesudahnya. Bila `runNow()` selesai di jendela itu, `refresh()` (dipanggil tiap aplikasi dibuka kembali) menimpa status selesai dengan status "sedang berjalan" yang basi. Tidak ada tempat lain yang mengembalikannya, jadi tombol Jalankan macet dan ketukan berikutnya ditolak sampai proses aplikasi mati. Peluangnya kecil (selebar lama pembacaan), tetapi akibatnya menetap.
- Audit kode (defensif, kegagalan belum teramati): `runNow()` tidak punya penangkap pengecualian di coroutine IO, sehingga kegagalan tak terduga (mis. dari `Scheduler.notifyChanged`) menjatuhkan aplikasi dan status "sedang berjalan" tak pernah dikembalikan.
- `MainViewModel.kt`: fungsi top-level baru `mergeUiState` menggabungkan hasil baca dengan state saat ini pada saat penulisan (status berjalan, cek pembaruan, unduhan). `refresh()` kini membaca dulu, lalu menggabungkan. `runNow()` dibungkus `try/catch/finally`: kegagalan dicatat apa adanya (logcat dan satu baris Riwayat `<Kelas>: <pesan>`), pembatalan dilempar ulang, dan status berjalan selalu dikembalikan di `finally`. Jalur normal tidak berubah (urutan, hasil, Riwayat, dan toast sama).
- Tes baru `UiStateMergeTest` (5 tes, tanpa Android): hasil baca dari data baru, field aksi dari state saat ini, status basi diabaikan, parameter `running` memaksa nilai, dan data baru null.
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint). Target run berikutnya: 146 tes lulus dan semua alat tetap 0 temuan.

## v165
- Laporan CI run 17 (commit v164): semua alat lulus. 137 tes lulus (target v164 tercapai), detekt polos dan typed 0 temuan, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, peringatan kompilator 0, deprecated 0, lint hanya 1 hint compose-bom (disengaja). Efek perbaikan latar Glass v164 pada kelancaran belum terukur karena belum ada berkas diag frame metrics dari APK v164.
- Audit kode (tanpa bukti perangkat baru): `FstrimExecutor.sh` membaca keluaran lalu menunggu proses selesai, tetapi proses Shizuku tidak pernah dihentikan dan aliran masukan/galatnya tidak ditutup, baik pada jalur normal maupun saat pembacaan gagal di tengah (pipa putus, thread dihentikan). Akibatnya shell bisa tertinggal dan pegangan pipa menumpuk satu per `sm fstrim` (pada mode radikal: tiap menit).
- `FstrimExecutor.kt`: pembacaan dipindah ke fungsi `collect(Process)` yang selalu menutup masukan/galat dan memanggil `destroy()` di `finally`. Hasil (kode keluar dan teks) serta exception aslinya tidak berubah; galat saat bersih-bersih ditelan. Bacaan tanpa batas waktu sengaja tidak diubah (tidak dipindah ke `BoundedShell`, karena kode keluar proses Shizuku hilang di v135).
- Tes baru `FstrimExecutorCollectTest` (4 tes, proses tiruan tanpa Shizuku): hasil dan kode keluar, kode keluar non-nol, exception asli tetap dilempar dan proses tetap dibereskan, serta `destroy()` yang gagal tidak menutupi hasil.
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint). Target run berikutnya: 141 tes lulus dan semua alat tetap 0 temuan.

## v164
- Laporan CI run 16 (commit v163): semua alat lulus. 135 tes lulus (target v163 tercapai), detekt polos dan typed 0 temuan, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, peringatan kompilator 0, deprecated 0, lint hanya 1 hint compose-bom (disengaja).
- Diag frame metrics 20 Nov 2026 (INFINIX X6855, tema Glass, 944 frame): semua frame melewati anggaran 8,3 ms pada 120 Hz; p50 total 23,2 ms, p99 60,8 ms; rerata tahap menggambar 3,0 ms, perintah GPU 3,4 ms, swap buffer 4,0 ms. Pola yang sama muncul pada sesi 18 Nov 2026 (704 frame, p50 23,1 ms).
- `Design.kt`: latar tema Glass sebelumnya menggambar empat lapis gradien layar penuh (satu vertikal, tiga radial) pada setiap frame saat layar bergerak. Kini digambar sekali ke bitmap kecil (1/8 resolusi, parameter gradien sama persis) lalu satu kali menggambar bitmap per frame, dengan pola yang sama seperti latar Neo sejak v145. Tampilan dimaksudkan identik; periksa dengan mata apakah ada pita warna atau pergeseran cahaya.
- `FrameJank.kt`: berkas diag frame metrics kini juga mencatat durasi GPU per frame (tahap `gpu`, Android 12 ke atas), rerata total (`avg`), dan jumlah frame di atas 16,667 ms (`over60hz`, tidak bergantung pada refresh rate yang terbaca). Dengan itu dugaan beban GPU bisa dibuktikan atau dibantah dari angka mentah di HP.
- `FrameJankTest.kt`: dua tes baru (`over60hz`, `gpu` sebagai tahap terakhir) dan tiga asersi disesuaikan dengan format baru.
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs, lint). Target run berikutnya: 137 tes lulus dan semua alat tetap 0 temuan. Efek pada kelancaran baru terbukti setelah APK dipasang dan diag frame metrics baru dikirim.

## v163
- Laporan CI run 15 (commit v162): semua alat lulus. detekt typed turun dari 17 ke 0 temuan (target v162 tercapai), detekt polos 0, SpotBugs 0, ArchUnit 7 aturan tanpa pelanggaran, 131 tes lulus, peringatan kompilator dan deprecated 0, APK terbentuk; lint hanya satu hint (compose-bom, disengaja).
- Atas izin pemilik proyek untuk menyentuh semua berkas (termasuk yang dilindungi) dalam beberapa batch yang wajar, batch ini memperbaiki cacat nyata hasil audit kode latar; tidak ada bukti perangkat baru, jadi bug notifikasi persisten belum disentuh.
- `TrimWorker.kt`: run manual dari widget saat Shizuku belum siap dulu berakhir dengan `Result.retry()`, sehingga WorkManager mengulangnya tanpa batas (toast dan satu baris Riwayat "dilewati" tiap ulang, bahkan berjam-jam kemudian). Kini run manual cukup satu catatan dan satu toast; rantai interval radikal tetap lanjut ke tik berikutnya dan jadwal periodik tetap `retry` seperti semula.
- `TrimWorker.kt`: pengecualian tak terduga di dalam worker kini ditangkap di batas `doWork()` (pembatalan WorkManager tetap dilempar ulang), dicatat apa adanya di logcat dan Riwayat, dan rantai interval radikal tetap dijadwalkan; sebelumnya rantai itu bisa berhenti diam-diam.
- `Scheduler.runOnce`: kerja manual dibuat unik (kebijakan KEEP), jadi ketukan ganda pada widget tidak lagi memulai dua `sm fstrim` paralel.
- `Prefs.kt`: `record()` dikunci (`synchronized`) karena membaca lalu menulis ulang log riwayat; worker dan ViewModel yang menulis bersamaan bisa menghilangkan satu baris. Format baris, kunci, dan nilai bawaan tidak berubah.
- `CrashLogger.kt`: bila penulisan lewat MediaStore gagal sesudah baris dibuat, baris `IS_PENDING` 0 byte kini dibuang (dulu tertinggal yatim); exception aslinya tetap dilempar ulang ke jalur cadangan seperti sebelumnya.
- Tes baru `TrimWorkerPolicyTest` (4 tes) mengunci kebijakan akhir worker saat Shizuku belum siap.
- Belum dijalankan di CI (kompilasi, tes, detekt, SpotBugs). Target run berikutnya: 135 tes lulus dan semua alat tetap 0 temuan.

## v162
- Laporan CI run 14 (commit v161): SpotBugs kembali berjalan (SARIF terbentuk, 0 temuan), jadi perbaikan jalur kelas v161 terbukti. detekt typed turun dari 41 ke 17 temuan persis seperti diperkirakan, tanpa temuan baru; 131 tes lulus, peringatan kompilator dan deprecated 0, APK terbentuk.
- Atas izin pemilik proyek, 17 temuan sisa ditutup di 8 berkas: `CatchJournal`, `KillCatcher`, `LogBuffer`, `MainActivity`, `RecentsExclusion`, serta tiga berkas yang biasanya dilindungi (`PersistentTrimService`, `FstrimExecutor`, `UpdateChecker`). Pola sama seperti v161: template log yang menyisipkan nilai nullable kini memakai `?: "null"` eksplisit, keluaran identik; rule `NullableToStringCall` tetap aktif.
- `PersistentTrimService` hanya berubah pada string log setelah `startForeground()`; alur, channel, dan keputusan servis tidak disentuh. `UpdateResult` (`UpdateChecker.kt`) menjadi `sealed interface`; pemakaian `is` dan `when` tidak berubah.
- `MainActivity.kt`: teks error daftar log memakai `loadError.orEmpty()` karena properti delegasi tidak bisa di-smart-cast; cabang itu hanya dimasuki saat nilainya tidak null, jadi tampilan sama.
- Belum dijalankan di CI (kompilasi, tes, detekt typed). Target run berikutnya: detekt typed 0 temuan. Perilaku aplikasi tidak berubah.

## v161
- Laporan CI run 13 (commit v160): Tahap D2 terbukti. Pesan deprecated Gradle/AGP/plugin 0, peringatan kompilator 0, detekt polos 0 temuan, 131 tes lulus, APK terbentuk, lint hanya satu hint (compose-bom, disengaja). Dua hal tersisa: SpotBugs `NOT VERIFIED` dan `detektDebug` (jaring typed) yang kini berjalan dan melaporkan 41 temuan pertama.
- SpotBugs: dengan Kotlin bawaan AGP 9 kelas hasil kompilasi tidak lagi ada di `build/tmp/kotlin-classes/debug`, sehingga SpotBugs berhenti dengan "No files to analyze could be opened". `config/spotbugs/spotbugs.gradle.kts` kini mengambil direktori kelas dari keluaran task `compileDebugKotlin` (dengan jalur cadangan dan pindai `MainActivity.class`) dan mencatat jalur nyatanya sebagai `SPOTBUGS_DIAG` di log laporan.
- detekt typed: 39 temuan `NullableToStringCall` adalah template log/diagnostik yang menyisipkan nilai nullable; teks "null" memang keluaran mentah yang diinginkan. Diperbaiki di sumber dengan `?: "null"` eksplisit (keluaran identik) di `OwnLogcat.kt` (4), `ProcessCatcher.kt` (7), `LogcatSnapshot.kt` (10) dan `BootTrimSetting.kt` (2). Rule tidak dimatikan.
- `BootTrimReading` (`BootTrimSetting.kt`) menjadi `sealed interface` (temuan `AbstractClassCanBeInterface`); pemakaian `is`, `==` dan `when` tidak berubah. Di `LogcatSnapshot.kt` blok catch dump mentah memakai variabel lokal `failure` alih-alih membaca ulang `shellError`.
- Belum diperbaiki (sengaja): 8 temuan di `CatchJournal`, `KillCatcher`, `LogBuffer`, `MainActivity`, `RecentsExclusion` (batas jumlah berkas per batch) dan 9 temuan di berkas yang dilindungi (`PersistentTrimService`, `UpdateChecker`, `FstrimExecutor`; butuh keputusan pengguna).
- Belum dijalankan di CI (kompilasi, tes, SpotBugs, detekt typed). Perilaku aplikasi tidak berubah.

## v160
- Laporan CI run 12 (commit v159): detekt 2.0.0-alpha.6 berjalan (0 temuan), pesan deprecated `ReportingExtension.file` hilang, 131 tes lulus, APK terbentuk. `detektDebug` gagal validasi config: rule set `standard-library` ada di panduan migrasi "Next" tetapi belum ada di alpha.6. Lint melaporkan dua hint baru karena ada rilis baru (compose-bom 2026.11.01, lifecycle-viewmodel-ktx 2.11.1).
- `config/detekt/detekt-typed.yml`: tiga rule (`ArrayPrimitive`, `ForEachOnRange`, `MapGetWithNotNullAssertionOperator`) dikembalikan ke rule set aslinya; rename `UnusedImport` dipertahankan karena diterima alpha.6.
- Tahap D2: `gradle.properties` tidak lagi mematikan Kotlin bawaan AGP dan DSL baru (`android.builtInKotlin=false`, `android.newDsl=false` dihapus). Plugin `org.jetbrains.kotlin.android` dilepas dari `build.gradle.kts` akar dan `app/build.gradle.kts`; versi Kotlin dipatok lewat classpath `kotlin-gradle-plugin:2.4.10`. Skrip laporan membaca versi Kotlin dari plugin compose.
- `lifecycle-viewmodel-ktx` naik ke 2.11.1. compose-bom sengaja tetap 2026.09.00: 2026.11.01 membawa Compose 1.13 dan Material 3 1.5, perlu keputusan dan cek visual tersendiri.
- Belum dijalankan di CI. Build utama kini memuat perubahan toolchain, jadi hasil run berikutnya menentukan. Aplikasi tidak berubah selain patch lifecycle.

## v159
- Laporan CI run 11 (commit v158): semuanya hijau dan Tahap C terbukti: compileSdk dan targetSdk 37, lint tanpa isu (tujuh hint hilang), 131 tes unit lulus, peringatan kompilator 0, APK debug terbentuk, detekt/detekt typed/ArchUnit/SpotBugs 0 temuan. Sisa target: tiga pesan deprecated (`android.newDsl=false`, `android.builtInKotlin=false`, `ReportingExtension.file`).
- Tahap D1: detekt dimigrasikan dari 1.23.8 ke 2.0.0-alpha.6 (plugin `dev.detekt`; sejak alpha.3 mendukung built-in Kotlin AGP 9, dan alpha.4 dibangun dengan Kotlin 2.4). `build.gradle.kts` dan `app/build.gradle.kts` memakai impor, ekstensi lazy, dan laporan `checkstyle` baru (laporan txt dihapus di detekt 2). Plugin tetap hanya dipasang saat `-Plagfix.detekt=true`, jadi build dan rilis tidak bergantung padanya.
- `config/detekt/detekt.yml`: `ForbiddenImport` memakai kunci `forbiddenImports`. `config/detekt/detekt-typed.yml`: `UnusedImports` menjadi `UnusedImport`; tiga rule stdlib (`ArrayPrimitive`, `ForEachOnRange`, `MapGetWithNotNullAssertionOperator`) pindah ke rule set `standard-library`. Aturan tidak dilonggarkan.
- `.github/scripts/laporan-untuk-claude.sh`: versi detekt dibaca dari id plugin baru.
- Belum dijalankan di CI (resolusi plugin, konfigurasi detekt 2, dan validasi config belum terverifikasi). Aplikasi tidak berubah. Pecahan Tahap D berikutnya (D2): hapus opt-out `newDsl`/`builtInKotlin` dan plugin `kotlin.android`.

## v158
- Build run 131 (v157) merah di langkah tes unit: satu galat kompilasi di `RecentsExclusion.kt` baris 48. Dengan compileSdk 37, `ActivityManager.AppTask.getTaskInfo()` ditandai nullable di android.jar, sehingga `it.taskInfo.baseIntent.flags` ditolak. Resolusi dependensi dan pemeriksaan metadata AAR untuk Tahap C lolos.
- Perbaikan: `recentsReadBack` kini memakai `it.taskInfo?.baseIntent?.flags`. Fungsi `recentsReadBackLine` sudah menangani nilai null ("baseIntent=null"), jadi jalur normal identik dan tes tidak berubah. Tidak ada pemakaian `taskInfo` lain di sumber.
- Belum dijalankan di CI. Build berhenti di kompilasi Kotlin, jadi kompilasi tes, tes, APK, dan lint belum terlihat; galat atau peringatan baru dari compileSdk 37, Compose 1.12, atau Material 3 1.4.0 masih mungkin muncul.

## v157
- Laporan CI run 9 (commit v156): semuanya hijau (122 tes unit lulus, APK debug terbentuk, detekt/detekt typed/ArchUnit/SpotBugs 0 temuan, peringatan kompilator 0). Perbaikan v156 terbukti: tujuh UseKtx di `Prefs.kt` hilang, lint tanpa error dan tanpa warning. Sisa target: tujuh hint lint (enam GradleDependency, satu OldTargetApi) dan tiga pesan deprecated.
- Tahap C: `app/build.gradle.kts` menaikkan compileSdk dan targetSdk dari 35 ke 37, compose-bom 2024.10.01 ke 2026.09.00, core-ktx 1.13.1 ke 1.19.1, activity-compose 1.9.3 ke 1.13.0, lifecycle-viewmodel-ktx 2.8.7 ke 2.11.0, work-runtime-ktx 2.9.1 ke 2.12.0 (angka sesuai saran lint di laporan). Compose 1.12 mewajibkan compileSdk 37 dan AGP 9.2.0 ke atas; AGP proyek 9.5.0. Kode produksi (`app/src/main`) tidak diubah. Perilaku API 37 hanya aktif di Android 17 ke atas, jadi perangkat Android 16 tidak terpengaruh oleh targetSdk 37; telaah sumber untuk perubahan API 37 tidak menemukan paparan.
- `config/spotbugs/spotbugs.gradle.kts`: classpath `android.jar` untuk SpotBugs kini mencoba `android-37.0`, `android-37`, lalu `android-35`, supaya selaras dengan compileSdk baru.
- `app/lint.xml`: hanya komentar yang diperbarui; aturan tidak berubah (GradleDependency dan OldTargetApi tetap informational).
- Tes baru `LogLineParserTest` (9 tes): mengunci kontrak baris riwayat antara `Prefs.record()` dan `parseLogLine` (token trigger, token telat, baris lama tanpa trigger, pesan kosong, baris tak terbaca) serta `formatInterval`. Audit statis tidak menemukan cacat pada format tulis dan baca riwayat.
- Belum dijalankan di CI (kompilasi, resolusi dependensi, lint, tes, SpotBugs belum terverifikasi). Risiko visual: Compose 1.7 ke 1.12 dan Material 3 1.3 ke 1.4.0 dapat menggeser tampilan bawaan; perlu dicek di perangkat. Tahap D (detekt 2.x dan penghapusan opt-out `newDsl`/`builtInKotlin`) belum dikerjakan.

## v156
- Laporan CI run 8 (commit v155): build hijau (122 tes unit lulus dengan mockito 5.24.0, APK debug terbentuk, detekt/detekt typed/ArchUnit 1.5.1/SpotBugs 0 temuan). Perbaikan v155 terbukti: deprecated `Task.project` dan dua NewerVersionAvailable hilang. Lint tinggal tujuh error, semuanya UseKtx di `Prefs.kt`.
- Atas perintah user ("Prefs boleh"), `Prefs.kt` diperbaiki hanya pada baris yang dilaporkan lint: enam setter (`themeMode`, `enabled`, `intervalMinutes`, `radicalInterval`, `scheduleAnchorMs`, `persistentServiceEnabled`) dan `record()` kini memakai `SharedPreferences.edit { ... }`. Perilaku identik (`edit {}` = `edit()` + aksi + `apply()`); kunci, nilai default, urutan penulisan, dan format log tidak berubah.
- Peringatan kompilator `Project.android(...)` deprecated di `app/build.gradle.kts` bersifat intermiten (run 6 dan 8 ada, run 7 tidak) dan berakar pada `android.newDsl=false`; hilang di Tahap D.
- Belum dijalankan di CI. Perilaku aplikasi tidak berubah.

## v155
- Laporan CI run 7 (commit v154): build hijau (122 tes unit lulus, APK debug terbentuk, detekt/detekt typed/ArchUnit/SpotBugs 0 temuan, peringatan kompilator 0). Perbaikan v154 terbukti: lima flag AGP yang dihapus tak lagi dicetak sebagai deprecated, dan UseKtx turun dari 16 menjadi 10.
- Tiga UseKtx tersisa di berkas non-terlarang diperbaiki dengan `SharedPreferences.edit { ... }` (apply di akhir, perilaku identik): `CatchJournal.kt`, `KillCatcher.kt`, dan `RecentsExclusion.kt` baris 161 (tiga `put*` dalam satu apply; pembacaan `getInt` terjadi sebelum apply, sama seperti sebelumnya).
- `config/spotbugs/spotbugs.gradle.kts`: `project.configurations` di dalam `CommandLineArgumentProvider` (dijalankan saat eksekusi, dihitung `Task.project` saat eksekusi, deprecated dan gagal di Gradle 10) diganti dengan `projectConfigurations` yang dibaca saat konfigurasi. Skrip diuji terkompilasi di Gradle 8.14.3.
- `app/build.gradle.kts`: dependensi tes `mockito-core` 5.23.0 menjadi 5.24.0 dan `archunit` 1.3.0 menjadi 1.5.1 (menghapus dua NewerVersionAvailable; hanya classpath tes).
- Belum dikerjakan: tujuh UseKtx di `Prefs.kt` (DO-NOT-TOUCH, perlu perintah user), sehingga lint tetap merah dengan 7 error. Sisa deprecated bergantung Tahap D.
- Belum dijalankan di CI. Perilaku aplikasi tidak berubah.

## v154
- Laporan CI run 6 (commit v153): build hijau (122 tes unit lulus, APK debug terbentuk, detekt/detekt typed/ArchUnit/SpotBugs 0 temuan), jadi perbaikan plugin Kotlin v153 berhasil. Temuan baru dari lint AGP 9.5.0: 16 UseKtx dan 2 NewerVersionAvailable (semua dihitung error karena warning dianggap error).
- Tujuh dari enam belas isu UseKtx (di empat berkas) diperbaiki di sumbernya dengan padanan KTX yang perilakunya identik: `SharedPreferences.edit { ... }` (apply di akhir, sama seperti `.edit()...apply()`) di `Design.kt` dan `RecentsExclusion.kt` (dua tempat), `String.toUri()` di `MainActivity.kt` (dua tempat) dan `MainViewModel.kt`, serta `Int.toDrawable()` di `Design.kt`. Impor yang tak terpakai (`ColorDrawable`, `Uri` di `MainViewModel.kt`) dihapus. `RecentsExclusion.checkBreaker` tetap menulis dua kunci secara atomik dalam satu apply; bila pembacaan kill timestamp melempar galat, tidak ada yang ditulis (sama seperti sebelumnya).
- `gradle.properties`: lima flag AGP yang dicetak "deprecated" oleh laporan dihapus karena proyek tak memakainya (tak ada resValue, targetSdk eksplisit 35, R tak dipakai di const/when/anotasi/tes, tak ada uses-sdk di manifest, R8 mati): defaults.buildfeatures.resvalues, sdk.defaultTargetSdkToCompileSdkIfUnset, enableAppCompileTimeRClass, usesSdkInManifest.disallowed, r8.optimizedResourceShrinking.
- Belum diselesaikan (dicatat di PROJECT_STATE): 7 UseKtx di `Prefs.kt` (DO-NOT-TOUCH, perlu perintah user), 2 UseKtx lain (`CatchJournal.kt`, `KillCatcher.kt`) karena batas 5 berkas per batch, 2 NewerVersionAvailable (mockito, archunit), serta deprecated yang bergantung pada Tahap D (`android.builtInKotlin`, `android.newDsl`, `ReportingExtension.file` dari detekt 1.23.8, peringatan `Project.android`) dan satu `Task.project` di skrip SpotBugs.
- Belum dijalankan di CI. Perilaku aplikasi tidak berubah.

## v153
- Build v152 gagal di `build.yml` (run 126) sebelum satu pun kode dikompilasi: Gradle tidak menemukan plugin `org.jetbrains.kotlin.android` versi 2.4.10 di Google, Maven Central, maupun Plugin Portal (yang dicari adalah artefak marker plugin, bukan modul Kotlin-nya). Penyebab marker tidak ditemukan belum terbukti karena sandbox tidak bisa menjangkau repositori. Modul `kotlin-gradle-plugin:2.4.10` tercantum di Maven Central (rilis 14 Juli 2026), jadi `settings.gradle.kts` kini mengarahkan plugin `org.jetbrains.kotlin.android` dan `org.jetbrains.kotlin.plugin.compose` langsung ke modulnya (`kotlin-gradle-plugin` dan `compose-compiler-gradle-plugin`) lewat `resolutionStrategy.eachPlugin`, sehingga tidak lagi bergantung pada marker. Hanya berlaku untuk permintaan berversi di `build.gradle.kts` akar; versi Kotlin tetap 2.4.10 dan tetap diatur di satu tempat.
- Diuji lokal dengan Gradle 8.14.3 secara offline: skrip settings terkompilasi, dan Gradle kini meminta `org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10` (sebelumnya marker). Resolusi nyata dan kompilasi tetap belum terbukti sampai CI berjalan.
- Tidak ada perubahan kode aplikasi (`app/src`), tampilan, atau versi toolchain. Lanjutan pengecualian toolchain v152. Belum dijalankan di CI.

## v152
- Migrasi toolchain tahap B (perintah user "Lanjut B"): Android Gradle plugin 8.7.3 menjadi 9.5.0, Gradle 8.9 menjadi 9.6.0 (kedua workflow), Kotlin dan plugin Compose 2.0.21 menjadi 2.4.10. Perilaku build lama dipertahankan: Kotlin bawaan AGP dan DSL baru dimatikan (`android.builtInKotlin=false`, `android.newDsl=false`), dan semua default yang berubah di AGP 9.0 dikembalikan ke nilai lama di `gradle.properties`. Heap Gradle naik ke 3 GB. Library (Compose, core, activity, lifecycle, work), compileSdk 35, dan targetSdk 35 belum berubah, jadi 6 info lint versi usang masih tampil.
- `android.kotlinOptions {}` diganti `kotlin { compilerOptions { jvmTarget } }` (nilai sama, JVM 17); sejak Kotlin 2.2 cara lama menjadi galat.
- Plugin detekt 1.23.8 belum resmi mendukung AGP 9, jadi kini dipasang hanya bila `-Plagfix.detekt=true` (dipakai workflow laporan). `build.yml` tidak memuat detekt lagi, sehingga bila detekt bermasalah di toolchain baru, build dan rilis tidak ikut gagal; laporan mencatat `NOT VERIFIED`.
- Laporan: hitungan "info" lint sebelumnya selalu 0 karena salah baca tingkat keparahan (laporan run4 menunjukkan 6 isu tapi 0 info); sudah diperbaiki, dan awalan path runner dibuang dari baris lint.
- Tidak ada perubahan kode aplikasi (`app/src`) dan tampilan. Belum dijalankan di CI.

## v151
- Lint dan detekt dipindah total dari `build.yml` ke workflow laporan `android-artifact.yml` (perintah user). `build.yml` kini hanya menjalankan tes unit dan build; gerbang lint+detekt yang memblokir build dihapus, begitu juga artifact `LagFix-lint-detekt`. Log kegagalan `build.yml` kini hanya berisi galat tes/build.
- Artifact laporan kini satu berkas `laporan-untuk-claude.txt` yang lengkap: penanda proyek, data lingkungan runner (JDK, Gradle, platform SDK, versi AGP/Kotlin/SDK), ArchUnit, SpotBugs, detekt, detekt typed, dan lint dengan rincian penuh (bukan lagi kutipan), tes unit beserta stack tes yang gagal, peringatan kompilator Kotlin, pesan deprecated dari Gradle/AGP/plugin, dan hasil `assembleDebug`. Cukup unggah satu berkas ini ke Claude. Perakit laporan dipindah ke `.github/scripts/laporan-untuk-claude.sh` dan sudah diuji lokal dengan berkas tiruan.
- Deprecated: `MainViewModel.checkUpdate` memakai `PackageInfoCompat.getLongVersionCode` sehingga satu `@Suppress("DEPRECATION")` untuk `versionCode` hilang (nilai sama). Dua `getRunningServices` di `ProcessCatcher` dan `LogcatSnapshot` tetap, karena tidak ada API pengganti untuk membaca layanan milik sendiri. `gradle/actions/setup-gradle` dinaikkan dari v4 (Node.js 20, anotasi deprecated di tiap run) ke v5 di kedua workflow.
- Lint: `GradleDependency` dan `OldTargetApi` dikembalikan dari `ignore` ke `informational` supaya tampil apa adanya di laporan. Naik versi library dan targetSdk belum dilakukan: rilis terbaru lifecycle butuh compileSdk 37 dan AGP 9.2 ke atas, jadi itu migrasi toolchain tersendiri.
- Tidak ada perubahan tampilan. Belum dijalankan di CI.

## v150
- Build v149 gagal di gerbang detekt: aturan `ThrowsCount` menolak fungsi `download` di `UpdateChecker` karena v149 menambah satu `throw` sehingga jadi tiga (maksimal dua). Pembuatan folder unduhan dan pengecekan kode HTTP kini dipindah ke dua fungsi privat (`prepareUpdateDir`, `ensureHttpOk`). Urutan langkah, pesan galat, dan perilaku unduhan sama seperti v149.
- Laporan CI run 2: ArchUnit lulus (0 pelanggaran), tes unit lulus (122 tes), peringatan kompilator Kotlin 0. SpotBugs turun dari 40 ke 1 temuan; sisanya (`PersistentTrimService.kt:116`) lolos dari filter karena letaknya di kelas lambda, bukan di kelas utama; filter kini mencakup kelas dalam. `PersistentTrimService` tidak diubah.
- Detekt typed: 17 temuan `InjectDispatcher` masih muncul walau dimatikan di `detekt-typed.yml`. Rule itu kini dimatikan juga di `detekt.yml` (berkas yang terbukti terbaca), karena menolak `Dispatchers.IO` yang diwajibkan aturan proyek. `detektDebug` kini mencetak berkas konfigurasi yang dipakainya (`DETEKT_TYPED_DIAG`) untuk membuktikan apakah berkas typed ikut terbaca.
- Lint: cek `GradleDependency` dan `OldTargetApi` (6 isu informational) kini diabaikan di `lint.xml` dengan alasan tertulis. Naik versi library dan targetSdk tidak dilakukan karena butuh naik AGP, Gradle, Kotlin, detekt, dan compileSdk bersamaan dan tidak bisa diverifikasi tanpa build; itu menjadi batch tersendiri atas perintah user.
- Tidak ada perubahan tampilan. Belum dijalankan di CI.

## v149
- Laporan CI pertama (workflow laporan v148, run 1) ditindaklanjuti sampai tuntas. Tes unit lulus (122 tes), detekt 0 temuan, peringatan kompilator Kotlin 0. Sisanya dibereskan satu per satu.
- Kode: pembuatan folder unduhan update kini memakai hasil `mkdirs()` (gagal membuat folder = galat jelas) dan penghapusan APK parsial memakai hasil `delete()` (gagal hapus dilampirkan sebagai suppressed, exception asli tetap dilempar ulang). `Prefs`: pembacaan tema tanpa `!!` dan pembacaan log memakai `orEmpty()`. `RecentsExclusion`: safe call yang tidak perlu dihapus. `LogcatSnapshot`: `orEmpty()`. Perilaku normal tidak berubah.
- Aturan ArchUnit "kelas non-UI tidak bergantung pada Compose" memberi 18 pelanggaran palsu karena anotasi `StabilityInferred` yang disisipkan Compose compiler ke setiap kelas; anotasi itu kini dikecualikan. Daftar rincian pelanggaran di laporan dinaikkan dari 8 ke 25 baris.
- Filter SpotBugs: 40 temuan ditriase per titik terhadap sumber. Semuanya artefak kompilator Kotlin/Compose (delegasi state `by rememberSaveable`, fungsi suspend, objek singleton, fire-and-forget di IO), disaring per kelas saja; temuan serupa di kelas lain tetap dilaporkan. Detekt typed: rule `InjectDispatcher` dimatikan karena bertentangan dengan aturan proyek yang mewajibkan `Dispatchers.IO`.
- Tidak dikerjakan: 6 info lint (`OldTargetApi` dan 5 `GradleDependency`). Itu butuh naik AGP, compileSdk, dan Kotlin, tidak bisa dicek tanpa build, dan memang sengaja informational di `lint.xml`.
- Tidak ada perubahan tampilan. Belum dijalankan di CI.

## v148
- Konfigurasi CI/analisis dari proyek Boomly diadaptasi ke LagFix. Workflow baru "Android CI Artifact for Claude" berjalan paralel dengan build dan menghasilkan satu berkas `laporan-untuk-claude.txt` (artifact `LagFix_laporan-untuk-claude_run<N>`, retensi 7 hari) berisi ArchUnit, SpotBugs, detekt, detekt typed (`detektDebug`), lintDebug, tes unit, dan peringatan kompilator Kotlin. Semua langkahnya tidak menggagalkan build; alat yang tidak jalan dicatat `NOT VERIFIED`, bukan lulus.
- `build.yml`: runner dipin ke `ubuntu-24.04` (GitHub memindah `ubuntu-latest` ke Ubuntu 26.04 sampai 19 Nov 2026), ditambah Stale Run Guard (run usang tidak mem-publish rilis), publish rilis berlapis (action lalu cadangan `gh release create` sampai 5 kali), ringkasan run di tab Summary, versi action dinaikkan (checkout v7, setup-java v6, upload-artifact v7, action-gh-release v3). Gerbang lint dan detekt tetap BLOCKING dan tidak diubah; tag, nama rilis, dan nama berkas APK sama seperti sebelumnya.
- Berkas baru: `config/spotbugs/spotbugs.gradle.kts` + `exclude.xml` (hanya aktif dengan `-Plagfix.spotbugs=true`), `config/detekt/detekt-typed.yml` (hanya dibaca `detektDebug`), `ArchitectureTest.kt` (7 aturan arsitektur, hanya melaporkan, tidak pernah menggagalkan tes). `app/build.gradle.kts`: dependency tes ArchUnit 1.3.0, laporan detekt xml/txt dinyalakan, config typed khusus `detektDebug`.
- Tidak ada perubahan tampilan atau perilaku aplikasi. Belum dijalankan di CI.

## v147
- Perbaikan pembaca riwayat start proses di berkas `process_catch`: baris `timestamps` selalu gagal (`UnsupportedOperationException`) di dua HP uji, karena cara mengurutkan peta stempel waktu dari sistem tidak didukung `ArrayMap` milik Android. Kini peta disalin dulu ke daftar biasa sehingga stempel waktu start terbaca; bila masih gagal, barisnya memuat frame penyebabnya, bukan hanya nama kelas galat.
- Berkas `process_catch` kini punya dua bagian baru yang tidak butuh Shizuku. `powerState` mencetak status pengecualian optimasi baterai dan bucket standby app. `observations` adalah jurnal keadaan tiap pengamatan (notifikasi persisten tampil atau tidak, importance proses, status dibatasi di latar belakang), tersimpan lintas proses dan dilipat per keadaan (maksimal 12 baris), sehingga terlihat kapan notifikasi hilang walau buffer logcat sudah tertimpa. Sebabnya: pada berkas 18 Nov di Infinix X6855, proses sudah hidup 20 jam dengan servis tanpa notifikasi, tetapi log hanya menyisakan 3 baris dan Shizuku mati.
- Tidak ada perubahan tampilan. Ditambah 8 tes unit. Belum diuji di perangkat.

## v146
- Tambah pengukur kelancaran layar bawaan untuk membuktikan apakah scroll tema Neumorphism masih tersendat. Saat app dipakai (mis. menggulir Pengaturan), app mencatat waktu render tiap frame, lalu menulis ringkasan angkanya (persentil waktu, jumlah frame yang melewati anggaran waktu, rerata tiap tahap render) ke `Documents/LagFix/LagFix_diag_frame_metrics_*.txt` ketika layar app berhenti terlihat, tema diganti, atau layar diputar. Berkas hanya ditulis bila sesi merekam minimal 120 frame dan maksimal 3 berkas per proses, jadi folder tidak penuh. Isi berkas hanya angka mentah. Tidak ada perubahan tampilan, izin, atau dependency; tema Glassmorphism dan Neumorphism tampil sama seperti v145. Belum diuji di perangkat.

## v145
- Polishing dan optimalisasi lanjutan tema Neumorphism untuk mengurangi scroll yang masih tersendat: bayangan di bawah pelat tidak lagi digambar penuh, latar digambar sekali ke bitmap kecil, dan dekorasi tiap kartu punya layer sendiri sehingga animasi satu kartu tidak menggambar ulang kartu lain. Tombol dan chip kini tenggelam dan timbul secara mulus saat ditekan atau dipilih. Snackbar mengikuti warna anggur. Tema Glassmorphism tidak berubah.

## v144
- Polishing dan optimalisasi tema Neumorphism: bayangan kartu, tombol, chip, dan switch kini digambar sebagai pita tipis yang tidak bertumpuk, jadi jauh lebih ringan di GPU saat menggulir (tampilan tetap sama). Latar jendela kini mengikuti tema, sehingga tema Neumorphism tidak lagi berkedip biru sesaat saat start dingin atau rotasi layar. Tema Glassmorphism tidak berubah.

## v143
- Perbaikan build CI v142: lint menolak posisi knob switch tema Neumorphism yang dianimasikan lewat `Modifier.offset` biasa. Posisi knob kini dihitung di fase layout; tampilan dan animasi tidak berubah.

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
