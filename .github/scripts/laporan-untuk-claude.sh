#!/usr/bin/env bash
# v151 — perakit `laporan-untuk-claude.txt` (SATU berkas, paket komplit) untuk workflow `android-artifact.yml`.
#
# Dipindah dari blok inline `android-artifact.yml` (v148-v150) supaya bisa diuji lokal dgn berkas tiruan dan supaya YAML
# workflow tetap pendek. Logika ArchUnit & SpotBugs (jq) DIBAWA APA ADANYA dari v150; yang baru: seksi lint/detekt
# LENGKAP (dulu hanya kutipan), tes gagal + frame stack, peringatan DEPRECATED Gradle/AGP (`--warning-mode all`),
# hasil `assembleDebug` (galat kompilasi/paket ikut masuk, jadi `LagFix-fail-log-*` dari build.yml tak perlu diunggah),
# dan data mentah lingkungan (JDK, Gradle, platform SDK runner, versi AGP/Kotlin/SDK dari berkas build).
#
# Masukan (env): REPO_NAME RUN_NUMBER RUN_ID COMMIT_SHA REF_NAME [GITHUB_WORKSPACE] [GITHUB_STEP_SUMMARY]
# Masukan (berkas di direktori kerja): gradle-{static,typed,arch,spotbugs,test,assemble}.log, gradle-exit.txt,
#   gradle-env.txt (opsional) + hasil build di app/build/**. Keluaran: laporan-untuk-claude.txt
#
# Prinsip: status tiap alat HANYA dari berkas laporan yang benar-benar ada; alat gagal/laporan absen = `NOT VERIFIED`
# (BUKAN lulus). Skrip ini TIDAK PERNAH gagal (exit 0) supaya artifact selalu terbentuk.

set +e

WS="${GITHUB_WORKSPACE:-$PWD}"
OUT=laporan-untuk-claude.txt
MAX_REPORT_BYTES=600000
DET_MAX=200
LINT_HEAD_MAX=120
LINT_FULL_MAX=400
TEST_MAX=40
KW_MAX=100
DEPR_MAX=60

DET_XML=app/build/reports/detekt/detekt.xml
DETT_XML=$(find app/build/reports/detekt -maxdepth 1 -name '*.xml' ! -name 'detekt.xml' 2>/dev/null | head -n 1)
LINT_XML=app/build/reports/lint-results-debug.xml
LINT_TXT=app/build/reports/lint-results-debug.txt
TEST_DIR=app/build/test-results/testDebugUnitTest
ARCH=app/build/reports/archunit/archunit-report.txt
SARIF=app/build/reports/spotbugs/spotbugs.sarif

exit_of() { sed -n "s/^$1=//p" gradle-exit.txt 2>/dev/null | head -n 1; }
STATIC_EXIT=$(exit_of STATIC_EXIT)
TYPED_EXIT=$(exit_of TYPED_EXIT)
TEST_EXIT=$(exit_of TEST_EXIT)
ARCH_EXIT=$(exit_of ARCH_EXIT)
SPOTBUGS_EXIT=$(exit_of SPOTBUGS_EXIT)
ASSEMBLE_EXIT=$(exit_of ASSEMBLE_EXIT)
STATIC_EXIT=${STATIC_EXIT:-n/a}
TYPED_EXIT=${TYPED_EXIT:-n/a}
TEST_EXIT=${TEST_EXIT:-n/a}
ARCH_EXIT=${ARCH_EXIT:-n/a}
SPOTBUGS_EXIT=${SPOTBUGS_EXIT:-n/a}
ASSEMBLE_EXIT=${ASSEMBLE_EXIT:-n/a}
A_VIOL=""
: > report-arch.tmp
: > report-sarif.tmp
: > report-static.tmp
: > report-test.tmp
: > report-kwarn.tmp
: > report-depr.tmp
: > report-asm.tmp

# Kutipan akar masalah dari log Gradle (tanpa baris stack frame): blok FAILURE/"What went wrong",
# baris kompilasi `e:`, task FAILED, lalu ekor log.
excerpt() {
  [ -f "$1" ] || { echo "(log $1 tidak ada)"; return; }
  echo "-- AKAR MASALAH (kutipan $1) --"
  sed -n '/^FAILURE:/,/^\* Try:/p' "$1" | grep -v -E '^\s+at ' | head -n 60
  grep -E '^e: |^> Task .* FAILED|Script compilation error|Could not resolve|Unresolved reference|No such file' "$1" | sed -E "s#^e: file://${WS}/#e: #" | head -n 30
  echo "-- EKOR LOG $1 (40 baris terakhir, tanpa stack frame) --"
  grep -v -E '^\s+at ' "$1" | tail -n 40
}

