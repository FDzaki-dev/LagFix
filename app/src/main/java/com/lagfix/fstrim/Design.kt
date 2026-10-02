@file:Suppress("MagicNumber") // file token: angka literal (warna/dp) memang tempatnya di sini

package com.lagfix.fstrim

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// v61: sumber tunggal token UI — DARK ONLY + gaya Glassmorphism & Glow (keputusan user).
// Visual-only: 0 perubahan state/callback/logic. Mode terang & "ikuti sistem" dihapus dari UI
// (ThemeMode/Prefs.themeMode tetap ada tapi tak dipakai: Prefs DO-NOT-TOUCH).
//
// Palet: midnight blue (dasar + glow utama) + 2 sekunder senada: cyan (dingin, kontras lembut) dan
// violet (hangat-dingin, menjembatani ke biru). Tidak ada putih/hitam pekat: teks lavender-putih
// #EDF0FF, latar #060A1E-#0A1233 -> tidak silau, nyaman di mata. Glow berintensitas rendah (alpha
// 0.12-0.22) dan statis (0 animasi -> 0 beban baterai/recomposition).
//
// Kaca = pendekatan Compose tanpa dependency: kartu translusen (gradien periwinkle alpha ~0.10)
// di atas backdrop berglow + border gradien 1dp + glow sudut dalam. BUKAN blur backdrop sungguhan
// (Modifier.blur hanya mengaburkan konten sendiri, bukan latar di belakangnya).
// Kontras (WCAG 2.x, dihitung di skenario terburuk: 3 glow menumpuk + fill kaca + glow sudut):
// onSurface 7.5, onSurfaceVariant 5.3, primary 4.6, secondary 5.5, tertiary 4.8, error >=4.5,
// success 5.6, warning 5.2, outline 3.0 (komponen >=3:1).

// Skala spasi. Nilai = literal lama di MainActivity.kt (16/12/8/4 dp) -> layout tidak bergeser.
// 6.dp (3x spacedBy) SENGAJA tak dimigrasi: bukan bagian skala 4/8 (keputusan M3/M5).
internal object LagSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp // belum dipakai; disiapkan untuk fase M3+
}

// Warna status Riwayat/Statistik (dipasang via CompositionLocal). `warning` = status "dilewati"
// (precondition Shizuku, bukan error eksekusi; v13 B4). Mint & amber lembut, selaras palet glass.
@Immutable
internal data class StatusColors(val success: Color, val warning: Color)

private val glassStatusColors = StatusColors(success = Color(0xFF6EE7B7), warning = Color(0xFFFBC06B))

internal val LocalStatusColors = staticCompositionLocalOf { glassStatusColors }

// --- Palet dasar ---------------------------------------------------------------------------------
private val MidnightDeep = Color(0xFF060A1E)
private val Midnight = Color(0xFF0A1233)
private val MidnightLow = Color(0xFF070C24)

// Glow (warna dasar; alpha diatur di titik pakai).
private val GlowBlue = Color(0xFF2F4FD8) // midnight blue terang (aksen utama)
private val GlowCyan = Color(0xFF22C6E0) // sekunder 1
private val GlowViolet = Color(0xFF8B5CF6) // sekunder 2

