plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // F1 (PENDING_ROADMAP.md): static analysis di CI. 1.23.8 = point release resmi terakhir
    // Detekt 1.23.x, dibangun utk Kotlin 2.0.21 (persis match versi Kotlin project ini).
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}
