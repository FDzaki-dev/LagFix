[BRANDING_NAME: LagFix]
[TERMUX_ROOT: LagFix]

# PENDING_UIUX_MILESTONE — Milestone UI/UX "Premium"

Dasar: dibaca langsung dari source ZIP v58 (`MainActivity.kt` 1150 baris, `themes.xml`/`colors.xml`/`strings.xml`,
drawable widget & launcher, `app/build.gradle.kts`, lint v54). **PLANNING ONLY — 0 source diubah di batch v59.**
Tiap fase butuh perintah eksplisit user ("lanjut M<n>"). Hubungan ke roadmap: `PENDING_ROADMAP.md` bagian G.

## 1. Definisi "Premium" (terukur, bukan klaim)
"100% premium" tak bisa diklaim tanpa bukti device. Milestone dianggap TUNTAS hanya kalau semua butir ini
DEVICE-VERIFIED (screenshot terang + gelap dari user):

| ID | Kriteria |
|----|----------|
| P1 | Konsistensi: 1 sumber token (warna/spasi/tipografi/bentuk); 0 `Color(0x` di luar file token |
| P2 | Ikonografi: 0 emoji sebagai ikon UI; ikon vektor konsisten + `contentDescription` |
| P3 | Hierarki: tiap layar punya 1 aksi primer jelas; aksi sekunder/tersier beda gaya |
| P4 | Motion: transisi tab/state halus (<=300 ms), hormati skala animasi sistem, tanpa animasi tak berujung |
| P5 | Kontras & aksesibilitas: teks >=4.5:1, komponen >=3:1 (terang & gelap); target sentuh >=48dp; TalkBack terbaca; font scale 1.3 & 2.0 tak terpotong |
| P6 | State lengkap: empty/loading/error/sukses tiap kartu punya desain, bukan teks polos |
| P7 | Brand seragam lintas permukaan: app, launcher (+monochrome), widget (tile & notifikasi tetap, lihat guard) |
| P8 | First frame: tak ada flash warna salah saat cold start (terang/gelap) |
| P9 | Zero-regresi: jadwal/widget/tile/service/persistensi/rotasi identik dgn v58 |

Level bukti per fase: PLANNED -> IMPLEMENTED (belum dikompilasi) -> CI-HIJAU -> DEVICE-VERIFIED. Build hijau != behavior terverifikasi.

## 2. Baseline audit (source v58) — semua dari baca kode/grep
| ID | Temuan | Bukti | Status |
|----|--------|-------|--------|
| A1 | Nav bawah memakai emoji teks sebagai ikon (`"🏠"`, `"⚙️"`) | `HomeScreen` -> `NavigationBarItem` | VERIFIED |
| A2 | 0 `Icon(`, 0 `contentDescription`, 0 `semantics` | grep `MainActivity.kt` = 0 | VERIFIED |
| A3 | 0 animasi (`animate*`, `AnimatedVisibility`, `Crossfade` = 0); ganti tab instan (if/else) | grep + `HomeScreen` | VERIFIED |
| A4 | Tipografi = skala default M3 (0 `Typography(` kustom); `FontFamily` hanya Monospace utk log | grep | VERIFIED |
| A5 | Brand ganda: launcher/widget/tile = `#0F62FE` (`colors.xml`), in-app primary terang `#5A6ACF` / gelap `#A9B4F2` | `colors.xml`, `calmLightScheme`/`calmDarkScheme` | VERIFIED |
| A6 | Hierarki datar: 18 `Card(` gaya default (0 `CardDefaults`/`containerColor`), 20 `Button(`, 15 `TextButton(`, 0 `OutlinedButton` | grep | VERIFIED |
| A7 | `successGreen #2E7D32` & `skippedAmber #B26A00` = val file-level (bukan bagian ColorScheme), dipakai sbg warna TEKS `LogLine` & chart di KEDUA tema | `MainActivity.kt:815-816`, `LogLine` | VERIFIED; DIPERBAIKI v60 (IMPLEMENTED, belum diuji device) |
| A8 | String UI di Kotlin; `strings.xml` hanya widget/tile/toast/notifikasi | `strings.xml` | VERIFIED |
| A9 | Launcher adaptive icon tanpa layer `<monochrome>` | `ic_launcher.xml` + lint v54 `MonochromeLauncherIcon` | VERIFIED |
| A10 | `Theme.LagFix` parent `android:Theme.Material.Light.NoActionBar`; 0 `core-splashscreen`; 0 `values-night` | `themes.xml`, `app/build.gradle.kts` | VERIFIED (struktur) |
| A10b | Efek A10: flash terang saat cold start di mode gelap | — | HIPOTESIS (belum diuji) |
| A11 | `ColorScheme` "calm" tak mengisi `surfaceContainer*` & Card tanpa `containerColor` -> latar kartu ikut default M3 (versi material3 dari BOM 2024.10.01, tak dicek langsung) | `calm*Scheme`, grep Card | struktur VERIFIED; warna kartu nyata = HIPOTESIS (butuh screenshot) |
| A12 | Pengaturan = 6 kartu satu kolom `verticalScroll` (Jadwal, Agar jadwal tetap jalan, Keandalan latar belakang, Tema, Tautan, Log Diagnostik) | grep judul kartu | VERIFIED |
| A13 | Tab Utama: StatusCard, tombol "Jalankan fstrim sekarang", Riwayat, Statistik, Pembaruan; empty state Riwayat = teks "Belum pernah dijalankan" | `MainTab` | VERIFIED |
| A14 | Tak ada tes visual/UI (hanya `PrefsTest`, `FstrimExecutorTest`) -> verifikasi visual = manual device | daftar test | VERIFIED |

