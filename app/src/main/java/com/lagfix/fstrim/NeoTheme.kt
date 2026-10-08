@file:Suppress("MagicNumber") // file token: angka literal (warna/dp/alpha) memang tempatnya di sini

package com.lagfix.fstrim

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// v142: tema ke-2 "Neo Burgundy" (Neumorphism, DARK ONLY). Dipilih user di Pengaturan > Tampilan; Glass tetap default.
//
// Prinsip (keputusan user: realistis, BUKAN trik cahaya murahan yang menyatu dgn latar):
// 1. Pelat timbul SELALU beda nada dgn latar (pelat lebih terang; rasio kontras plat/latar dijaga tes), jadi tak
//    "nyaru" walau bayangan gelap tak terlihat di AMOLED.
// 2. Satu sumber cahaya (kiri-atas) konsisten: bayangan jatuh ke kanan-bawah, tepi pelat memantulkan cahaya di
//    kiri-atas dan gelap di kanan-bawah (bevel 1,2dp), permukaan sedikit cembung (gradien diagonal halus).
// 3. Bayangan = DUA lapis fisik (ambient lebar-lembut + kontak sempit-tegas), keduanya hitam-anggur. TANPA bayangan
//    putih/terang tandingan (itulah trik murahan yg membuat pelat menyatu dgn latar).
// 4. Cekung (sumur) = gradien lebih gelap + bayangan dalam berjenjang dari tepi kiri-atas + bibir terang tipis di
//    kanan-bawah. Dipakai utk lencana, trek switch, chip terpilih, dan tombol saat ditekan.
// Semua digambar statis dgn jenjang bentuk (drawRoundRect), tanpa BlurMaskFilter/dependency/animasi abadi.
//
// Palet: burgundy (primer) + sage (sekunder, tenang & senada) + champagne (aksen tersier, tipis). Teks gading hangat.
// Kontras (WCAG 2.x, dihitung skrip; dijaga NeoThemeTest): onSurface 11.1, onSurfaceVariant 7.1, primary 5.7,
// secondary 7.3, tertiary 8.2, error 6.8, success 7.4, warning 7.7 (semua di atas pelat terterang); tombol
// burgundy teks gading >= 6.6; knob switch vs trek >= 3.2.
internal object NeoPalette {
    val BackdropTop = Color(0xFF2C1B20)
    val BackdropBottom = Color(0xFF1F1115)
    val PlateTop = Color(0xFF3E2830)
    val PlateBottom = Color(0xFF2F1E24)
    val ControlTop = Color(0xFF3A252C)
    val ControlBottom = Color(0xFF2E1D23)
    val WellTop = Color(0xFF1A0D11)
    val WellBottom = Color(0xFF26161B)
    val Shadow = Color(0xFF0A0407)
    val InsetShadow = Color(0xFF07020A)
    val Highlight = Color(0xFFFFE8E0)
    val Ivory = Color(0xFFF1E7DB)
    val Muted = Color(0xFFC9B8B2)
    val Rose = Color(0xFFDA95A1)
    val Sage = Color(0xFFB5C2AA)
    val SageText = Color(0xFFE3EDD8)
    val Champagne = Color(0xFFDCC79C)
    val BurgundyTop = Color(0xFF8E2C45)
    val BurgundyBottom = Color(0xFF611828)
    val BurgundyPressedTop = Color(0xFF4A1626)
    val BurgundyPressedBottom = Color(0xFF5A1D30)
    val SageTop = Color(0xFF46543F)
    val SageBottom = Color(0xFF34402F)
    val SagePressedTop = Color(0xFF2A3326)
    val SagePressedBottom = Color(0xFF34402F)
    val DisabledTop = Color(0xFF35252B)
    val DisabledBottom = Color(0xFF2D1F24)
    val TrackOnTop = Color(0xFF3A0F1C)
    val TrackOnBottom = Color(0xFF4A1626)
    val KnobOnTop = Color(0xFFD86A86)
    val KnobOnBottom = Color(0xFFBE5270)
    val KnobOffTop = Color(0xFF948082)
    val KnobOffBottom = Color(0xFF7C696D)
    val NavBar = Color(0xFF2A1A20)
    val Success = Color(0xFF9CCB9F)
    val Warning = Color(0xFFE6BE6A)
}

