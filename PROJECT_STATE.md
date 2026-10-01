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

- v33 (docs-only, 0 perubahan kode): user kirim ISI file `LagFix_test_*.txt` hasil tombol "Tes
  tulis log" v32 — **KONFIRMASI DEFINITIF, bukan lagi dugaan**: penulisan file (MediaStore atau
  fallback, `writeToFile()`) **BERHASIL** di device ini (SDK 36, Infinix X6855 — Android 16).
  Kesimpulan: kemungkinan (a) dari v32 (jalur tulis rusak total) **GUGUR**. Yang benar kemungkinan
  (b): `logDiagnostic()` sejauh ini 0 pernah kepanggil dari `PersistentTrimService` krn toggle
  "Layanan latar depan persisten" 0 pernah di-ON-kan (atau di-ON-kan tapi service gagal start SEBELUM
  sempat manggil `logDiagnostic()`) SEJAK pasang build yg ada fiturnya — BUKAN bug penulisan file.
  Root cause "crash logger tidak mencatat apa-apa" (keluhan ASLI dari awal thread ini) kini
  terjawab: bukan crash-nya yg tak tercatat (blm ada crash yg dilaporkan sama sekali sejauh ini),
  dan bukan pula soal file gagal ditulis (v30-v32 sudah buktikan itu OK) — cuma BELUM ada trigger
  (crash ATAU toggle servis) yg sungguh terjadi. 0 file diubah, 0 perlu repack ZIP baru scr kode
  (versi app tetap v32) — PROJECT_STATE.md diupdate murni sbg evidence log utk lanjut investigasi
  v27/28 (notifikasi servis latar depan tak muncul) yg dari awal jadi alasan `logDiagnostic()`
  dibuat.
- Docs: `PROJECT_STATE.md` diupdate (evidence test-write confirmed OK). `CHANGELOG.md` tetap
  (0 perubahan user-facing baru).
- Batch: v33 (docs-only; APK terpasang tetap hasil build v32, TIDAK perlu instal ulang)

[RESUME POINT: v33 — Test-write CrashLogger v32 KONFIRMASI BERHASIL di device user (SDK 36, Infinix
X6855) -> jalur tulis file TERBUKTI OK, bukan lagi tersangka. Investigasi kini kembali ke tujuan ASLI
`logDiagnostic()` (v29): kenapa notifikasi "Layanan latar depan persisten" tak pernah kelihatan (lihat
detail opsi a/b/c di RESUME POINT v29, msh berlaku penuh: (a) `areNotificationsEnabled()=false` ->
toggle OS/OEM level, (b) `true` tapi `startForeground()` exception -> bug kode nyata, (c) `true` &
sukses tanpa exception -> penyebab lebih dalam di luar API publik). -> Remaining: 0 kode berubah,
APK v32 yg sudah terpasang SUDAH cukup (jangan minta user install ulang) -> Next Action: minta user
(a) buka tab Pengaturan, toggle ON "Layanan latar depan persisten" (kalau blm pernah/msh OFF), (b)
buka card Log Diagnostik, tekan "Muat ulang", cari entri `LagFix_diag_persistent_service_*.txt`
(TERBARU, beda dari `LagFix_test_*.txt` yg td), (c) tap entri itu, tekan "Salin", kirim isinya PERSIS
ke sini — itu evidence definitif utk nutup investigasi notifikasi v27/28 yg sudah pending sejak lama.
JANGAN ubah kode apapun sebelum isi file diagnostic_persistent_service ini didapat.]

- v34 (laporan user: sudah toggle ON "Layanan latar depan persisten" + tekan "Muat ulang" di Log
  Diagnostik, tapi yg muncul CUMA file `LagFix_test_*.txt` lama — 0 entri `diag_persistent_service`
  baru sama sekali). Review kode `PersistentTrimService.kt` + `MainViewModel.setPersistentService()`
  + manifest (`foregroundServiceType="specialUse"` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` + permission
  `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_SPECIAL_USE`/`POST_NOTIFICATIONS` — SEMUA sudah benar &
  lengkap, 0 bug jelas ketemu scr static review) — `CrashLogger.logDiagnostic()` letaknya SEGERA di
  awal `onStartCommand()` (SEBELUM apapun yg berisiko lain), jadi KALAU `onStartCommand()` benar2
  ke-invoke OS, `logDiagnostic()` PASTI kepanggil (0 exception plausible di baris² sebelumnya).
  1. KESIMPULAN SEMENTARA (P0 NO HALLUCINATION — 2 lapis kemungkinan msh terbuka, BUKAN 1 kepastian):
     (a) UI: handler `onChange` toggle ini sendiri yg 0 pernah benar2 tereksekusi (mis. user pencet
     bagian yg salah, atau state re-render aneh) — PALING SEDERHANA tapi blm bisa dibuktikan/
     disingkirkan dari laporan user; (b) SERVICE/OS: handler jalan normal & `PersistentTrimService.
     start()` (`ContextCompat.startForegroundService()`) dipanggil, TAPI OS 0 pernah benar2
     meng-invoke `onCreate()`/`onStartCommand()` service-nya (mis. dibatasi restriksi background-
     start OEM/Android versi baru di luar kontrol kode aplikasi — device user SDK 36/Android 16,
     versi BARU yg riwayat restriksinya blm sepenuhnya diketahui dari dokumentasi publik saat ini).
  2. INSTRUMENTASI BARU (bisect (a) vs (b) tanpa perlu nebak lagi, `MainActivity.kt` — `SettingsTab`
     dpt `scope = rememberCoroutineScope()` baru): setiap toggle ditekan (ON MAUPUN OFF), LANGSUNG
     tulis log `diag_toggle_pressed` (via `CrashLogger.logDiagnostic()`, `Dispatchers.IO`, SEBELUM
     baris `vm.setPersistentService()` dipanggil) — independen total dari apakah service-nya
     berhasil start atau tidak. Diagnosis selanjutnya: file `toggle_pressed` ADA tapi
     `persistent_service` TETAP 0 -> confirmed (b) [masalah service/OS, bukan UI]; file
     `toggle_pressed` SENDIRI 0 ada -> confirmed (a) [masalah UI, toggle 0 ke-trigger].
  3. TIDAK ada perubahan ke `PersistentTrimService.kt`/`CrashLogger.kt`/manifest — 0 ada bug
     konkret ketemu di situ scr review, jadi 0 ada yg "diperbaiki" di sana (P0 NO SCOPE CREEP —
     jangan ubah kode yg blm terbukti salah cuma krn nebak).
  File diubah: `MainActivity.kt` — 1 file (instrumentasi kecil, feature yg sama: investigasi
  notifikasi/servis latar depan, kelanjutan v29/v31/v32).
- v34 VALIDASI: brace/paren `MainActivity.kt` — 208/208 `{}` OK; `()` 507/506 (delta 0 dari batch
  ini vs baseline v33: 1 mismatch pre-existing yg sama msh ada, dikonfirmasi ULANG bukan baru — SAAT
  edit ini SEMPAT ke-introduce 1 mismatch BARU dari komentar prosa ada kurung buka tak tertutup,
  KETAHUAN & DIPERBAIKI sebelum batch ini di-finalisasi, bukti proses validasi delta-check ini
  benar2 dijalankan bukan formalitas). Diff penuh thd ZIP v33: HANYA `MainActivity.kt` berubah.
  **BELUM compile/device test.** **CI utk v30-v34 (5 batch) MASIH BELUM ada satupun yg dikonfirmasi
  hijau ke user** — akumulasi risiko makin besar, SANGAT disarankan cek CI sblm lanjut batch baru.
- Docs: `CHANGELOG.md` — TIDAK ditambah entry (instrumentasi diagnostic internal, sama pola v28/29/32).
- Batch: v34

[RESUME POINT: v34 — instrumentasi baru `diag_toggle_pressed` (di `SettingsTab`, `MainActivity.kt`)
nulis log SEGERA saat toggle "Layanan latar depan persisten" ditekan (arah apapun), independen dari
apakah service-nya berhasil start — dipakai BISECT laporan "toggle sudah di-ON-kan tapi 0 entri
diag_persistent_service sama sekali": (a) kalau `toggle_pressed` ADA tapi `persistent_service` TETAP
0 -> masalah di level service/OS (`PersistentTrimService` 0 pernah di-invoke OS meski di-start), (b)
kalau `toggle_pressed` SENDIRI 0 ada -> masalah di level UI (handler toggle 0 ke-trigger). ->
Remaining: **5 batch (v30-v34) numpuk TANPA satupun terverifikasi compile CI** — jalankan DAILY
UPDATE, push, dan kali ini BENAR2 tunggu hasil CI sblm minta/kerjakan perubahan lain (risiko
akumulasi compile-error tersembunyi makin tinggi tiap batch ditambah tanpa verifikasi). -> Next
Action: minta user (a) pasang APK v34 stlh CI hijau, (b) toggle "Layanan latar depan persisten"
sekali lagi (ON kalau msh OFF, atau OFF-lalu-ON kalau msh ON dari sebelumnya — pastikan ada
PERUBAHAN state biar handler pasti kepanggil), (c) buka Log Diagnostik, Muat ulang, cari KEDUA:
`LagFix_diag_toggle_pressed_*.txt` (baru) DAN `LagFix_diag_persistent_service_*.txt` (baru, kalau
ada), (d) kirim isi KEDUANYA (atau laporkan kalau salah satu/keduanya tetap tak muncul) — itu bukti
definitif bisect (a) vs (b) di atas. JANGAN ubah kode apapun sebelum hasil ini didapat.]

- v35 (user kirim panduan eksternal `solusi_notifikasi_android.md` — 4 penyebab umum notifikasi
  persisten tak tampil di Android — minta debug ulang berdasarkan itu, di luar alur bisect
  toggle_pressed v34 yg msh menunggu evidence user). AUDIT PENUH kode aktual thd KEEMPAT poin
  panduan (dibaca langsung, bukan diasumsikan):
  1. Poin 1 (Channel dibuat & ID cocok sebelum startForeground()): SESUAI — `CHANNEL_ID =
     "lagfix_keep_alive"` sama persis dipakai di `onCreate()` (createNotificationChannel) MAUPUN
     `buildNotification()` (NotificationCompat.Builder), 0 mismatch. Channel dibuat di `onCreate()`
     yg SELALU jalan sebelum `onStartCommand()` pertama kali. 0 perubahan dibutuhkan.
  2. Poin 2 (SmallIcon wajib ada, tidak null): SESUAI scr literal — `R.drawable.ic_tile_fstrim`
     valid & ada di `res/drawable/`, path vector opaque (fillColor solid `#0F62FE`, 0 transparansi
     parsial), jadi TIDAK match kondisi "null/tidak ditemukan" yg dijelaskan panduan. Dicatat sbg
     observasi (BUKAN bug per definisi panduan): icon ini didesain utk QS tile (v20/v25, warna
     brand), dipakai ulang apa adanya sbg smallIcon notifikasi — Android me-masking smallIcon jadi
     siluet putih dari channel alpha (fillColor diabaikan sistem utk notifikasi/status bar,
     beda dari QS tile), jadi scr teknis TETAP tampil (bukan kosong), cuma bukan aset yg didesain
     khusus utk itu. 0 diubah (0 bug konkret, P0 NO SCOPE CREEP — tidak menebak ganti aset tanpa
     bukti ini penyebabnya).
  3. Poin 3 (foregroundServiceType wajib API 34+): SESUAI — `AndroidManifest.xml` sudah py
     `foregroundServiceType="specialUse"` + `<property PROPERTY_SPECIAL_USE_FGS_SUBTYPE>` lengkap +
     4 permission terkait (FOREGROUND_SERVICE/_SPECIAL_USE/POST_NOTIFICATIONS/RECEIVE_BOOT_COMPLETED),
     dikonfirmasi sejak v27, tidak berubah. Kode manggil `startForeground(id, notification)` versi
     2-argumen — scr resmi Android ini otomatis pakai TYPE yg dideklarasikan manifest kalau cuma 1
     type terdaftar (persis kasus ini), jadi 0 bug. 0 diubah.
  4. Poin 4 (startForeground() WAJIB baris pertama onStartCommand(), sebelum operasi apa pun): 1
     DEVIASI NYATA ditemukan — kode SEBELUMNYA menjalankan 2 Binder IPC ringan
     (`areNotificationsEnabled()`, `getNotificationChannelCompat()`) LEBIH DULU sebelum
     `startForeground()` (dipakai utk isi log diagnostik v29). Biasanya cepat (skala ms) & bukan
     "heavy I/O" spt contoh panduan (network/Okio), TAPI tetap melanggar aturan literal panduan &
     device ini (OEM sudah terbukti agresif/tak standar di riwayat v20-v25) — P0 stability: lebih
     aman dihilangkan preventif drpd dibiarkan. FIX: `startForeground()` dipindah jadi BENAR2 baris
     pertama; 2 pemanggilan diagnostik dipindah ke SESUDAHNYA (0 perubahan DATA yg direkam — channel
     dibuat di `onCreate()` jauh sebelumnya, jadi nilai importance/enabled identik dibaca sebelum
     atau sesudah startForeground()). 1 file: `PersistentTrimService.kt`.
  5. P0 NO HALLUCINATION eksplisit: poin 4 di atas adalah pengetatan DEFENSIF thd 1 deviasi literal
     yg ditemukan scr audit statis, BUKAN klaim kepastian ini akar masalah tunggal — investigasi
     bisect v34 (toggle_pressed vs persistent_service) TETAP berlaku penuh & msh perlu evidence
     device yg sama (belum ada perubahan di situ).
  File diubah: `PersistentTrimService.kt` — 1 file (reorder eksekusi, 0 perubahan logic/isi
  notifikasi/data diagnostik).
