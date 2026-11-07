@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.rassvet.essential.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
    darkColorScheme(
        primary = LazureDarkPrimary,
        onPrimary = EssentialDarkOnPrimary,
        primaryContainer = LazureDarkContainer,
        onPrimaryContainer = LazureDarkOnContainer,
        secondary = LazureDarkSecondary,
        onSecondary = EssentialDarkOnPrimary,
        secondaryContainer = LazureDarkContainer,
        onSecondaryContainer = LazureDarkOnContainer,
        tertiary = LazureDarkSecondary,
        onTertiary = EssentialDarkOnPrimary,
        tertiaryContainer = LazureDarkContainer,
        onTertiaryContainer = LazureDarkOnContainer,
        inversePrimary = LazureLightSecondary,
        surfaceTint = EssentialBrand,
        inverseSurface = Color(0xFFE2E8E8),
        inverseOnSurface = Color(0xFF202626),
        surfaceDim = EssentialDarkSurface,
        surfaceBright = EssentialDarkSurfaceContainerHighest,
        surfaceContainerLowest = EssentialDarkBackground,
        background = EssentialDarkBackground,
        onBackground = EssentialDarkOnBackground,
        surface = EssentialDarkSurface,
        onSurface = EssentialDarkOnSurface,
        surfaceContainerLow = EssentialDarkSurface,
        surfaceContainer = EssentialDarkSurfaceContainer,
        surfaceContainerHigh = EssentialDarkSurfaceContainerHigh,
        surfaceContainerHighest = EssentialDarkSurfaceContainerHighest,
        onSurfaceVariant = EssentialDarkOnSurfaceVariant,
        outline = EssentialDarkOutline,
        outlineVariant = EssentialDarkOutlineVariant,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = NeutralLightPrimary,
        onPrimary = NeutralLightOnPrimary,
        primaryContainer = LazureLightContainer,
        onPrimaryContainer = LazureLightOnContainer,
        secondary = LazureLightSecondary,
        onSecondary = Color.White,
        secondaryContainer = LazureLightContainer,
        onSecondaryContainer = LazureLightOnContainer,
        tertiary = LazureLightSecondary,
        onTertiary = Color.White,
        tertiaryContainer = LazureLightContainer,
        onTertiaryContainer = LazureLightOnContainer,
        inversePrimary = LazureDarkPrimary,
        surfaceTint = EssentialBrand,
        inverseSurface = Color(0xFF202626),
        inverseOnSurface = Color(0xFFE2E8E8),
        surfaceDim = NeutralLightSurfaceContainerHighest,
        surfaceBright = NeutralLightSurface,
        surfaceContainerLowest = NeutralLightSurface,
        surfaceContainerLow = Color(0xFFEDF3F4),
        background = NeutralLightBackground,
        onBackground = NeutralLightOnBackground,
        surface = NeutralLightSurface,
        onSurface = NeutralLightOnSurface,
        onSurfaceVariant = NeutralLightOnSurfaceVariant,
        surfaceContainer = NeutralLightSurfaceContainer,
        surfaceContainerHigh = NeutralLightSurfaceContainerHigh,
        surfaceContainerHighest = NeutralLightSurfaceContainerHighest,
        outline = NeutralLightOutline,
        outlineVariant = NeutralLightOutlineVariant,
    )

private val LazureShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)


@Composable
fun EssentialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = LazureShapes,
        motionScheme = MotionScheme.expressive(),
    ) {
        ProvideEssentialChrome(content = content)
    }
}


