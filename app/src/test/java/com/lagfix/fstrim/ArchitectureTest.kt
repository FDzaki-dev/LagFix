package com.lagfix.fstrim

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val BASE_PACKAGE = "com.lagfix.fstrim"
private const val REPORT_PATH = "build/reports/archunit/archunit-report.txt"
private const val MAX_DETAIL_LINES = 8
private const val MAX_DETAIL_CHARS = 220

// Berkas yang TIDAK meng-impor androidx.compose (hasil grep v148 atas app/src/main).
private val NON_COMPOSE_FILES = listOf(
    "AppLinks", "BootReceiver", "BootTrimSetting", "BoundedShell", "CatchJournal", "CrashLogger", "FstrimExecutor",
    "KillCatcher", "LagFixApp", "LagFixTileService", "LagFixWidgetProvider", "LeaveStop", "LogBuffer",
    "LogcatSnapshot", "OwnLogcat", "PersistentTrimService", "Prefs", "ProcessCatcher", "RecentsExclusion",
    "TimeFormat", "TrimWorker", "UpdateChecker", "VendorSettings",
)

// Berkas logika inti: tak memakai simbol apa pun dari berkas UI di bawah (pemeriksaan kode tanpa komentar, v148).
// Servis/widget/tile (PersistentTrimService, LagFixWidgetProvider, ...) SENGAJA tak termasuk: membuka MainActivity.
private val UI_FREE_CORE_FILES = listOf(
    "AppLinks", "BootTrimSetting", "BoundedShell", "CatchJournal", "CrashLogger", "FstrimExecutor", "KillCatcher",
    "LeaveStop", "LogBuffer", "LogcatSnapshot", "OwnLogcat", "Prefs", "ProcessCatcher", "RecentsExclusion",
    "TimeFormat", "TrimWorker", "UpdateChecker", "VendorSettings",
)
private val UI_FILES = listOf("MainActivity", "MainViewModel", "Design", "HomeHero", "NeoTheme", "FrameJank")

/** Regex nama kelas dari berkas [files]: kelas `X`, fasad top-level `XKt`, dan kelas bersarang/lambda `X$...`. */
private fun classesOf(files: List<String>): String =
    BASE_PACKAGE.replace(".", "\\.") + "\\.(" + files.joinToString("|") + ")(Kt)?(\\\$.*)?"

/** Regex semua kelas paket app KECUALI [name] (dan kelas bersarangnya). */
private fun allExcept(name: String): String =
    BASE_PACKAGE.replace(".", "\\.") + "\\.(?!" + name + "(\\\$.*)?\$).*"

/** Aturan arsitektur LagFix (label ID + aturan ArchUnit). Semuanya diuji REPORT-ONLY. */
private fun architectureRules(): List<Pair<String, ArchRule>> = listOf(
    "GlobalScope dilarang (coroutine lepas lifecycle)" to noClasses().should().dependOnClassesThat()
        .haveFullyQualifiedName("kotlinx.coroutines.GlobalScope"),
    "AsyncTask dilarang (deprecated)" to noClasses().should().dependOnClassesThat()
        .haveFullyQualifiedName("android.os.AsyncTask"),
    "Kelas produksi tidak bergantung pada pustaka tes (JUnit/Mockito)" to noClasses().should()
        .dependOnClassesThat().resideInAnyPackage("org.junit..", "org.mockito.."),
    "Thread.sleep hanya di BoundedShell (polling keluar proses; selain itu pakai delay)" to noClasses().that()
        .haveNameMatching(allExcept("BoundedShell")).should().callMethod("java.lang.Thread", "sleep", "long"),
    "Kelas non-UI tidak bergantung pada Compose" to noClasses().that()
        .haveNameMatching(classesOf(NON_COMPOSE_FILES)).should().dependOnClassesThat()
        .resideInAnyPackage("androidx.compose.."),
    "Logika inti tidak bergantung pada kelas UI (Activity/ViewModel/tema)" to noClasses().that()
        .haveNameMatching(classesOf(UI_FREE_CORE_FILES)).should().dependOnClassesThat()
        .haveNameMatching(classesOf(UI_FILES)),
    "ViewModel tidak merujuk Activity (kebocoran saat rotasi)" to noClasses().that()
        .areAssignableTo("androidx.lifecycle.ViewModel").should().dependOnClassesThat()
        .areAssignableTo("android.app.Activity"),
)

