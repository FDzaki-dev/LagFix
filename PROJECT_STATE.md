[BRANDING_NAME: LagFix]
[TERMUX_ROOT: LagFix]

# PROJECT_STATE
- App: LagFix (fstrim) — pemicu + penjadwal fstrim non-root (via Shizuku), setara mFSTRIM.
- Package: com.lagfix.fstrim | minSdk 26 | compile/target 35 | Kotlin 2.0.21 | AGP 8.7.3 | Compose M3
- Alur: Prefs (SharedPreferences) -> Scheduler (WorkManager periodic) -> TrimWorker -> FstrimExecutor (Shizuku shell: `sm fstrim`, fallback `sm idle-maint run`)
- UI: MainActivity + MainViewModel (state bertahan rotasi)
- Safety: CrashLogger -> Download/LagFix via MediaStore (API 29+)
- CI: .github/workflows/build.yml; secret KEYSTORE_BASE64/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD; tanpa secret -> debug APK
- Keputusan: referensi Aplikasi_mFSTRIM.md (Runtime.exec + WRITE_SECURE_SETTINGS) tidak dipakai — UID aplikasi tidak berhak menjalankan `sm fstrim`; diganti Shizuku (UID shell).

- Histori batch v2-v24 (semua sudah VERIFIED/CLOSED) diarsipkan ke
  `docs/archive/PROJECT_STATE_v2-v24.md` per v28 (fokus file ini ke pekerjaan terkini).

