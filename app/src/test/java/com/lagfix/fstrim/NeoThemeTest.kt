package com.lagfix.fstrim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class NeoThemeTest {
    private fun channel(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun parseAppTheme_knownNames_roundTrip() {
        AppTheme.entries.forEach { assertEquals(it, parseAppTheme(it.name)) }
    }

    @Test
    fun parseAppTheme_nullOrUnknown_fallsBackToGlass() {
        assertEquals(AppTheme.GLASS, parseAppTheme(null))
        assertEquals(AppTheme.GLASS, parseAppTheme(""))
        assertEquals(AppTheme.GLASS, parseAppTheme("neo"))
        assertEquals(AppTheme.GLASS, parseAppTheme("LIGHT"))
    }

    @Test
    fun neoText_meetsWcagAaOnBrightestPlate() {
        val plate = NeoPalette.PlateTop
        listOf(
            NeoPalette.Ivory, NeoPalette.Muted, NeoPalette.Rose, NeoPalette.Sage,
            NeoPalette.Champagne, NeoPalette.Success, NeoPalette.Warning, neoScheme.error
        ).forEach { fg ->
            assertTrue("kontras $fg di atas pelat < 4.5", contrast(fg, plate) >= 4.5)
        }
        assertTrue(contrast(NeoPalette.Ivory, NeoPalette.BurgundyTop) >= 4.5)
        assertTrue(contrast(NeoPalette.Ivory, NeoPalette.BurgundyPressedBottom) >= 4.5)
        assertTrue(contrast(NeoPalette.SageText, NeoPalette.SageTop) >= 4.5)
        assertTrue(contrast(NeoPalette.Rose, NeoPalette.WellBottom) >= 4.5)
    }

    @Test
    fun neoPlate_isDistinctFromBackdrop_notBlending() {
        assertTrue(contrast(NeoPalette.PlateTop, NeoPalette.BackdropTop) >= 1.1)
        assertTrue(contrast(NeoPalette.PlateBottom, NeoPalette.BackdropBottom) >= 1.1)
    }

    @Test
    fun neoSwitchKnob_hasNonTextContrastAgainstTrack() {
        assertTrue(contrast(NeoPalette.KnobOnBottom, NeoPalette.TrackOnBottom) >= 3.0)
        assertTrue(contrast(NeoPalette.KnobOffBottom, NeoPalette.WellBottom) >= 3.0)
    }

    @Test
    fun shadowStepAlphas_areValidAndComposeToTarget() {
        val steps = 10
        val peak = 0.5f
        val alphas = shadowStepAlphas(steps, peak)
        assertEquals(steps, alphas.size)
        assertTrue(alphas.all { it in 0f..1f })
        val composed = 1.0 - alphas.fold(1.0) { acc, a -> acc * (1.0 - a) }
        val expected = peak * (1.0 - 0.5 / steps).pow(2)
        assertEquals(expected, composed, 1e-4)
    }
}