private val glassScheme = darkColorScheme(
    primary = Color(0xFFA3BEFF),
    onPrimary = Color(0xFF0A1440),
    primaryContainer = Color(0xFF1F2F7A),
    onPrimaryContainer = Color(0xFFDCE6FF),
    inversePrimary = Color(0xFFA3BEFF),
    secondary = Color(0xFF6FE0F2),
    onSecondary = Color(0xFF002B33),
    secondaryContainer = Color(0xFF0E4A5C),
    onSecondaryContainer = Color(0xFFCFF5FC),
    tertiary = Color(0xFFC9B8FF),
    onTertiary = Color(0xFF24104F),
    tertiaryContainer = Color(0xFF3D2A7A),
    onTertiaryContainer = Color(0xFFE8DEFF),
    background = Midnight,
    onBackground = Color(0xFFEDF0FF),
    surface = Color(0xFF0E1634),
    onSurface = Color(0xFFEDF0FF),
    surfaceVariant = Color(0xFF1A2550),
    onSurfaceVariant = Color(0xFFC3CBEC),
    inverseSurface = Color(0xFF1C2A66),
    inverseOnSurface = Color(0xFFEAF0FF),
    error = Color(0xFFFFB0B7),
    onError = Color(0xFF3F0710),
    errorContainer = Color(0xFF5A1A24),
    onErrorContainer = Color(0xFFFFD9DC),
    outline = Color(0xFF8C98CC),
    outlineVariant = Color(0xFF2C3868),
    surfaceDim = MidnightDeep,
    surfaceBright = Color(0xFF243063),
    surfaceContainerLowest = Color(0xFF0A1029),
    surfaceContainerLow = Color(0xFF0D1531),
    surfaceContainer = Color(0xFF111A3C),
    surfaceContainerHigh = Color(0xFF172247),
    surfaceContainerHighest = Color(0xFF1D2A56)
)

// Bentuk lebih membulat: kesan panel kaca lembut. Radius besar utk kartu (large) & dialog (extraLarge).
private val glassShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// Tipografi: sans-serif sistem (0 dependency/font file), ukuran & line-height = default M3 (layout tak
// bergeser). Hierarki lewat bobot: display Light, judul SemiBold + glow halus (Shadow, blur 16px),
// body Normal, label Medium. Catatan: font sistem tanpa bobot 600 jatuh ke Bold; judul terpanjang
// ("Keandalan latar belakang (opsional)") perlu dicek tak membungkus di layar sempit.
private val glassFont = FontFamily.SansSerif
private val titleGlow = Shadow(color = Color(0x668EAEFF), offset = Offset.Zero, blurRadius = 16f)

private fun TextStyle.glass(weight: FontWeight, glow: Boolean = false): TextStyle =
    copy(fontFamily = glassFont, fontWeight = weight, shadow = if (glow) titleGlow else null)

private val baseTypography = Typography()
private val lagTypography = baseTypography.copy(
    displayLarge = baseTypography.displayLarge.glass(FontWeight.Light),
    displayMedium = baseTypography.displayMedium.glass(FontWeight.Light),
    displaySmall = baseTypography.displaySmall.glass(FontWeight.Light),
    headlineLarge = baseTypography.headlineLarge.glass(FontWeight.Normal),
    headlineMedium = baseTypography.headlineMedium.glass(FontWeight.Normal),
    headlineSmall = baseTypography.headlineSmall.glass(FontWeight.Normal),
    titleLarge = baseTypography.titleLarge.glass(FontWeight.SemiBold, glow = true),
    titleMedium = baseTypography.titleMedium.glass(FontWeight.SemiBold, glow = true),
    titleSmall = baseTypography.titleSmall.glass(FontWeight.Medium),
    bodyLarge = baseTypography.bodyLarge.glass(FontWeight.Normal),
    bodyMedium = baseTypography.bodyMedium.glass(FontWeight.Normal),
    bodySmall = baseTypography.bodySmall.glass(FontWeight.Normal),
    labelLarge = baseTypography.labelLarge.glass(FontWeight.Medium),
    labelMedium = baseTypography.labelMedium.glass(FontWeight.Medium),
    labelSmall = baseTypography.labelSmall.glass(FontWeight.Medium)
)

// --- Backdrop berglow (latar seluruh layar) ------------------------------------------------------
// Gradien vertikal midnight + 3 glow radial statis (biru kiri-atas, cyan kanan-tengah, violet
// kiri-bawah). Glow = sumber "cahaya" di belakang kaca; tanpanya kartu translusen terlihat datar.
private val backdropBase = Brush.verticalGradient(listOf(MidnightDeep, Midnight, MidnightLow))

