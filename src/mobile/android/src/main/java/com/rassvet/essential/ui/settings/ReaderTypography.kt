package com.rassvet.essential.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.ui.theme.MontFontFamily

data class ReaderFontOption(
    val id: String,
    val label: String,
    val family: FontFamily,
)

val ReaderFontCatalog: List<ReaderFontOption> = listOf(
    ReaderFontOption("georgia", "Georgia", FontFamily.Serif),
    ReaderFontOption("system", "System", FontFamily.SansSerif),
    ReaderFontOption("iowan", "Iowan", FontFamily.Serif),
    ReaderFontOption("mont", "Mont", MontFontFamily),
    ReaderFontOption("mono", "Mono", FontFamily.Monospace),
)

data class ReaderTypographySettings(
    val fontId: String = "georgia",
    val fontSizeSp: Float = 16f,
    val fontWeight: Int = 400,
) {
    val family: FontFamily
        get() = ReaderFontCatalog.firstOrNull { it.id == fontId }?.family ?: FontFamily.Serif

    val weight: FontWeight
        get() = when (fontWeight) {
            in 0..350 -> FontWeight.Light
            in 351..450 -> FontWeight.Normal
            in 451..550 -> FontWeight.Medium
            in 551..650 -> FontWeight.SemiBold
            else -> FontWeight.Bold
        }

    fun bodyStyle(color: androidx.compose.ui.graphics.Color): TextStyle =
        TextStyle(
            color = color,
            fontFamily = family,
            fontWeight = weight,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.55f).sp,
        )

    fun titleStyle(color: androidx.compose.ui.graphics.Color): TextStyle =
        bodyStyle(color).copy(
            fontSize = (fontSizeSp * 1.35f).sp,
            lineHeight = (fontSizeSp * 1.65f).sp,
            fontWeight = FontWeight.SemiBold,
        )
}

fun fontWeightLabel(weight: Int): String = weight.toString()

@Composable
fun rememberReaderTypography(prefs: VaultPreferencesRepository): ReaderTypographySettings {
    val fontId by prefs.readerFontId.collectAsState(initial = "georgia")
    val fontSize by prefs.readerFontSizeSp.collectAsState(initial = 16f)
    val fontWeight by prefs.readerFontWeight.collectAsState(initial = 400)
    return ReaderTypographySettings(fontId, fontSize, fontWeight)
}