# detekt_report <label> <xml> <berkas-detail>: cetak 1 baris ringkasan, SEMUA temuan (maks DET_MAX) ditambahkan ke
# <berkas-detail>. Status 1 = laporan XML tidak ada (NOT VERIFIED).
detekt_report() {
  label="$1"; xml="$2"; out="$3"
  if [ -n "$xml" ] && [ -f "$xml" ]; then
    n=$(grep -c '<error ' "$xml" 2>/dev/null)
    n=${n:-0}
    if [ "$n" = "0" ]; then
      echo "$label: LULUS (0 temuan)"
    else
      echo "$label: $n temuan"
      {
        echo "$label (maks $DET_MAX):"
        awk 'function dec(x) { gsub(/&quot;/, "\"", x); gsub(/&apos;/, "\047", x); gsub(/&lt;/, "<", x); gsub(/&gt;/, ">", x); gsub(/&amp;/, "\\&", x); return x } /<file name=/ { match($0, /name="[^"]*"/); f = substr($0, RSTART + 6, RLENGTH - 7); sub(/.*\/com\/lagfix\/fstrim\//, "", f) } /<error / { match($0, /line="[0-9]+"/); l = substr($0, RSTART + 6, RLENGTH - 7); match($0, /message="[^"]*"/); m = dec(substr($0, RSTART + 9, RLENGTH - 10)); match($0, /source="[^"]*"/); s = substr($0, RSTART + 8, RLENGTH - 9); print "- " s " @ " f ":" l " - " substr(m, 1, 220) }' "$xml" | head -n "$DET_MAX"
        echo ""
      } >> "$out"
    fi
    return 0
  fi
  echo "$label: NOT VERIFIED (laporan XML tidak terbentuk; lihat kutipan log)"
  echo "Laporan XML $label tidak ada." >> "$out"
  return 1
}

# --- ArchUnit: baris [PASS] dibuang, hanya header + pelanggaran. Status HANYA dari berkas laporan yang ditulis
#     `ArchitectureTest` (ARCHUNIT_STATUS=OK); tak ada berkas / status bukan OK -> NOT VERIFIED (dibawa dari v150) ---
NEED_ARCH_LOG=0
if [ -s "$ARCH" ]; then
  A_STATUS=$(sed -n 's/^ARCHUNIT_STATUS=//p' "$ARCH" | head -n 1)
  if [ "$A_STATUS" = "OK" ]; then
    A_VIOL=$(sed -n 's/^VIOLATIONS=//p' "$ARCH" | head -n 1)
    A_RULES=$(sed -n 's/^RULES=//p' "$ARCH" | head -n 1)
    if [ "$A_VIOL" = "0" ]; then
      ARCH_SUMMARY="ArchUnit: LULUS (${A_RULES:-?} aturan, 0 pelanggaran)"
    else
      ARCH_SUMMARY="ArchUnit: ${A_VIOL:-?} PELANGGARAN"
    fi
  else
    ARCH_SUMMARY="ArchUnit: NOT VERIFIED (alat gagal jalan)"
    NEED_ARCH_LOG=1
  fi
  grep -v '^\[PASS\] ' "$ARCH" > report-arch.tmp
else
  ARCH_SUMMARY="ArchUnit: NOT VERIFIED (laporan tidak terbentuk: tes dilewati/gagal; lihat kutipan log)"
  echo "Berkas $ARCH tidak ada." > report-arch.tmp
  NEED_ARCH_LOG=1
fi
if [ "$NEED_ARCH_LOG" = "1" ] || [ "$ARCH_EXIT" != "0" ]; then
  { echo ""; excerpt gradle-arch.log; } >> report-arch.tmp
fi