Jangan dirusak (sudah bagus): palet calm + shapes 6/10/16/22/28dp (v10), `enableEdgeToEdge` + `imePadding`, state tab `rememberSaveable`,
pembeda skip/FAIL (v13), konfirmasi sebelum run (v11), snackbar dismiss-dulu (v12).

## 3. Kontras warna (dihitung dari nilai `Color` di source, rumus WCAG 2.x)
Asumsi latar = `surface` (A11: latar kartu nyata belum terverifikasi). Ambang: teks 4.5, komponen 3.0.

| Pasangan | Terang | Gelap | Catatan |
|----------|--------|-------|---------|
| onSurface / surface | 13.90 | 11.78 | OK |
| onSurfaceVariant / surface | 6.63 | 8.27 | OK |
| primary / surface | 4.78 | 7.25 | OK |
| onPrimary / primary (tombol) | 4.78 | 7.23 | OK |
| error / surface | 4.62 | 5.94 | OK |
| **successGreen / surface** | 5.13 | **2.83** | GELAP gagal (teks `bodySmall` di `LogLine`) |
| **skippedAmber / surface** | **4.24** | **3.42** | TERANG tipis & GELAP gagal (teks `bodySmall`) |
| secondary / surface | 3.76 | 7.34 | terang gagal HANYA bila dipakai utk teks (pemakaian tak dicek) |
| tertiary / surface | 3.06 | 7.37 | idem |
| outline / surface | 1.95 | 3.00 | terang gagal HANYA bila dipakai sbg batas komponen (pemakaian tak dicek) |
| putih / `#0F62FE` (tombol widget) | 5.00 | — | OK |

Implikasi: status Riwayat/Statistik (A7) = temuan kontras NYATA di tema gelap -> diperbaiki di M1 via token status per-tema.

## 4. Fase (1 fase = 1 batch, visual-only)
**Aturan semua fase**
- Visual-only: callback, kondisi `enabled`, state, Prefs/ViewModel/Shizuku/Worker/Scheduler dipertahankan persis.
- DO-NOT-TOUCH (kecuali user minta eksplisit): `PersistentTrimService`, `LagFixApp`, `Scheduler`/`TrimWorker`, `Prefs`, `FstrimExecutor`, `UpdateChecker`, `CrashLogger`, `BootReceiver`, signing/CI release, `ic_tile_fstrim.xml` (riwayat v20/v25, sensitif OEM), notifikasi persistent (investigasi ditutup v57).
- Maks 3-5 file target per fase; lebih besar dipecah (a/b).
- 0 dependency baru tanpa keputusan eksplisit (D2). Release `isMinifyEnabled=false` -> dependency ikon besar menambah ukuran APK -> pakai vector drawable XML.
- `MainActivity.kt` TIDAK dipecah/ditulis ulang (stabil, 91 temuan detekt = backlog lain). Komposabel baru -> file baru (additive); call-site diubah minimum.
- State UI baru wajib `rememberSaveable`/ViewModel; side-effect via `LaunchedEffect`; cegah recomposition berlebih.
- String baru -> `strings.xml`; string lama tidak dimigrasi (scope creep).

| Fase | Isi | File target (perkiraan) | Gate khusus |
|------|-----|--------------------------|-------------|
| M0 | Dokumen ini (v59) | 0 kode | SELESAI |
| M1 | Fondasi token: spacing scale, Typography kustom, `surfaceContainer*` eksplisit di scheme calm, token status per-tema (ganti `successGreen`/`skippedAmber`) | `Design.kt` (baru), `MainActivity.kt` | kontras status >=4.5 terang & gelap; ukuran/spasi tak bergeser (beda teks hanya bobot judul kartu). IMPLEMENTED v60, lihat bagian 9 |
| M2 | Ikon & navigasi: vector drawable (nav Utama/Pengaturan, status Shizuku), `Icon` + `contentDescription`, hapus emoji | 2-3 drawable baru, `MainActivity.kt`, `strings.xml` | 0 emoji ikon; label TalkBack |
| M3 | Hero status + hierarki aksi tab Utama: `StatusCard` jadi hero per `ShizukuState` (copy sama), tombol run tetap `enabled = READY && !running`, progress saat running, empty state Riwayat | `MainActivity.kt` (+ file komposabel baru) | callback & enabled identik v58 |
| M4 | Motion & haptic: Crossfade/AnimatedContent ganti tab, `animateContentSize`/`AnimatedVisibility` kartu, haptic ringan di konfirmasi run | `MainActivity.kt` | uji skala animasi 0x & 1x; 0 animasi tak berujung (baterai) |
| M5 | IA Pengaturan: 6 kartu -> 4 bagian (Jadwal / Keandalan / Tampilan / Info & Diagnostik); kartu jarang pakai collapsible (`rememberSaveable`) | `MainActivity.kt` | semua tombol/callback identik; tak ada UI terpotong |
| M6 | Poles Riwayat/Statistik: `LogLine`, `StatsCard`, `RunHistoryChart`, `StatsLegend` dari token; chart non-teks >=3:1 | `MainActivity.kt` | 0 perubahan parse/format (`PrefsTest` tetap valid) |
| M7 | Permukaan luar Activity: launcher `<monochrome>` (themed icon API 33+; minSdk 26 aman), restyle widget | `ic_launcher.xml`, drawable mono baru, `widget_*.xml` | lint `MonochromeLauncherIcon` hilang; widget render terang/gelap |
| M8 | First frame: `windowBackground` per tema lewat `values/` + `values-night/themes.xml` (0 dependency) | `themes.xml`, `values-night/themes.xml` | cold start terang & gelap tanpa flash; BATAS: mode tema manual in-app != sistem, XML tak tahu pilihan in-app -> flash bisa tersisa saat beda (INFERENSI) |
| M9 | QA aksesibilitas & poles akhir: TalkBack, font scale 1.3/2.0, layar kecil/landscape/gesture-nav, hitung ulang kontras; centang P1-P9 | sesuai temuan (<=5) | DoP P1-P9 DEVICE-VERIFIED |

