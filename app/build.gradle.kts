import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    // v160 (Tahap D2): tanpa plugin Kotlin-Android (Kotlin bawaan AGP 9); compiler Compose tetap lewat plugin di bawah.
    id("org.jetbrains.kotlin.plugin.compose")
    // v152: plugin detekt TIDAK lagi di sini; di-apply bersyarat di bawah (`-Plagfix.detekt=true`).
}

android {
    namespace = "com.lagfix.fstrim"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lagfix.fstrim"
        minSdk = 26
        targetSdk = 37
        // CI (GitHub Actions) selalu menyetel GITHUB_RUN_NUMBER otomatis; tag rilis = build-<run_number>.
        // Dipakai agar versionCode APK terpasang match dengan run_number rilis -> compare update before/after riil.
        // Lokal/dev (tanpa env ini) tetap fallback ke 1, tidak berubah dari sebelumnya.
        val ghRunNumber = System.getenv("GITHUB_RUN_NUMBER")
        versionCode = ghRunNumber?.toIntOrNull() ?: 1
        // v8 (D3): versionName ikut run_number juga -> label versi di UI ("v1.0.<n>") berubah tiap
        // rilis, tidak lagi statis "1.0.0" terus. Lokal/dev tanpa env ini tetap "1.0.0-dev" (jelas
        // beda dari build CI, non-breaking).
        versionName = ghRunNumber?.let { "1.0.$it" } ?: "1.0.0-dev"
    }

    // Secret HANYA dari environment (GitHub Secrets) — tidak ada hardcode.
    signingConfigs {
        create("release") {
            val ks = rootProject.file("release.keystore")
            val pw = System.getenv("KEYSTORE_PASSWORD")
            if (ks.exists() && pw != null) {
                storeFile = ks
                storePassword = pw
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // reflection Shizuku: aman tanpa R8
            val cfg = signingConfigs.getByName("release")
            if (cfg.storeFile != null) signingConfig = cfg
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    // v88 (perintah user: lint/detekt diperketat): warning dihitung error. v151: lintDebug TIDAK lagi di build.yml; hanya jalan di
    // workflow laporan `android-artifact.yml` (non-blocking, hasil di laporan-untuk-claude.txt).
    // Tanpa baseline & tanpa disable rule: temuan harus diperbaiki di sumbernya.
    lint {
        abortOnError = true
        warningsAsErrors = true
        // v89: pengecualian beralasan per-berkas + cek bergantung-waktu jadi informational.
        lintConfig = file("lint.xml")
    }
}

// v152: `android.kotlinOptions {}` = ERROR sejak Kotlin Gradle plugin 2.2 -> `kotlin.compilerOptions` (nilai sama: JVM 17).
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // v157 (Tahap C): versi = rilis stabil terbaru yang disarankan lint GradleDependency (laporan CI run 9).
    // Compose 1.12 (BOM 2026.09.00) mewajibkan compileSdk 37 + AGP >= 9.2.0 (AGP proyek 9.5.0);
    // lifecycle 2.11 & activity 1.13 ikut compileSdk 37.
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // v180 (perintah user: "terapkan OkHttp+Okio"): jaringan alur pembaruan (cek rilis, changelog, jalur web
    // cadangan, unduh APK) lewat OkHttp (rilis stabil terbaru 5.5.1, 22 Okt 2026); tubuh respons dialirkan lewat
    // Okio. Okio dideklarasikan eksplisit karena paket okio diimpor langsung; versinya = yang ditarik OkHttp 5.5.x
    // (Okio 3.18.1). Tanpa aturan R8: `isMinifyEnabled = false`.
    // v181 (laporan CI run 32, lint NewerVersionAvailable): okio 3.18.1 -> 3.18.2 (patch terbaru; nilai dari lint).
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("com.squareup.okio:okio:3.18.2")

    // v7: unit test murni (JVM, tanpa device) — Prefs.record() + FstrimExecutor.state()
    // v17 (C4 kandidat b): FstrimExecutorTest.kt pindah dari mockStatic ke mock interface biasa
    // (ShizukuGateway) — mockito-core tetap dipakai utk mock() non-static ini.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.24.0")
    // v148 (adaptasi Boomly Batch 201): ArchUnit utk `ArchitectureTest` (REPORT-ONLY, tak pernah menggagalkan tes; laporan
    // ke app/build/reports/archunit/archunit-report.txt dibaca workflow `android-artifact.yml`). Hanya classpath tes.
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
}

// F1 (PENDING_ROADMAP.md, CI hardening): static analysis Kotlin. v88-v150: BLOCKING di build.yml; v151 (perintah user): task
// `detekt` HANYA jalan di workflow laporan `android-artifact.yml` (non-blocking) — temuan detekt (maxIssues default 0)
// menggagalkan build. v58: config custom MINIMUM di
// config/detekt/detekt.yml (override di atas ruleset default via buildUponDefaultConfig) —
// hanya FunctionNaming & MagicNumber utk @Composable (false-positive Compose); sisanya default.
// v152: detekt 1.x belum resmi mendukung AGP 9 -> di-apply BERSYARAT (v159: detekt 2.0.0-alpha.6), HANYA bila `-Plagfix.detekt=true` (dipakai workflow
// laporan `android-artifact.yml`). `build.yml` (tes + build + rilis) tak memuat detekt sama sekali, jadi jika plugin ini
// bermasalah di toolchain baru, build/rilis TIDAK ikut gagal; laporan mencatat `NOT VERIFIED`. Isi blok = isi lama (v148-v151).
if (providers.gradleProperty("lagfix.detekt").orNull == "true") {
    apply(plugin = "dev.detekt")

    configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        allRules.set(false)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }

    tasks.withType<Detekt>().configureEach {
        // v159 (detekt 2.x): `jvmTarget` & classpath diisi plugin per kompilasi (tak perlu manual); laporan `xml` = `checkstyle`
        // (dibaca workflow `android-artifact.yml` utk ringkasan); `txt` DIHAPUS di detekt 2 (skrip laporan hanya membaca XML).
        reports {
            html.required.set(true)
            checkstyle.required.set(true)
        }
    }

    // v148 (adaptasi Boomly Batch 164): jaring TAMBAHAN type-resolution HANYA utk task `detektDebug` (dijalankan workflow laporan
    // NON-BLOCKING, bukan gerbang `build.yml`). `config/detekt/detekt-typed.yml` digabung DI ATAS `detekt.yml` (rule lama tetap
    // aktif); task `detekt` polos TIDAK membaca file itu, jadi temuan/salah ketik di sana tak bisa
    // membuat build merah.
    tasks.withType<Detekt>().matching { it.name == "detektDebug" }.configureEach {
        config.setFrom(
            rootProject.file("config/detekt/detekt.yml"),
            rootProject.file("config/detekt/detekt-typed.yml"),
        )
        // v150: pencatatan MENTAH berkas config yang benar-benar dipakai task ini saat dieksekusi (dibaca workflow laporan
        // sbg `DETEKT_TYPED_DIAG`). Dasar: v149 mematikan `InjectDispatcher` di detekt-typed.yml tetapi laporan CI run 2
        // masih memuatnya -> bukti apakah berkas typed ikut terbaca. Tidak mengubah aturan/hasil analisis.
        val typedConfig = config
        doFirst { println("DETEKT_TYPED_DIAG config=" + typedConfig.files.joinToString(",") { it.name }) }
    }
}

// v148 (adaptasi Boomly Batch 201): SpotBugs REPORT-ONLY. Skrip HANYA di-apply bila `-Plagfix.spotbugs=true` (workflow
// `android-artifact.yml`); build/rilis/lint/detekt/tes biasa tak memuatnya sama sekali.
if (providers.gradleProperty("lagfix.spotbugs").orNull == "true") {
    apply(from = rootProject.file("config/spotbugs/spotbugs.gradle.kts"))
}