# --- SpotBugs (SARIF): 1 baris per titik, duplikat digabung "xN" (dibawa APA ADANYA dari v150) ---
NEED_SB_LOG=0
if [ -s "$SARIF" ] && jq -e '.runs' "$SARIF" > /dev/null 2>&1 && S_COUNT=$(jq '[.runs[].results[]?] | length' "$SARIF" 2>/dev/null) && [ -n "$S_COUNT" ]; then
  if [ "$S_COUNT" = "0" ]; then
    SARIF_SUMMARY="SpotBugs: LULUS (0 temuan)"
  else
    SARIF_SUMMARY="SpotBugs: $S_COUNT temuan"
  fi
  {
    if [ "$S_COUNT" -gt 0 ]; then
      echo "Per aturan (terbanyak dulu):"
      jq -r '[.runs[].results[]?] | group_by(.ruleId) | map({r: .[0].ruleId, n: length}) | sort_by(-.n)[] | "  \(.n)x \(.r)"' "$SARIF" 2>/dev/null | head -n 40
      echo ""
      echo "Titik temuan (noise Kotlin disaring di config/spotbugs/exclude.xml; maks 120):"
      jq -r '[.runs[].results[]?] | map({k: "\(.ruleId) @ \(.locations[0].physicalLocation.artifactLocation.uri // "?"):\(.locations[0].physicalLocation.region.startLine // 0)"}) | group_by(.k) | map("- \(.[0].k)" + (if length > 1 then " x\(length)" else "" end)) | .[:120][]' "$SARIF" 2>/dev/null
    fi
  } > report-sarif.tmp
else
  SARIF_SUMMARY="SpotBugs: NOT VERIFIED (SARIF tidak terbentuk / tidak valid / jq gagal; lihat kutipan log)"
  echo "Berkas $SARIF tidak ada atau tidak bisa dibaca." > report-sarif.tmp
  NEED_SB_LOG=1
fi
if [ "$NEED_SB_LOG" = "1" ] || [ "$SPOTBUGS_EXIT" != "0" ]; then
  { echo ""; excerpt gradle-spotbugs.log; } >> report-sarif.tmp
fi

# --- detekt polos + detekt typed (detektDebug) + lintDebug ---
NEED_STATIC_LOG=0
NEED_TYPED_LOG=0
DET_SUMMARY=$(detekt_report "detekt" "$DET_XML" report-static.tmp) || NEED_STATIC_LOG=1
DETT_SUMMARY=$(detekt_report "detekt typed (detektDebug)" "$DETT_XML" report-static.tmp) || NEED_TYPED_LOG=1
DETEKT_TYPED_DIAG=$(grep -h -m1 'DETEKT_TYPED_DIAG' gradle-typed.log 2>/dev/null | head -n 1 | cut -c1-200)
DETEKT_TYPED_DIAG=${DETEKT_TYPED_DIAG:-n/a}
if [ -f "$LINT_XML" ]; then
  LNT=$(grep -c -E '<issue([[:space:]]|$)' "$LINT_XML" 2>/dev/null)
  LNE=$(grep -c -E 'severity="(Error|Fatal)"' "$LINT_XML" 2>/dev/null)
  LNW=$(grep -c 'severity="Warning"' "$LINT_XML" 2>/dev/null)
  LNI=$(grep -c -E 'severity="Information(al)?"' "$LINT_XML" 2>/dev/null)
  LN_DEP=$(grep -c 'id="GradleDependency"' "$LINT_XML" 2>/dev/null)
  LN_TGT=$(grep -c 'id="OldTargetApi"' "$LINT_XML" 2>/dev/null)
  LNT=${LNT:-0}
  LNE=${LNE:-0}
  LNW=${LNW:-0}
  LNI=${LNI:-0}
  LN_DEP=${LN_DEP:-0}
  LN_TGT=${LN_TGT:-0}
  if [ "$LNT" = "0" ]; then
    LINT_SUMMARY="lintDebug: LULUS (0 isu)"
  else
    LINT_SUMMARY="lintDebug: $LNT isu ($LNE error, $LNW warning, $LNI info; versi usang: GradleDependency=$LN_DEP, OldTargetApi=$LN_TGT)"
    {
      echo "lintDebug - isu per id (terbanyak dulu):"
      grep -o 'id="[A-Za-z0-9_]*"' "$LINT_XML" | sed -E 's/id="([^"]*)"/\1/' | sort | uniq -c | sort -rn | head -n 30
      echo ""
      echo "lintDebug - daftar isu (maks $LINT_HEAD_MAX):"
      grep -E '^[^[:space:]].*: (Fatal|Error|Warning|Information): ' "$LINT_TXT" 2>/dev/null | sed "s#^${WS}/##" | cut -c1-420 | head -n "$LINT_HEAD_MAX"
      echo ""
      echo "lintDebug - rincian lengkap dari lint-results-debug.txt (maks $LINT_FULL_MAX baris):"
      if [ -f "$LINT_TXT" ]; then sed "s#^${WS}/##" "$LINT_TXT" | cut -c1-420 | head -n "$LINT_FULL_MAX"; else echo "(lint-results-debug.txt tidak ada)"; fi
      echo ""
    } >> report-static.tmp
  fi
