plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // v7: unit test murni (JVM, tanpa device) — Prefs.record() + FstrimExecutor.state()
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0") // 5.x: inline mock maker default (mockStatic tanpa artifact tambahan)
}