internal val neoScheme = darkColorScheme(
    primary = NeoPalette.Rose,
    onPrimary = Color(0xFF2E0A15),
    primaryContainer = Color(0xFF6B1A2D),
    onPrimaryContainer = Color(0xFFF8DCE1),
    inversePrimary = Color(0xFF8C2D45),
    secondary = NeoPalette.Sage,
    onSecondary = Color(0xFF1F2B1B),
    secondaryContainer = Color(0xFF34402F),
    onSecondaryContainer = Color(0xFFE0EBD5),
    tertiary = NeoPalette.Champagne,
    onTertiary = Color(0xFF2E2410),
    tertiaryContainer = Color(0xFF4D3F1F),
    onTertiaryContainer = Color(0xFFF4E6C4),
    background = Color(0xFF241519),
    onBackground = NeoPalette.Ivory,
    surface = Color(0xFF2B1B20),
    onSurface = NeoPalette.Ivory,
    surfaceVariant = Color(0xFF3A262D),
    onSurfaceVariant = NeoPalette.Muted,
    inverseSurface = Color(0xFFEADFD3),
    inverseOnSurface = Color(0xFF2C1F1F),
    error = Color(0xFFF5A29A),
    onError = Color(0xFF3A0A08),
    errorContainer = Color(0xFF5C1F1B),
    onErrorContainer = Color(0xFFFFDAD5),
    outline = Color(0xFF9A8680),
    outlineVariant = Color(0xFF4A3439),
    surfaceDim = Color(0xFF1F1115),
    surfaceBright = Color(0xFF46303A),
    surfaceContainerLowest = Color(0xFF1D1013),
    surfaceContainerLow = Color(0xFF271A1D),
    surfaceContainer = NeoPalette.PlateBottom,
    surfaceContainerHigh = Color(0xFF36232A),
    surfaceContainerHighest = NeoPalette.PlateTop
)

internal val neoStatusColors = StatusColors(success = NeoPalette.Success, warning = NeoPalette.Warning)

internal val neoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// --- Tipografi -----------------------------------------------------------------------------------
// Judul = TeX Gyre Chorus (kaligrafi Chancery miring; jarang dipakai, kesan kertas surat tua). Isi/label = Caladea
// (serif layar yang tegas, bukan Roboto). Font DI-BUNDLE UTUH di res/font (lisensi: docs/licenses/FONTS.md).
// Bobot: Chorus hanya satu bobot -> semua gaya judul meminta Normal (tanpa faux-bold); Caladea punya Normal & Bold.
private val neoTitleFont = FontFamily(Font(R.font.neo_title, FontWeight.Normal))
private val neoBodyFont = FontFamily(
    Font(R.font.neo_body, FontWeight.Normal),
    Font(R.font.neo_body_bold, FontWeight.Bold)
)

private fun neoStyle(font: FontFamily, size: Int, line: Int, weight: FontWeight, track: Float): TextStyle =
    TextStyle(
        fontFamily = font,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = track.sp
    )

internal val neoTypography = Typography(
    displayLarge = neoStyle(neoTitleFont, 57, 64, FontWeight.Normal, 0f),
    displayMedium = neoStyle(neoTitleFont, 45, 52, FontWeight.Normal, 0f),
    displaySmall = neoStyle(neoTitleFont, 36, 44, FontWeight.Normal, 0f),
    headlineLarge = neoStyle(neoTitleFont, 32, 40, FontWeight.Normal, 0f),
    headlineMedium = neoStyle(neoTitleFont, 28, 36, FontWeight.Normal, 0f),
    headlineSmall = neoStyle(neoTitleFont, 24, 32, FontWeight.Normal, 0f),
    titleLarge = neoStyle(neoTitleFont, 26, 32, FontWeight.Normal, 0.2f),
    titleMedium = neoStyle(neoTitleFont, 20, 26, FontWeight.Normal, 0.2f),
    titleSmall = neoStyle(neoBodyFont, 16, 22, FontWeight.Bold, 0.2f),
    bodyLarge = neoStyle(neoBodyFont, 17, 24, FontWeight.Normal, 0.2f),
    bodyMedium = neoStyle(neoBodyFont, 15, 21, FontWeight.Normal, 0.2f),
    bodySmall = neoStyle(neoBodyFont, 13, 18, FontWeight.Normal, 0.3f),
    labelLarge = neoStyle(neoBodyFont, 15, 20, FontWeight.Bold, 0.6f),
    labelMedium = neoStyle(neoBodyFont, 13, 18, FontWeight.Normal, 0.6f),
    labelSmall = neoStyle(neoBodyFont, 12, 16, FontWeight.Normal, 0.6f)
)

