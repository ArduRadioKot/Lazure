package com.rassvet.essential.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorContrastTest {
    @Test fun staticThemesKeepReadableTextContrast() {
        listOf(
            "light primary" to (NeutralLightOnPrimary to NeutralLightPrimary),
            "light content" to (NeutralLightOnSurface to NeutralLightBackground),
            "light secondary text" to (NeutralLightOnSurfaceVariant to NeutralLightSurfaceContainerHigh),
            "light action" to (LazureLightOnContainer to LazureLightContainer),
            "dark primary" to (EssentialDarkOnPrimary to LazureDarkPrimary),
            "dark secondary text" to (EssentialDarkOnSurfaceVariant to EssentialDarkSurfaceContainerHigh),
            "dark action" to (LazureDarkOnContainer to LazureDarkContainer),
        ).forEach { (name, pair) ->
            val a = pair.first.luminance(); val b = pair.second.luminance()
            val contrast = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
            assertTrue("$name: $contrast", contrast >= 4.5f)
        }
    }
}
