package com.rassvet.essential.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp
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

private val BaseTypography =
    Typography(
        fontFamily = MontFontFamily,
    )


val Typography = BaseTypography.copy(
    titleLarge = BaseTypography.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = BaseTypography.titleSmall.copy(fontSize = 18.sp, lineHeight = 24.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = BaseTypography.labelSmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
)


val EssentialDisplayFontFamily =
    FontFamily(
        Font(R.font.oddval_semibold, FontWeight.Normal),
        Font(R.font.oddval_semibold, FontWeight.SemiBold),
    )