// --- Latar ---------------------------------------------------------------------------------------
// Gradien vertikal anggur-gelap + cahaya lembut kiri-atas (sumber cahaya) + peredam gelap kanan-bawah.
internal fun Modifier.neoBackdrop(): Modifier = this
    .background(Brush.verticalGradient(listOf(NeoPalette.BackdropTop, NeoPalette.BackdropBottom)))
    .drawBehind {
        val reach = size.maxDimension
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(NeoPalette.Highlight.copy(alpha = 0.06f), NeoPalette.Highlight.copy(alpha = 0f)),
                center = Offset(size.width * 0.1f, size.height * 0.05f),
                radius = reach * 0.9f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Black.copy(alpha = 0f)),
                center = Offset(size.width, size.height),
                radius = reach * 0.9f
            )
        )
    }

// --- Relief: bayangan, pelat, bevel, sumur ---------------------------------------------------------
internal class ShadowSpec(val dx: Dp, val dy: Dp, val blur: Dp, val spread: Dp, val peak: Float, val steps: Int)

/** Tingkat ketinggian pelat: kartu (tinggi), kontrol (tombol/chip), knob (kecil). Tiap tingkat = ambient + kontak. */
internal enum class NeoLevel(val ambient: ShadowSpec, val contact: ShadowSpec) {
    CARD(
        ShadowSpec(8.dp, 10.dp, 16.dp, (-5).dp, 0.50f, 10),
        ShadowSpec(2.5.dp, 3.5.dp, 5.dp, (-1).dp, 0.55f, 7)
    ),
    CONTROL(
        ShadowSpec(4.dp, 5.dp, 9.dp, (-3).dp, 0.45f, 8),
        ShadowSpec(1.5.dp, 2.dp, 3.dp, (-0.5).dp, 0.50f, 5)
    ),
    KNOB(
        ShadowSpec(2.dp, 3.dp, 5.dp, (-1.5).dp, 0.45f, 6),
        ShadowSpec(1.dp, 1.5.dp, 2.dp, 0.dp, 0.50f, 4)
    )
}

private val BevelWidth = 1.2.dp
private val InsetReach = 7.dp
private val InsetShiftX = 2.5.dp
private val InsetShiftY = 3.dp
private val LipWidth = 1.2.dp
private val NavShadowReach = 14.dp
private const val INSET_STEPS = 10
private const val INSET_STEP_ALPHA = 0.075f

/**
 * Alpha tiap PITA bayangan, dari pita TERDALAM (indeks 0) ke terluar. Pita i (1 = terdalam) membentang sampai jarak
 * blur*i/steps dari tepi dgn alpha peak*(1-t)^2 (t = (i-0.5)/steps): kepekatan turun halus (mendekati Gaussian)
 * tanpa BlurMaskFilter. Pita tak saling menumpuk -> tiap piksel digambar ~1x (bukan N lapis). Murni (diuji unit).
 */
internal fun shadowBandAlphas(steps: Int, peak: Float): List<Float> = List(steps) { k ->
    val t = (k + 0.5f) / steps
    peak * (1f - t) * (1f - t)
}

// v144: dulu N persegi-bulat penuh bertumpuk (17 lapis/kartu = ~17x overdraw per frame saat scroll). Kini 1 inti terisi
// + (N-1) cincin Stroke tipis non-tumpang-tindih: alpha efektif per wilayah SAMA, piksel digambar ~1-2x.
private fun DrawScope.drawShadowBands(spec: ShadowSpec, radius: Float) {
    val dx = spec.dx.toPx()
    val dy = spec.dy.toPx()
    val step = spec.blur.toPx() / spec.steps
    val spread = spec.spread.toPx()
    val alphas = shadowBandAlphas(spec.steps, spec.peak)
    val coreGrow = step + spread
    drawRoundRect(
        color = NeoPalette.Shadow.copy(alpha = alphas[0]),
        topLeft = Offset(dx - coreGrow, dy - coreGrow),
        size = Size(size.width + 2f * coreGrow, size.height + 2f * coreGrow),
        cornerRadius = CornerRadius((radius + coreGrow).coerceAtLeast(0f))
    )
    for (i in 1 until spec.steps) {
        val center = step * (i + 0.5f) + spread
        drawRoundRect(
            color = NeoPalette.Shadow.copy(alpha = alphas[i]),
            topLeft = Offset(dx - center, dy - center),
            size = Size(size.width + 2f * center, size.height + 2f * center),
            cornerRadius = CornerRadius((radius + center).coerceAtLeast(0f)),
            style = Stroke(width = step)
        )
    }
}

