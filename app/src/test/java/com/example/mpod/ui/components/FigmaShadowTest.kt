package com.example.mpod.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FigmaShadowTest {
    @Test
    fun figmaShadowStylesHaveIncreasingElevations() {
        val styles = FigmaShadowStyle.entries
        for (i in 0 until styles.size - 1) {
            assertTrue(
                "Elevation should increase: ${styles[i]} (${styles[i].elevation}) < ${styles[i + 1]} (${styles[i + 1].elevation})",
                styles[i].elevation < styles[i + 1].elevation
            )
        }
    }

    @Test
    fun figmaShadowStylesMatchExpectedElevations() {
        assertEquals(1.dp, FigmaShadowStyle.TwoXs.elevation)
        assertEquals(2.dp, FigmaShadowStyle.Xs.elevation)
        assertEquals(3.dp, FigmaShadowStyle.Sm.elevation)
        assertEquals(6.dp, FigmaShadowStyle.Md.elevation)
        assertEquals(12.dp, FigmaShadowStyle.Lg.elevation)
        assertEquals(20.dp, FigmaShadowStyle.Xl.elevation)
        assertEquals(28.dp, FigmaShadowStyle.TwoXl.elevation)
    }

    @Test
    fun figmaShadowStylesHaveNonZeroAlphas() {
        for (style in FigmaShadowStyle.entries) {
            assertTrue(style.color.alpha > 0f)
            assertEquals(Color.Black.red, style.color.red, 0.01f)
            assertEquals(Color.Black.green, style.color.green, 0.01f)
            assertEquals(Color.Black.blue, style.color.blue, 0.01f)
        }
    }

    @Test
    fun figmaShadowAmbientAndSpotColorsHaveHighContrastAlphasForDarkTheme() {
        assertTrue(FigmaAmbientShadowColor.alpha >= 0.35f)
        assertTrue(FigmaSpotShadowColor.alpha >= 0.65f)
    }
}
