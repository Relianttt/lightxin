package com.lightxin.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color(0xFFFFFDF9),
    primaryContainer = AccentLight.copy(alpha = 0.12f),
    onPrimaryContainer = AccentTextLight,
    secondary = LxSageRaw,
    onSecondary = Color.White,
    secondaryContainer = LxSageSoftRaw,
    onSecondaryContainer = LxSageRaw,
    tertiary = LxAmberRaw,
    onTertiary = Color.White,
    tertiaryContainer = LxAmberSoftRaw,
    onTertiaryContainer = LxAmberRaw,
    background = CanvasLight,
    onBackground = TextLight,
    surface = SurfaceLight,
    onSurface = TextLight,
    surfaceVariant = TintStrongLight,
    onSurfaceVariant = TextMutedLight,
    outline = HairlineLight,
    outlineVariant = HairlineLight,
    error = DangerLight,
    onError = Color.White,
    errorContainer = DangerSurfaceLight,
    onErrorContainer = DangerOnSurfaceLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = CanvasLight,
    surfaceContainerHigh = TintLight,
    surfaceContainerHighest = TintStrongLight,
    surfaceBright = SurfaceLight,
    surfaceDim = TintStrongLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF1F1E1D),
    primaryContainer = AccentDark.copy(alpha = 0.12f),
    onPrimaryContainer = AccentTextDark,
    secondary = LxDarkSecondaryRaw,
    onSecondary = LxDarkSurfaceRaw,
    secondaryContainer = LxDarkSecondaryContainerRaw,
    onSecondaryContainer = LxDarkSecondaryRaw,
    tertiary = LxDarkAmberRaw,
    onTertiary = LxDarkSurfaceRaw,
    tertiaryContainer = LxDarkAmberSoftRaw,
    onTertiaryContainer = LxDarkAmberRaw,
    background = CanvasDark,
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    surfaceVariant = TintStrongDark,
    onSurfaceVariant = TextMutedDark,
    outline = HairlineDark,
    outlineVariant = HairlineDark,
    error = DangerDark,
    onError = Color.White,
    errorContainer = DangerSurfaceDark,
    onErrorContainer = DangerOnSurfaceDark,
    surfaceContainerLowest = SurfaceDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = CanvasDark,
    surfaceContainerHigh = TintDark,
    surfaceContainerHighest = TintStrongDark,
    surfaceBright = SurfaceDark,
    surfaceDim = TintStrongDark,
)

@Composable
fun LightXinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LightXinTypography,
        shapes = LightXinShapes,
        content = content,
    )
}