else
  LINT_SUMMARY="lintDebug: NOT VERIFIED (lint-results-debug.xml tidak terbentuk; lihat kutipan log)"
  echo "Berkas $LINT_XML tidak ada." >> report-static.tmp
  NEED_STATIC_LOG=1
fi
if [ "$NEED_STATIC_LOG" = "1" ] || [ "$STATIC_EXIT" != "0" ]; then
  { echo ""; excerpt gradle-static.log; } >> report-static.tmp
fi
if [ "$NEED_TYPED_LOG" = "1" ] || [ "$TYPED_EXIT" != "0" ]; then
  { echo ""; excerpt gradle-typed.log; } >> report-static.tmp
fi

# --- Tes unit (JUnit XML Gradle): status HANYA dari laporan tes, bukan dari nama kelas di log ---
NEED_TEST_LOG=0
if ls "$TEST_DIR"/*.xml > /dev/null 2>&1; then
  T_SUM=$(awk '/<testsuite / { match($0, / tests="[0-9]+"/); t += substr($0, RSTART + 8, RLENGTH - 9); match($0, / failures="[0-9]+"/); f += substr($0, RSTART + 11, RLENGTH - 12); match($0, / errors="[0-9]+"/); e += substr($0, RSTART + 9, RLENGTH - 10); match($0, / skipped="[0-9]+"/); s += substr($0, RSTART + 10, RLENGTH - 11) } END { print t + 0, f + 0, e + 0, s + 0 }' "$TEST_DIR"/*.xml)
  set -- $T_SUM
  T_TOTAL=${1:-0}; T_FAIL=$(( ${2:-0} + ${3:-0} )); T_SKIP=${4:-0}
  if [ "$T_TOTAL" = "0" ]; then
    TEST_SUMMARY="Tes unit: NOT VERIFIED (0 tes dieksekusi; lihat kutipan log)"
    NEED_TEST_LOG=1
  elif [ "$T_FAIL" = "0" ]; then
    TEST_SUMMARY="Tes unit: LULUS ($T_TOTAL tes, $T_SKIP dilewati)"
  else
    TEST_SUMMARY="Tes unit: GAGAL ($T_FAIL gagal dari $T_TOTAL tes)"
    {
      echo "Tes gagal (maks $TEST_MAX; tiap tes: pesan + hingga 8 frame stack):"
      awk -v max="$TEST_MAX" 'function dec(x) { gsub(/&quot;/, "\"", x); gsub(/&apos;/, "\047", x); gsub(/&lt;/, "<", x); gsub(/&gt;/, ">", x); gsub(/&amp;/, "\\&", x); return x } /<testcase / { match($0, /classname="[^"]*"/); c = substr($0, RSTART + 11, RLENGTH - 12); match($0, / name="[^"]*"/); n = dec(substr($0, RSTART + 7, RLENGTH - 8)); sub(/.*\./, "", c); body = 0 } /<(failure|error) / { if (shown >= max) next; shown++; match($0, /message="[^"]*"/); m = dec(substr($0, RSTART + 9, RLENGTH - 10)); print "- " c "." n ": " substr(m, 1, 200); body = 8; next } /<\/(failure|error)>/ { body = 0; next } body > 0 && /^[ \t]*(at |Caused by)/ { print "    " substr(dec($0), 1, 200); body-- }' "$TEST_DIR"/*.xml
    } >> report-test.tmp
  fi
else
  TEST_SUMMARY="Tes unit: NOT VERIFIED (laporan tes tidak terbentuk: kompilasi/tes gagal sebelum jalan; lihat kutipan log)"
  echo "Direktori $TEST_DIR tidak ada / kosong." >> report-test.tmp
  NEED_TEST_LOG=1
fi
if [ "$NEED_TEST_LOG" = "1" ] || { [ "$TEST_EXIT" != "0" ] && [ "${T_FAIL:-0}" = "0" ]; }; then
  { echo ""; excerpt gradle-test.log; } >> report-test.tmp
fi

# --- Peringatan kompilator Kotlin: baris `w: file://...` (kode app + skrip build), tanpa kode tes, dedupe ---
LOGS_ALL=""
for f in gradle-static.log gradle-typed.log gradle-arch.log gradle-spotbugs.log gradle-test.log gradle-assemble.log; do
  [ -f "$f" ] && LOGS_ALL="$LOGS_ALL $f"
done
if [ -n "$LOGS_ALL" ] && grep -q -E '^> Task :app:compileDebugKotlin$' $LOGS_ALL 2>/dev/null; then
  grep -h '^w: file://' $LOGS_ALL 2>/dev/null | grep -v -E '/src/(test|androidTest)/' | sed -E "s#^w: file://${WS}/#w: #" | sed -E 's#^w: file://.*/com/lagfix/fstrim/#w: #' | cut -c1-200 | sort -u > kwarn.list
  KW=$(wc -l < kwarn.list | tr -d ' ')
  KW=${KW:-0}
  if [ "$KW" = "0" ]; then
    KWARN_SUMMARY="Peringatan kompilator Kotlin: LULUS (0)"
  else
    KWD=$(grep -c -i 'deprecated' kwarn.list)
    KWD=${KWD:-0}
    KWARN_SUMMARY="Peringatan kompilator Kotlin: $KW ($KWD deprecated, $((KW - KWD)) lainnya)"
    {
      if [ "$((KW - KWD))" -gt 0 ]; then
        echo "Selain deprecated (maks $KW_MAX):"
        grep -v -i 'deprecated' kwarn.list | head -n "$KW_MAX"
      fi
      if [ "$KWD" -gt 0 ]; then
        echo "Deprecated (maks $KW_MAX):"
        grep -i 'deprecated' kwarn.list | head -n "$KW_MAX"
      fi
    } > report-kwarn.tmp
  fi