Urutan default: M1 -> M2 -> M3 -> M4 -> M5 -> M6 -> M7 -> M8 -> M9 (M1 dulu: memperbaiki kontras nyata + dasar semua fase).

## 5. Keputusan terbuka (blokir fase tertentu saja)
- **D1 (M1/M7) arah brand:** (a) in-app ikut `#0F62FE`; (b) launcher/widget ikut palet calm `#5A6ACF`; (c) pertahankan dua-duanya. Default tanpa jawaban user = (c): 0 risiko. Catatan: `ic_launcher_background` juga dipakai fill `ic_tile_fstrim.xml` (v25) dan tombol widget -> opsi (b) mengubah tile sekaligus (area DO-NOT-TOUCH); (a) menyalahi palet calm permintaan user v10.
- **D2 (M8) dependency baru** (mis. `core-splashscreen`)? Default TIDAK.
- **D3 (M2) sumber ikon:** vector drawable kustom (default) vs `material-icons-extended` (APK lebih besar, R8 mati).

## 6. Verifikasi per fase
1. Sintaks: `xmllint` utk XML; keseimbangan `()` `{}` delta utk Kotlin. Sandbox tanpa Android SDK/Gradle -> IMPLEMENTED = belum dikompilasi; wajib CI (unit test + build + lint, cek warning baru).
2. Device: checklist fase + screenshot sebelum/sesudah (terang & gelap) dari user (A14: tak ada tes visual otomatis).
3. Regresi P9 (tiap fase): jadwal on/off + interval; run manual (konfirmasi -> snackbar hasil); widget; tile; toggle notifikasi persistent; rotasi tak mereset tab/dialog; 3 mode tema; cek pembaruan.

## 7. Status board
| Fase | Status | Bukti |
|------|--------|-------|
| M0 | SELESAI (v59) | docs-only |
| M1 | IMPLEMENTED (v60) — belum dikompilasi, belum diuji device | bagian 9 |
| v61 (dark-only + glass, bagian 10) | IMPLEMENTED; device: tampil & tak crash (screenshot v1.0.52), detail bagian 11 | screenshot user |
| M2 | IMPLEMENTED (v62 nav + v66 status Shizuku) — belum dikompilasi/diuji device | bagian 11, 13 |
| v64 | label interval kustom 1 baris — IMPLEMENTED; user melaporkan label tak membungkus lagi | bagian 11 poin 2 |
| v65 | `UpdateCard`: tombol aksi simetris (full-width, tinggi sama) — IMPLEMENTED, belum dikompilasi/diuji device | bagian 12 |
| v66 (M2b) | `StatusCard`: ikon status Shizuku (centang mint / peringatan amber) + `contentDescription` + 2 string — IMPLEMENTED, belum dikompilasi/diuji device | bagian 13 |
| M3 | IMPLEMENTED (v68) — belum dikompilasi/diuji device | bagian 14 |
| M4 | IMPLEMENTED (v71; fade tab diganti fade-through di v74) — belum dikompilasi/diuji device | bagian 17 |
| M5-M9 | PLANNED | — |

## 8. Di luar scope
Ganti arsitektur/dependency utama (Shizuku/WorkManager/Compose), migrasi modul, hitam murni, Material You dinamis (diganti calm di v10), fitur non-visual, jalur revive/notifikasi persistent.

## 9. Hasil M1 (v60) — IMPLEMENTED, BELUM dikompilasi (sandbox tanpa SDK/Gradle) & BELUM diuji device
**File:** `Design.kt` (baru), `MainActivity.kt` (1150 -> 1082 baris). 0 file lain; 0 dependency; 0 string baru.
1. `Design.kt` = sumber token: skema calm terang/gelap, shapes, tipografi, `LagSpacing` (4/8/12/16/24), `StatusColors` per-tema via `LocalStatusColors`, `LagFixTheme` (dipindah dari MainActivity.kt, jadi `internal`). `MainActivity.kt` kini 0 `Color(0x` (P1 warna: tercapai untuk src main+test).
2. Status Riwayat/Statistik: `successGreen`/`skippedAmber` statis dihapus; `LogLine`, `RunHistoryChart`, `StatsLegend` membaca `LocalStatusColors` (di scope Composable, bukan di DrawScope).
3. Nilai yang BERUBAH (selain pemindahan): status terang 276F2D / 94550A, gelap 7FCB8A / E3A951; `error` terang C0524B -> B03A34; `outline` terang B8B9C6 -> 84869A, gelap 6E7180 -> 858899; `inversePrimary` gelap 4A59BD; terang: semua `surfaceContainer*` putih; gelap: Lowest..Highest 171921 / 20222B / 262933 / 2D303A / 33363F; role baru eksplisit (errorContainer, tertiary*, inverse*, outlineVariant, surfaceDim/Bright). Nilai LAIN (primary, secondary, background, surface, surfaceVariant, shapes) = identik v58.
4. Tipografi: ukuran/line-height/letter-spacing = default M3; beda hanya `titleMedium` -> SemiBold (10 pemakaian judul kartu).
5. Spasi: `padding(16.dp)` 11x, `spacedBy` 8/4/12 dp 14x/3x/1x -> token (nilai identik). `6.dp` (3x spacedBy) dan `.padding(top=4.dp,bottom=8.dp)`, `height(80.dp)`, `heightIn(400.dp)` sengaja tak disentuh.