private fun Modifier.glassBackdrop(): Modifier = this
    .background(backdropBase)
    .drawBehind {
        val reach = size.maxDimension
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(GlowBlue.copy(alpha = 0.22f), GlowBlue.copy(alpha = 0f)),
                center = Offset(size.width * 0.15f, size.height * 0.08f),
                radius = reach * 0.75f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(GlowCyan.copy(alpha = 0.12f), GlowCyan.copy(alpha = 0f)),
                center = Offset(size.width * 0.95f, size.height * 0.45f),
                radius = reach * 0.6f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(GlowViolet.copy(alpha = 0.14f), GlowViolet.copy(alpha = 0f)),
                center = Offset(size.width * 0.1f, size.height * 0.95f),
                radius = reach * 0.65f
            )
        )
    }

// --- Kartu kaca ----------------------------------------------------------------------------------
// Pengganti `Card(Modifier.fillMaxWidth())` di MainActivity.kt (signature & isi ColumnScope identik).
// Isi: fill gradien translusen + glow sudut kiri-atas + border gradien 1dp (terang di sudut atas,
// memudar ke bawah) = tepi kaca yang memantulkan cahaya.
private val glassFill = Brush.linearGradient(listOf(Color(0x1AA9BCFF), Color(0x08FFFFFF)))
private val glassBorder = Brush.linearGradient(
    listOf(Color(0x66FFFFFF), Color(0x1F6F8CFF), Color(0x0DFFFFFF))
)

// --- Motion (v71, M4) ----------------------------------------------------------------------------
// Semua animasi app = tween berdurasi tetap (<= 300 ms per animasi, P4), BUKAN loop abadi (baterai). Compose
// membaca skala animasi sistem (Opsi Pengembang) -> 0x = langsung ke keadaan akhir (INFERENSI, belum
// diuji device).
internal object LagMotion {
    const val TAB_OUT_MS = 90 // ganti tab: tab lama memudar dulu (v74); tab diganti saat alpha 0 (v75)
    const val TAB_IN_MS = 180 // ganti tab: tab baru memudar masuk setelah frame komposisi berat lewat (v75)
    const val CONTENT_MS = 250 // kartu menyesuaikan tinggi saat isinya berubah
    const val FADE_MS = 150 // progress run muncul/hilang
}

// `animateSize = true` -> tinggi kartu berubah halus saat isinya berubah (mis. baris Riwayat baru,
// status pembaruan). animateContentSize diletakkan PALING DALAM (setelah border) supaya latar kaca &
// tepi ikut tumbuh bersama isi, bukan terpotong. Default false = semua pemanggil lama identik.
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    animateSize: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = modifier
                .clip(shape)
                .background(glassFill)
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(GlowBlue.copy(alpha = 0.16f), GlowBlue.copy(alpha = 0f)),
                            center = Offset.Zero,
                            radius = size.width * 0.8f
                        )
                    )
                }
                .border(1.dp, glassBorder, shape)
                .then(
                    if (animateSize) Modifier.animateContentSize(animationSpec = tween(durationMillis = LagMotion.CONTENT_MS))
                    else Modifier
                ),
            content = content
        )
    }
}

// --- Bar atas/bawah kaca -------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun glassTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
    titleContentColor = MaterialTheme.colorScheme.onBackground
)

// Bilah bawah translusen (alpha 0.72) di atas backdrop berglow + garis tepi atas tipis.
internal val GlassNavContainer = Color(0xB80B1233)

internal fun Modifier.glassTopEdge(): Modifier = this.drawWithContent {
    drawContent()
    drawLine(
        brush = Brush.horizontalGradient(
            listOf(Color(0x1AFFFFFF), Color(0x4D8EAEFF), Color(0x1AFFFFFF))
        ),
        start = Offset.Zero,
        end = Offset(size.width, 0f),
        strokeWidth = 2f
    )
}

@Composable
internal fun LagFixTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides glassStatusColors) {
        MaterialTheme(colorScheme = glassScheme, shapes = glassShapes, typography = lagTypography) {
            // Latar translusen -> tanpa Surface yang mengisi LocalContentColor; set eksplisit supaya Text
            // default tidak jatuh ke hitam (default LocalContentColor = Black).
            CompositionLocalProvider(LocalContentColor provides glassScheme.onBackground) {
                Box(Modifier.fillMaxSize().glassBackdrop()) { content() }
            }
        }
    }
}