else
  KWARN_SUMMARY="Peringatan kompilator Kotlin: NOT VERIFIED (tak ada tugas :app:compileDebugKotlin yang dieksekusi / log tidak ada)"
  echo "Tak ada baris '> Task :app:compileDebugKotlin' yang dieksekusi di log Gradle manapun." > report-kwarn.tmp
fi

# --- Deprecated dari Gradle/AGP/plugin (`--warning-mode all`): pesan + frame pertama (lokasi skrip), dedupe ---
if [ -n "$LOGS_ALL" ]; then
  # shellcheck disable=SC2086
  awk '
    function flush() { if (msg != "") { print msg; msg = "" } }
    /^w: / { flush(); next }
    /has been deprecated|is deprecated|scheduled to be removed|will fail with an error in Gradle|^WARNING:.*deprecated/ {
      flush(); msg = $0; sub(/^[ \t]+/, "", msg); next
    }
    msg != "" && /^[ \t]+at / { l = $0; sub(/^[ \t]+/, "", l); print msg " @ " l; msg = ""; next }
    { flush() }
    END { flush() }
  ' $LOGS_ALL 2>/dev/null | sed -E "s#${WS}/##g" | cut -c1-300 | sort | uniq -c | sort -rn > depr.list
  DEPR_N=$(wc -l < depr.list | tr -d ' ')
  DEPR_N=${DEPR_N:-0}
  DEPR_SUMMARY_LINE=$(grep -h -m1 'Deprecated Gradle features were used' $LOGS_ALL 2>/dev/null | head -n 1 | cut -c1-200)
  if [ "$DEPR_N" = "0" ] && [ -z "$DEPR_SUMMARY_LINE" ]; then
    DEPR_SUMMARY="Deprecated Gradle/AGP/plugin: LULUS (0)"
  else
    DEPR_SUMMARY="Deprecated Gradle/AGP/plugin: $DEPR_N pesan unik"
    {
      [ -n "$DEPR_SUMMARY_LINE" ] && echo "Ringkasan Gradle: $DEPR_SUMMARY_LINE"
      echo "Pesan deprecated unik (jumlah muncul, pesan @ lokasi; maks $DEPR_MAX):"
      head -n "$DEPR_MAX" depr.list
    } > report-depr.tmp
  fi
