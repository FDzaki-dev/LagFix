pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // v153: build.yml run 126 gagal di konfigurasi `build.gradle.kts` baris 1: "Plugin [id: 'org.jetbrains.kotlin.android',
    // version: '2.4.10'] was not found ... could not resolve plugin artifact 'org.jetbrains.kotlin.android:
    // org.jetbrains.kotlin.android.gradle.plugin:2.4.10'" (Google, MavenRepo, Plugin Portal; artefak MARKER plugin, bukan modul
    // KGP). Penyebab marker tak ditemukan TIDAK terbukti (sandbox tak bisa menjangkau repo). Modul `kotlin-gradle-plugin:2.4.10`
    // (rilis 14 Jul 2026) tercantum di Maven Central, jadi dua plugin Kotlin diarahkan LANGSUNG ke modulnya dan tak lagi
    // bergantung pada marker. Hanya berlaku utk permintaan BERVERSI (di `build.gradle.kts` akar); `id(...)` tanpa versi di
    // `app/build.gradle.kts` tetap diambil dari classpath akar. Versi Kotlin tetap diatur di satu tempat: `build.gradle.kts`.
    resolutionStrategy {
        eachPlugin {
            val pluginVersion = requested.version
            if (pluginVersion != null) {
                when (requested.id.id) {
                    "org.jetbrains.kotlin.android" ->
                        useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:$pluginVersion")
                    "org.jetbrains.kotlin.plugin.compose" ->
                        useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$pluginVersion")
                }
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "LagFix"
include(":app")