- v35 VALIDASI: brace/paren balance OK (13/13 `{}`, 65/65 `()`). Diff penuh thd ZIP v34
  (dikonfirmasi via `diff -rq`): HANYA `PersistentTrimService.kt` berubah, 0 file lain (termasuk 0
  perubahan ke `MainActivity.kt`/`CrashLogger.kt`/manifest — instrumentasi bisect v34 msh utuh apa
  adanya). Cross-check: `getString(R.string.persistent_service_notif_text)` & `R.string.app_name`
  dikonfirmasi ada di `strings.xml`, `@color/ic_launcher_background` ada di `colors.xml` (solid
  opaque). **BELUM compile/device test** (0 SDK/Gradle/device di sandbox ini) — DITAMBAH di atas 5
  batch (v30-v34) yg JUGA belum terverifikasi CI hijau, jadi v35 ini BATCH KE-6 beruntun tanpa
  konfirmasi build sungguhan. SANGAT prioritas: jalankan CI SEKARANG sebelum batch berikutnya.
- Docs: `CHANGELOG.md` +entry v35 (percobaan perbaikan, framing best-effort spt v25 — bukan fix
  yg dikonfirmasi pasti, konsisten P0 NO HALLUCINATION).
- Batch: v35

[RESUME POINT: v35 — audit statis PENUH kode aktual thd 4 poin panduan notifikasi eksternal dari
user: 3 poin (channel ID match, smallIcon valid-non-null, foregroundServiceType+property manifest)
SUDAH SESUAI sejak batch2 sebelumnya, 0 diubah. 1 poin (startForeground() harus baris pertama)
ditemukan deviasi nyata (2 Binder IPC ringan jalan duluan) -> DIPERBAIKI, `startForeground()`
sekarang benar2 baris pertama `onStartCommand()`, 0 perubahan pada data diagnostik yg direkam
(cuma urutan baca). Investigasi bisect v34 (`diag_toggle_pressed` vs `diag_persistent_service`)
TETAP BERLAKU PENUH, belum ada evidence device sama sekali dari user. -> Remaining: **6 batch
(v30-v35) numpuk TANPA satupun terverifikasi compile CI** — jalankan DAILY UPDATE, push, dan WAJIB
tunggu CI hijau sblm kerjakan/minta perubahan lain apa pun (akumulasi risiko compile-error
tersembunyi sudah cukup tinggi). -> Next Action: minta user (a) pasang APK v35 SETELAH CI hijau
dikonfirmasi, (b) toggle "Layanan latar depan persisten" OFF lalu ON lagi (pastikan ada perubahan
state), (c) buka Log Diagnostik, Muat ulang, cari & kirim isi KEDUA file terbaru:
`LagFix_diag_toggle_pressed_*.txt` DAN `LagFix_diag_persistent_service_*.txt` (bisect v34 msh
berlaku: kalau toggle_pressed ada tapi persistent_service tetap 0 -> masalah service/OS level di
luar kode; kalau toggle_pressed sendiri 0 -> masalah UI), (d) laporkan apakah notifikasi permanen
AKHIRNYA kelihatan atau tidak stlh fix reorder v35 ini. JANGAN ubah kode apapun sebelum evidence
ini didapat — kalau notifikasi TETAP tak tampil & KEDUA file diagnostik tetap muncul normal dgn
`startForeground() SUKSES tanpa exception` + `areNotificationsEnabled()=true`, itu confirmed bukan
lagi soal kode (4 poin panduan semua sudah sesuai) — kemungkinan besar restriksi OS/OEM di luar API
publik (spesifik device SDK 36 ini), butuh diskusi opsi lain dgn user (bukan lagi coba-coba kode).]

- v36 (laporan user: v35 TETAP tidak menyelesaikan — notifikasi masih tak tampil; user eksplisit
  minta pindah ke "plan user facing lainnya", tanpa menyebut yang mana). CATATAN P0 NO
  HALLUCINATION: laporan ini TIDAK menyebutkan apakah sudah reinstall APK v35 (stlh CI hijau)
  sebelum menilai "masih gak muncul", atau evidence file diagnostik `toggle_pressed`/
  `persistent_service` yg diminta v34/v35 — TIDAK diasumsikan salah satu, bisect tsb msh 100%
  terbuka/belum tertutup.
  1. AUTO-HALT: investigasi notifikasi/foreground-service (v25-v35, 11 batch beruntun) DIJEDA atas
     permintaan eksplisit user (P0 USER INTENT dihormati — 0 kode diubah batch ini shg 0 risiko
     regresi baru dari jeda ini sendiri). BUKAN ditutup sbg gagal permanen (blm ada evidence
     definitif), cuma dijeda sesuai arah user.
  2. "Plan user facing lainnya" DICEK ke `PENDING_ROADMAP.md` (bagian wajib cold-start) — isinya
     rencana LAMA (v6-v18), SEMUA item A-F sudah ✅ SELESAI kecuali D2 (R8/minify, eksplisit
     "Prioritas rendah", bukan user-facing) & F2 (Dependabot, tooling CI, bukan user-facing). 0
     match ditemukan dgn permintaan user. P0 NO SCOPE CREEP + NO HALLUCINATION: 0 dieksekusi/
     ditebak tanpa scope pasti (PENDING_ROADMAP.md sendiri mewajibkan "approval eksplisit sebelum
     dieksekusi" utk tiap item).
  3. 0 file kode diubah (docs-only, `PROJECT_STATE.md`).
- Docs: `CHANGELOG.md` tidak ditambah (0 perubahan user-facing/kode).
- Batch: v36 (docs-only; APK terpasang tetap hasil build v35, TIDAK perlu instal ulang)

[RESUME POINT: v36 — investigasi notifikasi/foreground-service (v25-v35) DIJEDA atas permintaan
eksplisit user krn v35 belum menyelesaikan masalah & user minta pindah fokus ke "plan user facing
lainnya" yg BELUM disebutkan spesifik. BELUM ditutup permanen (bisect v34/v35 msh 100% terbuka, 0
evidence file diagnostik diterima) — kalau user mau lanjut lagi nanti, resume dari RESUME POINT v35
di atas. `PENDING_ROADMAP.md` dicek: 2 item sisa (D2/F2) BUKAN user-facing, jadi TIDAK match
permintaan ini. -> Remaining: TUNGGU user sebutkan scope plan/fitur yg dimaksud. -> Next Action:
minta user sebutkan SECARA SPESIFIK fitur/plan user-facing apa yg dimaksud sebelum kode apapun
disentuh (P0 NO SCOPE CREEP — PENDING_ROADMAP.md sendiri wajib approval eksplisit per item).]

- v37 (user menjawab v36: "grafik statistik, dll — yang bikin project ini hidup, gak teknis
  banget"). Investigasi notifikasi/foreground-service (v25-v35) TETAP DIJEDA (0 disentuh batch
  ini), fokus resmi pindah ke fitur user-facing sesuai arah user.
  1. FITUR BARU: kartu "Statistik" di tab Utama (`MainTab`), tepat di bawah kartu Riwayat yg sudah
     ada — ringkasan teks (jumlah berhasil/dilewati/gagal + rata-rata durasi run berhasil) + grafik
     batang (`RunHistoryChart`, `Canvas`) durasi tiap run, kiri=terlama kanan=terbaru, warna
     konsisten dgn dot Riwayat (hijau=OK/amber=dilewati/merah=gagal, reuse `successGreen`/
     `skippedAmber`/`MaterialTheme.colorScheme.error` yg SUDAH ADA, 0 warna baru).
  2. DESAIN sengaja PARSE-ONLY di sisi UI, pola SAMA PERSIS dgn `LogLine` (v8/v13): reuse
     `logLineRegex` yg sudah ada (0 pola regex baru/beda), 0 perubahan ke `Prefs.kt` (format baris
     log & `record()` TIDAK disentuh sama sekali -> `PrefsTest.kt` tetap valid, 0 risiko regresi ke
     data/jadwal fstrim inti). 0 dependency baru (Canvas dari `androidx.compose.foundation`, bagian
     Compose yg SUDAH jadi dependency project, bukan library chart pihak ketiga — sesuai larangan
     dependency swap). Kartu tidak tampil kalau riwayat masih kosong (hindari tampilan statistik
     kosong yg membingungkan sebelum ada data run apa pun).
  3. TIDAK ada perubahan ke `MainViewModel.kt` (`ui.log` sudah cukup, tidak perlu state baru),
     `TrimWorker.kt`, `PersistentTrimService.kt`, atau manifest.
  File diubah: `MainActivity.kt` — 1 file, 1 fitur logis baru (0 file lain disentuh).
- v37 VALIDASI: brace balance OK (224/224 `{}`, delta +16/+16 seimbang dari baseline v36
  208/208). Paren: 568/567 — delta murni dari batch ini +61/+61 (seimbang); 1 mismatch tetap ada
  tapi PERSIS mismatch pre-existing yg sama sejak v27 (baseline v36 507/506), dikonfirmasi BUKAN
  baru dari batch ini. Cross-check manual: `StatsCard(ui.log)` dipanggil dgn tipe yg cocok
  (`List<String>`), `RunStat`/`parseRunStats`/`RunHistoryChart` 0 konflik nama dgn deklarasi lain
  di file, import baru (`Canvas`, `height`, `Offset`, `Size`) 0 duplikat/konflik. Diff penuh thd
  ZIP v36 (dikonfirmasi via `diff -rq`): HANYA `MainActivity.kt` berubah. **BELUM compile/device
  test** (0 SDK/Gradle/device di sandbox ini) — fitur UI murni (0 sentuh logic fstrim/jadwal/
  service), risiko regresi ke fitur inti RENDAH, tapi tetap butuh verifikasi compile CI sebelum
  device test spt biasa (akumulasi batch blm-verified msh berlanjut dari v30-v36).
- Docs: `CHANGELOG.md` +entry v37 (fitur baru user-facing).
- Batch: v37

[RESUME POINT: v37 — kartu "Statistik" baru di tab Utama (`StatsCard`+`RunHistoryChart` di
`MainActivity.kt`): ringkasan berhasil/dilewati/gagal + rata-rata durasi, plus grafik batang durasi
per run (parse-only dari `ui.log` yg sama, 0 perubahan `Prefs.kt`/data/jadwal). Investigasi
notifikasi/foreground-service (v25-v35) TETAP DIJEDA, belum ditutup, resume dari RESUME POINT v35
kalau user mau lanjut lagi nanti. -> Remaining: jalankan DAILY UPDATE, push, tunggu CI hijau (masih
ada akumulasi batch v30-v37 blm terverifikasi compile sungguhan) -> baru user install & lihat tab
Utama: kartu "Statistik" harus muncul di bawah Riwayat (kalau riwayat sudah ada minimal 1 entri) dgn
angka ringkasan + grafik batang berwarna. -> Next Action: minta user (a) pasang APK v37 setelah CI
hijau, (b) buka tab Utama, cek kartu "Statistik" muncul & datanya masuk akal (cocok dgn Riwayat di
atasnya), (c) laporkan kalau ada yang aneh/tidak sesuai (grafik kosong, angka tak cocok, dll).]

