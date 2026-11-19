// v160 (Tahap D2): Kotlin BAWAAN AGP 9 (plugin Kotlin-Android dilepas). Versi Kotlin yang dipakai AGP = versi KGP di classpath
// akar; default AGP terlalu lama utk membaca kelas Compose 1.12 -> dipatok eksplisit ke 2.4.10 (sama dgn plugin compose).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    // F1 (PENDING_ROADMAP.md): static analysis di CI. v159 (Tahap D1): detekt 1.23.8 -> 2.0.0-alpha.6 (plugin id & paket baru;
    // sejak alpha.3 mendukung Kotlin 2.4 + AGP 9; alpha.4+ dibangun dgn Kotlin 2.4). Plugin ini di-apply BERSYARAT
    // (`-Plagfix.detekt=true`, hanya workflow laporan) supaya build/rilis TIDAK bergantung padanya.
    id("dev.detekt") version "2.0.0-alpha.6" apply false
}
