package com.example.mpod.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorContrastTest {
    @Test
    fun lightPrimaryMeetsNormalTextContrastInBothDirections() {
        assertContrastAtLeast(Primary, Background, 4.5f)
        assertContrastAtLeast(PrimaryForeground, Primary, 4.5f)
    }

    @Test
    fun darkPrimaryAndMutedTextMeetNormalTextContrast() {
        assertContrastAtLeast(DarkPrimary, DarkBackground, 4.5f)
        assertContrastAtLeast(DarkPrimaryForeground, DarkPrimary, 4.5f)
        assertContrastAtLeast(DarkMutedForeground, DarkBackground, 4.5f)
    }

    @Test
    fun lightMutedTextMeetsNormalTextContrast() {
        assertContrastAtLeast(MutedForeground, Background, 4.5f)
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Float) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("Expected contrast >= $minimum, actual=$ratio", ratio >= minimum)
    }
}