- v38 (feedback user setelah v37 terpasang, + screenshot). BUKTI DEVICE NYATA pertama sejak lama: kartu
  "Statistik" v37 tampil di tab Utama — "23 berhasil · 0 dilewati · 0 gagal · rata-rata 820ms" +
  grafik batang, angkanya COCOK dgn 23 baris Riwayat di atasnya -> fitur v37 TERVERIFIKASI jalan.
  Implikasi (P0 NO HALLUCINATION, dibatasi): APK yg memuat kartu itu berhasil dibangun & jalan,
  jadi KOMPILASI seluruh kode s/d v37 (termasuk v30-v36 yg sebelumnya blm terverifikasi) terbukti
  lolos; PERILAKU fitur lain (mis. layanan persisten/notifikasi) TIDAK ikut terverifikasi oleh
  screenshot ini.
  Permintaan user: (1) tampilkan run dipicu oleh apa (Manual/Otomatis interval); (2) batang grafik
  Statistik "kurang informatif utk user awam".
  1. PEMICU RUN (perlu ubah format data -> 5 file, 1 fitur logis): `Prefs.record(r, trigger)` (param
     wajib, tanpa default -> compiler memaksa semua call site) + `object TriggerSource {MANUAL="Manual",
     AUTO="Otomatis"}`; baris log baru: `dd/MM HH:mm OK [Manual] 574ms ...` (token disisipkan antara
     status & durasi, sisanya identik). Call site: `TrimWorker` (2x, pakai flag `manual` yg SUDAH ada:
     widget/tile=Manual, periodik=Otomatis), `MainViewModel.runNow()` (tombol di app=Manual).
     UI: regex `logLineRegex` dibuat BACKWARD-COMPAT (token opsional, group 3 kosong utk baris lama) —
     23 baris lama di HP user tetap terbaca, tampil tanpa tag; helper baru `parseLogLine`/`ParsedLog`
     dipakai bareng oleh `LogLine`, `parseRunStats`, & ringkasan atas Riwayat. `LogLine` menampilkan
     "27/09 08:00 OK (Otomatis) 574ms ...". Ringkasan atas Riwayat +baris "Dipicu oleh: Manual
     (dijalankan sendiri oleh pengguna)" / "Otomatis (jadwal interval)"; TIDAK tampil utk baris lama
     (label null, tidak menebak) -> baru muncul stlh ada 1 run baru.
  2. GRAFIK INFORMATIF (`MainActivity.kt` saja, 0 dependency baru): durasi ditulis dlm DETIK
     (`formatDuration`, "820ms"->"0,8 detik") bukan ms; ringkasan +rata-rata/tercepat/terlama;
     kalimat penjelas "makin tinggi batang, makin lama"; sumbu vertikal (nilai tertinggi & 0);
     sumbu waktu (stamp terlama kiri, terbaru kanan); garis putus-putus rata-rata; legenda warna
     (`StatsLegend`, satu Text AnnotatedString -> otomatis wrap, aman font besar/layar sempit; hanya
     kategori yg ada).
  Observasi (TIDAK dikerjakan, di luar permintaan): semua baris Riwayat berujung "PersistableBundle[{}]"
  = stdout ASLI `sm fstrim` di ROM ini (`FstrimExecutor.run` menempel output), bukan bug kita; noise
  utk user awam -> KANDIDAT batch berikutnya (rapikan tampilan pesan Riwayat), tunggu approval.
  Konsekuensi diketahui: `LagFixTileService` menampilkan `Prefs.log.firstOrNull()` MENTAH sbg subtitle
  tile -> kini memuat token "[Manual]/[Otomatis]" (kosmetik; file tile tidak disentuh).
  File diubah: `Prefs.kt`, `TrimWorker.kt`, `MainViewModel.kt`, `MainActivity.kt`, `PrefsTest.kt`.
- v38 VALIDASI: braces seimbang di semua file; `MainActivity.kt` 225/225 `{}`, paren delta +2 IDENTIK
  dgn baseline (artefak heuristik strip, bukan dari batch ini); blok baru seimbang murni. Regex baru
  diuji (port Python) thd 9 kasus: baris LAMA dari screenshot, BARU auto/manual, skip lama/baru,
  "belum siap" manual, kurung `[..]` di dalam pesan (tak kacau), sampah (no match -> fallback polos) —
  semua benar. `formatDuration` diuji (0/99/100/574/820/1234/2050 ms). `PrefsTest.kt` disesuaikan:
  signature, assertion "OK [Manual] 964ms", `substring(28)` (dihitung & diverifikasi =120), +1 tes
  token "Otomatis". Semua call site `.record(` dicek (grep) = 3 kode + 5 tes, tak ada yg tertinggal.
  **BELUM compile / unit test / device test untuk perubahan v38** (tak ada SDK/Gradle di sandbox).
- Docs: `CHANGELOG.md` +entry v38.
- Batch: v38

[RESUME POINT: v38 — pemicu run (Manual/Otomatis) tampil di Riwayat + grafik Statistik lebih informatif
(detik, sumbu, garis rata-rata, legenda). v37 terverifikasi di device (screenshot). Investigasi
notifikasi/foreground-service (v25-v35) TETAP DIJEDA, resume dari RESUME POINT v35 kalau user mau.
-> Remaining: DAILY UPDATE, push, tunggu CI hijau (kini juga mengeksekusi PrefsTest yg diubah) -> user
install v38, lakukan 1x "Jalankan fstrim sekarang" + tunggu/lihat 1 run otomatis. -> Next Action: minta
user cek (a) baris baru di Riwayat berformat "... OK (Manual) ..." & ringkasan atas memuat "Dipicu
oleh: Manual ...", (b) grafik: angka sumbu, garis putus-putus, legenda terbaca & tak terpotong,
(c) baris lama tetap tampil normal tanpa tag. Kandidat berikutnya (butuh approval): rapikan pesan
"PersistableBundle[{}]" di Riwayat.]

