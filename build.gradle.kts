plugins {
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    // F1 (PENDING_ROADMAP.md): static analysis di CI. v159 (Tahap D1): detekt 1.23.8 -> 2.0.0-alpha.6 (plugin id & paket baru;
    // sejak alpha.3 mendukung Kotlin 2.4 + AGP 9; alpha.4+ dibangun dgn Kotlin 2.4). Plugin ini di-apply BERSYARAT
    // (`-Plagfix.detekt=true`, hanya workflow laporan) supaya build/rilis TIDAK bergantung padanya.
    id("dev.detekt") version "2.0.0-alpha.6" apply false
}