private fun DrawScope.drawPlate(radius: Float, top: Color, bottom: Color) {
    drawRoundRect(
        brush = Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(size.width, size.height)),
        cornerRadius = CornerRadius(radius)
    )
}

// Tepi pelat: pantulan cahaya hangat di kiri-atas, gelap di kanan-bawah. Antar-warna memakai RGB yg sama dgn alpha 0
// (bukan Transparent hitam) supaya tak ada pinggiran abu-abu.
private fun DrawScope.drawBevel(radius: Float) {
    val width = BevelWidth.toPx()
    val half = width / 2f
    val hi = NeoPalette.Highlight.copy(alpha = 0.22f)
    val lo = Color.Black.copy(alpha = 0.38f)
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to hi,
            0.45f to hi.copy(alpha = 0f),
            0.55f to lo.copy(alpha = 0f),
            1f to lo,
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        topLeft = Offset(half, half),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius((radius - half).coerceAtLeast(0f)),
        style = Stroke(width = width)
    )
}

// Relief cekung di DALAM bentuk (dipanggil di dalam clipPath): bayangan dalam berjenjang yg digeser ke kanan-bawah
// (jadi menebal di tepi kiri-atas) + bibir terang tipis di tepi kanan-bawah.
private fun DrawScope.drawInsetRelief(radius: Float, depth: Float) {
    val reach = InsetReach.toPx() * depth
    val shade = NeoPalette.InsetShadow.copy(alpha = INSET_STEP_ALPHA * depth)
    translate(InsetShiftX.toPx() * depth, InsetShiftY.toPx() * depth) {
        for (i in INSET_STEPS downTo 1) {
            drawRoundRect(
                color = shade,
                cornerRadius = CornerRadius(radius),
                style = Stroke(width = reach * i / INSET_STEPS)
            )
        }
    }
    val lip = LipWidth.toPx()
    val lipHi = NeoPalette.Highlight.copy(alpha = 0.16f)
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to lipHi.copy(alpha = 0f),
            0.45f to lipHi.copy(alpha = 0f),
            1f to lipHi,
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        topLeft = Offset(lip / 2f, lip / 2f),
        size = Size(size.width - lip, size.height - lip),
        cornerRadius = CornerRadius((radius - lip / 2f).coerceAtLeast(0f)),
        style = Stroke(width = lip)
    )
}

/** Pelat TIMBUL: bayangan ambient + kontak, isi gradien cembung, lalu bevel. Isi tak dipotong (pakai `clip`). */
internal fun Modifier.neoRaised(radius: Dp, level: NeoLevel, top: Color, bottom: Color): Modifier = this.drawBehind {
    val r = radius.toPx()
    drawShadowBands(level.ambient, r)
    drawShadowBands(level.contact, r)
    drawPlate(r, top, bottom)
    drawBevel(r)
}

/** Sumur CEKUNG: isi lebih gelap + relief dalam. `depth` 0..1 menskalakan kedalaman (bentuk kecil = lebih dangkal). */
internal fun Modifier.neoInset(
    radius: Dp,
    depth: Float = 1f,
    top: Color = NeoPalette.WellTop,
    bottom: Color = NeoPalette.WellBottom
): Modifier = this.drawBehind {
    val r = radius.toPx()
    drawPlate(r, top, bottom)
    val bounds = RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))
    clipPath(Path().apply { addRoundRect(bounds) }) { drawInsetRelief(r, depth) }
}

// Bilah navigasi: bayangan naik ke atas (bar terangkat dari latar) + garis cahaya di tepi atas.
internal fun Modifier.neoTopEdge(): Modifier = this.drawWithContent {
    drawContent()
    val reach = NavShadowReach.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(NeoPalette.Shadow.copy(alpha = 0f), NeoPalette.Shadow.copy(alpha = 0.34f)),
            startY = -reach,
            endY = 0f
        ),
        topLeft = Offset(0f, -reach),
        size = Size(size.width, reach)
    )
    drawLine(
        color = NeoPalette.Highlight.copy(alpha = 0.18f),
        start = Offset.Zero,
        end = Offset(size.width, 0f),
        strokeWidth = 2f
    )
}