**Kontras setelah M1** (WCAG 2.x, dihitung dari nilai di `Design.kt`; "kartu" terang = putih):
| Pasangan | Terang: putih / F4F4F8 / E6E6EE | Gelap: 262933 / 33363F (kartu) / 1C1E27 |
|---|---|---|
| success | 6.18 / 5.63 / 4.98 | 7.47 / 6.21 / 8.55 |
| warning (dilewati) | 5.89 / 5.37 / 4.75 | 6.95 / 5.78 / 7.96 |
| error | 6.00 / 5.47 / 4.83 | 5.94 / 4.94 / 6.80 |
| onSurfaceVariant | 6.63 / 6.04 / 5.34 | 8.27 / 6.88 / 9.47 |
| primary (teks) | 4.78 / 4.35 / 3.85 | 7.25 / 6.03 / 8.30 |
| outline (non-teks, >=3) | 3.58 / 3.27 / — | 4.13 / 3.44 / 4.73 |
Batang grafik (non-teks): terang 6.18 / 5.89 / 6.00, gelap (di kartu) 6.21 / 5.78 / 4.94 — semua >=3.
Kolom E6E6EE = cadangan kalau Card ternyata memakai `surfaceVariant` (bukan Highest): status & error tetap >=4.5.
**Residual (dicatat, tidak diubah — brand):** primary terang sebagai teks di F4F4F8 = 4.35 (<4.5) dan 3.85 di E6E6EE; teks primary di dalam kartu putih = 4.78 (lolos). Diperiksa lagi di M9.

**INFERENSI yang menentukan tampilan (belum terbukti):** (a) BOM `2024.10.01` -> material3 1.3.0 (kalau lebih lama, parameter `surfaceContainer*` tak ada -> gagal compile, terlihat di CI); (b) `Card` filled = `surfaceContainerHighest` di versi itu -> kartu terang jadi PUTIH (sebelumnya nada baseline ungu-krem E6E0E9), kartu gelap 33363F (sebelumnya 36343B). Kalau (b) salah, kartu tetap `surfaceVariant` (E6E6EE/33363F) dan hanya warna status/error/outline yang berubah.

**Checklist device M1** (user, terang + gelap, beserta screenshot):
1. Aplikasi terbuka tanpa crash; ganti tema Ikuti sistem/Terang/Gelap langsung berubah.
2. Kartu: terang = putih di atas latar F4F4F8 (cukup terpisah?), gelap = 33363F; nav bawah ikut.
3. Judul kartu SemiBold; "Keandalan latar belakang (opsional)" TIDAK membungkus di layar sempit (kalau membungkus -> turunkan bobot ke Medium).
4. Riwayat/Statistik: status OK/dilewati/gagal terbaca jelas di KEDUA tema; legenda & batang grafik berwarna sama dgn teks.
5. Chip pilihan (tema/interval), kolom interval, switch OFF: batas terlihat; snackbar tampil dgn warna seragam calm.
6. Layout/jarak tak bergeser dibanding v58; rotasi tak mereset tab/dialog.