else
  DEPR_SUMMARY="Deprecated Gradle/AGP/plugin: NOT VERIFIED (log Gradle tidak ada)"
  echo "Tak ada log Gradle." > report-depr.tmp
fi

# --- Build APK debug (assembleDebug): lulus HANYA bila exit 0 DAN berkas APK ada ---
APK_FOUND=$(find app/build/outputs/apk/debug -name '*.apk' -type f 2>/dev/null | head -n 1)
if [ "$ASSEMBLE_EXIT" = "n/a" ]; then
  ASM_SUMMARY="Build APK debug: NOT VERIFIED (assembleDebug tidak dijalankan)"
  echo "assembleDebug tidak dijalankan." > report-asm.tmp
elif [ "$ASSEMBLE_EXIT" = "0" ] && [ -n "$APK_FOUND" ]; then
  ASM_SUMMARY="Build APK debug: LULUS (assembleDebug exit 0, APK terbentuk)"
else
  ASM_SUMMARY="Build APK debug: GAGAL (assembleDebug exit $ASSEMBLE_EXIT, APK ${APK_FOUND:-tidak ada})"
  excerpt gradle-assemble.log > report-asm.tmp
fi

# --- Penanda proyek & data mentah lingkungan ---
APP_NAME=$(grep -m1 'name="app_name"' app/src/main/res/values/strings.xml 2>/dev/null | sed -E 's/.*>([^<]*)<.*/\1/')
APPLICATION_ID=$(grep -m1 'applicationId' app/build.gradle.kts 2>/dev/null | sed -E 's/.*"([^"]+)".*/\1/')
GRADLE_ROOT=$(grep -m1 'rootProject.name' settings.gradle.kts 2>/dev/null | sed -E 's/.*"([^"]+)".*/\1/')
BATCH_PS=$(grep -o -m1 'RESUME POINT: v[0-9]*' PROJECT_STATE.md 2>/dev/null | grep -o '[0-9]*$')
AGP_V=$(grep -m1 'com.android.application' build.gradle.kts 2>/dev/null | sed -E 's/.*version "([^"]+)".*/\1/')
KOTLIN_V=$(grep -m1 'org.jetbrains.kotlin.android' build.gradle.kts 2>/dev/null | sed -E 's/.*version "([^"]+)".*/\1/')
DETEKT_V=$(grep -m1 'io.gitlab.arturbosch.detekt' build.gradle.kts 2>/dev/null | sed -E 's/.*version "([^"]+)".*/\1/')
COMPILE_SDK=$(grep -m1 'compileSdk' app/build.gradle.kts 2>/dev/null | sed -E 's/.*= *([0-9.]+).*/\1/')
TARGET_SDK=$(grep -m1 'targetSdk' app/build.gradle.kts 2>/dev/null | sed -E 's/.*= *([0-9.]+).*/\1/')
ENV_LINES=$(cat gradle-env.txt 2>/dev/null)

