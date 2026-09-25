[BRANDING_NAME: LagFix]
[TERMUX_ROOT: LagFix]

# PENDING_ROADMAP — Rencana Penyempurnaan (basis: source v6 nyata + APK build-6)

Dasar dokumen: dibaca langsung dari LagFix_v6.zip (9 file Kotlin, 756 baris total) +
LagFix_build-6_release.apk yang sudah dikompilasi. Tidak ada source yang diubah di batch ini —
dokumen ini PLANNING ONLY. Tiap item butuh approval eksplisit sebelum dieksekusi (no scope creep).

## A. BLOCKING — status setelah evidence video real-device
1. ✅ VERIFIED (evidence video): prompt "unknown sources" muncul sekali, FileProvider->Package
   Installer kebuka, install sukses, app kebuka ulang tanpa crash, state persist.
2. ✅ VERIFIED (evidence video): versi terpasang naik build 6 -> build 7 sesuai versionCode CI.
3. ✅ DITUTUP via pembacaan kode (`UpdateChecker.download()`): `deleteRecursively()` + nama file
   tetap `update.apk` -> menumpuk arsitektural tidak mungkin. Verified via source, bukan runtime
   device-test — beda level bukti, tapi cukup kuat. Cek ukuran Cache di Setelan > Aplikasi tetap
   opsional kalau user mau extra-sure, tidak blocking apapun.
Build hijau ≠ behavior terverifikasi (P0) — poin 1&2 verified via device, poin 3 verified via source.

## B. Robustness / edge case (logic minimum, non-breaking, TIDAK dieksekusi tanpa approval)
1. `TrimWorker` polling `Shizuku.pingBinder()` tiap 500ms maks 5 detik lalu langsung skip kalau
   belum ready — belum ada retry di luar jadwal periodik berikutnya. Perlu keputusan: cukup as-is
   atau perlu backoff/retry.
2. `UpdateChecker` — perilaku saat GitHub API rate-limit (403) atau tanpa koneksi belum ditelusuri
   eksplisit; perlu pastikan pesan error yang tampil ke user jelas (bukan stacktrace mentah).
3. Unduhan APK putus di tengah jalan (koneksi hilang) — perlu pastikan file partial di
   `cache/updates/` ikut terhapus, tidak nyangkut.
4. Shizuku mati setelah reboot: README sudah sebut job dilewati & tercatat di Riwayat — perlu
   pastikan status ini kelihatan jelas di UI Riwayat, bukan cuma silent skip di background.

## C. Testing (gap nyata — dicek langsung: tidak ada app/src/test atau app/src/androidTest sama sekali)
1. Unit test `Prefs.record()` (rotasi log maks 30 baris, format timestamp `dd/MM HH:mm`) dan
   `FstrimExecutor.state()` (mapping 4 status Shizuku) — logic murni, tidak butuh device fisik.
2. Checklist manual/instrumented untuk alur Shizuku (grant/revoke permission via
   `requestPermission()`) dan alur install-update (FileProvider + Package Installer).
3. `build.yml` saat ini cuma assembleRelease/Debug, belum ada step `test` — kalau C1 dikerjakan,
   tambah step test di CI sebagai validasi tambahan (bukan pengganti verifikasi behavior nyata).

## D. Technical debt (dicatat sebagai risiko, backlog — bukan refactor sekarang)
1. `FstrimExecutor.sh()` panggil `Shizuku.newProcess` (method private) via reflection — titik
   rapuh utama. Kalau versi `dev.rikka.shizuku` naik dan tanda tangan method berubah, gagalnya
   diam-diam di runtime, bukan compile error. Mitigasi realistis: versi sudah di-pin (13.1.5, OK);
   catat di README bahwa upgrade Shizuku wajib re-verifikasi manual reflection ini.
2. `isMinifyEnabled = false` permanen di release (sengaja, karena poin D1) — APK release lebih
   besar dari perlu. Kalau nanti mau aktifkan R8: wajib keep-rule spesifik hanya utk method yang
   direflect, BUKAN blanket keep-rule (sesuai guard R8 di .cursorrules). Prioritas rendah.
3. `versionName` statis `"1.0.0"` sementara `versionCode` dinamis (`GITHUB_RUN_NUMBER`) — tidak
   breaking, tapi label versi di UI selalu tampil "1.0.0" tiap rilis. Opsional, butuh keputusan
   user dulu (bukan diam-diam diubah).

## E. UX minor (opsional, backlog — butuh approval eksplisit)
1. Riwayat cuma daftar teks polos 30 baris terakhir, tanpa indikator visual OK/FAIL.
2. Tidak ada halaman "Tentang" terpisah; info app tersebar di card Tautan.
3. Tidak ada toggle dark/light manual (ikut system default M3) — cek apakah disengaja.

## F. Release/CI hardening (opsional, backlog)
1. Belum ada lint/static-analysis (ktlint/detekt) di `build.yml`.
2. Belum ada dependency-update check otomatis (mis. Dependabot) utk `dev.rikka.shizuku`.

## Urutan eksekusi disarankan
- v6 (current): compile OK + A FULLY CLOSED (A1/A2 via device evidence, A3 via source analysis).
- v7 (siap dimulai): C1 (unit test Prefs + FstrimExecutor.state), maks 2 file test baru + config
  test di `app/build.gradle.kts` kalau perlu.
- v8 (usulan, kondisional): item B yang benar-benar terbukti jadi masalah dari evidence real —
  jangan eksekusi B secara spekulatif tanpa evidence.
- Backlog bebas urutan (D2, D3, E1–E3, F1–F2): hanya kalau user eksplisit minta per item.

## Eksplisit DI LUAR SCOPE
Tidak ada rencana ganti arsitektur, ganti dependency utama (Shizuku/WorkManager/Compose), migrasi
modul, atau redesign UI besar. Semua di atas incremental & non-breaking per item.
