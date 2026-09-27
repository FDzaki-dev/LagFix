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