- v39 (user kirim `logcat_2026-09-28_08-59-18.txt`, tanpa pesan; docs-only, 0 kode diubah, kode = v38).
  Logcat sistem 7MB (126k baris); jejak app HANYA 08:54:47-08:59 (sebelumnya tak ada baris lagfix).
  Perangkat dari log: Transsion XOS (tag Tran*, XOSLauncher) + MediaTek, layar 1080x2436.
  BUKTI TERUKUR (kutipan rekaman, uid app = 10088):
  1. 08:54:51.471 `tranpm/TranManualCleanMgr` "kill proc pid:25668 ... com.lagfix.fstrim", didahului
     `Usf_Hiber removeTask reason=remove-task` (app di-swipe dari Recents). 08:54:51.562
     `ApplicationExitInfo reason=2 (SIGNALED) status=9` (SIGKILL), rss 192MB.
  2. 08:54:51.551-.553 `tranpm/ServicePolicy limitServiceRestartLocked limit: trdApp process` ->
     `scheduleServiceRestartLocked skip ServiceRestart` utk `LagFixTileService` DAN
     `PersistentTrimService`. 08:54:51.563 `TranRestartProcessFeature whiteList not contain
     packageName : com.lagfix.fstrim`. => di ROM ini XOS membunuh proses saat di-swipe DAN sengaja
     menolak me-restart service app pihak ketiga yg tidak di whitelist-nya.
  3. 08:55:16 cold start proses baru (pid 20065) dari launcher. Kode hanya men-start
     `PersistentTrimService` dari `BootReceiver` & toggle (`MainViewModel.setPersistentService`),
     BUKAN saat app dibuka -> pasca-relaunch service tak hidup sendiri.
  4. 0 hit: `FATAL EXCEPTION`, `MissingForegroundServiceType`, `ForegroundServiceDidNotStart`,
     `RemoteServiceException`, `TrimWorker`. 2 baris `ForegroundServiceTypeLoggerModule` ("has no
     types") milik UID 10455 = `com.dp.logcatapp` (dicocokkan ke key NotificationService), BUKAN app
     kita -> diabaikan.
  5. `NotificationService` MENCATAT post notifikasi app lain di jendela itu (logcatapp 08:55:32,
     claude 08:58:47) tapi 0 utk `com.lagfix.fstrim`; XOS `BackgroundProcessFilter newList=[]` (tak
     ada FGS terdaftar) saat app dibuka 08:55:16. Baris "foregroundService=[...]" di log itu = app yg
     sedang di depan (juga muncul utk launcher/logcatapp), BUKAN bukti FGS kita.
  KESIMPULAN (dibatasi, P0 NO HALLUCINATION): (a) MENGUATKAN hipotesis restriksi OS/OEM di RESUME v35 —
  service persisten & tile TIDAK selamat dari swipe-recents di ROM ini, dan itu perilaku sistem,
  bukan bug kode. (b) TIDAK membuktikan kenapa notifikasi tak tampil SAAT service jalan: log tak
  memuat momen toggle ditekan (kita tak menulis Log.*), jadi ada 2 kemungkinan tak terbedakan —
  toggle tak ditekan di jendela itu, atau start gagal diam-diam. Bisect v34 (`diag_toggle_pressed`
  vs `diag_persistent_service`, lewat Pengaturan -> Log Diagnostik) TETAP terbuka & TETAP satu2nya
  pembeda. (c) `me.piebridge.brevent` (Brevent) ada di daftar app terbaru pada log — pembekuan/
  penghentian app oleh pihak ketiga; TIDAK ada bukti keterlibatan, cuma layak dicek user.
  KANDIDAT (semua butuh approval, tak dikerjakan): kartu user-facing "Agar jadwal tetap jalan" di app
  (jangan swipe dari Recents / kunci app di Recents / izin autostart) — sejalan dgn arah "tidak
  teknis"; atau tunggu isi file Log Diagnostik utk lanjut bisect.
- Batch: v39 (docs-only; APK terpasang tetap build v38, TIDAK perlu instal ulang)

[RESUME POINT: v39 — logcat user memberi bukti OS: XOS SIGKILL saat swipe-recents + skip restart
PersistentTrimService/LagFixTileService (trdApp, bukan whitelist). Penyebab notifikasi tak tampil SAAT
service jalan TETAP belum terbukti (bisect v34 terbuka). Kode = v38 (belum ada konfirmasi CI/device utk
perubahan v38: pemicu Manual/Otomatis + grafik informatif). -> Remaining: DAILY UPDATE + CI hijau,
user uji v38. -> Next Action: tanyakan (a) apakah toggle "Layanan latar depan persisten" ditekan
di rentang 08:55-08:59 saat logcat direkam, (b) isi file Log Diagnostik (toggle_pressed &
persistent_service) kalau mau lanjut bisect, ATAU pilih kartu panduan "Agar jadwal tetap jalan".]

- v40 (permintaan user: notifikasi persistent tak tampil krn app di-kill sebelum sempat menampilkan notifikasi yg baru ter-trigger ~3 menit kemudian; "perbaiki"). Kode diubah 2 file: `PersistentTrimService.kt`, `LagFixApp.kt` (+ docs).
  Root cause (dibatasi, P0 NO HALLUCINATION): sejalan dgn bukti logcat v39 (XOS SIGKILL saat swipe-Recents + skip restart servis, trdApp bukan whitelist). Angka "3 menit" = keterangan user, TIDAK terverifikasi di log/kode (0 delay 3 menit di kode; log v39 tak memuat momen toggle). 3 celah nyata di kode:
  1. Servis cuma distart dari toggle/BootReceiver -> setelah proses dibunuh lalu app dibuka lagi, notifikasi tak pernah kembali.
  2. Android 12+ menunda tampil notifikasi FGS ~10 dtk kecuali `FOREGROUND_SERVICE_IMMEDIATE` -> di ROM yg membunuh proses dlm hitungan detik notifikasi tak sempat muncul.
  3. `CrashLogger.logDiagnostic()` (IO MediaStore) jalan di Main thread di `onStartCommand()` -> dgn poin 1 jadi tiap cold start; melanggar guard Thread Safety.
  FIX: (1) `PersistentTrimService.startIfEnabled()` (baru, cek `Prefs.persistentServiceEnabled`, `start()` dibungkus `runCatching` + `Log.w` supaya penolakan OS Android 12+ dari proses background tak crash) dipanggil di `LagFixApp.onCreate()` setelah `CrashLogger.install()`. (2) `.setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)` di `buildNotification()` (core-ktx 1.13.1 >= 1.5.0). (3) log diagnostik dipindah ke `CoroutineScope(Dispatchers.IO).launch`. 0 perubahan manifest/permission/dependency/Scheduler/TrimWorker/UI.
  SENGAJA TIDAK: `onTaskRemoved()`+AlarmManager (butuh izin exact-alarm; start FGS dari alarm diam2 ditolak Android 12+; SIGKILL XOS tak memanggil callback) -> risiko > manfaat, 0 bukti.
  BATAS: XOS tetap bisa SIGKILL saat swipe-Recents; notifikasi kembali saat app dibuka/proses dibuat lagi, BUKAN otomatis stlh swipe. Tak ada jaminan 100%.
- v40 VALIDASI: keseimbangan () {} 2 file OK; import/referensi dicek manual (`Log`, `CoroutineScope`, `Dispatchers`, `launch`, `Prefs`); 0 kompilasi (sandbox tanpa Android SDK/Gradle) -> CI. Behavior BELUM diuji di device.
- Batch: v40

[RESUME POINT: v40 — notifikasi persistent: servis kini distart ulang tiap cold start proses (jika toggle ON) + notifikasi FGS langsung tampil (IMMEDIATE) + log diagnostik keluar dari Main thread. -> Remaining: DAILY UPDATE, tunggu CI hijau, user install v40, toggle "Layanan latar depan persisten" ON, swipe app dari Recents, buka lagi. -> Next Action: minta user cek apakah notifikasi tampil (a) segera setelah toggle ON dan (b) setelah app dibuka lagi pasca-swipe; kalau tetap 0, kirim isi Log Diagnostik (`diag_persistent_service`, `diag_toggle_pressed`) — jangan ubah kode lagi tanpa evidence itu. Kandidat (butuh approval): kartu panduan "Agar jadwal tetap jalan" (kunci app di Recents / izin autostart).]

- v41 (permintaan user: buat pathway APK debug ke artefak GitHub; user install lalu kirim logcat via aplikasi logcat reader). Kode app = v40 (0 diubah). 1 file diubah: `.github/workflows/build.yml` (+ docs). CHANGELOG.md TIDAK diubah (CI-only, bukan perubahan user-facing; isi file itu dibaca mentah oleh updater in-app).
  PATHWAY (4 step baru, ditambahkan di PALING AKHIR job `build`, 0 baris lama diubah/dihapus): `Build APK debug` (`assembleDebug`, `continue-on-error`) -> `Siapkan APK debug` (nama `LagFix_build-<run>_debug.apk` di dir `dist_debug/`) -> `Unggah APK debug` (artifact `LagFix-debug-apk-<run>`) -> `Unggah log gagal build debug` (artifact `LagFix-debug-fail-log-<run>`, hanya kalau build debug gagal).
  KEPUTUSAN DESAIN: (1) di job yg SAMA dgn rilis, bukan workflow terpisah -> `GITHUB_RUN_NUMBER` sama -> versionCode APK debug = rilis run yg sama (workflow terpisah punya hitungan run sendiri, versionCode-nya lebih kecil -> install ditolak sbg downgrade). (2) di step TERAKHIR & dir `dist_debug` (BUKAN `dist`) -> 0 masuk GitHub Release, updater in-app (`releases/latest`) tak pernah kena APK debug; step lama `find ... | head -n 1` dan artifact `LagFix-apk` berjalan SEBELUM debug dibuild, jadi tak terpengaruh. (3) kalau `release.keystore` ada (secret terisi), APK debug ditandatangani ULANG pakai `apksigner` dgn kunci rilis (secret cuma via env `env:`, 0 dicetak) supaya bisa menimpa app rilis tanpa uninstall; kunci debug bawaan Gradle dibuat baru di tiap runner CI -> tak bisa dipakai menimpa apa pun, tiap build harus uninstall (hapus pengaturan + izin Shizuku). Tanpa secret -> APK debug apa adanya (perilaku sama dgn jalur lama, yg juga debug).
  BATAS (P0 NO HALLUCINATION): sandbox tanpa Android SDK/Gradle/runner -> YAML divalidasi parser (struktur/kondisi step) tapi `apksigner sign` ke APK yg sudah bertanda-tangan-debug BELUM dijalankan; asumsi: apksigner mengganti tanda tangan lama (perilaku standar, belum terbukti di sini). Kalau step "Siapkan APK debug" gagal, run merah tapi rilis sudah selesai; lihat log step itu.
  CARA LOGCAT (usulan alur uji): install APK debug dari artifact (unzip dulu; artifact GitHub selalu berbentuk .zip) -> buka aplikasi logcat reader & mulai rekam SEBELUM uji -> di LagFix: toggle "Layanan latar depan persisten" ON (beri izin notifikasi) -> catat jam -> swipe LagFix dari Recents -> buka lagi -> tunggu ~3 menit sambil catat apakah notifikasi tampil -> hentikan rekam & kirim. Kata kunci yg relevan: `com.lagfix.fstrim`, `PersistentTrimService`, `LagFixCrashLogger`, `ForegroundService`, `NotificationService`, `tranpm`, `ServicePolicy`. Kirim juga isi kartu Log Diagnostik (`diag_persistent_service`, `diag_toggle_pressed`).
  CATATAN: kode app belum menulis `Log.*` di jalur servis persisten (kecuali `Log.w` saat start ditolak OS, v40) -> logcat cuma memuat jejak SISTEM (ActivityManager/NotificationService/XOS). Itu cukup utk bukti v39, tapi kalau kurang, kandidat: `Log.i` di `onCreate`/`onStartCommand`/`startIfEnabled` (butuh approval, belum dikerjakan).
- v41 VALIDASI: YAML parse OK (13 -> 17 step, kondisi `if`/`continue-on-error` dicek), 0 baris lama workflow berubah (diff: hanya penambahan). BELUM dijalankan di GitHub Actions.
- Batch: v41

[RESUME POINT: v41 — pathway APK debug -> artifact `LagFix-debug-apk-<run>` ditambahkan di build.yml (kode app = v40, belum diuji di device). -> Remaining: DAILY UPDATE, tunggu CI hijau, cek artifact debug muncul di run; user download (unzip), install, ambil logcat sesuai alur di v41. -> Next Action: minta user (a) konfirmasi APK debug terpasang (kalau "App not installed" = beda tanda tangan -> uninstall dulu) dan (b) kirim logcat + isi Log Diagnostik; jangan ubah kode app lagi sebelum evidence itu (v40 belum terverifikasi). Kandidat (butuh approval): `Log.i` di jalur servis persisten; kartu panduan "Agar jadwal tetap jalan".]

- v42 (user kirim `logcat_2026-09-28_09-28-12.txt` dari APK debug v41, tanpa pesan; docs-only, 0 kode diubah, kode = v41/v40, CHANGELOG.md tak diubah). Log: rekam mulai 09:26:07, berakhir 09:28:09 (baris 09-24 = 63 baris lama, diabaikan). Perangkat dari log: Transsion XOS 16.2 (`ro.tranos.version=xos16.2.0`) + MediaTek; uid app 10088; pembaca log `com.dp.logcatapp` uid 10455.
  BUKTI TERUKUR (kutipan rekaman):
  1. Proses A (pid 29542, hidup sebelum log mulai): buka dari Recents 09:26:09; 09:26:30 user buka Recents; 09:26:32.567 `tranpm/TranManualCleanMgr kill proc pid:29542` + `removeTask reason=remove-task` -> SIGKILL (`ApplicationExitInfo reason=2 status=9`). 09:26:32.641-.655 `ServicePolicy limitServiceRestartLocked limit: trdApp process` + `scheduleServiceRestartLocked skip ServiceRestart ... PersistentTrimService` + `TranRestartProcessFeature whiteList not contain`. Indikasi kuat servis masih tercatat berjalan saat di-kill & restart ditolak XOS. TAPI 0 rekaman notifikasi/FGS (id=42, `lagfix_keep_alive`, FGS start/stop, onNotify*) di 09:26:07-09:26:33 -> apakah proses A punya notifikasi TAK TERBUKTI (servis mulai sebelum jendela rekam).
  2. Proses B (pid 29114): cold start dari launcher 09:27:22.367; 09:27:22.552 `Background started FGS: Allowed ... cmp=PersistentTrimService ... uidState: TOP` (185 ms setelah proses dibuat = `LagFixApp.onCreate -> startIfEnabled`, v40); 09:27:23.422 event FGS start; 09:27:23.440 notifikasi id=42 channel `lagfix_keep_alive` flags `ONGOING_EVENT|FOREGROUND_SERVICE|SILENT` diterima NotificationService; 09:27:23.665 diteruskan ke listener; 09:27:23.76-.88 SystemUI merender ikon (`StatusBarIconView`). => notifikasi tampil ~1 dtk setelah app dibuka, tanpa penundaan ~10 dtk.
  3. 09:27:46.775 user buka Recents. 09:27:47.100 `FGS stop call` + event FGS stop (eventType 20) + notifikasi diposting ulang TANPA flag FOREGROUND_SERVICE — terjadi ~1,7 dtk SEBELUM SIGKILL; kode app tak memanggil stopForeground/stopSelf di jalur ini -> pemicunya TIDAK terlihat di log. 09:27:48.835 `TranManualCleanMgr kill proc pid:29114` + `remove with kill taskId:16266` (swipe) -> SIGKILL 09:27:48.847; 09:27:48.898-.909 skip ServiceRestart lagi; 09:27:48.923 notifikasi id=42 dihapus (`onNotifyRemoved`).
  4. `ForegroundServiceTypeLoggerModule` "does not have any types"/"has no types" muncul utk uid 10088 (09:27:23, 09:27:47) DAN uid 10455 (logcatapp, 09:26:52/58) -> logger ROM utk semua app, FGS kita tetap `Allowed` & notifikasi terposting; BUKAN bukti cacat manifest. (Koreksi v39: baris itu dulu dinilai milik app lain saja.)
  5. 0 hit: FATAL EXCEPTION, ForegroundServiceStartNotAllowed, `ditolak` (Log.w v40), MissingForegroundServiceType, RemoteServiceException -> tak ada crash.
  6. Log berhenti 09:28:09 (21 dtk setelah kill): jendela "~3 menit" TIDAK terekam; user swipe app ~25 dtk setelah notifikasi muncul.
  KESIMPULAN (dibatasi, P0 NO HALLUCINATION): (a) Fix v40 TERKONFIRMASI di device utk jalur cold start (servis distart otomatis, notifikasi tampil ~1 dtk) — bukti log. (b) Hilangnya notifikasi setelahnya disebabkan swipe dari Recents: XOS `TranManualCleanMgr` SIGKILL + menolak restart servis (trdApp non-whitelist) — perilaku OS, tak bisa dicegah kode app (SIGKILL tak memanggil callback). (c) TIDAK terjelaskan: siapa menghentikan FGS di 09:27:47.100 (sebelum SIGKILL) & apakah proses A punya notifikasi. (d) BELUM teruji: app+notifikasi bertahan >=3 menit TANPA swipe (cuma Home/layar mati) — log ini tak memuatnya.
  KANDIDAT (semua butuh approval, tak dikerjakan): (1) kartu panduan in-app "Agar jadwal tetap jalan": kunci app di Recents/izin autostart/jangan swipe — satu2nya tuas nyata thd `TranManualCleanMgr`. (2) `Log.i` di `onCreate`/`onStartCommand`/`onDestroy`/`onTaskRemoved` `PersistentTrimService` (+ stack trace di `onDestroy`) utk menjawab (c). (3) `ServiceCompat.startForeground(..., TYPE_SPECIAL_USE)` — prioritas rendah, tak ada bukti perlu (poin 4).
  UJI BERIKUTNYA (usulan): rekam logcat, buka app (notifikasi muncul), tekan HOME (JANGAN swipe), tunggu >=3 menit layar hidup lalu layar mati ~3 menit, cek notifikasi + kirim log. Terpisah: kunci app di Recents, lalu swipe/Clear-all, lihat apakah proses selamat.
- v42 VALIDASI: analisis atas 23.106 record log (parser format logcat reader), kutipan di atas dicocokkan ke rekaman; 0 kode/CI diubah. Perilaku app pasca-v40 dikonfirmasi lewat log device, BUKAN lewat build/CI.
- Batch: v42 (docs-only; APK terpasang tetap build v41, TIDAK perlu instal ulang)

[RESUME POINT: v42 — logcat debug v41 mengonfirmasi fix v40 (cold start -> servis+notifikasi ~1 dtk) & menunjukkan notifikasi hilang krn swipe-Recents (XOS TranManualCleanMgr SIGKILL + skip restart servis). Belum terjelaskan: siapa stop FGS 09:27:47.100; ketahanan >=3 menit tanpa swipe belum teruji. Kode = v41. -> Remaining: DAILY UPDATE (docs-only), user uji "Home tanpa swipe >=3 menit" + kirim logcat. -> Next Action: tanya user pilih (a) kartu panduan "Agar jadwal tetap jalan", (b) `Log.i` lifecycle servis, atau (c) tunggu hasil uji Home>=3 menit dulu; jangan ubah kode sebelum dipilih.]

- v43 (user kirim screenshot notifikasi persistent + keterangan "muncul ~2 menit, walaupun aplikasi sudah di-kill"; docs-only, 0 kode/CI diubah, kode = v41, CHANGELOG.md tak diubah).
  BUKTI (screenshot, bukan logcat): notifikasi milik `PersistentTrimService` (judul "LagFix (fstrim)", isi "Menjaga proses LagFix tetap berjalan untuk jadwal fstrim o..."), header usia "2 m". Usia itu = `Notification.when` = saat `buildNotification()` jalan di `onStartCommand` -> instance servis yg membuat notifikasi ini mulai ~2 menit sebelum screenshot.
  IMPLIKASI (dibatasi): v42 mencatat XOS menghapus notifikasi 32 ms setelah SIGKILL. Jadi notifikasi tampil padahal app "di-kill" hanya masuk akal kalau proses/servis DIHIDUPKAN LAGI setelah kill oleh pemicu selain user membuka Activity (lewat `LagFixApp.onCreate -> startIfEnabled`, v40). Ini MENGOREKSI asumsi v42 "notifikasi tak kembali otomatis setelah swipe" — masih bergantung pada pembacaan user (lihat AMBIGU).
  AMBIGU (tak bisa diputuskan dari gambar): (A) notifikasi muncul sendiri ~2 mnt SETELAH kill; (B) notifikasi sudah ada, usianya 2 mnt, dan tetap tampil setelah kill; (C) kill bukan swipe Recents. Pemicu revive TAK TERBUKTI. Kandidat dari kode (belum diverifikasi): SystemUI rebind `LagFixTileService`; broadcast widget `notifyChanged`/update (`updatePeriodMillis` 30 mnt); WorkManager periodik (interval jam + constraint charging/idle, jadi tak cocok utk ~2 mnt). Tak ada yg diubah.
  DATA PENENTU: logcat yg mencakup kill -> notifikasi tampil (cari `Start proc ... com.lagfix.fstrim`, `Background started FGS`, `pid:` baru, dan `for service`/`activityInfo` = pemicu di baris Start proc), + isi Log Diagnostik (`diag_persistent_service`).
- Batch: v43 (docs-only; APK terpasang tetap build v41, TIDAK perlu instal ulang)

[RESUME POINT: v43 — screenshot: notifikasi persistent tampil (usia 2 m) padahal app "di-kill"; pemicu revive proses belum diketahui, arti "muncul ~2 menit" ambigu (A/B/C). Kode = v41. -> Remaining: DAILY UPDATE (docs-only), user kirim logcat yg mencakup kill sampai notifikasi tampil + jawab A/B/C. -> Next Action: baca baris `Start proc com.lagfix.fstrim` utk pemicu; jangan ubah kode sebelum itu. Kandidat lama (butuh approval): kartu panduan "Agar jadwal tetap jalan", `Log.i` lifecycle servis.]

- v44 (permintaan user: "bikin custom interval yang diisi sendiri nilainya oleh user, biar gak kelamaan nunggu"). 4 file source: `Prefs.kt`, `TrimWorker.kt` (Scheduler), `MainViewModel.kt`, `MainActivity.kt` (+ README, CHANGELOG, PROJECT_STATE).
  PERUBAHAN: (1) `Prefs.intervalHours` diganti `Prefs.intervalMinutes` (kunci baru `intervalMin`; fallback baca kunci lama `interval` (jam) x 60 -> user existing tak kehilangan pilihan, 0 migrasi). (2) `Scheduler.MIN_INTERVAL_MINUTES = 15L` (batas periodik WorkManager); `Scheduler.apply()` pakai `PeriodicWorkRequestBuilder(..., TimeUnit.MINUTES)` dgn `coerceAtLeast(15)`. (3) `UiState.intervalHours` -> `intervalMinutes`; `setInterval(minutes)` clamp >= 15. (4) SettingsTab: chip preset tetap (jam x 60), + `OutlinedTextField` angka (filter digit, maks 6 digit, `rememberSaveable` tahan rotasi) + tombol "Terapkan" & `ImeAction.Done`; error "Minimal 15 menit" bila < 15; `supportingText` menampilkan interval aktif via `formatInterval()` (fungsi private baru). Pilih chip mengosongkan kolom kustom. (5) `imePadding()` ditambah di Column scroll utama (satu-satunya input teks di app; guard WindowInsets/IME).
  KEPUTUSAN JUJUR: interval < 15 menit SENGAJA ditolak, bukan disimulasikan. WorkManager periodik tak bisa < 15 mnt (diam2 dinaikkan). Cara lain (rantai OneTimeWork/AlarmManager/loop di FGS) = ubah mekanisme jadwal, butuh approval — tak dikerjakan. Constraint "Hanya saat mengisi daya" (default ON) & "idle" tetap menahan run walau interval pendek; utk uji cepat matikan charging/idle. Run periodik pertama tak dijamin tepat di menit ke-N (WorkManager/Doze/XOS).
  0 perubahan: Manifest, dependency, TrimWorker.doWork, FstrimExecutor, PersistentTrimService, CI.
- v44 VALIDASI: keseimbangan () {} tiap file OK (delta MainActivity +37/+37, +15/+15; ketidakseimbangan mentah di file itu sudah ada sejak v39 = artefak pemeriksa kasar); 0 sisa `intervalHours` di src; import baru dicek (OutlinedTextField, KeyboardOptions/Actions, KeyboardType, ImeAction, LocalFocusManager, imePadding); BOM Compose 2024.10.01 mendukung API yg dipakai. 0 kompilasi (sandbox tanpa Android SDK/Gradle) -> CI. Behavior BELUM diuji di device.
- Batch: v44

[RESUME POINT: v44 — interval kustom (menit, min 15) di Pengaturan > Jadwal; chip preset tetap; kode belum dikompilasi/diuji di device. -> Remaining: DAILY UPDATE, tunggu CI hijau, install, uji: ketik 15 -> Terapkan -> "Interval aktif: 15 menit", rotasi layar (teks bertahan), keyboard tak menutupi kolom, matikan charging/idle utk uji cepat. -> Next Action: bila user ingin < 15 menit, minta approval mekanisme lain (OneTimeWork berantai/AlarmManager). Tertunda dari v43: arti "muncul ~2 menit" (A/B/C) + logcat kill->notifikasi (`Start proc com.lagfix.fstrim`).]

- v45 (permintaan user, respons atas screenshot v44: "interval 15 menit tercatat melakukan trim dengan nyata, cuman informasinya kurang jelas ditampilkan (mis. ditaruh di widget, grafik statistik yang malah hanya mencatat yang dilakukan manual only, dll)"). 3 file: `MainActivity.kt`, `LagFixWidgetProvider.kt`, `res/values/strings.xml` (+ CHANGELOG, PROJECT_STATE).
  KLARIFIKASI JUJUR (dicek di kode SEBELUM edit, P0 NO HALLUCINATION): premis "grafik hanya mencatat manual only" TIDAK didukung kode — `StatsCard(ui.log)` & `parseRunStats()` sudah memproses SEMUA baris riwayat tanpa filter trigger sejak v37/v38; di screenshot v44 sendiri, batang tertinggi (12,0 detik, paling kanan) adalah entri "(Otomatis)". Kesimpulan: bukan bug data, tapi TAMPILAN tak membedakan Otomatis/Manual secara visual -> user tak bisa MEMVERIFIKASI dari grafik/kartu itu sendiri. Fix diarahkan ke situ, BUKAN mengubah logic penghitungan (yg sudah benar).
  PERUBAHAN: (1) `MainActivity.kt`: `ParsedLog`/`parseLogLine`/`formatInterval` diubah `private` -> `internal` (dipakai lintas-file, 0 perubahan perilaku). `RunStat` +field `trigger`. Kartu Statistik: baris ringkasan kini +"(N otomatis · M manual)" bila ada baris riwayat dgn token trigger (baris lama pra-v38 dikecualikan dari rincian, tak diberi angka 0 palsu). `RunHistoryChart`: titik kecil di atas batang utk entri trigger Manual — HANYA digambar kalau ada >=1 entri Manual di jendela 30 itu (mayoritas user berjadwal murni-otomatis -> 0 perubahan visual). `StatsLegend`: keterangan "• titik = dipicu manual", tampil bersyarat sama. (2) `LagFixWidgetProvider.kt`: `friendlyStatus()` kini 2 baris (layout `maxLines="2"` SUDAH mengantisipasi ini sejak versi lama, 0 ubah XML) — baris 1 +" (Otomatis)"/" (Manual)" bila baris log riwayat py token trigger; baris 2 = interval aktif via `formatInterval(prefs.intervalMinutes)` (string baru `widget_interval_line`, "Tiap %1$s"). (3) `strings.xml`: +3 string (`widget_interval_line`, `widget_status_ok_trigger`, `widget_status_fail_trigger`); string lama TIDAK dihapus (dipakai jalur fallback baris log lama tanpa trigger).
  0 perubahan: skema data Prefs/log, Scheduler/TrimWorker/interval v44, LagFixTileService (subtitle QS tile TETAP baris log mentah — user hanya sebut "widget", tile beda mekanisme & tak disebut, sengaja tak disentuh biar tak scope creep), Manifest, dependency.
- v45 VALIDASI: XML `strings.xml` parse OK; keseimbangan () {} per-file: `LagFixWidgetProvider.kt` seimbang; `MainActivity.kt` delta vs v44 = +14/+14 `()`, +7/+7 `{}` (SEIMBANG persis) — sisa ketidakseimbangan mentah 1 di file itu SAMA sejak v39 (artefak pemeriksa regex kasar utk string/komentar kompleks, BUKAN dari v44/v45); referensi silang dicek manual (`formatInterval`, `parseLogLine` dipanggil widget, deklarasi `internal` cocok; 1 situs constructor `RunStat` & 1 call site `StatsLegend` cocok jumlah parameter baru). 0 kompilasi (sandbox tanpa SDK/Gradle) -> CI. Behavior (widget 2 baris tak terpotong, titik grafik, dll) BELUM diuji di device.
- Batch: v45

[RESUME POINT: v45 — widget kini 2 baris (status+trigger, interval aktif); Statistik: ringkasan +rincian Otomatis/Manual, grafik +titik utk batang Manual (bersyarat ada campuran), legenda +keterangan. Kode interval = v44 (tak diubah). -> Remaining: DAILY UPDATE, tunggu CI hijau, install, cek widget (2 baris tak terpotong di berbagai ukuran widget/font), cek Statistik (rincian angka & titik manual muncul benar saat ada run Manual+Otomatis campur). -> Next Action: kalau user masih anggap grafik "salah hitung" setelah lihat rincian baru, minta screenshot lagi — evidence kode sudah dicatat di sini supaya tak diulang analisis. Tertunda dari v43: arti "muncul ~2 menit" (A/B/C) + logcat kill->notifikasi.]

- v46 (permintaan user, respons atas penjelasan root-cause: "Berarti fitur Hanya saat mengisi daya/perangkat idle itu gimmick karena gak pernah tercatat dengan jelas oleh sistem dong?!!" -> user setuju ditambah indikator, minta "jangan setengah-setengah ... totalitas"). 4 file source: `Prefs.kt`, `MainViewModel.kt`, `MainActivity.kt`, `LagFixWidgetProvider.kt` (+ CHANGELOG, PROJECT_STATE).
  ROOT CAUSE (dijelaskan ke user sebelum coding, terverifikasi di kode): BUKAN gimmick — `Constraints.Builder().setRequiresCharging()/.setRequiresDeviceIdle()` adalah API asli WorkManager. Tapi `TrimWorker.doWork()` (satu2nya tempat `Prefs.record()` dipanggil) baru dieksekusi WorkManager SETELAH constraint terpenuhi -> selama ditahan, 0 kode kita yang jalan -> literally tak ada apa pun utk dicatat/ditampilkan sebelum v46. WorkManager juga TIDAK expose API publik "alasan spesifik blokir" (cuma state ENQUEUED/RUNNING/dst, tak bisa dibedakan "nunggu waktu" vs "nunggu constraint").
  FIX (bukan menebak dari WorkManager — baca kondisi sistem SAAT INI langsung & bandingkan ke toggle): `Prefs.kt` (+5 fungsi/tipe baru, `internal`, SATU sumber logic dipakai app & widget, 0 duplikasi): `isCurrentlyCharging()` (`BatteryManager.isCharging`), `isCurrentlyDeviceIdle()` (`PowerManager.isDeviceIdleMode`), `ScheduleWait` (data class), `computeScheduleWait()` (0/false kalau `Prefs.enabled` mati, cocok logic `Scheduler.apply()`), `scheduleWaitLabel()` (null kalau tak menunggu). `MainViewModel.kt`: `UiState.scheduleWaitLabel` dihitung tiap `read()` (SENGAJA pola cek-saat-refresh, KONSISTEN dgn `isBatteryUnrestricted()` yg sudah ada — refresh via onResume/event Shizuku, bukan listener baru; ini pola established project, bukan "setengah2"). `MainActivity.kt`: ditampilkan di DUA tempat — kartu Riwayat (tab Utama, tempat pertama user lihat) DAN tab Pengaturan tepat di sebelah toggle constraint yg relevan (warna `skippedAmber`, konsisten dgn semantik "Dilewati" yg sudah ada); tab Pengaturan +teks konfirmasi positif "Syarat terpenuhi — jadwal akan jalan sesuai interval." saat toggle ON tapi TAK sedang menunggu (bukti positif fitur bekerja, bukan cuma diam). `LagFixWidgetProvider.kt`: baris ke-2 widget (`maxLines="2"`, v45, TETAP tak diubah) memprioritaskan "Menunggu: ..." saat ada, fallback ke interval (v45) saat tidak — 0 baris ke-3 -> 0 risiko UI Truncation guard.
  CAKUPAN "TOTALITAS" (permintaan eksplisit user): app (2 tab) + widget = SEMUA tempat status pernah ditampilkan sebelumnya (v45) kini ikut fitur ini, bukan cuma satu sudut. TIDAK disentuh (di luar cakupan "tercatat oleh sistem" + guard file/scope): QS tile subtitle (mekanisme beda, tak disebut user), `PersistentTrimService` notification text.
  0 permission baru: `BatteryManager.isCharging`/`PowerManager.isDeviceIdleMode` = baca state, tanpa `<uses-permission>` (dicek Manifest, 0 diubah). 0 perubahan skema Prefs/data lama, 0 perubahan Scheduler/TrimWorker/constraint-nya sendiri (murni observability, bukan ubah logic constraint).
- v46 VALIDASI: keseimbangan () {} per-file OK (`Prefs.kt`/`MainViewModel.kt`/`LagFixWidgetProvider.kt` seimbang persis; `MainActivity.kt` delta vs v45 = +7/+7 `()`, +3/+3 `{}` SEIMBANG, offset mentah 1 di file itu = sama sejak v39, sudah dicatat v45); semua situs pemanggilan fungsi baru dicek cocok (Prefs.kt deklarasi <-> MainViewModel.kt & LagFixWidgetProvider.kt pemanggil); Manifest dicek 0 permission baru dibutuhkan. 0 kompilasi (sandbox tanpa SDK/Gradle) -> CI. Behavior (label muncul/hilang sesuai kondisi charging/idle nyata) BELUM diuji di device — device uji sebelumnya screenshot v44 (unplugged/state tak diketahui saat itu).
- Batch: v46

[RESUME POINT: v46 — indikator "Menunggu: mengisi daya/perangkat idle" (live, dicek sistem tiap refresh) kini di kartu Riwayat, tab Pengaturan, DAN widget; teks positif "Syarat terpenuhi" saat toggle ON tapi tak menunggu. Constraint/Scheduler sendiri tak diubah. -> Remaining: DAILY UPDATE, tunggu CI hijau, install, uji: cabut charger dgn toggle "Hanya saat mengisi daya" ON -> label "Menunggu: mengisi daya" harus muncul di 3 tempat; colok lagi -> label hilang / ganti "Syarat terpenuhi". -> Next Action: minta user konfirmasi label muncul/hilang sesuai colok-cabut charger; kalau PowerManager.isDeviceIdleMode() susah diuji manual (butuh nunggu Doze asli), boleh cek toggle idle-nya OFF dulu saat uji charging. Tertunda dari v43: arti "muncul ~2 menit" (A/B/C) + logcat kill->notifikasi.]

- v47 (permintaan user: "Hilangkan opsi hanya saat mengecas & saat idle. Karena ... interval dan manual aja sudah cukup banget"). 5 file source: `TrimWorker.kt` (Scheduler), `Prefs.kt`, `MainViewModel.kt`, `MainActivity.kt`, `LagFixWidgetProvider.kt` (+ README, CHANGELOG, PROJECT_STATE). `LagFixApp.kt` SENGAJA tak disentuh (batas 5 file).
  DIHAPUS: kedua `ToggleRow` di SettingsTab; `UiState.requireCharging/requireIdle/scheduleWaitLabel`; `MainViewModel.setCharging/setIdle`; `Prefs.requireCharging/requireIdle`; seluruh helper v46 (`isCurrentlyCharging`, `isCurrentlyDeviceIdle`, `ScheduleWait`, `computeScheduleWait`, `scheduleWaitLabel`) + import `BatteryManager`/`PowerManager` di Prefs.kt; label "Menunggu" di kartu Riwayat & Pengaturan; widget baris ke-2 kembali = interval saja (perilaku v45). `Scheduler.apply()`: `setRequiresCharging`/`setRequiresDeviceIdle` dibuang.
  DITAHAN (disengaja, di luar permintaan): `setRequiresBatteryNotLow(true)` — constraint ke-3 yg SELALU aktif, bukan opsi user. Masih menunda run terjadwal saat baterai rendah (ambang OS). Dicatat di CHANGELOG & dilaporkan ke user; hapus hanya kalau user minta.
  ROOT RISK yg ditangani (BUKAN sekadar hapus UI): `Prefs.requireCharging` default TRUE & `Scheduler.apply()` TIDAK dipanggil saat startup (cuma dari setter UI `reschedule()`) -> user existing yg jadwalnya sudah ter-enqueue di WorkManager membawa constraint charging yg PERSISTEN; hanya membuang constraint di `apply()` akan meninggalkan gate tersembunyi tanpa UI utk mematikannya (lebih buruk dari v46). Fix: `Prefs.constraintsDropped` (flag 1x) + `MainViewModel.init` memanggil `Scheduler.apply()` SEKALI (`ExistingPeriodicWorkPolicy.UPDATE`, sudah dipakai sejak lama) lalu `markConstraintsDropped()` (sekalian hapus kunci lama `charging`/`idle`). `apply()` saat `enabled=false` = `cancelUniqueWork` -> no-op aman utk install baru (default enabled=false).
  BATAS (P0 NO HALLUCINATION): migrasi jalan saat UI app dibuka pertama kali pasca-update (bukan saat proses background); user yg tak pernah membuka app pasca-update tetap membawa constraint lama sampai saat itu. Perilaku `UPDATE` terhadap waktu run berikutnya (apakah bergeser) TIDAK diverifikasi di sandbox (tanpa SDK/WorkManager) — diasumsikan boleh bergeser; 1x saja.
- v47 VALIDASI: keseimbangan () {} delta vs v46 per file SEIMBANG (Prefs -23/-23 & -5/-5, MainViewModel -3/-3 & -1/-1, MainActivity -15/-15 & -5/-5, Widget -2/-2, TrimWorker -2/-2); grep 0 sisa `requireCharging|requireIdle|setCharging|setIdle|scheduleWait|computeScheduleWait|isCurrently*` (satu2nya `PowerManager` tersisa = `isBatteryUnrestricted()` lama); 0 import duplikat; import `Constraints` masih terpakai. 0 kompilasi (sandbox tanpa SDK/Gradle) -> CI. Behavior BELUM diuji di device.
- Batch: v47

[RESUME POINT: v47 — opsi charging/idle + indikator v46 dihapus total; constraint lama dibersihkan lewat re-enqueue 1x (`Prefs.constraintsDropped`, `MainViewModel.init`); `BatteryNotLow` masih aktif. -> Remaining: DAILY UPDATE, tunggu CI hijau, install, buka app 1x, uji: tab Pengaturan tanpa 2 toggle; jadwal 15 menit tetap jalan walau TIDAK dicolok charger. -> Next Action: tanya user apakah `BatteryNotLow` juga mau dihapus bila run terjadwal masih terlewat saat baterai rendah. Tertunda dari v43: arti "muncul ~2 menit" (A/B/C) + logcat kill->notifikasi.]

- v48 (permintaan user: "Hapus batasan baterai rendah, pokoknya totalitas hapus nya. Gak usah pakai jalur backup segala macem, karena user nya saya doang"). 3 file source: `TrimWorker.kt` (Scheduler), `Prefs.kt`, `MainViewModel.kt` (+ CHANGELOG, PROJECT_STATE).
  DIHAPUS: `setRequiresBatteryNotLow(true)` + seluruh `Constraints.Builder` & import `androidx.work.Constraints` -> `PeriodicWorkRequestBuilder` tanpa `setConstraints()` = murni interval, 0 constraint tersisa. "Jalur backup" (= migrasi 1x v47) DICABUT sesuai perintah: `Prefs.constraintsDropped`/`markConstraintsDropped()` dan blok `if (!prefs.constraintsDropped)` di `MainViewModel.init`. Kunci SharedPreferences lama `charging`/`idle` jadi yatim (tak dibaca siapa pun, tak berbahaya; tak dibersihkan karena jalur pembersihnya ikut dicabut).
  KONSEKUENSI DICATAT (P0 NO HALLUCINATION, sudah dilaporkan ke user): `Scheduler.apply()` tak dipanggil saat startup -> jadwal periodik yang SUDAH ter-enqueue di WorkManager (dari versi lama) MASIH membawa constraint lamanya (minimal BatteryNotLow) sampai `apply()` dipanggil ulang. Tanpa migrasi, user harus 1x: matikan-nyalakan "Jadwal otomatis" ATAU tekan "Terapkan"/chip interval (keduanya -> `reschedule()` -> `apply()`, UPDATE). Keputusan user; bukan bug.
  CHANGELOG: entri v40-v47 yg sebelumnya saya append di DASAR file (melanggar aturan Descending Changelog) dipindah ke ATAS (v48, v47, v46, v45, v44, v40) di bawah judul; isi v1-v38 lama TIDAK diubah (bukan milik saya, bukan structural rewrite). Entri v47 lama tetap apa adanya sbg riwayat (klaim "menyegarkan jadwal 1x" & catatan baterai di sana sudah digantikan v48).
- v48 VALIDASI: keseimbangan () {} delta vs v47 per file SEIMBANG (TrimWorker -4/-4, Prefs -8/-8 & -1/-1, MainViewModel -4/-4 & -1/-1); grep 0 sisa `BatteryNotLow|constraintsDropped|markConstraintsDropped|Constraints|setConstraints` di src (kecuali 1 komentar); 0 import duplikat. 0 kompilasi (sandbox tanpa SDK/Gradle) -> CI. Behavior BELUM diuji di device.
- Batch: v48

[RESUME POINT: v48 — semua constraint jadwal dihapus (charging, idle, baterai-rendah); migrasi 1x dicabut; CHANGELOG diurutkan descending. -> Remaining: DAILY UPDATE, tunggu CI hijau, install, LALU matikan-nyalakan "Jadwal otomatis" 1x (atau Terapkan interval) agar jadwal lama bersih dari constraint; uji jadwal jalan walau baterai rendah. -> Next Action: tunggu laporan user. Tertunda dari v43: arti "muncul ~2 menit" (A/B/C) + logcat kill->notifikasi.]

- v49 (user memilih lewat pertanyaan pilihan: milestone 3 "Log.i lifecycle servis" + 4 "Kartu panduan jadwal"; pilihan lain D2 R8 & F2 Dependabot TIDAK dipilih, tak dikerjakan). 2 file source: `PersistentTrimService.kt`, `MainActivity.kt` (+ CHANGELOG, PROJECT_STATE). Catatan: "milestone" TIDAK didefinisikan di PENDING_ROADMAP/PROJECT_STATE/README (dicek grep) -> ditanyakan ke user, bukan ditebak.
  (3) LOG LIFECYCLE di `PersistentTrimService.kt` (tag `PersistentTrimService`, kata kunci `LIFECYCLE`; 7 baris log): `onCreate` (pid, `procAge`), `onStartCommand` (pid, procAge, startId, flags, `intentNull`, hasil startForeground; dicatat SETELAH `startForeground()` agar aturan v35 tetap utuh), `onDestroy` (pid, `serviceUptime`), `start()`, `startIfEnabled()` (toggleOn, procAge; tercatat tiap proses lahir walau toggle OFF = penanda kelahiran proses), `stop()` (dipanggil kode app). `procAge` = `SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()` (API 24+, minSdk 26). SENGAJA tanpa override `onTaskRemoved()` (manifest tak set `stopWithTask="false"` -> sistem menghentikan servis langsung & callback itu tak dipanggil = dead code) dan tanpa stack trace di `onDestroy` (dipanggil looper utama, stack-nya selalu sama, tak menunjukkan siapa yg meminta stop). Tak menulis file diagnostik baru (satu file per panggilan di MediaStore, berisik & tak selesai saat proses di-kill). 0 perubahan perilaku.
  CARA BACA LOG (utk uji berikut, pakai APK debug jalur v41 + logcat reader, filter `PersistentTrimService`): `intentNull=true` di onStartCommand = servis dihidupkan ulang SISTEM (sticky); `false` = distart kode app. `onDestroy` TANPA baris `stop() dipanggil dari kode app` tepat sebelumnya = servis dihentikan SISTEM/OEM (menjawab pertanyaan terbuka v42 sebagian: apakah FGS stop 09:27:47.100 berasal dari sistem; TETAP tak menunjukkan siapa di dalam sistem). `startIfEnabled ... procAge` kecil + `onCreate procAge` kecil = servis lahir bersama proses baru (revive lewat cold start). Kalau `onDestroy` TAK muncul sama sekali sebelum proses hilang = proses di-SIGKILL tanpa callback.
  (4) KARTU "Agar jadwal tetap jalan" di SettingsTab (antara kartu Jadwal & Keandalan): 3 poin + disclaimer "Tidak ada jaminan 100%" + tombol "Buka Info Aplikasi LagFix" (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`, `runCatching`, konvensi sama dgn tombol baterai; 0 intent khusus merek sesuai alasan v24). Paragraf teks Autostart lama di kartu Jadwal DIHAPUS (dipindah ke poin 3, bukan duplikat). Tingkat bukti TIAP poin: (1) jangan geser Recents = TERBUKTI di log HP ini (v42: `TranManualCleanMgr kill proc` + `skip ServiceRestart`); (2) Kunci di Recents = praktik umum ROM, BELUM diverifikasi di HP ini & teks sengaja menyebut menggeser satu kartu tetap bisa mematikan; (3) Autostart = sama seperti teks lama v24 (belum diverifikasi efeknya). Teks kartu "Keandalan" yg bilang "opsi baterai & Autostart di atas" tetap benar (kartu baru ada di atasnya).
- v49 VALIDASI: keseimbangan () {} delta vs v48 SEIMBANG (Service +12/+12 & +1/+1, MainActivity +18/+18 & +5/+5); 0 import duplikat; `import android.provider.Settings` ditambah (sebelumnya tak ada), `Uri`/`Intent`/`TextButton` sudah ada; 7 baris LIFECYCLE, 1 definisi `procAgeMs`. 0 kompilasi (sandbox tanpa SDK/Gradle) -> CI. Behavior (log muncul, kartu tak terpotong, tombol membuka Info Aplikasi) BELUM diuji di device.
- Batch: v49

[RESUME POINT: v49 — log LIFECYCLE di servis persisten + kartu "Agar jadwal tetap jalan" di Pengaturan (paragraf Autostart lama dipindah ke sana). Kode constraint/interval = v48 (tak diubah). -> Remaining: DAILY UPDATE, tunggu CI hijau, install (APK debug jalur v41 utk logcat), uji kartu: terbaca utuh, tombol membuka Info Aplikasi. Uji log: toggle "Layanan latar depan persisten" ON -> rekam logcat filter `PersistentTrimService` -> tekan Home, tunggu; lalu swipe Recents -> buka lagi; kirim logcat. -> Next Action: baca urutan baris LIFECYCLE (onDestroy vs stop(), intentNull, procAge) utk menutup pertanyaan terbuka v42 (siapa stop FGS sebelum SIGKILL) & v43 (A/B/C revive); jangan ubah kode servis sebelum ada evidence itu. Backlog roadmap tak dipilih: D2 (R8, berisiko), F2 (Dependabot; catatan: PR Dependabot bertabrakan dgn aturan ZIP=SoT, bump akan tertimpa ZIP berikutnya).]

- v50 (user kirim `LagFix_diag_persistent_service_20261001_073121.txt` + ZIP v49, pesan: "Tinggalkan file obsolete, fokus ke file yang saya lampirkan"; docs-only, 0 kode/CI diubah, kode = v49, CHANGELOG.md tak diubah). ZIP v49 = source of truth; jalur sandbox lama (v16-v18 di luar lineage repo ini) DIABAIKAN sesuai permintaan user, tidak dipakai.
  ISI FILE (5 baris, penulis = `PersistentTrimService.onStartCommand()` -> `CrashLogger.logDiagnostic`): SDK 36; Device INFINIX Infinix X6855; `areNotificationsEnabled() = true`; `channel importance (getNotificationChannelCompat) = 4`; `startForeground() SUKSES tanpa exception`.
  TEMUAN TERUKUR: (1) Notifikasi app TIDAK diblokir (izin aktif) dan `startForeground()` sukses -> hipotesis lama v27/v28 "notifikasi servis tak muncul krn izin/channel/startForeground gagal" TIDAK didukung evidence ini. (2) Importance channel terbaca 4 (IMPORTANCE_HIGH), padahal kode `onCreate()` membuat channel `lagfix_keep_alive` dgn `IMPORTANCE_LOW` (2). Android tak mengizinkan app menaikkan importance channel yg sudah ada -> nilai 4 berasal dari perubahan di luar kode (pengaturan user atau ROM). Penyebab pastinya TIDAK TERBUKTI dari file ini. Dampak belum diuji (notifikasi memakai `setSilent(true)`, jadi suara kemungkinan tetap mati; ada/tidaknya banner heads-up tak terekam). (3) File tidak memuat versi build, pemicu start (toggle vs cold start), maupun baris LIFECYCLE (itu di logcat, bukan di file ini).
  YANG TIDAK DIJAWAB file ini (tetap terbuka): v42(c) siapa menghentikan FGS ~1,7 dtk sebelum SIGKILL; v43 arti "muncul ~2 menit" (A/B/C) & pemicu revive proses.
  KEPUTUSAN: kode servis TIDAK diubah (aturan RESUME POINT v49: tunggu evidence LIFECYCLE). Tidak ada "perbaikan" importance krn tak ada bukti itu bermasalah.
- v50 VALIDASI: isi file dicocokkan baris-per-baris ke kode penulis (`PersistentTrimService.kt` preCheck/outcome + `CrashLogger.logDiagnostic` header SDK/Device) -> format identik; importance LOW di `onCreate()` dicek langsung di source. 0 kompilasi/CI (docs-only). Behavior device tak diuji ulang.
- Batch: v50 (docs-only; APK terpasang tetap build v49, TIDAK perlu instal ulang)

[RESUME POINT: v50 — diag persistent_service: notifikasi aktif, channel importance 4 (kode buat LOW=2 -> diubah user/ROM, sebab belum terbukti), startForeground sukses; hipotesis "notif diblokir" gugur. Kode = v49 (tak diubah). -> Remaining: DAILY UPDATE (docs-only), lalu uji logcat v49 yg belum ada: toggle "Layanan latar depan persisten" ON -> rekam logcat filter `PersistentTrimService` -> Home, tunggu; lalu swipe Recents -> buka lagi; kirim logcat. -> Next Action: baca urutan baris LIFECYCLE (onDestroy vs stop(), intentNull, procAge) utk menutup v42(c) & v43 (A/B/C); jangan ubah kode servis sebelum evidence itu. Backlog tak dipilih: D2 (R8, berisiko), F2 (Dependabot).]

- v51 (permintaan user: "Ubah directory log agar embedded ke folder document, biar gak menuhin folder download saya"). 2 file source: `CrashLogger.kt`, `MainActivity.kt` (+ README, CHANGELOG, PROJECT_STATE). Kode lain = v49.
  1. `CrashLogger.writeViaMediaStore()`: RELATIVE_PATH `Download/LagFix/` -> `Documents/LagFix/` (`Environment.DIRECTORY_DOCUMENTS`); insert ke koleksi `MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY)` lewat helper baru `filesCollection()` (`MediaStore.Downloads` hanya utk folder Download). Siklus IS_PENDING, pelemparan exception eksplisit, dan fallback app files dir TIDAK diubah. Semua jalur tulis (crash, `logDiagnostic`, `testWrite`) lewat `writeToFile()`, jadi ikut pindah.
  2. `CrashLogger.listLogs()`: query ke koleksi Files (bukan Downloads), kolom RELATIVE_PATH ditambah ke projection; label sumber = folder asli baris itu. Filter `%LagFix%` + `LagFix_%.txt` tetap, jadi log baru (Documents/LagFix) DAN lama (Download/LagFix) sama2 terbaca.
  3. `MainActivity.kt`: 2 teks UI (deskripsi kartu Log Diagnostik; pesan kosong menyebut Documents/LagFix + Download/LagFix lama).
  KEPUTUSAN SCOPE: file lama di Download/LagFix TIDAK dipindah/dihapus otomatis (migrasi = fitur tambahan tak diminta; user bisa hapus manual). Komentar historis (KDoc v30, `PersistentTrimService` v29) sengaja tak diubah.
- v51 VALIDASI: keseimbangan () {} delta vs v49: CrashLogger +13/+13 `()` & +0/+0 `{}`, MainActivity +0/+0; import `Environment`/`ContentUris` masih terpakai; 0 sisa `MediaStore.Downloads` di kode aktif. Asumsi yg BELUM teruji: insert text/plain ke `Documents/` lewat koleksi Files diterima MediaProvider di XOS (Infinix X6855, SDK 36) — kalau ditolak, `writeToFile()` jatuh ke app files dir (bukan diam) dan `testWrite()` melapor. 0 kompilasi (sandbox tanpa Android SDK/Gradle) -> CI. Behavior BELUM diuji di device.
- Docs: `CHANGELOG.md` +entry v51 (user-facing), `README.md` baris Crash log diperbarui.
- Batch: v51

[RESUME POINT: v51 — log crash/diagnostik kini ke Documents/LagFix (koleksi MediaStore.Files), pembaca di Pengaturan > Log Diagnostik membaca Documents + Download lama. Kode servis persisten = v49 (tak diubah). -> Remaining: DAILY UPDATE, tunggu CI hijau, install, tekan "Tes tulis log" di Pengaturan > Log Diagnostik: harus "BERHASIL" dan file muncul di Documents/LagFix (bukan Download). Lalu lanjut uji logcat LIFECYCLE v49 (toggle persisten ON -> filter `PersistentTrimService` -> Home, tunggu; swipe Recents -> buka lagi; kirim logcat). -> Next Action: kalau "Tes tulis" GAGAL, kirim persis pesan errornya (jalur Files/Documents ditolak ROM); kalau berhasil, baca baris LIFECYCLE utk menutup v42(c) & v43 (A/B/C); jangan ubah kode servis sebelum evidence itu. Backlog tak dipilih: D2 (R8), F2 (Dependabot).]

- v52 (user konfirmasi log v51 kini masuk Documents; pilih milestone "1+4" = F1 (lint-results-debug.html tak muncul di artifact) + servis persisten MENUNGGU logcat LIFECYCLE v49 dari user, jadi kode servis TIDAK disentuh). 1 file source/CI: `.github/workflows/build.yml` (+ README, PENDING_ROADMAP, PROJECT_STATE). 0 kode app diubah, CHANGELOG tak diubah (bukan perubahan user-facing).
  FAKTA (dari catatan v18 di `docs/archive/PROJECT_STATE_v2-v24.md`): run CI #20 -> artifact cuma `detekt/detekt.html`, tanpa `lint-results-debug.html`; path `app/build/reports/lint-results-debug.html` = default AGP (bukan salah path). Log mentah step lint TIDAK pernah diunggah (`lint_detekt_output.log` hanya ditulis `tee`, tak ada di `path:` upload) -> penyebab tak bisa dibaca, hanya ditebak.
  INFERENSI (BELUM terbukti): `detekt.html` ada = detekt sempat jalan; command lama `gradle lintDebug detekt` tanpa `--continue` berhenti di task gagal pertama menurut urutan baris perintah -> hipotesis (b) v18 ("Gradle berhenti sebelum lint") kurang cocok; penyebab sebenarnya butuh log.
  PERUBAHAN `build.yml`: (1) step "Lint & detekt" dipecah jadi "Lint (non-blocking)" (`gradle lintDebug` -> `lint_output.log`) dan "Detekt (non-blocking)" (`gradle detekt` -> `detekt_output.log`), keduanya tetap `continue-on-error: true`; (2) step baru "Kumpulkan laporan lint & detekt" (`if: always()`) menyalin ke `lint_artifacts/`: kedua log mentah, `app/build/reports/detekt`, SEMUA file `lint-results*` di mana pun di `app/build` (`cp --parents`), `lint_files_found.txt` (nama semua file ber-"lint"), `reports_listing.txt` (`ls -laR app/build/reports`); (3) upload artifact `LagFix-lint-detekt-report-<run>` kini `path: lint_artifacts/`. Step Unit test/Build/Release/debug-pathway TIDAK disentuh.
  CARA BACA HASIL: buka `lint_output.log` -> cari baris "Wrote HTML report to ..." (lokasi asli) atau stack trace lint; `lint_files_found.txt` menunjukkan file lint apa pun yang ada; kalau `lint-results*` ada di path lain, sesuaikan di sini. Format artifact bertambah (dulu hanya `detekt/detekt.html`).
- v52 VALIDASI: YAML `safe_load` OK; `bash -n` OK untuk 3 blok `run` baru; blok "Kumpulkan" disimulasikan lokal pada struktur tiruan (file `lint-results*` di intermediates ikut tersalin, `detekt.html` tersalin, tanpa error walau file lain tak ada). 0 jalan Gradle/CI (sandbox tanpa SDK/jaringan) -> perilaku di runner BELUM terbukti. Nilai/outcome lint sendiri tak dijamin berubah: batch ini hanya menambah bukti.
- Docs: `README.md` Pathway CI +1 baris artifact lint/detekt; `PENDING_ROADMAP.md` F1 +catatan v52.
- Batch: v52

[RESUME POINT: v52 — F1 (akar masalah `lint-results-debug.html` hilang): `build.yml` kini memisah step Lint & Detekt dan mengunggah log mentah + semua file `lint-results*` + listing (artifact `LagFix-lint-detekt-report-<run>`); kode app = v51 (tak diubah); penyebab lint TETAP belum diketahui, menunggu log. -> Remaining: DAILY UPDATE, tunggu CI jalan, unduh artifact `LagFix-lint-detekt-report-<run>`, kirim isi `lint_output.log` (atau hanya baris "Wrote HTML report"/stack trace) + `lint_files_found.txt`. Paralel (user-initiated): uji v51 "Tes tulis log" (harus BERHASIL, file di Documents/LagFix) lalu logcat LIFECYCLE v49 (toggle persisten ON -> filter `PersistentTrimService` -> Home, tunggu; swipe Recents -> buka lagi). -> Next Action: baca log lint -> putuskan fix minimum (mis. path upload / config lint) HANYA setelah penyebab terbaca; jangan ubah kode servis sebelum logcat LIFECYCLE. Backlog tak dipilih: D2 (R8), F2 (Dependabot). Temuan detekt 91 (run #20) tetap backlog, bukan bagian F1.]

- v53 (user tanya: "Kenapa notifikasi persistent hanya aktif jika sudah melewati 1x interval pasca aplikasi di-swipe/di-kill. Kenapa tidak aktif ASAP?"; user juga melampirkan ZIP v52 + `LagFix-lint-detekt-report-47.zip` — laporan lint/detekt itu TIDAK diproses di batch ini karena di luar pertanyaan; docs-only, 0 kode/CI diubah, kode app = v51/v52, CHANGELOG.md tak diubah).
  ANALISIS KODE (dibaca langsung dari ZIP v52):
  1. Satu-satunya re-entry servis setelah proses mati = `PersistentTrimService.startIfEnabled()`, dipanggil HANYA dari `LagFixApp.onCreate()` (LagFixApp.kt:9). `start()` lain cuma dari toggle (`MainViewModel.setPersistentService`, baris 110) & `BootReceiver`. Servis `START_STICKY`, tanpa `onTaskRemoved`/`stopWithTask`.
  2. Setelah swipe-Recents: bukti v42 = XOS `TranManualCleanMgr` SIGKILL + `skip ServiceRestart` (trdApp non-whitelist) -> `START_STICKY` tak berlaku & SIGKILL tanpa callback -> TIDAK ADA kode app yang jalan sampai ada pemicu SISTEM lain yang melahirkan proses baru. Ini perilaku OS, bukan cacat baris kode.
  3. Pemicu proses yang ada di kode: (a) WorkManager periodik `Scheduler.apply()` (TrimWorker.kt:87-100) — periode = `Prefs.intervalMinutes` pilihan user (min 15 mnt, default 24 jam; tanpa constraint sejak v48); (b) widget `updatePeriodMillis=1800000` (30 mnt, hanya jika widget terpasang; sistem boleh menunda); (c) bind `LagFixTileService` (jika tile ditambahkan & panel dibuka); (d) `BootReceiver` (reboot). Begitu proses lahir lewat salah satunya -> `LagFixApp.onCreate` -> `startIfEnabled` -> notifikasi tampil. Latensi revive = waktu sampai pemicu sistem terdekat; tanpa widget/tile ~ sisa interval fstrim -> cocok dgn laporan user "baru aktif setelah 1x interval".
  STATUS (P0 NO HALLUCINATION): INFERENSI dari kode + bukti v42 + laporan user. Pemicu revive yang PASTI belum dibuktikan logcat (v43 A/B/C tetap terbuka). 0 fungsi/baris cacat ditemukan di `startIfEnabled`/`onStartCommand`; "lambat" = konsekuensi desain (revive terikat ke interval fstrim, bukan ke swipe).
  KANDIDAT (semua butuh approval, tak dikerjakan): (K1) tuas user, bukan kode: kunci app di Recents + whitelist autostart XOS (kartu "Agar jadwal tetap jalan" v49 sudah ada) — satu2nya tuas nyata thd `TranManualCleanMgr`. (K2) worker periodik KHUSUS revive, terpisah dari interval fstrim (floor WorkManager 15 mnt -> tetap BUKAN "ASAP"; isi no-op karena `LagFixApp.onCreate` sudah memanggil `startIfEnabled`). Risiko K2: bentrok guard "dilarang custom watchdog" (perlu keputusan eksplisit user) & start FGS dari proses background bisa ditolak OS Android 12+ (`ForegroundServiceStartNotAllowedException`, sudah ditangkap `runCatching` v40) — laporan user mengindikasikan jalur itu berhasil, tapi izin jalur worker belum terbukti di log.
  UJI PENENTU: logcat debug (filter `PersistentTrimService` + `Start proc ... com.lagfix.fstrim`): notifikasi ON -> swipe Recents -> tunggu sampai notifikasi muncul lagi -> baca baris `Start proc` (pemicu: `SystemJobService`/broadcast widget/service tile) + `LIFECYCLE startIfEnabled procAge=` & `onStartCommand intentNull=false`.
- v53 VALIDASI: referensi kode dicocokkan ke ZIP v52 (LagFixApp.kt:9, PersistentTrimService.kt companion + onStartCommand, TrimWorker.kt `Scheduler.apply`, MainViewModel.kt:110, widget_lagfix_info.xml `updatePeriodMillis`, AndroidManifest.xml service tanpa `stopWithTask`); bukti XOS dikutip dari catatan v42 (bukan hasil rekam baru). 0 kode/CI diubah, 0 kompilasi (docs-only). Behavior device tak diuji ulang.
- Batch: v53 (docs-only; APK terpasang tetap build v51, TIDAK perlu instal ulang)

[RESUME POINT: v53 — notifikasi persistent baru kembali setelah >=1 interval pasca swipe: BUKAN bug kode; XOS SIGKILL + skip restart (bukti v42), revive cuma lewat pemicu sistem (WorkManager periodik = interval fstrim / widget 30 mnt / tile / boot) -> `LagFixApp.onCreate` -> `PersistentTrimService.startIfEnabled()` (INFERENSI, belum dibuktikan logcat). Kode = v51/v52 (tak diubah). -> Remaining: DAILY UPDATE (docs-only); logcat LIFECYCLE v49 (swipe -> tunggu -> baca `Start proc` + `procAge`/`intentNull`); baca laporan lint/detekt #47 (F1) yg sudah dilampirkan user tapi belum diproses. -> Next Action: tunggu keputusan user soal K2 (worker revive 15 mnt, bentrok guard watchdog) sebelum ubah apa pun; JANGAN ubah `PersistentTrimService`/`LagFixApp`/`Scheduler` tanpa itu — `startIfEnabled()` bukan komponen cacat (jangan blind-debug ke sana). Backlog tak dipilih: D2 (R8), F2 (Dependabot).]

- v54 (user menegur "Bacalah kocak!!" krn v53 melewatkan `LagFix-lint-detekt-report-47.zip` yang ia lampirkan; kini DIBACA penuh; docs-only, 0 kode/CI diubah, kode app = v51, CHANGELOG.md tak diubah). Basis = ZIP v53.
  ISI ARTIFACT (dibaca langsung): (1) `lint_output.log`: `Wrote HTML report to .../app/build/reports/lint-results-debug.html`, `BUILD SUCCESSFUL in 25s`. (2) `lint-results-debug.txt`: 0 error, 15 warning (InlinedApi CrashLogger:105 `VOLUME_EXTERNAL_PRIMARY` API 29 vs minSdk 26; OldTargetApi 35; UnusedAttribute widget targetCellWidth/Height/previewLayout; BatteryLife `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` MainViewModel:79; GradleDependency compose-bom/core-ktx/activity-compose/lifecycle/work; DataExtractionRules; ObsoleteSdkInt PersistentTrimService:52 + mipmap-anydpi-v26; MonochromeLauncherIcon; AutoboxingStateCreation MainActivity:190). (3) `detekt_output.log`: `app:detekt FAILED` = `MaxIssuesReached: 127 weighted issues` (maxIssues default 0; step non-blocking) — laporan tetap tertulis. (4) `detekt.txt` 127 temuan: MagicNumber 84, FunctionNaming 15, MaxLineLength 10, TooGenericExceptionCaught 4, LongMethod 4, TooManyFunctions 2, NestedBlockDepth 2, LongParameterList 2, EmptyFunctionBlock 2, LoopWithTooManyJumpStatements 1, CyclomaticComplexMethod 1; per file MainActivity 91, UpdateChecker 13, MainViewModel 6, TrimWorker 4, Prefs 4, CrashLogger 4, PrefsTest 3, FstrimExecutor 2. 0 temuan di PersistentTrimService/LagFixApp/BootReceiver.
  KESIMPULAN (dibatasi): F1 VERIFIED — `lint-results-debug.html` ada di artifact (reports_listing.txt: 138851 byte). Penyebab HTML hilang di run #20 TIDAK terbaca dari artifact ini; yang terbukti cuma: dgn step lint terpisah + log mentah (v52) laporan kini muncul. Lint/detekt TIDAK memuat apa pun yg menjelaskan pertanyaan notifikasi persistent (v53) — 0 hubungan; satu2nya baris di service = ObsoleteSdkInt kosmetik.
- v54 VALIDASI: semua angka di atas dihitung ulang dari file di ZIP report (grep/awk per rule & per file; jumlah per file = 127 = jumlah baris detekt.txt); `build.yml` v52 dicocokkan ke struktur artifact (lint_output.log, detekt_output.log, lint_files_found.txt, reports_listing.txt, detekt/, app/build/reports/lint-results*). 0 kompilasi/CI (docs-only). Behavior device tak diuji.
- Docs: `PENDING_ROADMAP.md` F1 +catatan v54 (VERIFIED).
- Batch: v54 (docs-only; APK terpasang tetap build v51, TIDAK perlu instal ulang)

[RESUME POINT: v54 — F1 DITUTUP (VERIFIED): artifact lint/detekt #47 dibaca, `lint-results-debug.html` ada, lint 0 error/15 warning, detekt 127 temuan (backlog triase, non-blocking). Kode app = v51 (tak diubah). Notifikasi persistent (v53): baru kembali setelah >=1 interval pasca swipe = XOS SIGKILL + skip restart, revive lewat pemicu sistem -> `LagFixApp.onCreate` -> `startIfEnabled()` (INFERENSI). -> Remaining: DAILY UPDATE (docs-only); logcat LIFECYCLE v49 (swipe -> tunggu -> baca `Start proc` + `procAge`/`intentNull`); uji v51 "Tes tulis log". -> Next Action: tunggu keputusan user soal K2 (worker revive 15 mnt, bentrok guard watchdog) — JANGAN ubah `PersistentTrimService`/`LagFixApp`/`Scheduler` tanpa itu (`startIfEnabled()` bukan komponen cacat). Triase detekt hanya kalau user minta (MagicNumber 84 & FunctionNaming 15 = noise terbesar; FunctionNaming kemungkinan false-positive Compose, BELUM diverifikasi di config). Backlog tak dipilih: D2 (R8), F2 (Dependabot).]

- v55 (user menjawab pertanyaan K2 dgn "Pakai jalur aksesibilitas" -> memilih layanan aksesibilitas sbg jangkar revive proses, BUKAN worker 15 mnt; ZIP basis = v54; 5 file source/res + CHANGELOG/PROJECT_STATE). Kode diubah:
  (1) BARU `KeepAliveAccessibilityService.kt` — `onServiceConnected()` -> `PersistentTrimService.startIfEnabled(this)` (0 diubah di servis lama); `onAccessibilityEvent`/`onInterrupt` no-op; log `LIFECYCLE a11y onServiceConnected/onUnbind/onDestroy` (tag `KeepAliveA11y`, pid + procAge). (2) BARU `res/xml/accessibility_keepalive_config.xml` — tanpa `accessibilityEventTypes`, `canRetrieveWindowContent=false` (0 baca layar). (3) `AndroidManifest.xml` +`<service .KeepAliveAccessibilityService>` (exported=true, permission `BIND_ACCESSIBILITY_SERVICE`). (4) `strings.xml` +`a11y_keepalive_label`/`a11y_keepalive_description`. (5) `MainActivity.kt` — kartu "Keandalan latar belakang": teks + tombol `Settings.ACTION_ACCESSIBILITY_SETTINGS` (delta +14/+14 `()`, +3/+3 `{}`). 0 perubahan `PersistentTrimService`/`LagFixApp`/`Scheduler`/`TrimWorker`.
  EVIDENCE (riset web, dibatasi): (a) dok resmi Android "Restrictions on starting a foreground service from the background" (diperbarui 2026-09-16): AccessibilityService TIDAK ada di daftar eksepsi start FGS dari background; yang ada & relevan: "user turns off battery optimizations for your app" (app punya `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, MainViewModel:79) -> start FGS saat revive lewat jalur ini bisa ditolak OS bila optimasi baterai LagFix masih aktif; ditangkap `runCatching` di `startIfEnabled` (v40). (b) Dok `AccessibilityService`: siklus hidup dikelola SISTEM; berhenti hanya saat user mematikan atau `disableSelf()`. (c) Laporan pihak ketiga (BUKAN uji di HP ini): rilis Origin Isle v1.4.0 (vivo OriginOS) menyebut keep-alive aksesibilitas tak menjangkarkan proses andal setelah kill; PR kaeawc/auto-mobile #7452 menyebut Android bisa memasukkan layanan aksesibilitas ke "Crashed services" setelah kematian proses berulang -> tak di-bind ulang.
  STATUS (P0 NO HALLUCINATION): jalur ini BELUM TERBUKTI di XOS 16.2 (Infinix X6855); tak ada klaim "pasti ASAP". Syarat: user aktifkan sendiri di Setelan Aksesibilitas (app tak bisa); Android 13+ sideload bisa terkunci "Pengaturan terbatas"; notifikasi hanya kembali bila toggle "Layanan latar depan persisten" ON.
  UJI PENENTU: (1) pasang APK debug, aktifkan layanan di Setelan Aksesibilitas, toggle persisten ON; (2) logcat filter `KeepAliveA11y|PersistentTrimService|ActivityManager`; swipe Recents; catat berapa detik sampai `LIFECYCLE a11y onServiceConnected` + `onStartCommand` + notifikasi muncul; (3) kalau tak muncul: cek `dumpsys accessibility` (Bound vs Crashed services) & `startForeground` ditolak/tidak; (4) coba juga optimasi baterai LagFix OFF.