- v25 (2 laporan user): (1) v20 poin 3 CONFIRMED NEGATIF ke-2x — icon tile TETAP "default putih"
  walau `onTileAdded()` (v20) sudah ditambah. (2) User eksplisit minta lanjut opsi "foreground
  service persisten" (nemu dari catatan RESUME POINT sendiri di PROJECT_STATE.md ZIP v24) krn
  Autostart TIDAK KETEMU sama sekali di pencarian Settings device.
  1. FIX (best-effort, root-cause dugaan kuat bukan kepastian — P0 NO HALLUCINATION dinyatakan
     eksplisit): `ic_tile_fstrim.xml` fill putih (`#FFFFFF`) di atas badge tile-picker OEM yg
     kemungkinan terang/putih & TIDAK selalu re-tint by alpha-mask (beda dari asumsi standar AOSP)
     -> putih-di-atas-putih = tak kelihatan, match PERSIS laporan "putih aja". Ganti fillColor ke
     `@color/ic_launcher_background` (warna brand, sama dgn tombol widget, 0 warna baru) — aman utk
     KEDUA skenario: kalau OS re-tint (mask peduli alpha bukan hue, aman), kalau TIDAK re-tint
     (warna kini kontras thd badge terang, sebelumnya tidak). 1 file (`ic_tile_fstrim.xml`). Server
     tidak ada jaminan 100% — rendering picker OEM di luar kendali kode, minta user test ulang.
  2. AUTO-HALT (BUKAN dieksekusi batch ini — P0 "STABILITY+ZERO-REGRESSION > USER INTENT" scr
     eksplisit didahulukan di atas permintaan lanjut user, krn ada temuan risiko BARU yg user blm
     tahu saat minta "lanjut"): riset (web search) sblm eksekusi nemu 2 hal krusial yg BELUM
     diketahui saat catatan "perlu diskusi eksplisit" ditulis di v24: (a) dokumentasi RESMI Android
     Developers sendiri (developer.android.com/about/versions/14/changes/fgs-types-required)
     eksplisit bilang: kalau use-case bisa dicover WorkManager (PERSIS kasus app ini — SUDAH pakai
     WorkManager dari awal), Google MEREKOMENDASIKAN JANGAN pakai foreground service `specialUse`.
     (b) app ini targetSdk 35 (Android 15) — ditemukan kasus nyata (PR proyek open-source lain,
     ActivityWatch/aw-android#190) `startForeground()` TANPA foregroundServiceType yg presisi tepat
     CRASH di Android 14+/targetSdk 35. Implementasi produksi butuh: +permission
     `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE`, `<service>` +`foregroundServiceType=
     "specialUse"` +`<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE".../>`
     PERSIS presisi (salah satu meleset -> crash), +permission runtime `POST_NOTIFICATIONS` (API
     33+, perlu flow permintaan baru di UI), +`BroadcastReceiver` BOOT_COMPLETED (+permission
     `RECEIVE_BOOT_COMPLETED`) spy service auto-restart stlh reboot (foreground service TIDAK
     auto-persist lintas reboot spt WorkManager periodic), + NOTIFIKASI PERMANEN tak bisa
     disembunyikan selama service jalan. Estimasi ~6 file baru/diubah (Service baru, BootReceiver
     baru, Manifest, Scheduler start/stop, MainActivity request permission). RISIKO: kalau SATU
     saja dari kombinasi manifest+kode di atas meleset (sangat presisi & TIDAK BISA dicompile/test
     di sandbox ini — 0 Android SDK/Gradle/device), app bisa CRASH SETIAP KALI toggle jadwal
     otomatis dinyalakan — regresi JAUH lebih parah drpd masalah asal (skip 1 kali trim terjadwal).
     DAN belum tentu menyelesaikan akar masalah jg: kalau XOS device ini memang tak expose Autostart
     sama sekali (bukan cuma nama beda), OS bisa saja tetap membunuh foreground service jg pada ROM
     yg SANGAT agresif (tidak ada jaminan). KEPUTUSAN: TIDAK dieksekusi dulu batch ini — 0 file
     Service/BootReceiver dibuat. Ditawarkan ke user: (i) 1 alternatif RINGAN & ZERO-RISK dulu
     sblm opsi berat ini — coba cari toggle di Settings > **Battery** (bukan Apps) dgn kata kunci
     "Protected apps"/"Battery Lab"/"Power Marathon"/"Background restriction" (istilah "Autostart"
     yg dicari user mungkin memang TIDAK ada persis di build XOS 16 ini, tapi konsepnya kemungkinan
     ada dgn nama beda — sumber: tiktask.ai/salestrail.io khusus Infinix), ATAU cek app drawer utk
     ikon "Phone Master"/"XManager" bawaan HP (app terpisah dari Settings, py Autostart Manager
     sendiri — Settings-search TIDAK selalu mengindeks isi app lain). (ii) kalau tetap 0 hasil &
     user tetap mau foreground service MESKIPUN sudah tahu risiko crash presisi di atas -> minta
     konfirmasi eksplisit sekali lagi (bukan menahan-nahan, tapi krn ini benar2 informasi baru yg
     user blm tahu saat bilang "lanjut").
  File diubah batch ini: `ic_tile_fstrim.xml` (1 file, poin 1 saja). 0 file lain (poin 2 di-HALT).
- v25 VALIDASI: xmllint OK (ic_tile_fstrim.xml, termasuk komentar XML baru). 0 brace/paren relevan
  (bukan Kotlin). BELUM dites device (poin 1, best-effort, minta user test ulang tile).
- Docs: `CHANGELOG.md` +entry v25 (poin 1 saja, user-facing attempt). Poin 2 (halt) TIDAK masuk
  changelog (0 kode dieksekusi).
- Batch: v25

[RESUME POINT: v25 — (1) icon tile diganti warna brand (best-effort, blm pasti akar masalah
sepenuhnya krn rendering picker OEM di luar kendali kode) MENUNGGU test ulang user. (2) Permintaan
"foreground service persisten" di-HALT SEMENTARA (bukan ditolak) — P0 stability > user intent
diterapkan krn nemu risiko crash presisi (targetSdk 35) + Google sendiri menyarankan JANGAN utk
use-case yg sudah pakai WorkManager (persis app ini) via riset baru yg blm ada saat user minta
"lanjut". Ditawarkan alternatif ringan dulu (cek Settings>Battery / app Phone Master-XManager
scr terpisah dari Settings search) + minta konfirmasi ulang eksplisit kalau user TETAP mau opsi
berat itu -> Next Action: tunggu balasan user: (a) hasil coba alternatif ringan tile Autostart, DAN
(b) konfirmasi ulang ya/tidak utk lanjut foreground service given risiko yg baru dijelaskan. JANGAN
mulai bikin Service/BootReceiver/manifest FGS sebelum (b) dikonfirmasi ulang eksplisit.]

- v26 (2 request user): (1) laporan lanjutan — tile QS MASIH "default putih menyala, gak pernah
  kelihatan mati/idle" walau v25 (ganti warna icon) sudah dicoba. (2) "selagi masih ada chance
  utk SDK 35, just do it" — DICEK LEBIH DULU (P0 NO HALLUCINATION, bukan asumsi): `app/build.
  gradle.kts` SUDAH `compileSdk = 35` & `targetSdk = 35` sejak batch lama (bukan baru, dikonfirmasi
  baca langsung file, juga match `PROJECT_STATE.md` baris ringkasan "compile/target 35"), AGP
  8.7.3 + Gradle 8.9 (build.yml) sudah kompatibel penuh. KESIMPULAN: 0 perubahan dibutuhkan/
  dilakukan utk poin (2) — sudah 35 di seluruh toolchain, tidak ditebak jadi "naikkan ke 36"
  (di luar scope, tidak diminta).
  1. ROOT CAUSE BARU DITEMUKAN (beda dari dugaan v20/v25 soal warna icon): `LagFixTileService.
     refresh()` HARDCODE `tile.state = Tile.STATE_ACTIVE` 100% waktu sejak pertama kali tile
     dibuat (v18/v19) — TIDAK PERNAH `STATE_INACTIVE`. Akibatnya OS SELALU render tile dalam
     gaya "menyala/on" (apa pun warna icon-nya, termasuk fix v25) — persis cocok dgn 2x laporan
     user "menyala terus, gak pernah mati". Asumsi lama di komentar kode (state INACTIVE akan
     "memblokir tap") DICEK ulang thd kontrak resmi `TileService`: SALAH — hanya
     `STATE_UNAVAILABLE` yang membuat tile tak merespons klik; `ACTIVE`/`INACTIVE` sama-sama
     tetap memanggil `onClick()` normal, beda cuma tampilan visual. Fix: `tile.state` sekarang
     ikut parameter `running` yg sudah ada (`STATE_INACTIVE` saat idle, `STATE_ACTIVE` cuma pas
     benar-benar memproses) — 0 perubahan pada kapan/bagaimana tap diterima, 0 skema data baru.
     1 file: `LagFixTileService.kt` (KDoc kelas + 1 baris logic `refresh()`).
  File diubah: `LagFixTileService.kt` (1 file). 0 file lain disentuh untuk poin 1. 0 file
  disentuh untuk poin 2 (sudah terpenuhi, dikonfirmasi bukan dieksekusi).
- v26 VALIDASI: brace/paren balance OK (7/7, 36/36). Cross-check: `Tile.STATE_ACTIVE` &
  `Tile.STATE_INACTIVE` keduanya konstanta resmi `android.service.quicksettings.Tile` yg sudah
  ter-import, 0 import baru dibutuhkan. Diff thd ZIP v25: persis 1 file
  (`LagFixTileService.kt`), dikonfirmasi via `diff -rq`. BELUM pernah dicompile compiler/AGP
  sungguhan & BELUM dites device asli (sandbox tanpa SDK/Gradle/jaringan, sama spt semua batch
  sebelumnya) — **WAJIB user test ulang di device**: (a) tambah/lihat tile LagFix di panel Quick
  Settings saat idle -> icon HARUS kelihatan "mati"/redup (bukan lagi putih menyala default),
  (b) tap tile -> tetap trigger fstrim seperti biasa (toast hasil dari v20/v21 tetap harus
  muncul, tidak regresi), (c) selama proses jalan singkat, icon boleh kelihatan "menyala" (itu
  `STATE_ACTIVE` yg disengaja utk indikasi sedang memproses) lalu balik redup lagi setelah
  selesai (`Scheduler.notifyChanged()` dari v21 yg memicu `onStartListening()` ulang).
- Docs: `CHANGELOG.md` +entry v26 (user-facing: fix icon tile idle). `PENDING_ROADMAP.md` tidak
  disentuh (0 item baru).
- Batch: v26

[RESUME POINT: v26 — root cause BARU tile "menyala terus" ditemukan & difix: `tile.state`
dulu hardcode STATE_ACTIVE selalu (bukan soal warna icon spt dugaan v20/v25) -> sekarang ikut
`running` (INACTIVE=idle, ACTIVE=proses). SDK 35 dicek: SUDAH terpenuhi penuh di toolchain
(compileSdk/targetSdk/AGP/Gradle), 0 perubahan dilakukan/dibutuhkan. 1 file diubah
(LagFixTileService.kt), validasi statis only (brace OK, diff vs v25 confirmed 1 file), BELUM
compile/device test sungguhan -> Remaining: jalankan DAILY UPDATE, push, CI build hijau -> user
WAJIB test ulang tile di device: idle harus kelihatan redup/mati (bukan putih menyala lagi), tap
tetap jalan normal, kembali redup setelah proses selesai -> Next Action: tunggu hasil test tile
v26 dari user; kalau MASIH menyala terus setelah fix state ini (kemungkinan sangat kecil, karena
ini kontrak resmi Android TileService, bukan rendering OEM yg tak pasti spt teori v20/v25),
investigasi lanjut jadi genuinely OS/OEM-level di luar kendali kode (beda level bukti dgn 2 teori
sebelumnya yg sudah terbukti salah keduanya).]

- v27 (implementasi "foreground service persisten" yg di-HALT di v25 — user KONFIRMASI ULANG
  eksplisit dua kali stlh risiko dipaparkan lengkap di chat: pertama pilih "Ya, lanjutkan (paham
  risikonya)" dari opsi konfirmasi, kedua re-affirm "selama dibikin gak asal jadi, hasilnya sesuai
  SOP" — P0 AUTO-HALT poin (b) v25 TERPENUHI, dieksekusi):
  1. DESAIN dipilih SENGAJA paling minim-risiko dari opsi yg ada: service ini 0 logic fstrim
     sendiri (TIDAK reimplement/duplikasi jadwal — itu akan menciptakan 2 sumber kebenaran yg bisa
     divergen = regresi baru). Satu-satunya tugas: `startForeground()` + notifikasi permanen biar
     proses app tetap hidup, dengan harapan OS lebih jarang membunuhnya dibanding proses background
     murni — jadwal fstrim yg SEBENARNYA jalan tetap 100% lewat `Scheduler.apply()`/`TrimWorker`
     yang SUDAH ADA, 0 diubah sama sekali (0 risiko regresi ke jalur fstrim yg sudah battle-tested
     sejak v1).
  2. File BARU: `PersistentTrimService.kt` (Service `foregroundServiceType="specialUse"`,
     notification channel IMPORTANCE_LOW senyap, `START_STICKY`, `start()`/`stop()` companion) +
     `BootReceiver.kt` (restart service stlh reboot KALAU toggle aktif — service beda dari
     WorkManager yg auto-reschedule sendiri lintas reboot).
  3. File DIUBAH: `AndroidManifest.xml` (+4 permission: FOREGROUND_SERVICE,
     FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED; +`<service>`
     dgn `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`; +`<receiver>` BOOT_COMPLETED, exported=true krn
     protected system broadcast, standar); `Prefs.kt` (+1 pref `persistentServiceEnabled`,
     default false); `MainViewModel.kt` (+field UiState +`setPersistentService()` start/stop
     service ikut toggle); `MainActivity.kt` SettingsTab (+1 Card baru "Keandalan latar belakang
     (opsional)" — toggle + minta izin `POST_NOTIFICATIONS` runtime kalau API 33+ blm granted
     [granted/ditolak keduanya tetap nyalakan service, cuma notifnya yg tak tampil kalau ditolak —
     sesuai kontrak resmi Android, dicek dulu sblm diasumsikan] + teks trade-off notifikasi
     permanen + saran coba opsi baterai/Autostart dulu); `strings.xml` (+1 string teks notifikasi).
  4. TIDAK dilakukan (di luar scope diminta, P0 zero-regression): TIDAK mengubah/menghapus jalur
     WorkManager existing, TIDAK toggle otomatis nyala sendiri (default false, benar2 opt-in),
     TIDAK ada logic polling/timer sendiri di service (dijelaskan poin 1 kenapa).
  File diubah/baru: `PersistentTrimService.kt` (baru), `BootReceiver.kt` (baru),
  `AndroidManifest.xml`, `Prefs.kt`, `MainViewModel.kt`, `MainActivity.kt`, `strings.xml` — 7
  file, 1 fitur logis (exception thd batas 3-5 file, sama spt precedent v19/8-file). 0 file lain
  disentuh (dikonfirmasi diff vs ZIP v26 yg sudah dikirim ke user).
- v27 VALIDASI: xmllint OK (AndroidManifest.xml, strings.xml). Brace/paren balance OK di SEMUA
  file baru/diubah batch ini (PersistentTrimService.kt, BootReceiver.kt, Prefs.kt,
  MainViewModel.kt: 0/0; MainActivity.kt: parens_diff +1 tapi DIKONFIRMASI via diff langsung thd
  ZIP v26 baseline — mismatch itu SUDAH ADA SEBELUM batch ini disentuh, murni teks komentar
  prosa, pola sama persis spt yg sudah dicatat v22, BUKAN diintroduksi batch ini). Cross-check
  simbol: `persistentServiceEnabled`/`PersistentTrimService`/`setPersistentService`/
  `R.string.persistent_service_notif_text` semua konsisten dipanggil di titik yg tepat (grep
  manual per simbol). Diff vs ZIP v26: PERSIS 7 file di atas (+PROJECT_STATE.md/CHANGELOG.md,
  VIP docs tak dihitung), dikonfirmasi via `diff -rq`. **BELUM PERNAH dicompile compiler/AGP
  sungguhan & BELUM dites device asli sama sekali** (sandbox tanpa Android SDK/Gradle/device) —
  fitur ini PALING BERISIKO dari semua batch sejauh ini (kombinasi manifest+kode
  foregroundServiceType specialUse harus persis, TIDAK BISA divalidasi lebih jauh dari static
  check di sini, sesuai peringatan risiko yg sudah disampaikan ke user sebelum eksekusi) — **WAJIB
  gradle assembleDebug/CI hijau DULU sblm test device**, lalu **WAJIB test device lengkap**: (a)
  toggle ON -> app TIDAK crash, dialog izin notifikasi muncul (API 33+), notifikasi permanen
  muncul; (b) app di-force-close/swipe dari recents -> cek proses/notifikasi masih ada (indikasi
  "bertahan"); (c) restart HP -> notifikasi otomatis muncul lagi TANPA buka app manual (BootReceiver
  jalan); (d) toggle OFF -> notifikasi hilang, service berhenti; (e) PALING PENTING — tunggu 1+
  siklus interval jadwal, cek apakah Riwayat AKHIRNYA dapat entri otomatis (root masalah asli v22-
  v24) — kalau (e) MASIH nihil stlh semua ini, berarti device/ROM memang tak bisa dibantu lewat
  jalur apa pun tanpa root (kesimpulan final, sesuai yg sudah diperingatkan sblm eksekusi).
- Docs: `CHANGELOG.md` +entry v27. `PENDING_ROADMAP.md` tidak disentuh.
- Batch: v27

[RESUME POINT: v27 — foreground service persisten (opsional, default OFF) diimplementasi atas
konfirmasi ulang eksplisit user thd risiko yg dipaparkan v25/sesi ini. Desain sengaja 0 duplikasi
logic fstrim (cuma keep-alive, jadwal asli tetap 100% via WorkManager existing, 0 diubah) utk
minimalkan risiko regresi. 7 file baru/diubah, validasi statis only (xmllint+brace OK kecuali 1
mismatch paren pre-existing di MainActivity.kt yg dikonfirmasi BUKAN dari batch ini, cross-check
simbol OK, diff vs v26 confirmed 7 file) — BELUM PERNAH compile/AGP sungguhan & BELUM device test
SAMA SEKALI, fitur paling berisiko sejauh ini (presisi manifest FGS specialUse) -> Remaining:
jalankan DAILY UPDATE, push, **CI build WAJIB hijau dulu** (kalau merah di sini kemungkinan besar
typo/kesalahan properti manifest FGS, PALING PRIORITAS utk dicek duluan) -> baru lanjut 5 poin
test device di atas (a-e) -> Next Action: tunggu evidence CI + hasil 5 poin test device dari user;
KALAU CI merah, JANGAN coba tebak-tebak fix tanpa lihat log error asli dulu (P0 NO HALLUCINATION).]

- v28 (2 hal dari user): (1) laporan v27 — toggle sudah aktif, dialog izin `POST_NOTIFICATIONS`
  muncul, TAPI notifikasi permanen TAK PERNAH kelihatan. (2) minta PROJECT_STATE.md dirapikan,
  histori lama dipindah ke `docs/archive/` (aturan STATE yg memang sudah ada di constitution,
  belum pernah dieksekusi sblm ini).
  1. INVESTIGASI poin (1) — DICEK ULANG (bukan ditebak) seluruh kode `PersistentTrimService.kt`
     (channel creation, timing `startForeground()`), `AndroidManifest.xml` (`<service>`+property),
     & alur toggle di `MainActivity.kt`: SEMUA cocok dgn kontrak resmi Android, 0 bug ditemukan di
     kode. `CrashLogger.kt` jg dicek — TIDAK menelan crash diam-diam (tetap teruskan ke handler
     default stlh nulis log), jadi kalau ini crash asli harusnya ketahuan (user tdk lapor force-
     close). Kesimpulan PALING mungkin (P0 NO HALLUCINATION — dinyatakan sbg dugaan terkuat, BUKAN
     kepastian, krn 0 device log asli): izin runtime `POST_NOTIFICATIONS` (level Android) BEDA dari
     toggle "Izinkan notifikasi" di level Setelan HP/OEM (XOS dkk kadang punya toggle app-level
     terpisah, sama polanya dgn Autostart yg sudah terbukti terpisah dari izin baterai di v22-v24).
     `startForeground()` tidak butuh notifikasi BENAR2 tampil supaya proses tetap dipromosikan
     foreground — jadi service kemungkinan TETAP jalan, cuma indikatornya tersembunyi.
  2. FIX minimal, DIAGNOSTIK bukan tebak-tebak fix: tambah 1 pengecekan
     `NotificationManagerCompat.from(ctx).areNotificationsEnabled()` di `SettingsTab` — kalau HP
     ternyata memblokir notifikasi app-level, tampil teks peringatan jelas + jalan keluarnya
     (Setelan > Aplikasi > LagFix > Notifikasi), dgn `remember(ui)` supaya otomatis re-check tiap
     `onResume()` (mis. abis dari Setelan HP). 0 perubahan pada `PersistentTrimService.kt`/logic
     inti (P0 NO SCOPE CREEP — tidak menebak-nebak ubah kode service tanpa evidence konkret dulu).
     1 file: `MainActivity.kt`.
  3. PROJECT_STATE.md dirapikan: histori batch v2-v24 (semua sudah VERIFIED/CLOSED per RESUME
     POINT masing2, 959 baris) dipindah ke `docs/archive/PROJECT_STATE_v2-v24.md` (baru). File
     utama sekarang cuma: header arsitektur (tak diubah) + pointer ke arsip + batch v25-v27 (masih
     relevan krn saling terkait langsung dgn investigasi berjalan) + v28 ini. VIP doc, tidak
     dihitung ke batas file.
  File diubah: `MainActivity.kt` (1 file, poin 2), `PROJECT_STATE.md` (VIP, poin 3), +1 file baru
  `docs/archive/PROJECT_STATE_v2-v24.md` (VIP/arsip, tidak dihitung).
- v28 VALIDASI: brace/paren balance `MainActivity.kt` OK (delta 0 dari edit ini; 1 mismatch paren
  pre-existing yg sama dari v27 msh ada, bukan baru). Cross-check: `NotificationManagerCompat`
  import benar, `remember(ui)` valid (dipakai persis sama pola dgn `remember` lain di file ini).
  0 file lain disentuh selain yg disebut di atas (dikonfirmasi diff vs ZIP v27). **BELUM compile/
  device test** — root cause notifikasi MASIH DUGAAN kuat, BELUM dikonfirmasi 100% (butuh user cek
  Setelan HP scr langsung + lihat apakah teks peringatan baru ini muncul atau tidak di device).
- Docs: `CHANGELOG.md` TIDAK ditambah entri (diagnostik internal, bukan fitur/fix user-facing yg
  final — konsisten konvensi v14-v18/v24 skip changelog utk investigasi belum tuntas).
- Batch: v28

[RESUME POINT: v28 — investigasi notifikasi v27 yg tak pernah tampil: 0 bug ditemukan di kode
(dicek ulang penuh), dugaan terkuat = toggle notifikasi app-level Setelan HP/OEM terpisah dari izin
Android `POST_NOTIFICATIONS` (BELUM dikonfirmasi 100%, cuma dugaan terkuat berbasis pola Autostart
v22-v24). Ditambah 1 diagnostik: teks peringatan otomatis di SettingsTab kalau
`areNotificationsEnabled()==false`. PROJECT_STATE.md dirapikan — histori v2-v24 pindah ke
`docs/archive/PROJECT_STATE_v2-v24.md`, file utama fokus ke v25-v28 (thread investigasi aktif).
5 poin test device v27 (a-e) MASIH BELUM ada evidence sama sekali dari user -> Remaining: jalankan
DAILY UPDATE, push, CI hijau dulu -> Next Action: minta user (a) cek Setelan > Aplikasi > LagFix >
Notifikasi scr langsung di HP (apakah ada toggle master/channel yg mati), (b) lihat apakah teks
peringatan baru muncul di app kalau itu penyebabnya, (c) tetap lanjutkan 5 poin test device v27
yg blm pernah dikonfirmasi (toggle ON/force-close/restart HP/toggle OFF/tunggu 1 siklus jadwal) —
JANGAN ubah lagi kode `PersistentTrimService.kt` tanpa evidence baru dari (a)/(b) dulu.]

- v29 (permintaan user: "buatkan crash logger biar langsung ke bukti masalahnya" — dipahami sbg
  minta EVIDENCE KONKRET, bukan tebakan lagi, utk investigasi notifikasi v27/v28 yg msh belum
  pasti). ANDROID VITAL GUARDS eksplisit larang bikin sistem logging baru tanpa perlu -> REUSE
  `CrashLogger.kt` yang sudah ada (0 sistem baru):
  1. `CrashLogger.kt` DIREFAKTOR (0 perubahan perilaku jalur crash existing — dicek line-by-line,
     nama file/isi body crash TETAP IDENTIK): logic tulis-file diekstrak jadi `writeToFile()`
     private, dipakai ULANG oleh crash handler (`install()`) MAUPUN fungsi baru `logDiagnostic(ctx,
     tag, message)` (nama file beda prefix `LagFix_diag_<tag>_...` vs `LagFix_crash_...`, jadi
     gampang dibedakan user di folder Download/LagFix/).
  2. `PersistentTrimService.onStartCommand()`: SEBELUM `startForeground()` dipanggil, rekam
     `NotificationManagerCompat.areNotificationsEnabled()` + importance channel asli
     (`getNotificationChannelCompat().importance`); `startForeground()` sendiri dibungkus
     `runCatching` (BUKAN menyembunyikan error — TUJUANNYA justru menangkap exception exact-nya
     kalau ada, ditulis ke file yg sama). Semua 3 data itu ditulis 1 file lewat
     `CrashLogger.logDiagnostic()` tiap kali service start.
  3. TIDAK dibuat sistem logging terpisah/baru, TIDAK ada permission baru (folder Download/LagFix/
     sudah dipakai crash logger sejak awal), TIDAK ada perubahan ke jadwal fstrim/WorkManager.
  File diubah: `CrashLogger.kt`, `PersistentTrimService.kt` — 2 file, 1 fitur logis (instrumentasi
  diagnostik, bukan fitur baru).
- v29 VALIDASI: brace/paren balance OK kedua file (0/0 masing2). Cross-check: `logDiagnostic`
  dipanggil dgn signature yg cocok persis definisinya, `NotificationManagerCompat` sudah diimport.
  0 file lain disentuh (dikonfirmasi diff vs ZIP v28). **BELUM compile/device test** — file bukti
  (`LagFix_diag_persistent_service_*.txt`) baru akan MUNCUL NYATA di Download/LagFix/ setelah user
  toggle ON di device asli; isinya itulah yg akan jadi evidence definitif utk nutup investigasi
  v27/v28 (bukan dugaan lagi).
- Docs: `CHANGELOG.md` TIDAK ditambah (instrumentasi diagnostik internal, konsisten konvensi
  v14-v18/v24/v28 skip changelog utk investigasi belum tuntas/non-user-facing).
- Batch: v29

[RESUME POINT: v29 — `CrashLogger.kt` direfaktor (0 regresi jalur crash, dicek line-by-line) +
`logDiagnostic()` baru, dipanggil dari `PersistentTrimService.onStartCommand()` utk rekam
`areNotificationsEnabled()` + channel importance asli + hasil `startForeground()` (sukses/exception
exact) ke file `Download/LagFix/LagFix_diag_persistent_service_<timestamp>.txt` tiap toggle ON.
Ini GANTI dugaan v28 jadi EVIDENCE KONKRET begitu user toggle ON di device -> Remaining: jalankan
DAILY UPDATE, push, CI hijau dulu -> Next Action: minta user toggle ON layanan latar depan sekali
lagi di device, lalu AMBIL/BUKA file `LagFix_diag_persistent_service_*.txt` terbaru di folder
Download/LagFix/ & kirim isinya (bukan cuma laporan tekstual) — dari situ baru bisa dipastikan:
(a) kalau `areNotificationsEnabled()=false` -> CONFIRMED toggle OS/OEM level yg jadi penyebab
(user tinggal diarahkan ke Setelan), (b) kalau `true` tapi `startForeground()` GAGAL dgn exception
-> ada bug kode nyata yg baru ketahuan, prioritas fix berikutnya, (c) kalau `true` & SUKSES tanpa
exception -> penyebab lebih dalam lagi (rendering OEM di luar API publik), butuh diskusi lanjutan
dgn user apa langkah berikutnya. JANGAN ubah kode lagi sebelum isi file diagnostik ini didapat.]

- v30 (laporan user: "crash logger tidak mencatat apa-apa, bahkan foldernya sendiri gak nampak" —
  evidence v29 yg ditunggu TIDAK PERNAH muncul sama sekali, 0 file, 0 folder). Bug = root-cause
  minimum: dibaca ulang `writeToFile()` (dipakai BERSAMA oleh jalur crash `install()` DAN
  `logDiagnostic()` — 1 root cause utk 2 gejala sekaligus).
  1. ROOT CAUSE (P0 NO HALLUCINATION — dugaan TERKUAT dari review kode, BUKAN kepastian 100% krn 0
     device di sandbox ini): versi lama BISA "sukses" (0 exception, ketangkep `runCatching` luar)
     padahal 0 byte tertulis & folder Download/LagFix/ TIDAK PERNAH benar2 dibuat, di 2 titik: (a)
     `contentResolver.openOutputStream(uri)?.use { }` diam2 SKIP nulis kalau return null (bisa
     terjadi di sejumlah ROM/kondisi) — 0 exception, jadi tak pernah ketahuan penyebabnya; (b) file
     diinsert TANPA siklus `IS_PENDING` resmi (insert pending -> tulis -> clear pending, pola resmi
     Android utk Downloads collection) — tanpa ini sejumlah OEM (mis. Infinix XOS, sudah terbukti
     agresif/beda perilaku di riwayat v22-v25 kasus Autostart/baterai) bisa menahan file/folder dari
     file-manager/MediaStore query sampai tak pernah "nampak", match PERSIS laporan user.
  2. FIX (`CrashLogger.kt`, 0 perubahan titik panggil — signature `install()`/`logDiagnostic()`
     tetap identik, 0 file lain disentuh): (i) siklus `IS_PENDING` resmi ditambahkan
     (`writeViaMediaStore()` baru, private). (ii) null/gagal di titik mana pun kini MELEMPAR
     exception eksplisit (bukan diam2 no-op). (iii) kalau jalur MediaStore gagal total (exception
     apapun), fallback otomatis ke app-specific external files dir (`writeToAppFilesDir()` baru,
     0 permission dibutuhkan, dijamin ada) + pesan error MediaStore asli ikut ditulis di body-nya —
     jadi SELALU ada bukti tertulis di suatu tempat, bukan diam total lagi. (iv) `Log.e` ditambah
     di tiap titik gagal sbg lapis observability terakhir (logcat) — sejalan tujuan ANDROID VITAL
     GUARDS (bug jadi observable & diagnosable). ini JUGA otomatis memperbaiki evidence v29 yg
     ditunggu (`PersistentTrimService.kt` 0 diubah — dia cuma pemanggil, root cause ada di fungsi
     tulis file bersama).
  3. TIDAK dibuat sistem logging baru (REUSE `CrashLogger.kt` yg sudah ada, sesuai larangan
     eksplisit constitution), TIDAK ada permission baru, TIDAK ada perubahan folder tujuan (tetap
     Download/LagFix/ sbg primary).
  File diubah: `CrashLogger.kt` — 1 file, 1 bug root-cause fix (dikonfirmasi via diff penuh thd ZIP
  v29: 0 file lain berubah).
- v30 VALIDASI: brace/paren balance OK (26 buka/26 tutup `{}`, 67/67 `()`). Cross-check manual:
  `ContentValues.clear()`+`put()` valid, `contentResolver.update(uri, values, null, null)` cocok
  signature (where/selectionArgs nullable), `MediaStore.MediaColumns.IS_PENDING` tersedia di
  compileSdk 35 (constant int, aman direferensi dlm blok `SDK_INT >= 29`), import `IOException`
  ditambah. **BELUM compile/device test** (0 Android SDK/Gradle/device di sandbox ini, sama spt
  batch2 sebelumnya) — root cause dinyatakan sbg dugaan terkuat berbasis kode, BUKAN kepastian,
  krn tidak menutup kemungkinan device/OS ini punya restriksi lain di luar kode (mis. storage
  penuh, kebijakan OEM lain) yg baru kelihatan setelah user test ulang.
- Docs: `CHANGELOG.md` +entry v30 (user-facing bug fix, beda dari v28/v29 yg diagnostik internal
  murni — ini fix nyata atas laporan bug konkret user).
- Batch: v30

[RESUME POINT: v30 — `CrashLogger.kt` (`writeToFile()`) diperbaiki: siklus `IS_PENDING` resmi +
exception eksplisit (bukan diam2 no-op) + fallback ke app files dir + `Log.e` observability. Ini
target root cause paling mungkin (dugaan terkuat via review kode, BELUM device-confirmed) dari 2
gejala sekaligus: crash logger 0 pernah mencatat & folder Download/LagFix/ 0 pernah nampak. Investigasi
notifikasi v27/v28 yg masih pending JUGA akan terbantu otomatis (evidence v29 yg dulu tak pernah
muncul, sekarang seharusnya muncul di Download/LagFix/ ATAU fallback-nya) -> Remaining: jalankan
DAILY UPDATE, push, CI hijau dulu -> Next Action: minta user (a) toggle ON layanan latar depan
sekali lagi di device, (b) cek folder Download/LagFix/ — kalau msh 0 file, cek fallback
Android/data/com.lagfix.fstrim/files/ (app files dir, mungkin butuh file manager yg bisa akses
folder app), (c) kirim isi file `LagFix_diag_persistent_service_*.txt` terbaru yg ditemukan (dari
folder mana pun) — dari situ baru bisa dipastikan akar masalah notifikasi v27/28 (lihat detail
opsi a/b/c di RESUME POINT v29 di atas, msh berlaku sama). JANGAN ubah kode lagi sebelum evidence
ini didapat — kalau user lapor MASIH 0 file sama sekali di KEDUA lokasi setelah fix ini, itu sinyal
kuat penyebabnya di luar kode (device/OS/storage), bukan lagi di `CrashLogger.kt`.]

- v31 (laporan user lanjutan: "Gak ada yang muncul" — folder tetap 0 kelihatan; minta dibuatkan
  pembaca log LANGSUNG di tab Pengaturan + bisa disalin teksnya di dalam aplikasi). CATATAN P0 NO
  HALLUCINATION: TIDAK ada kepastian dari laporan user apakah ini sudah di build v30 yg ter-install
  (v30 blm dikonfirmasi lewat DAILY UPDATE/push/CI sblm laporan ini masuk) atau msh build lama —
  tapi permintaan user (pembaca in-app) SUDAH memecahkan ambiguitas itu jg: begitu user pakai build
  v31, hasil query lewat `ContentResolver` sendiri jadi bukti definitif terlepas dari apakah file
  manager OS/OEM menampilkannya atau tidak.
  1. FITUR BARU: Card "Log Diagnostik" di tab Pengaturan (`SettingsTab`, komposabel baru
     `LogReaderCard`) — tombol "Muat ulang" (auto-load sekali saat tab dibuka via
     `LaunchedEffect(Unit)`) menampilkan daftar semua file log (crash + diagnostic) dari KEDUA
     lokasi (Download/LagFix via MediaStore query, DAN app files dir cadangan v30) — dibaca lewat
     `ContentResolver`/`File` API app sendiri, 0 tergantung file manager pihak lain. Tap 1 entri ->
     dialog isi log penuh (scroll, monospace, sama pola dgn dialog changelog `UpdateCard` yg sudah
     ada) + tombol "Salin" (clipboard, `ClipboardManager`). Semua IO (query MediaStore, baca file)
     dijalankan di `Dispatchers.IO` (P0 Thread Safety — 0 blocking Main thread), state
     dialog/isi-log-terpilih pakai `rememberSaveable` (P0 UI State — bertahan dari rotasi), daftar
     mentah pakai `remember` biasa (cukup di-reload ulang, murah, non-user-input).
  2. `CrashLogger.kt`: fungsi baca baru `listLogs(ctx): List<LogFile>` (+ nested class `LogFile`)
     — query MediaStore Downloads (filter `RELATIVE_PATH LIKE '%LagFix%'` + `DISPLAY_NAME LIKE
     'LagFix_%.txt'`, guard `SDK_INT >= 29` sama spt jalur tulis) DAN scan app files dir cadangan,
     digabung + dibatasi `take(50)` (cegah daftar membengkak tanpa batas). 0 permission baru
     dibutuhkan (app selalu boleh query/baca entri MediaStore miliknya sendiri). 0 perubahan pada
     fungsi tulis (`install()`/`logDiagnostic()`/`writeToFile()` v30 tetap identik) — REUSE murni,
     bukan sistem logging baru.
  3. TIDAK ada perubahan ke `PersistentTrimService.kt`, jadwal fstrim, atau lokasi folder tujuan.
  File diubah: `CrashLogger.kt`, `MainActivity.kt` — 2 file, 1 fitur logis (pembaca log in-app).
- v31 VALIDASI: brace/paren `CrashLogger.kt` OK (44/44 `{}`, 103/103 `()`). `MainActivity.kt`:
  delta 0 dari edit ini (60 `{}` & 60 `()` ditambah, seimbang di kedua sisi) — 1 mismatch paren
  pre-existing yg sama dari v27/v28 msh ada (dikonfirmasi: file original v29 pun sudah 421/420
  sblm batch ini disentuh), BUKAN baru. Cross-check manual: smart-cast `selectedContent` (var,
  nullable) TIDAK dipakai langsung di dalam lambda bersarang (rawan compile-error Kotlin) —
  ditangani dgn capture ke `val currentLogContent` lokal dulu sebelum dipakai di
  `AlertDialog`/`onClick`. Import baru dicek: `ClipData`, `ClipboardManager`, `ContentUris`,
  `Dispatchers`, `withContext` — 0 duplikat/konflik dgn import lama. Diff penuh thd ZIP v30
  dikonfirmasi: HANYA `CrashLogger.kt` + `MainActivity.kt` berubah, 0 file lain. **BELUM
  compile/device test** (0 Android SDK/Gradle/device di sandbox ini) — terutama fungsi Compose
  baru (`LogReaderCard`) blm pernah lewat compiler Kotlin/Compose sungguhan, jadi ada risiko
  typo/type-mismatch kecil yg cuma ketahuan saat build asli (CI) — root-cause-analysis & review
  manual sudah seteliti mungkin tapi bukan pengganti compiler.
- Docs: `CHANGELOG.md` +entry v31 (fitur baru user-facing: Log Diagnostik + salin teks).
- Batch: v31

[RESUME POINT: v31 — Card "Log Diagnostik" baru di tab Pengaturan (`LogReaderCard` di
`MainActivity.kt`) baca log via `CrashLogger.listLogs()` (baru) dari Download/LagFix (MediaStore)
+ app files dir cadangan, tampil dialog isi lengkap + tombol salin clipboard — full bypass dari
ketergantungan file manager OS/OEM. -> Remaining: jalankan DAILY UPDATE, push, tunggu CI hijau
(BELUM pernah dicompile compiler sungguhan batch v30 MAUPUN v31 — 2 batch build sekaligus blm
lolos verifikasi CI) -> Next Action: minta user (a) pasang APK hasil CI terbaru (build v31), (b)
buka tab Pengaturan -> card "Log Diagnostik" -> tekan "Muat ulang", (c) laporkan: muncul entri log
atau tidak? Kalau MUNCUL -> tap salah satu, salin isinya, kirim ke sini (evidence definitif utk
investigasi notifikasi v27/28 yg masih pending). Kalau TETAP 0 entri sama sekali stlh build v31
terpasang & CI hijau -> itu bukti KUAT penulisan log memang gagal total di device ini (bukan lagi
soal visibility/file-manager), prioritas berikutnya jadi investigasi kenapa `writeViaMediaStore()`
DAN `writeToAppFilesDir()` v30 berdua gagal (mis. storage penuh, restriksi OEM lain di luar kode).
JANGAN ubah kode `CrashLogger.kt` lagi sebelum hasil test build v31 ini didapat.]

- v32 (laporan user: build v31 SUDAH terpasang — dikonfirmasi via screenshot, teks card cocok
  persis — "spam pencet Muat ulang", 0 entri sama sekali. INI BEDA dari v29/v30: kali ini BUKAN lagi
  soal visibility file manager, app sendiri via `ContentResolver` juga 0 nemu apa-apa).
  1. ROOT CAUSE (P0 NO HALLUCINATION — 2 kemungkinan, BELUM bisa dibedakan dari laporan user saja):
     (a) jalur tulis (`writeViaMediaStore()` DAN `writeToAppFilesDir()`, v30) BERDUA gagal total di
     HP ini, ATAU (b) — LEBIH MUNGKIN scr Occam's razor — `logDiagnostic()` cuma pernah dipanggil
     dari `PersistentTrimService.onStartCommand()` (jalan CUMA kalau toggle "Layanan latar depan
     persisten" di-ON-kan) dan `install()` cuma jalan kalau app CRASH; kalau user BELUM pernah
     toggle ON fitur itu (atau app blm pernah crash) SEJAK pasang build v31, 0 entri itu WAJAR —
     bukan bug, krn jalur tulisnya memang belum pernah dipanggil sama sekali. Laporan user TIDAK
     menyebutkan apakah toggle itu sudah dicoba di build v31 ini.
  2. FITUR BARU (memisahkan 2 kemungkinan di atas tanpa perlu nebak): tombol "Tes tulis log" baru
     di card Log Diagnostik, di sebelah "Muat ulang" — manggil `CrashLogger.testWrite()` (baru,
     `CrashLogger.kt`) yg nulis 1 file tes LANGSUNG, 0 precondition (0 butuh toggle servis/crash).
     Hasil (`Result<Unit>`) dilaporkan LANGSUNG ke UI via Snackbar (`onFeedback`) — BUKAN cuma
     Log.e/logcat yg tak kelihatan tanpa ADB: sukses -> "Tes tulis BERHASIL" + daftar auto-reload
     (artinya jalur tulis TERBUKTI OK, 0 entri sebelumnya krn kemungkinan (b) di atas); gagal ->
     "Tes tulis GAGAL: <pesan error>" (BARU itu bukti nyata kemungkinan (a)).
  3. `writeToFile()` diperkuat (`CrashLogger.kt`): kalau fallback `writeToAppFilesDir()` JUGA gagal
     stlh MediaStore gagal (dulu exception fallback mentah lgs propagate, pesan MediaStore-nya
     hilang), sekarang keduanya digabung jadi 1 `IOException` eksplisit berisi PESAN ERROR
     KEDUANYA — supaya `testWrite()` bisa laporkan diagnosis lengkap ke user kalau device ini
     benar2 pathological (0 perubahan perilaku observable di `install()`/`logDiagnostic()` — msh
     sama2 di-`Log.e` diam2 spt v30/v31, cuma `testWrite()` yg baru dpt manfaat pesan gabungan ini).
  4. TIDAK ada perubahan ke `PersistentTrimService.kt`, `listLogs()`, jadwal fstrim, atau lokasi
     folder tujuan.
  File diubah: `CrashLogger.kt`, `MainActivity.kt` — SAMA 2 file dgn v31 (1 fitur logis lanjutan:
  tooling diagnosa jalur tulis log).
- v32 VALIDASI: brace/paren `CrashLogger.kt` OK (50/50 `{}`, 124/124 `()`). `MainActivity.kt`:
  205/205 `{}`, 496/495 `()` — delta 0 dari batch ini (1 mismatch paren pre-existing yg sama msh
  ada, terus dikonfirmasi bukan baru sejak v27). Diff penuh thd ZIP v31: HANYA `CrashLogger.kt` +
  `MainActivity.kt` berubah. **BELUM compile/device test** (0 SDK/Gradle/device di sandbox ini).
  **PERINGATAN AKUMULASI RISIKO:** per catatan v31, batch v30 DAN v31 BELUM pernah dikonfirmasi
  lolos compile CI sungguhan sblm laporan bug ini masuk — v32 ini jadi BATCH KE-3 beruntun yg
  ditumpuk di atas kode yg blm terverifikasi compile. Kalau ternyata ada 1 typo/type-mismatch kecil
  tersembunyi di v30 atau v31 yg baru ketahuan compiler asli, itu akan blokir v32 ini juga. SANGAT
  disarankan jalankan DAILY UPDATE + cek CI SEKARANG (sebelum minta perubahan lain) drpd terus
  menumpuk batch di atas fondasi yg blm certain buildable.
- Docs: `CHANGELOG.md` — TIDAK ditambah entry baru (v32 ini tooling diagnosa internal utk
  developer/investigasi, bukan fitur user-facing baru yg berdiri sendiri — sama pola dgn v28/v29).
- Batch: v32

[RESUME POINT: v32 — tombol "Tes tulis log" baru (`CrashLogger.testWrite()` + `LogReaderCard`) di
card Log Diagnostik, nulis 1 file tes tanpa precondition & lapor sukses/gagal LANGSUNG ke Snackbar
(bukan cuma logcat). Tujuan: pisahkan "jalur tulis rusak" vs "logger blm pernah dipanggil krn toggle
servis/crash blm pernah terjadi" — 2 kemungkinan yg msh sama2 terbuka dari laporan "0 entri" v31.
-> Remaining: **CI utk v30+v31+v32 BELUM ada satupun yg dikonfirmasi hijau** — jalankan DAILY
UPDATE, push, WAJIB tunggu & cek CI hijau dulu sblm lanjut apapun (3 batch numpuk tanpa build
confirm = risiko makin besar). -> Next Action: minta user (a) pasang APK v32 stlh CI hijau, (b)
buka Log Diagnostik, tekan "Tes tulis log" (BUKAN "Muat ulang" dulu), (c) laporkan hasil Snackbar-
nya persis: "Tes tulis BERHASIL" -> lanjut tekan "Muat ulang", cek muncul 1 entri `LagFix_test_...`
atau tidak (kalau BERHASIL tapi tetap 0 di daftar -> ada bug lain lg di `listLogs()`/query, beda
lagi diagnosisnya); "Tes tulis GAGAL: <pesan>" -> kirim PERSIS pesan errornya ke sini, itu bukti
definitif jalur tulis (MediaStore+fallback) berdua gagal & pesannya kasih tahu kenapa. JANGAN ubah
kode `CrashLogger.kt` lagi sebelum hasil tombol "Tes tulis log" ini didapat.]
