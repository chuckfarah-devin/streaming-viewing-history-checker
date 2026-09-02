package com.chuckfarah.streaminghistory.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ColorContrastTest {

    @Test
    fun `light theme key text contrast meets WCAG AA`() {
        assertContrast(
            background = md_theme_light_surface,
            foreground = md_theme_light_onSurface,
            minimum = 4.5,
            name = "surface/onSurface light",
        )
        assertContrast(
            background = md_theme_light_primary,
            foreground = md_theme_light_onPrimary,
            minimum = 4.5,
            name = "primary/onPrimary light",
        )
        assertContrast(
            background = md_theme_light_error,
            foreground = md_theme_light_onError,
            minimum = 4.5,
            name = "error/onError light",
        )
    }

    @Test
    fun `dark theme key text contrast meets WCAG AA`() {
        assertContrast(
            background = md_theme_dark_surface,
            foreground = md_theme_dark_onSurface,
            minimum = 4.5,
            name = "surface/onSurface dark",
        )
        assertContrast(
            background = md_theme_dark_primary,
            foreground = md_theme_dark_onPrimary,
            minimum = 4.5,
            name = "primary/onPrimary dark",
        )
        assertContrast(
            background = md_theme_dark_error,
            foreground = md_theme_dark_onError,
            minimum = 4.5,
            name = "error/onError dark",
        )
    }

    @Test
    fun `success contrast meets WCAG AA in both themes`() {
        assertContrast(
            background = success_light,
            foreground = onSuccess_light,
            minimum = 4.5,
            name = "success/onSuccess light",
        )
        assertContrast(
            background = successContainer_light,
            foreground = onSuccessContainer_light,
            minimum = 4.5,
            name = "successContainer/onSuccessContainer light",
        )
        assertContrast(
            background = success_dark,
            foreground = onSuccess_dark,
            minimum = 4.5,
            name = "success/onSuccess dark",
        )
        assertContrast(
            background = successContainer_dark,
            foreground = onSuccessContainer_dark,
            minimum = 4.5,
            name = "successContainer/onSuccessContainer dark",
        )
    }

    @Test
    fun `warning contrast meets WCAG AA in both themes`() {
        assertContrast(
            background = warning_light,
            foreground = onWarning_light,
            minimum = 4.5,
            name = "warning/onWarning light",
        )
        assertContrast(
            background = warningContainer_light,
            foreground = onWarningContainer_light,
            minimum = 4.5,
            name = "warningContainer/onWarningContainer light",
        )
        assertContrast(
            background = warning_dark,
            foreground = onWarning_dark,
            minimum = 4.5,
            name = "warning/onWarning dark",
        )
        assertContrast(
            background = warningContainer_dark,
            foreground = onWarningContainer_dark,
            minimum = 4.5,
            name = "warningContainer/onWarningContainer dark",
        )
    }

    private fun assertContrast(background: Color, foreground: Color, minimum: Double, name: String) {
        val contrast = contrastRatio(foreground, background)
        assertTrue(
            "$name contrast ratio was $contrast, required $minimum",
            contrast >= minimum,
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val l1 = relativeLuminance(first)
        val l2 = relativeLuminance(second)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        val r = channelToLinear(color.red)
        val g = channelToLinear(color.green)
        val b = channelToLinear(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channelToLinear(value: Float): Double {
        val c = value.toDouble().coerceIn(0.0, 1.0)
        return if (c <= 0.03928) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
    }
}
