plugins {
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    // F1 (PENDING_ROADMAP.md): static analysis di CI. 1.23.8 = point release resmi terakhir Detekt 1.23.x (dibangun utk
    // Kotlin 2.0.21). v152: AGP 9.5.0 + Kotlin 2.4.10 + Gradle 9.6.0 (opt-out built-in Kotlin & DSL baru, lihat
    // gradle.properties); detekt 1.x belum resmi mendukung AGP 9 -> plugin ini di-apply BERSYARAT (`-Plagfix.detekt=true`,
    // hanya workflow laporan) supaya build/rilis TIDAK bergantung padanya. Detekt 2.x = batch tersendiri.
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}