## 10. Arah visual (v61, keputusan user) — DARK ONLY + Glassmorphism & Glow — IMPLEMENTED, BELUM dikompilasi & BELUM diuji device
**Menimpa** bagian 9 yang menyangkut tema terang/ikut-sistem & palet calm (ceklis M1 poin "terang + gelap" tak berlaku lagi). Keputusan: (1) hanya mode gelap; (2) tema, tipografi, shape = glassmorphism; aksen midnight blue + 2 sekunder senada (cyan `#22C6E0`/`#6FE0F2`, violet `#8B5CF6`/`#C9B8FF`) + efek kaca + gradasi glow. Brand ganda launcher/widget/tile `#0F62FE` (A5) TIDAK disentuh (tile/widget DO-NOT-TOUCH).
Implementasi (3 file source: `Design.kt`, `MainActivity.kt`, `themes.xml`):
- `Design.kt`: skema gelap tunggal, `LagFixTheme(content)` (param `themeMode` dihapus) membungkus `Box` berlatar gradien midnight + 3 glow radial statis; `GlassCard` (pengganti `Card`: fill gradien translusen + glow sudut + border gradien 1dp); `glassTopBarColors()`, `GlassNavContainer`, `glassTopEdge()`; tipografi SansSerif (display Light, judul SemiBold + `Shadow` glow, body Normal, label Medium; ukuran/line-height default M3); shapes 8/14/20/26/32dp.
- `MainActivity.kt`: 9 `Card(Modifier.fillMaxWidth())` -> `GlassCard`; kartu \"Tema\" dihapus; `Scaffold(containerColor = Transparent)`, TopAppBar/NavigationBar transparan-kaca; `enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT))` x2 (tanpa ini ikon status bar gelap saat sistem terang -> tak terbaca).
- `themes.xml`: parent `android:Theme.Material.NoActionBar` (gelap) + `windowBackground #FF060A1E` -> menutup A10b (flash terang cold start) secara struktur; hipotesis flash BELUM diuji.
- `ThemeMode`, `Prefs.themeMode`, `MainViewModel.setThemeMode/themeMode` TETAP ada tapi tak dipakai (Prefs DO-NOT-TOUCH; hapus = debt kecil, butuh perintah).
**Bukan blur backdrop sungguhan:** Compose `Modifier.blur` hanya mengaburkan konten sendiri; efek kaca = translusen + glow di belakang + tepi gradien (0 dependency, 0 animasi).
Kontras (WCAG 2.x, skenario TERBURUK: 3 glow menumpuk + fill kaca + glow sudut = latar ~#324795): onSurface 7.49, onSurfaceVariant 5.29, primary 4.60, secondary 5.51, tertiary 4.77, error 4.92 (FFB0B7), success 5.58, warning 5.20, outline 3.03 (>=3). Latar normal (surface #0E1634): semua >=8.
**INFERENSI/RISIKO (belum terbukti):** (a) parameter `surfaceContainer*` butuh material3 1.3.0 (asumsi BOM 2024.10.01, sama seperti v60); (b) `LocalContentColor` diset manual karena latar Scaffold transparan (default hitam) — kalau ada teks hitam di layar, cek ini; (c) `Shadow` pada judul bisa terlihat berkabut di layar tertentu -> hapus `glow = true` di `Design.kt` (2 baris); (d) judul \"Keandalan latar belakang (opsional)\" mungkin membungkus (SemiBold, tak berubah dari v60).
**Checklist device v61** (screenshot): 1. Tak crash, tak ada flash terang saat dibuka; 2. Ikon status/nav bar terang walau HP mode terang; 3. Kartu terlihat sebagai kaca (tepi bercahaya, latar berpendar) & teks terbaca di semua kartu; 4. Riwayat/Statistik: warna status terbaca; 5. Switch OFF, kolom interval, snackbar, dialog (Jalankan/Tentang) terbaca; 6. Layout tak bergeser, rotasi tak mereset tab/dialog; 7. Pengaturan tak lagi punya kartu Tema.

## 11. Bukti device v61 (screenshot user, build label v1.0.52) + M2a (v62) — M2a IMPLEMENTED, BELUM dikompilasi & BELUM diuji device
**Terlihat di screenshot (fakta):** app terbuka & berfungsi (tab Utama + Pengaturan); latar midnight berpendar, kartu kaca bertepi bercahaya, judul bercahaya; ikon status bar putih; Riwayat/Statistik: baris status hijau-mint terbaca, batang grafik tampil; switch Jadwal ON (track periwinkle, thumb navy) terbaca; label "Baterai: berjalan tanpa batas" + centang tampil.
**Temuan (bug isolation, TIDAK diperbaiki di v62, di luar scope M2a):**
1. Ikon nav = emoji berwarna (A1) -> DIPERBAIKI v62 (M2a).
2. Pengaturan > Jadwal: `OutlinedTextField` label "Interval kustom (menit)" membungkus 2 baris dan outline-notch label tampak berantakan (`MainActivity.kt` ~baris 348-352). Dugaan: lebar kolom sempit + font tebal override perangkat user (poin 3; BELUM terbukti). **DIPERBAIKI v64 (IMPLEMENTED, belum dikompilasi/diuji device)** atas perintah user "Lanjut kesitu": label -> "Kustom (menit)" + `maxLines = 1` + `overflow = Ellipsis` (+import `TextOverflow`), 1 file `MainActivity.kt`. Teks `supportingText` "Interval aktif: ..." TIDAK disentuh (bisa membungkus di kolom sempit; bukan temuan yang diminta).
3. ~~Teks body/label tampak tebal~~ -> **BUKAN BUG (klarifikasi user, v63):** itu override font di perangkat user sendiri, di luar kendali app. TIDAK ada aksi di `Design.kt`; jangan diselidiki/dipertanyakan lagi.
4. ~~Chip interval terpotong di tepi kanan~~ -> **BUKAN BUG (klarifikasi user, v63):** baris chip memang bisa digeser ke samping. Perilaku benar.
**Belum terlihat/diuji:** flash terang saat cold start, snackbar, dialog (Jalankan/Tentang), rotasi, TalkBack, bagian bawah Pengaturan (kartu Tema harus sudah hilang).
**M2a (v62):** `ic_nav_home.xml` (path rumah Material) + `ic_nav_settings.xml` (cincin evenOdd + 8 gigi lewat 4 grup rotasi; gambar sendiri, 0 dependency, 0 `material-icons`) + `MainActivity.kt` 2 baris `icon = { Icon(painterResource(...), contentDescription = null) }` (+2 import). `contentDescription = null` disengaja: item nav sudah berlabel teks (TalkBack tak membaca ganda). `strings.xml` tak diubah. Ikon tertint otomatis oleh `LocalContentColor` NavigationBarItem (terpilih = `onSecondaryContainer` cyan-pucat di pil `secondaryContainer`).
**INFERENSI:** bentuk roda gigi hasil gambar sendiri belum dilihat di device (mungkin perlu penyesuaian ukuran gigi); path rumah = path Material standar.
**M2b:** DIKERJAKAN v66 (bagian 13).

## 12. UpdateCard simetris (v65, permintaan user setelah screenshot tab Utama) — IMPLEMENTED, BELUM dikompilasi & BELUM diuji device
**Terlihat di screenshot (fakta):** kartu Pembaruan keadaan `Available` = baris `TextButton("Lihat changelog")` + `Button("Update sekarang")` berdampingan, lalu `Button("Cek ulang")` selebar kartu. Teks "Update sekarang" membungkus 2 baris (tombol lebih tinggi dari changelog), `TextButton` menjorok ke dalam relatif teks kartu, dua gaya tombol campur (side-by-side vs full-width) = tak simetris. (Screenshot itu build terpasang 52 dengan ikon nav emoji -> ikon v62 belum terlihat dari screenshot tsb.)
**Perubahan (`MainActivity.kt`, hanya `UpdateCard`, visual-only):** Row dibongkar -> kolom tombol selebar kartu, urutan: `Button` "Update sekarang" (primer, `enabled = !downloading`, callback `onInstall` identik) -> `FilledTonalButton` "Lihat changelog" (`showChangelog = true` identik) -> teks error unduh (posisi relatif sama) -> "Cek ulang": `FilledTonalButton` bila `result is Available` (agar hanya 1 aksi primer), selain itu `Button` seperti sebelumnya. Teks info/versi, dialog changelog, state `showChangelog`, dan progress TIDAK disentuh. +import `FilledTonalButton` (material3 bawaan, 0 dependency).
**INFERENSI/RISIKO:** tampilan tonal (secondaryContainer teal + teks cyan-pucat) di atas kaca belum dilihat di device; kontras teoretis `onSecondaryContainer`/`secondaryContainer` = 8.4:1. Baris teks "Versi tersedia: build N (nama)" masih bisa membungkus (sengaja tak disentuh: salinan teks).

## 13. M2b — ikon status Shizuku (v66, perintah user "kerjakan next kandidat milestone") — IMPLEMENTED, BELUM dikompilasi & BELUM diuji device
**Pilihan kandidat (fakta dokumen):** urutan default M1 -> M2 -> M3; M2 baru separuh (M2a v62), sisa eksplisit = M2b -> dikerjakan sebelum M3.
**Perubahan (visual-only, 0 logic/state/callback):** `ic_status_ok.xml` (lingkaran+centang, path Material standar) & `ic_status_warning.xml` (segitiga peringatan, path Material standar) BARU; `strings.xml` +`cd_shizuku_status_ready` "Status: baik" & `cd_shizuku_status_action` "Status: perlu tindakan"; `MainActivity.kt` hanya `StatusCard`: judul dibungkus `Row` (Icon + `Text(title, Modifier.weight(1f))`), tint = `LocalStatusColors` (READY -> `success` mint, 3 keadaan lain -> `warning` amber; 0 warna baru), +import `stringResource`. Body teks, tombol aksi (`onGrant`/`onOpen`), `enabled` tombol Jalankan TIDAK disentuh.
**contentDescription:** sengaja tak mengulang judul kartu (judul sudah terbaca TalkBack) -> hanya memberi makna kategori status yang di layar disampaikan lewat bentuk+warna.
**INFERENSI/RISIKO:** tampilan ikon 24dp sejajar `titleMedium` + glow judul belum dilihat di device; kontras mint/amber di atas kaca belum diukur di device (token sama dgn status Riwayat, kontras teoretis dari M1). Karakter teks `✓` di baris "Baterai: berjalan tanpa batas" (Pengaturan) TIDAK diubah (bukan ikon StatusCard, di luar scope).

## 14. M3 — hero status + hierarki aksi tab Utama (v68, perintah user "Lanjutkan milestone!!" setelah uji tile v67 berhasil) — IMPLEMENTED, BELUM dikompilasi & BELUM diuji device
**Perubahan (visual-only, 0 logic/state/callback):** `HomeHero.kt` BARU (`HeroStatusCard`, `HistoryEmptyState`; additive sesuai aturan "komposabel baru -> file baru"); `ic_empty_history.xml` BARU; `strings.xml` +`history_empty_title` (copy lama "Belum pernah dijalankan") & `history_empty_hint`; `MainActivity.kt`: `MainTab` memanggil `HeroStatusCard(...)` menggantikan `StatusCard` + `Button` run, kartu Riwayat memanggil `HistoryEmptyState()` saat `lastRunMs == 0L`, `StatusCard` private dihapus (tak terpakai), import `stringResource` dihapus (tak terpakai).
**Gate (identik v58/v67):** `onClick = onRunNow`, `enabled = READY && !running`, label "Jalankan fstrim sekarang"/"Menjalankan…", judul/isi/aksi per `ShizukuState` (Triple disalin persis), aksi `onGrant` (NEED_PERMISSION) / `onOpen` (lainnya), tombol run SELALU ada (juga saat belum READY, disabled seperti sebelumnya).
**Detail:** lencana 72dp berisi ikon 36dp (READY = `ic_status_ok` mint, selain itu = `ic_status_warning` amber; tint `LocalStatusColors`, alpha latar lencana 0.16); judul `titleLarge` tengah; `LinearProgressIndicator` indeterminate HANYA saat `running` (terikat state, bukan animasi abadi) dalam slot 4dp yang selalu dicadangkan (tata letak tak melompat); spasi hero `LagSpacing.xl` (24dp, token yang disiapkan M1 untuk M3).
**Hierarki aksi:** READY -> 1 aksi (Jalankan). Belum READY -> aksi status (Button utama) di atas, Jalankan disabled di bawah.
**Verifikasi sandbox (BUKAN device):** kontras ikon vs lencana dihitung WCAG: mint 6.8:1 (kartu) / 6.1:1 (+glow sudut), amber 6.5:1 / 5.9:1 (>= 3:1 non-teks). Bentuk 5 vector drawable (`ic_nav_home`, `ic_nav_settings`, `ic_status_ok`, `ic_status_warning`, `ic_empty_history`) dirasterisasi dengan parser path buatan sendiri (XOR evenodd + arc) dan dilihat: semua berbentuk wajar, termasuk roda gigi M2a (cincin + 8 gigi tersambung) yang di v62 masih INFERENSI. Render sandbox != render Compose di device.
**INFERENSI/RISIKO:** tinggi hero & tata letak di layar kecil/font tebal (override perangkat user) belum dilihat; `LinearProgressIndicator` (material3 1.3.0 via BOM 2024.10.01) belum dikompilasi; judul `titleLarge` bergaya glow bisa terlihat berkabut (kalau ya: ganti `titleLarge` -> `headlineSmall` di `HeroStatusCard`, 1 baris).

## 15. Widget selaras gaya utama + ikon app baru (v69, permintaan user; di luar M-seri) — IMPLEMENTED, BELUM dikompilasi & BELUM diuji device
**Fakta sebelum:** kartu widget `?android:attr/colorBackground` + teks `?android:attr/textColorPrimary/Secondary` (ikut tema sistem/launcher, bukan dark glass app); tombol biru brand `#0F62FE` radius 12dp teks putih; pratinjau pemilih widget kosong (TextView tanpa teks awal). Ikon launcher = petir putih di atas `#0F62FE` datar (generik, tanpa `<monochrome>`).
**Widget (visual-only; `LagFixWidgetProvider.kt` TIDAK diubah, id `widget_root/title/status/button` dipertahankan):** `widget_card_bg.xml` = layer-list: tepi gradien 1dp (`#66FFFFFF`->`#1F6F8CFF`->`#0DFFFFFF`, = `glassBorder`), dasar midnight diagonal (alpha ~94%), 3 glow radial (biru kiri-atas, cyan kanan-tengah, violet kiri-bawah; = `Design.kt`), radius 26dp (= `Shapes.large`). `widget_button_bg.xml` = selector pil periwinkle `#A3BEFF` (pressed `#7E9DEB`), teks `#0A1440` (= `onPrimary`). `widget_lagfix.xml`: baris header (ikon app 22dp dari `@mipmap/ic_launcher` + judul `#EDF0FF` bold + shadow glow halus), status `#C3CBEC` (= `onSurfaceVariant`), tombol `sans-serif-medium`, `stateListAnimator=@null` (datar); `android:text` awal dari string yang sudah ada (pratinjau terisi; provider menimpa saat update).
**Ikon:** konsep = chip memori flash (12 pin + badan cincin gradien periwinkle->cyan) berisi kilau "bersih" (besar+kecil) = isi app (trim blok penyimpanan flash). `ic_launcher_backdrop.xml` BARU (gradien midnight + 3 glow, `aapt:attr`), `ic_launcher_foreground.xml` ditulis ulang, `ic_launcher_monochrome.xml` BARU (ikon bertema Android 13+), `mipmap-anydpi-v26/ic_launcher.xml` +`<monochrome>`. `colors.xml` TIDAK diubah: `ic_launcher_background` masih dipakai `ic_tile_fstrim.xml` (DO-NOT-TOUCH, ikon tile & ikon notifikasi persisten tidak disentuh).
**Verifikasi sandbox (BUKAN device):** `xmllint` 7 file OK. Kontras WCAG widget (skenario terburuk: 3 glow menumpuk di atas dasar): judul 7.06:1, status 4.99:1, tombol 9.58:1 (pressed 6.66:1). Ikon dirasterisasi dengan parser path buatan sendiri: seluruh bentuk dalam safe-zone lingkaran 66dp (titik terjauh ke pusat 29.6 < 33), terbaca pada 36-300px, versi monokrom wajar. Mock widget = aproksimasi PIL, bukan render Android.
**INFERENSI/RISIKO:** (a) layer-list gradien radial `gradientRadius` dalam dp di `RemoteViews` & ImageView berisi adaptive icon (`@mipmap/ic_launcher`) di dalam widget belum dilihat di launcher user (bila bentuk topeng aneh -> ganti `src` ke drawable kecil khusus); (b) bentuk chip+kilau bisa dibaca "ikon AI" bila tanpa konteks — keputusan estetika, belum divalidasi user; (c) tinggi widget +~3dp (header 22dp vs teks 17dp lama), `minHeight` 110dp tak diubah.

## 16. Ikon QS tile ikut ikon baru (v70, permintaan user) — IMPLEMENTED, BELUM dikompilasi & BELUM diuji device
**Fakta:** v69 sengaja tak menyentuh `ic_tile_fstrim.xml` (daftar DO-NOT-TOUCH) -> tile masih petir lama; user menegur. **Perubahan:** hanya `pathData` (+komentar) `ic_tile_fstrim.xml`: cincin chip 12x12dp evenOdd (tebal 1,8dp), 8 pin (2/sisi), kilau 4 titik; fill tetap `@color/ic_launcher_background` (keputusan v25). Drawable yang sama = ikon kecil notifikasi persisten (`PersistentTrimService.kt:125`, 0 kode diubah) -> ikut berganti.
**Verifikasi sandbox (BUKAN device):** `xmllint` OK; siluet dirasterisasi 24-192px (parser path buatan sendiri): terbaca >=48px; terjauh dari pusat 8,6dp (<12). **INFERENSI/RISIKO:** pin 1,6dp & kilau kecil bisa terlihat rapat di mdpi/tile kecil; kalau terlalu ramai -> sederhanakan HANYA `ic_tile_fstrim.xml` (mis. buang pin, sisakan cincin+kilau).

## 17. M4 — motion & haptic (v71, perintah user "Lanjutkan milestone!!") — IMPLEMENTED, BELUM dikompilasi & BELUM diuji device
**Perubahan (visual-only; 3 file: `Design.kt`, `HomeHero.kt`, `MainActivity.kt`; 0 perubahan state/ViewModel/Prefs/callback):**
- `Design.kt`: `LagMotion` (TAB_MS 200, CONTENT_MS 250, FADE_MS 150 — semua tween finite <= 300 ms) + `GlassCard(animateSize: Boolean = false)`; true -> `animateContentSize(tween 250)` di rantai modifier PALING DALAM (setelah border). Default false = pemanggil lama identik.
- `HomeHero.kt`: kartu hero `animateSize = true`; progress run = fade 150 ms lewat `animateFloatAsState` + `Modifier.alpha` di slot 4dp yang sama (slot tetap dicadangkan; indikator hanya dikomposisi saat alpha > 0 -> setelah fade-out selesai keluar dari komposisi, animasi indeterminate TETAP berhenti, tak ada loop abadi). **v73 (fix):** versi awal v71 memakai `AnimatedVisibility` di dalam `Box` di dalam `Column` -> CI run 58 gagal kompilasi (`ColumnScope.AnimatedVisibility ... cannot be called in this context with an implicit receiver`, DslMarker: receiver `Column` di luar `Box` terpilih tapi diblokir) + error turunan `@Composable invocations can only happen from the context of a @Composable function`. Itu 2 satu-satunya error di log; `Design.kt` & `MainActivity.kt` lolos kompilasi. PELAJARAN: di dalam `Box`/scope bersarang, JANGAN pakai `AnimatedVisibility`/`AnimatedContent` tanpa receiver eksplisit — pakai `animate*AsState` atau panggilan ber-qualifier.
- `MainActivity.kt`: `Crossfade(selectedTab, tween 200)`; `Column(fillMaxSize+imePadding+verticalScroll+padding)` dipindah KE DALAM Crossfade (urutan modifier sama dgn v70) dan isi memakai `tab` (bukan `selectedTab`) agar tab yang memudar tetap benar; kartu Riwayat/Statistik/Pembaruan `animateSize = true`; `LocalHapticFeedback` + `HapticFeedbackType.TextHandleMove` di tombol "Jalankan" dialog konfirmasi (urutan: tutup dialog -> haptic -> `vm.runNow()`).
**Perubahan perilaku yang disengaja:** scroll tidak lagi dibagi antar tab (sebelumnya 1 `rememberScrollState` untuk keduanya). Tanpa ini Crossfade membuat tinggi kotak = tab tertinggi selama fade dan konten melompat di akhir. Efek: pindah tab = tab tujuan mulai dari atas.
**Verifikasi sandbox (BUKAN device):** keseimbangan `{}`/`()`/`[]` 3 file = 0; semua import baru ada (BOM 2024.10.01 -> `Crossfade(label)`, `animateContentSize`, `HapticFeedbackType.TextHandleMove` tersedia; TERBUKTI oleh CI run 58 utk `Design.kt`/`MainActivity.kt`); tak ada `LaunchedEffect`/launcher baru; `LaunchedEffect(Unit){load()}` (Pengaturan) & launcher izin notifikasi tak berubah perilaku (jalan saat tab masuk, seperti v70). 0 kompilasi/CI di sandbox (tanpa Android SDK/Gradle).
**INFERENSI/RISIKO (cek device):** (a) gate "skala animasi 0x/1x": Compose membaca skala animasi sistem -> 0x = langsung ke akhir — belum diuji; (b) `TextHandleMove` = haptic API 27+, API 26 (minSdk) = no-op — belum diuji; (c) saat Crossfade 200 ms dua tab terkomposisi bersamaan (hanya sesaat); (d) `animateContentSize` di dalam `verticalScroll` bisa terasa "mengejar" bila isi berubah cepat berulang (mis. baris Riwayat saat run) — bila mengganggu, matikan `animateSize` HANYA di kartu itu; (e) bila build gagal lagi: kandidat pertama blok `Crossfade` di `MainActivity.kt`, lalu `GlassCard` (`.then(if/else)`) di `Design.kt` — keduanya SUDAH lolos kompilasi di CI run 58 (frontend), jadi risikonya kecil.
**v74 (fix fade tab, laporan user "buggy, not smooth like iOS"):** `Crossfade` di `MainActivity.kt` diganti `AnimatedContent` fade-through (keluar 90 ms, masuk 210 ms tertunda 90 ms; `LagMotion.TAB_MS` -> `TAB_OUT_MS`/`TAB_IN_MS`) supaya dua tab tak menumpuk di tengah transisi. Analisis statis, BELUM diuji device.
**Gate M4 yang masih terbuka (device):** skala animasi 0x & 1x; progress berhenti saat `running=false`; rotasi di tengah crossfade; TalkBack tetap membaca satu tab saja.
