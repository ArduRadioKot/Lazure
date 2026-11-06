package com.rassvet.essential.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.rassvet.essential.R


val MontFontFamily =
    FontFamily(
        Font(R.font.mont_thin, FontWeight.Thin),
        Font(R.font.mont_extralight, FontWeight.ExtraLight),
        Font(R.font.mont_light, FontWeight.Light),
        Font(R.font.mont_regular, FontWeight.Normal),
        Font(R.font.mont_semibold, FontWeight.Medium),
        Font(R.font.mont_semibold, FontWeight.SemiBold),
        Font(R.font.mont_bold, FontWeight.Bold),
        Font(R.font.mont_heavy, FontWeight.ExtraBold),
        Font(R.font.mont_black, FontWeight.Black),
    )

val Typography =
    Typography(
        fontFamily = MontFontFamily,
    )


val EssentialDisplayFontFamily =
    FontFamily(
        Font(R.font.oddval_semibold, FontWeight.Normal),
        Font(R.font.oddval_semibold, FontWeight.SemiBold),
    )


