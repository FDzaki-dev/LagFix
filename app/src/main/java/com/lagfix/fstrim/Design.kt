@file:Suppress("MagicNumber") // file token: angka literal (warna/dp) memang tempatnya di sini

package com.lagfix.fstrim

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// v60 (M1, PENDING_UIUX_MILESTONE.md): sumber tunggal token UI. Skema "calm" + shapes + LagFixTheme
// DIPINDAH dari MainActivity.kt (v10) — nilai lama dipertahankan kecuali yang tercatat di bawah.
// Visual-only: 0 perubahan state/callback/logic.

// Skala spasi. Nilai = literal lama di MainActivity.kt (16/12/8/4 dp) -> layout tidak bergeser.
// 6.dp (3x spacedBy) SENGAJA tak dimigrasi: bukan bagian skala 4/8 (keputusan M3/M5).
internal object LagSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp // belum dipakai; disiapkan untuk fase M3+
}

// Warna status Riwayat/Statistik (sebelumnya val statis successGreen/skippedAmber di MainActivity.kt,
// sama di kedua tema -> kontras gelap 2.83:1 / 3.42:1, A7). Kini per-tema, dipasang via CompositionLocal.
// `warning` = status "dilewati" (precondition Shizuku, bukan error eksekusi; v13 B4).
// Kontras dihitung (WCAG 2.x): terang >=4.75 di putih/E6E6EE & >=5.4 di F4F4F8; gelap >=5.7 di 262933/33363F.
@Immutable
internal data class StatusColors(val success: Color, val warning: Color)

private val lightStatusColors = StatusColors(success = Color(0xFF276F2D), warning = Color(0xFF94550A))
private val darkStatusColors = StatusColors(success = Color(0xFF7FCB8A), warning = Color(0xFFE3A951))

internal val LocalStatusColors = staticCompositionLocalOf { lightStatusColors }

// Palet kustom "calm" (v10): biru-lavender lembut + sage muted, terinspirasi Cupertino/iOS; dark = navy-charcoal
// lembut #1C1E27 (bukan hitam pekat). Perubahan v60 (semua dihitung kontrasnya, lihat dokumen M1):
//  - error terang C0524B -> B03A34 (teks error di kartu >=4.5:1); outline terang B8B9C6 -> 84869A & gelap 6E7180 -> 858899
//    (batas komponen >=3:1: chip/field/switch off); inversePrimary gelap 4A59BD (aksi snackbar >=4.5:1).
//  - role yang dulu jatuh ke default baseline M3 (nada ungu-krem, tak seragam dgn calm) kini eksplisit:
//    surfaceContainer*, surfaceBright/Dim, errorContainer, tertiary*, inverse*, outlineVariant.
//  - terang: seluruh container putih (gaya grouped Cupertino; kartu terpisah dari latar F4F4F8, bukan lewat nada).
//    Card filled M3 = surfaceContainerHighest (INFERENSI utk material3 1.3.0 dari BOM 2024.10.01; belum dicek di device).
private val calmLightScheme = lightColorScheme(
    primary = Color(0xFF5A6ACF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E3FA),
    onPrimaryContainer = Color(0xFF1B2560),
    inversePrimary = Color(0xFFA9B4F2),
    secondary = Color(0xFF6E8A7C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEAE1),
    onSecondaryContainer = Color(0xFF1E2E26),
    tertiary = Color(0xFFB98A5E),
    onTertiary = Color(0xFF2A1B0A),
    tertiaryContainer = Color(0xFFF3E4D2),
    onTertiaryContainer = Color(0xFF3A2810),
    background = Color(0xFFF4F4F8),
    onBackground = Color(0xFF2B2C33),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B2C33),
    surfaceVariant = Color(0xFFE6E6EE),
    onSurfaceVariant = Color(0xFF5B5C66),
    inverseSurface = Color(0xFF2F3038),
    inverseOnSurface = Color(0xFFF1F1F6),
    error = Color(0xFFB03A34),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE4E2),
    onErrorContainer = Color(0xFF5C1A16),
    outline = Color(0xFF84869A),
    outlineVariant = Color(0xFFD4D5E0),
    surfaceDim = Color(0xFFDCDCE4),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF)
)

private val calmDarkScheme = darkColorScheme(
    primary = Color(0xFFA9B4F2),
    onPrimary = Color(0xFF1C2557),
    primaryContainer = Color(0xFF394487),
    onPrimaryContainer = Color(0xFFE1E3FA),
    inversePrimary = Color(0xFF4A59BD),
    secondary = Color(0xFF9FC0AE),
    onSecondary = Color(0xFF17301F),
    secondaryContainer = Color(0xFF32493B),
    onSecondaryContainer = Color(0xFFDCEAE1),
    tertiary = Color(0xFFD9B287),
    onTertiary = Color(0xFF3A2810),
    tertiaryContainer = Color(0xFF5A4228),
    onTertiaryContainer = Color(0xFFF3E4D2),
    background = Color(0xFF1C1E27),
    onBackground = Color(0xFFE7E7ED),
    surface = Color(0xFF262933),
    onSurface = Color(0xFFE7E7ED),
    surfaceVariant = Color(0xFF33363F),
    onSurfaceVariant = Color(0xFFC2C3CC),
    inverseSurface = Color(0xFFE7E7ED),
    inverseOnSurface = Color(0xFF2B2C33),
    error = Color(0xFFE0918B),
    onError = Color(0xFF3A1210),
    errorContainer = Color(0xFF5A2420),
    onErrorContainer = Color(0xFFF5C4C0),
    outline = Color(0xFF858899),
    outlineVariant = Color(0xFF454856),
    surfaceDim = Color(0xFF171921),
    surfaceBright = Color(0xFF3A3D49),
    surfaceContainerLowest = Color(0xFF171921),
    surfaceContainerLow = Color(0xFF20222B),
    surfaceContainer = Color(0xFF262933),
    surfaceContainerHigh = Color(0xFF2D303A),
    surfaceContainerHighest = Color(0xFF33363F)
)

// Sudut lebih membulat drpd default M3 — kesan kartu ala Cupertino/iOS (soft rounded). Nilai v10, tak berubah.
private val calmShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Tipografi: ukuran, line-height, letter-spacing = default M3 (layout tak bergeser); satu-satunya beda = bobot
// judul kartu (titleMedium) Medium -> SemiBold. Catatan: font sistem tanpa bobot 600 (mis. Roboto statis)
// jatuh ke Bold; judul terpanjang ("Keandalan latar belakang (opsional)") perlu dicek tak membungkus di layar sempit.
private val baseTypography = Typography()
private val lagTypography = baseTypography.copy(
    titleMedium = baseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
)

@Composable
internal fun LagFixTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) calmDarkScheme else calmLightScheme
    val status = if (dark) darkStatusColors else lightStatusColors
    CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(colorScheme = scheme, shapes = calmShapes, typography = lagTypography, content = content)
    }
}
