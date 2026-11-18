import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.lagfix.fstrim"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lagfix.fstrim"
        minSdk = 26
        targetSdk = 35
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
    kotlinOptions { jvmTarget = "17" }
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

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // v7: unit test murni (JVM, tanpa device) — Prefs.record() + FstrimExecutor.state()
    // v17 (C4 kandidat b): FstrimExecutorTest.kt pindah dari mockStatic ke mock interface biasa
    // (ShizukuGateway) — mockito-core tetap dipakai utk mock() non-static ini.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    // v148 (adaptasi Boomly Batch 201): ArchUnit utk `ArchitectureTest` (REPORT-ONLY, tak pernah menggagalkan tes; laporan
    // ke app/build/reports/archunit/archunit-report.txt dibaca workflow `android-artifact.yml`). Hanya classpath tes.
    testImplementation("com.tngtech.archunit:archunit:1.3.0")
}

// F1 (PENDING_ROADMAP.md, CI hardening): static analysis Kotlin. v88-v150: BLOCKING di build.yml; v151 (perintah user): task
// `detekt` HANYA jalan di workflow laporan `android-artifact.yml` (non-blocking) — temuan detekt (maxIssues default 0)
// menggagalkan build. v58: config custom MINIMUM di
// config/detekt/detekt.yml (override di atas ruleset default via buildUponDefaultConfig) —
// hanya FunctionNaming & MagicNumber utk @Composable (false-positive Compose); sisanya default.
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        // v148: xml (dibaca workflow `android-artifact.yml` utk ringkasan) & txt (dibaca manusia) dinyalakan eksplisit,
        // tak bergantung default plugin. Hanya menambah berkas laporan; aturan tak berubah.
        xml.required.set(true)
        txt.required.set(true)
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

// v148 (adaptasi Boomly Batch 201): SpotBugs REPORT-ONLY. Skrip HANYA di-apply bila `-Plagfix.spotbugs=true` (workflow
// `android-artifact.yml`); build/rilis/lint/detekt/tes biasa tak memuatnya sama sekali.
if (providers.gradleProperty("lagfix.spotbugs").orNull == "true") {
    apply(from = rootProject.file("config/spotbugs/spotbugs.gradle.kts"))
}
