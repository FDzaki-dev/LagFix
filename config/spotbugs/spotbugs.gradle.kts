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
// Aux classpath = `android.jar` (platform compileSdk lebih dulu, lalu platform terbaru yang ada) + dependensi debug bila bisa
// di-resolve (best effort). Bila tak ada, SpotBugs hanya mencetak peringatan "missing class" dan analisis tetap jalan
// dengan presisi lebih rendah.
//
// Jalankan dgn `--no-configuration-cache` (task ini membaca konfigurasi proyek saat eksekusi).
//
// v161: AGP 9 + Kotlin bawaan (v160) tak lagi menulis kelas ke `build/tmp/kotlin-classes/debug` (laporan CI run 13:
// "No files to analyze could be opened" walau compileDebugKotlin sukses). Direktori kelas kini dibaca dari KELUARAN
// task `compileDebugKotlin` (+ 2 jalur kandidat; terakhir: pindai `MainActivity.class`) dan dicatat mentah sebagai
// `SPOTBUGS_DIAG` di gradle-spotbugs.log.
//
// NOT VERIFIED: belum pernah dijalankan (sandbox tanpa akses Maven/Gradle). Bukti Boomly: skrip yang sama berjalan di
// CI Boomly sejak Batch 201 (SARIF terbentuk, 285 temuan pada run pertama).

import org.gradle.api.attributes.Attribute
import org.gradle.process.CommandLineArgumentProvider

val spotbugsVersion = "4.8.6"
// v157: compileSdk 37 -> platform `android-37.0` (nama direktori di runner untuk API 37; lihat SDK_PLATFORMS di laporan CI), lalu
// `android-37`, lalu `android-35` (platform lama yang masih ada di runner) sebagai cadangan.
val compileSdkJars = listOf("android-37.0", "android-37", "android-35")

val spotbugsRunner = configurations.create("spotbugsRunner") {
    description = "Classpath CLI SpotBugs (hanya di-resolve oleh task spotbugsDebug)"
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add("spotbugsRunner", "com.github.spotbugs:spotbugs:$spotbugsVersion")
}

val spotbugsClassesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug")
// v161: dibaca saat EKSEKUSI (bukan `Task.project`; configuration cache dimatikan lewat `--no-configuration-cache`).
val spotbugsBuildDir = layout.buildDirectory
val projectTasks = tasks
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
    compileSdkJars.map { File(platforms, "$it/android.jar") }.firstOrNull { it.isFile }?.let { return it }
    return platforms.listFiles()
        ?.filter { File(it, "android.jar").isFile }
        ?.maxByOrNull { it.name.removePrefix("android-").toIntOrNull() ?: 0 }
        ?.let { File(it, "android.jar") }
}

fun hasClassFiles(dir: File): Boolean =
    dir.isDirectory && dir.walkTopDown().any { it.isFile && it.extension == "class" }

// v161: direktori kelas Kotlin hasil kompilasi debug. Urutan: keluaran task `compileDebugKotlin` -> jalur AGP 9
// (Kotlin bawaan) -> jalur lama; bila semuanya kosong, pindai `build/` mencari `MainActivity.class` (akar paket = 4
// tingkat di atasnya). Mengembalikan (direktori, cara menemukan) supaya `SPOTBUGS_DIAG` mencatat bukti mentahnya.
fun findKotlinClassDirs(): Pair<List<File>, String> {
    val build = spotbugsBuildDir.get().asFile
    val fromTask = runCatching { projectTasks.getByName("compileDebugKotlin").outputs.files.files.toList() }
        .getOrDefault(emptyList())
    val candidates = fromTask + listOf(
        File(build, "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
        File(build, "tmp/kotlin-classes/debug")
    )
    val found = candidates.distinct().filter { hasClassFiles(it) }
    if (found.isNotEmpty()) return found to "task+kandidat"
    val scanned = build.walkTopDown().filter { it.isFile && it.name == "MainActivity.class" }
        .mapNotNull { it.parentFile?.parentFile?.parentFile?.parentFile }
        .distinct().sortedBy { it.path }.toList()
    return scanned.take(1) to "pindai(${scanned.size})"
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
            val (classDirs, how) = findKotlinClassDirs()
            val shown = classDirs.joinToString(",") { it.absolutePath }.ifEmpty { "NONE" }
            println("SPOTBUGS_DIAG classesDirs=$shown via=$how")
            if (classDirs.isEmpty()) {
                args.add(spotbugsClassesDir.get().asFile.absolutePath)
            } else {
                classDirs.forEach { args.add(it.absolutePath) }
            }
            args
        }
    )
}