- v55 VALIDASI: XML manifest/config/strings parse OK; keseimbangan () {} MainActivity delta vs v54 SEIMBANG, file baru seimbang; `import Intent`/`Settings` sudah ada di MainActivity; tombol memakai pola `TextButton` yang sama dgn kartu v49; 0 kompilasi (sandbox tanpa Android SDK/kotlinc) -> CI. Behavior device BELUM diuji.
- Docs: `CHANGELOG.md` +v55 (di atas, descending).
- Batch: v55 (KODE BERUBAH: perlu DAILY UPDATE + CI hijau + instal APK baru)

[RESUME POINT: v55 — jalur aksesibilitas (pilihan user utk revive notifikasi persistent): `KeepAliveAccessibilityService` kosong + config XML + manifest + tombol Setelan Aksesibilitas di kartu "Keandalan latar belakang"; memanggil `PersistentTrimService.startIfEnabled()` saat terhubung. BELUM terbukti di XOS (belum dikompilasi/diuji). -> Remaining: DAILY UPDATE, tunggu CI hijau, instal APK debug, aktifkan layanan di Setelan Aksesibilitas, uji swipe-Recents + logcat `KeepAliveA11y` (latensi revive), kirim logcat/`dumpsys accessibility`. -> Next Action: baca baris `LIFECYCLE a11y` (connected? procAge? start FGS ditolak?) — JANGAN ubah `PersistentTrimService`/`LagFixApp` sebelum logcat itu; kalau FGS ditolak saat revive, kandidat: minta user matikan optimasi baterai LagFix (tombol sudah ada) — bukan blind-debug ke servis. K2 (worker 15 mnt) DIBATALKAN user (diganti aksesibilitas). Backlog tak dipilih: D2 (R8), F2 (Dependabot), triase detekt.]