/** Hanya kelas produksi: lokasi kelas tes unit (`...UnitTest...`) dibuang. */
private fun productionOnly(): ImportOption =
    ImportOption { location -> !location.asURI().toString().contains("UnitTest") }

private data class RuleResult(val violations: Int, val errored: Boolean)

private fun evaluateRule(label: String, rule: ArchRule, classes: JavaClasses, out: StringBuilder): RuleResult {
    val outcome = runCatching { rule.evaluate(classes).failureReport.details }
    val details = outcome.getOrNull()
    return when {
        details == null -> {
            out.append("[ERROR] ").append(label).append(": ").append(outcome.exceptionOrNull()).append('\n')
            RuleResult(0, true)
        }
        details.isEmpty() -> {
            out.append("[PASS] ").append(label).append('\n')
            RuleResult(0, false)
        }
        else -> {
            out.append("[FAIL] ").append(label).append(" (").append(details.size).append(" pelanggaran)\n")
            details.take(MAX_DETAIL_LINES).forEach {
                out.append("  - ").append(it.replace('\n', ' ').take(MAX_DETAIL_CHARS)).append('\n')
            }
            RuleResult(details.size, false)
        }
    }
}

/**
 * Isi laporan. Baris pertama `ARCHUNIT_STATUS=OK|INCOMPLETE|ERROR`: OK hanya bila kelas berhasil diimpor dan SEMUA
 * aturan terevaluasi; selain itu workflow mencatat `NOT VERIFIED` (alat gagal != lulus). Semua pengecualian ditangkap.
 */
private fun buildReport(): String {
    val outcome = runCatching {
        val classes = ClassFileImporter().withImportOption(productionOnly()).importPackages(BASE_PACKAGE)
        val rules = architectureRules()
        val body = StringBuilder()
        var violations = 0
        var errors = 0
        for ((label, rule) in rules) {
            val result = evaluateRule(label, rule, classes, body)
            violations += result.violations
            if (result.errored) errors++
        }
        val status = if (classes.isEmpty() || errors > 0) "INCOMPLETE" else "OK"
        "ARCHUNIT_STATUS=$status\nCLASSES=${classes.size}\nRULES=${rules.size}\n" +
            "VIOLATIONS=$violations\nERRORS=$errors\n$body"
    }
    return outcome.getOrElse { "ARCHUNIT_STATUS=ERROR\nERROR=$it\n" }
}

/** Tulis laporan ke `app/build/reports/archunit/` (direktori kerja tes Gradle = modul). Gagal tulis = diam. */
private fun writeReport(text: String) {
    runCatching {
        val file = File(REPORT_PATH)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}

/**
 * v148: ArchUnit REPORT-ONLY (adaptasi `ArchitectureTest` Boomly Batch 201). `architectureReport_*` TIDAK PERNAH gagal:
 * pelanggaran/galat alat hanya tercatat di berkas laporan yang dibaca workflow `android-artifact.yml` (non-blocking).
 * Tes kedua menjaga regex pemilih kelas (murni, tanpa ArchUnit) supaya aturan tak diam-diam kosong.
 */
class ArchitectureTest {
    @Test
    fun architectureReport_isWrittenAndNeverFails() {
        writeReport(buildReport())
    }

    @Test
    fun classSelectors_matchTopLevelFacadeAndNestedClasses() {
        val core = Regex(classesOf(listOf("Prefs", "TimeFormat")))
        assertTrue("com.lagfix.fstrim.Prefs".matches(core))
        assertTrue("com.lagfix.fstrim.TimeFormatKt".matches(core))
        assertTrue("com.lagfix.fstrim.Prefs\$Companion".matches(core))
        assertFalse("com.lagfix.fstrim.PrefsX".matches(core))
        assertFalse("com.lagfix.fstrim.MainActivity".matches(core))
        val notShell = Regex(allExcept("BoundedShell"))
        assertTrue("com.lagfix.fstrim.Prefs".matches(notShell))
        assertFalse("com.lagfix.fstrim.BoundedShell".matches(notShell))
        assertFalse("com.lagfix.fstrim.BoundedShell\$sleepQuietly\$1".matches(notShell))
    }
}
