package com.rassvet.essential.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance


data class EssentialChromeColors(
    val barSurface: Color,
    val sendCircle: Color,
    val hint: Color,
    val thinkingMuted: Color,
    val primaryText: Color,
    val inputSurface: Color,
    val inputBorder: Color,
    val inputIcon: Color,
    val inputSendBg: Color,
    val inputSendIcon: Color,
    val assistantText: Color,
    val sheetSurface: Color,
    val sheetContainer: Color,
    val sheetText: Color,
    val sheetMuted: Color,
    val sheetBorder: Color,
    val sheetDivider: Color,
    val sheetIcon: Color,
    val sheetSelectedBg: Color,
    val overlayScrim: Color,
    val tileBorder: Color,
    val attachmentSurface: Color,
    val attachmentMuted: Color,
    val logoStroke: Color,
)

val LocalEssentialChrome =
    staticCompositionLocalOf {
        essentialChromeColorsForScheme(isDark = true, scheme = null)
    }

@Composable
fun ProvideEssentialChrome(content: @Composable () -> Unit) {
    val chrome = rememberEssentialChromeColors()
    CompositionLocalProvider(LocalEssentialChrome provides chrome, content = content)
}

@Composable
fun rememberEssentialChromeColors(): EssentialChromeColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    return essentialChromeColorsForScheme(isDark, scheme)
}

private fun essentialChromeColorsForScheme(
    isDark: Boolean,
    scheme: androidx.compose.material3.ColorScheme?,
): EssentialChromeColors {
    if (isDark) {
        return EssentialChromeColors(
            barSurface = EssentialDarkSurface,
            sendCircle = EssentialDarkSurfaceContainerHighest,
            hint = EssentialDarkOnSurfaceVariant,
            thinkingMuted = Color(0xFFADADB2),
            primaryText = EssentialDarkOnSurface,
            inputSurface = EssentialDarkSurface,
            inputBorder = EssentialDarkOutline,
            inputIcon = Color(0xFFAEAEB2),
            inputSendBg = Color(0xFFE5E5EA),
            inputSendIcon = EssentialDarkSurface,
            assistantText = Color(0xFFE8E8E8),
            sheetSurface = EssentialDarkSurface,
            sheetContainer = EssentialDarkSurfaceContainer,
            sheetText = EssentialDarkOnSurface,
            sheetMuted = EssentialDarkOnSurfaceVariant,
            sheetBorder = EssentialDarkOutline,
            sheetDivider = EssentialDarkOutlineVariant,
            sheetIcon = Color(0xFFAEAEB2),
            sheetSelectedBg = EssentialDarkSurfaceContainerHigh,
            overlayScrim = Color(0x99000000),
            tileBorder = EssentialDarkOutlineVariant,
            attachmentSurface = EssentialDarkSurfaceContainerHigh,
            attachmentMuted = EssentialDarkOnSurfaceVariant,
            logoStroke = Color(0xFFC4C4C4),
        )
    }
    val s = scheme!!
    return EssentialChromeColors(
        barSurface = s.surface,
        sendCircle = s.surfaceContainerHighest,
        hint = s.onSurfaceVariant,
        thinkingMuted = s.onSurfaceVariant,
        primaryText = s.onSurface,
        inputSurface = s.surfaceContainerHigh,
        inputBorder = s.outline,
        inputIcon = s.onSurfaceVariant,
        inputSendBg = s.surfaceContainerHighest,
        inputSendIcon = s.onSurface,
        assistantText = s.onSurface.copy(alpha = 0.88f),
        sheetSurface = s.surface,
        sheetContainer = s.surfaceContainer,
        sheetText = s.onSurface,
        sheetMuted = s.onSurfaceVariant,
        sheetBorder = s.outlineVariant,
        sheetDivider = s.outline,
        sheetIcon = s.onSurfaceVariant,
        sheetSelectedBg = s.surfaceContainerHigh,
        overlayScrim = Color(0x66000000),
        tileBorder = s.outlineVariant,
        attachmentSurface = s.surfaceContainerHigh,
        attachmentMuted = s.onSurfaceVariant,
        logoStroke = s.onSurfaceVariant.copy(alpha = 0.55f),
    )
}