{
  echo "=== LAPORAN EVALUASI UNTUK CLAUDE (berkas tunggal, paket komplit) ==="
  echo "Dibuat (UTC): $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo ""
  echo "--- PENANDA PROYEK (WAJIB cocok sebelum dipakai) ---"
  echo "PROJECT=$REPO_NAME"
  echo "APP_NAME=${APP_NAME:-n/a}"
  echo "APPLICATION_ID=${APPLICATION_ID:-n/a}"
  echo "GRADLE_ROOT_PROJECT=${GRADLE_ROOT:-n/a}"
  echo "BATCH_PROJECT_STATE=${BATCH_PS:-n/a}"
  echo "COMMIT=$COMMIT_SHA"
  echo "BRANCH=$REF_NAME"
  echo "RUN_NUMBER=$RUN_NUMBER (counter workflow laporan; BUKAN versionCode APK) · RUN_ID=$RUN_ID"
  echo "DETEKT_TYPED_DIAG=$DETEKT_TYPED_DIAG"
  echo "Gradle exit: detekt+lintDebug=$STATIC_EXIT · detektDebug=$TYPED_EXIT · ArchUnit=$ARCH_EXIT · SpotBugs=$SPOTBUGS_EXIT · assembleDebug=$ASSEMBLE_EXIT · tes unit=$TEST_EXIT"
  echo ""
  echo "--- LINGKUNGAN (data mentah) ---"
  echo "TOOLCHAIN_PROYEK: AGP=${AGP_V:-n/a} Kotlin=${KOTLIN_V:-n/a} detekt=${DETEKT_V:-n/a} compileSdk=${COMPILE_SDK:-n/a} targetSdk=${TARGET_SDK:-n/a}"
  if [ -n "$ENV_LINES" ]; then echo "$ENV_LINES"; else echo "(gradle-env.txt tidak ada)"; fi
  echo ""
  echo "--- RINGKASAN ---"
  echo "$ARCH_SUMMARY"
  echo "$SARIF_SUMMARY"
  echo "$DET_SUMMARY"
  echo "$DETT_SUMMARY"
  echo "$LINT_SUMMARY"
  echo "$TEST_SUMMARY"
  echo "$KWARN_SUMMARY"
  echo "$DEPR_SUMMARY"
  echo "$ASM_SUMMARY"
  echo "Catatan: NOT VERIFIED = alat tidak jalan / laporan tidak ada; BUKAN berarti bersih."
  if [ "$A_VIOL" != "0" ] || [ "$NEED_ARCH_LOG" = "1" ] || [ "$ARCH_EXIT" != "0" ]; then
    echo ""
    echo "--- ARSITEKTUR (ARCHUNIT) ---"
    cat report-arch.tmp
  fi
  if [ -s report-sarif.tmp ]; then
    echo ""
    echo "--- KUALITAS KODE (SPOTBUGS · SARIF) ---"
    cat report-sarif.tmp
  fi
  if [ -s report-static.tmp ]; then
    echo ""
    echo "--- STATIC ANALYSIS (DETEKT · LINT) ---"
    cat report-static.tmp
  fi
  if [ -s report-test.tmp ]; then
    echo ""
    echo "--- TES UNIT ---"
    cat report-test.tmp
  fi
  if [ -s report-kwarn.tmp ]; then
    echo ""
    echo "--- PERINGATAN KOMPILATOR KOTLIN ---"
    cat report-kwarn.tmp
  fi
  if [ -s report-depr.tmp ]; then
    echo ""
    echo "--- DEPRECATED (GRADLE · AGP · PLUGIN) ---"
    cat report-depr.tmp
  fi
  if [ -s report-asm.tmp ]; then
    echo ""
    echo "--- BUILD APK DEBUG (assembleDebug) ---"
    cat report-asm.tmp
  fi
  echo ""
  echo "=== AKHIR LAPORAN (berkas lengkap) ==="
} > "$OUT"

# Pagar ukuran: dipotong HANYA bila melewati batas; penanda akhir hilang = laporan terpotong.
if [ "$(wc -c < "$OUT" | tr -d ' ')" -gt "$MAX_REPORT_BYTES" ]; then
  head -c "$MAX_REPORT_BYTES" "$OUT" > "$OUT.cut"
  { echo ""; echo "=== LAPORAN DIPOTONG di $MAX_REPORT_BYTES byte (tanpa penanda AKHIR LAPORAN) ==="; } >> "$OUT.cut"
  mv "$OUT.cut" "$OUT"
fi

echo "::notice::$ARCH_SUMMARY · $SARIF_SUMMARY · $DET_SUMMARY · $DETT_SUMMARY · $LINT_SUMMARY · $TEST_SUMMARY · $KWARN_SUMMARY · $DEPR_SUMMARY · $ASM_SUMMARY"
if [ -n "$GITHUB_STEP_SUMMARY" ]; then
  {
    echo "### 🔎 Laporan untuk Claude (non-blocking; lint+detekt HANYA di workflow ini sejak v151)"
    echo ""
    echo "- $ARCH_SUMMARY"
    echo "- $SARIF_SUMMARY"
    echo "- $DET_SUMMARY"
    echo "- $DETT_SUMMARY"
    echo "- $LINT_SUMMARY"
    echo "- $TEST_SUMMARY"
    echo "- $KWARN_SUMMARY"
    echo "- $DEPR_SUMMARY"
    echo "- $ASM_SUMMARY"
    echo "- Artifact (1 berkas): \`LagFix_laporan-untuk-claude_run${RUN_NUMBER}\`"
  } >> "$GITHUB_STEP_SUMMARY"
fi
cat "$OUT"
exit 0