// --- Komponen --------------------------------------------------------------------------------------
private val CardRadius = 24.dp
private val ControlRadius = 18.dp
private val ChipRadius = 16.dp
private val SwitchTrackWidth = 52.dp
private val SwitchTrackHeight = 30.dp
private val SwitchKnob = 24.dp
private val SwitchPad = 3.dp

/** Pengganti [GlassCard] utk tema Neo: kartu pelat timbul. Parameter & isi `ColumnScope` identik. */
@Composable
internal fun NeoCard(
    modifier: Modifier = Modifier,
    animateSize: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = modifier
                .neoRaised(CardRadius, NeoLevel.CARD, NeoPalette.PlateTop, NeoPalette.PlateBottom)
                .clip(RoundedCornerShape(CardRadius))
                .then(
                    if (animateSize) {
                        Modifier.animateContentSize(animationSpec = tween(durationMillis = LagMotion.CONTENT_MS))
                    } else {
                        Modifier
                    }
                ),
            content = content
        )
    }
}

internal enum class NeoTone { BURGUNDY, SAGE }

/** Tombol Neo: pelat timbul (burgundy = utama, sage = tonal); saat ditekan berubah cekung. Tanpa ripple. */
@Composable
internal fun NeoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: NeoTone = NeoTone.BURGUNDY,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val burgundy = tone == NeoTone.BURGUNDY
    val surface = when {
        !enabled -> Modifier.neoRaised(
            ControlRadius, NeoLevel.KNOB, NeoPalette.DisabledTop, NeoPalette.DisabledBottom
        )
        pressed && burgundy -> Modifier.neoInset(
            ControlRadius, 0.8f, NeoPalette.BurgundyPressedTop, NeoPalette.BurgundyPressedBottom
        )
        pressed -> Modifier.neoInset(
            ControlRadius, 0.8f, NeoPalette.SagePressedTop, NeoPalette.SagePressedBottom
        )
        burgundy -> Modifier.neoRaised(
            ControlRadius, NeoLevel.CONTROL, NeoPalette.BurgundyTop, NeoPalette.BurgundyBottom
        )
        else -> Modifier.neoRaised(ControlRadius, NeoLevel.CONTROL, NeoPalette.SageTop, NeoPalette.SageBottom)
    }
    val contentColor = when {
        !enabled -> NeoPalette.Ivory.copy(alpha = 0.38f)
        burgundy -> NeoPalette.Ivory
        else -> NeoPalette.SageText
    }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(surface)
            .clip(RoundedCornerShape(ControlRadius))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
        }
    }
}

/** Chip Neo: tak terpilih = pelat timbul kecil; terpilih (atau ditekan) = sumur cekung dgn teks rose. */
@Composable
internal fun NeoChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sunken = selected || pressed
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(
                if (sunken) {
                    Modifier.neoInset(ChipRadius, 0.8f)
                } else {
                    Modifier.neoRaised(ChipRadius, NeoLevel.CONTROL, NeoPalette.ControlTop, NeoPalette.ControlBottom)
                }
            )
            .clip(RoundedCornerShape(ChipRadius))
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (selected) NeoPalette.Rose else NeoPalette.Muted
        CompositionLocalProvider(LocalContentColor provides tint) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge, label)
        }
    }
}

/** Switch Neo (visual saja; sentuhan ditangani `toggleable` di pemanggil): trek cekung + knob timbul. */
@Composable
internal fun NeoSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val travel = SwitchTrackWidth - SwitchKnob - SwitchPad * 2
    val shift by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = tween(durationMillis = LagMotion.FADE_MS),
        label = "neoKnob"
    )
    val trackTop = if (checked) NeoPalette.TrackOnTop else NeoPalette.WellTop
    val trackBottom = if (checked) NeoPalette.TrackOnBottom else NeoPalette.WellBottom
    val knobTop = if (checked) NeoPalette.KnobOnTop else NeoPalette.KnobOffTop
    val knobBottom = if (checked) NeoPalette.KnobOnBottom else NeoPalette.KnobOffBottom
    Box(
        modifier
            .size(width = SwitchTrackWidth, height = SwitchTrackHeight)
            .neoInset(SwitchTrackHeight / 2, 0.8f, trackTop, trackBottom)
    ) {
        Box(
            Modifier
                .padding(SwitchPad)
                .offset { IntOffset(shift.roundToPx(), 0) } // lambda: dibaca di fase layout (lint State-backed offset)
                .size(SwitchKnob)
                .neoRaised(SwitchKnob / 2, NeoLevel.KNOB, knobTop, knobBottom)
        )
    }
}
