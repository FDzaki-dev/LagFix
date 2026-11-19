// v148 — SpotBugs REPORT-ONLY untuk LagFix (adaptasi `config/spotbugs/spotbugs.gradle.kts` Boomly Batch 201).
//
// Di-apply dari `app/build.gradle.kts` HANYA bila `-Plagfix.spotbugs=true` (dipakai workflow `android-artifact.yml`).
// Build/rilis biasa (`build.yml`), lint, detekt, dan tes TIDAK memuat file ini sama sekali: kalau skrip ini salah, yang
// rusak hanya langkah SpotBugs di laporan (jadi `NOT VERIFIED`), bukan gerbang BLOCKING lint+detekt dan bukan build APK.
//
// Kenapa bukan plugin `com.github.spotbugs`: plugin itu tidak membuat task untuk varian Android (hanya source set
// Java), dan menambah plugin ke classpath build = menyentuh konfigurasi yang stabil. Di sini SpotBugs dijalankan
// langsung sebagai `JavaExec` (CLI `edu.umd.cs.findbugs.FindBugs2`) terhadap kelas Kotlin hasil `compileDebugKotlin`.
//
// Keluaran: `app/build/reports/spotbugs/spotbugs.sarif` (dibaca `jq` di workflow). Filter noise Kotlin:
// `config/spotbugs/exclude.xml`. NON-BLOCKING: exit code SpotBugs diabaikan.
// Aux classpath = `android.jar` (compileSdk 35 lebih dulu, lalu platform terbaru yang ada) + dependensi debug bila bisa
// di-resolve (best effort). Bila tak ada, SpotBugs hanya mencetak peringatan "missing class" dan analisis tetap jalan
// dengan presisi lebih rendah.
//
// Jalankan dgn `--no-configuration-cache` (task ini membaca konfigurasi proyek saat eksekusi).
//
// NOT VERIFIED: belum pernah dijalankan (sandbox tanpa akses Maven/Gradle). Bukti Boomly: skrip yang sama berjalan di
// CI Boomly sejak Batch 201 (SARIF terbentuk, 285 temuan pada run pertama).

import org.gradle.api.attributes.Attribute
import org.gradle.process.CommandLineArgumentProvider

val spotbugsVersion = "4.8.6"
val compileSdkJar = "android-35"

val spotbugsRunner = configurations.create("spotbugsRunner") {
    description = "Classpath CLI SpotBugs (hanya di-resolve oleh task spotbugsDebug)"
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add("spotbugsRunner", "com.github.spotbugs:spotbugs:$spotbugsVersion")
}

val spotbugsClassesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug")
val spotbugsSarif = layout.buildDirectory.file("reports/spotbugs/spotbugs.sarif")
val spotbugsExclude = rootProject.layout.projectDirectory.file("config/spotbugs/exclude.xml")
val spotbugsSources = layout.projectDirectory.dir("src/main/java")

// v155: dipakai di `CommandLineArgumentProvider` (jalan saat EKSEKUSI). Sebelumnya `project.configurations` di sana = `Task.project`
// saat eksekusi (laporan run 6/7: deprecated, gagal di Gradle 10). `configurations` di sini = milik proyek skrip, dibaca saat konfigurasi.
val projectConfigurations = configurations

// android.jar: platform compileSdk dulu; bila belum terpasang, platform bernomor tertinggi yang ada.
fun findAndroidJar(sdkRoot: String?): File? {
    if (sdkRoot == null) return null
    val platforms = File(sdkRoot, "platforms")
    val preferred = File(platforms, "$compileSdkJar/android.jar")
    if (preferred.isFile) return preferred
    return platforms.listFiles()
        ?.filter { File(it, "android.jar").isFile }
        ?.maxByOrNull { it.name.removePrefix("android-").toIntOrNull() ?: 0 }
        ?.let { File(it, "android.jar") }
}

tasks.register<JavaExec>("spotbugsDebug") {
    group = "verification"
    description = "SpotBugs report-only (SARIF) untuk varian debug; aktif hanya dengan -Plagfix.spotbugs=true"
    dependsOn("compileDebugKotlin")
    classpath = spotbugsRunner
    mainClass.set("edu.umd.cs.findbugs.FindBugs2")
    maxHeapSize = "1g"
    isIgnoreExitValue = true
    outputs.upToDateWhen { false }

    doFirst {
        spotbugsSarif.get().asFile.parentFile.mkdirs()
    }

    argumentProviders.add(
        CommandLineArgumentProvider {
            val aux = LinkedHashSet<File>()
            findAndroidJar(System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT"))?.let { aux.add(it) }
            runCatching {
                val compileClasspath = projectConfigurations.getByName("debugCompileClasspath")
                for (type in listOf("android-classes-jar", "jar")) {
                    val view = compileClasspath.incoming.artifactView {
                        lenient(true)
                        attributes { attribute(Attribute.of("artifactType", String::class.java), type) }
                    }
                    aux.addAll(view.files.files)
                }
            }
            val args = mutableListOf(
                "-sarif",
                "-output", spotbugsSarif.get().asFile.absolutePath,
                "-exclude", spotbugsExclude.asFile.absolutePath,
                "-effort:default",
                "-medium",
                "-nested:false",
                "-sourcepath", spotbugsSources.asFile.absolutePath
            )
            if (aux.isNotEmpty()) {
                args.add("-auxclasspath")
                args.add(aux.joinToString(File.pathSeparator))
            }
            args.add(spotbugsClassesDir.get().asFile.absolutePath)
            args
        }
    )
}
