package com.lightxin.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = LxTerraRaw,
    onPrimary = Color.White,
    primaryContainer = LxTerraSoftRaw,
    onPrimaryContainer = LxTerraRaw,
    secondary = LxSageRaw,
    onSecondary = Color.White,
    secondaryContainer = LxSageSoftRaw,
    onSecondaryContainer = LxSageRaw,
    tertiary = LxAmberRaw,
    onTertiary = Color.White,
    tertiaryContainer = LxAmberSoftRaw,
    onTertiaryContainer = LxAmberRaw,
    background = LxParchmentRaw,
    onBackground = LxInkRaw,
    surface = LxCardRaw,
    onSurface = LxInkRaw,
    surfaceVariant = LxSandRaw,
    onSurfaceVariant = LxInkMutedRaw,
    outline = LxSandDeepRaw,
    outlineVariant = LxSandRaw,
    error = LxRoseRaw,
    onError = Color.White,
    errorContainer = LxRoseRaw.copy(alpha = 0.12f),
    onErrorContainer = LxRoseRaw,
)

private val DarkColorScheme = darkColorScheme(
    primary = LxDarkPrimaryRaw,
    onPrimary = LxDarkSurfaceRaw,
    primaryContainer = LxDarkPrimaryContainerRaw,
    onPrimaryContainer = LxDarkPrimaryRaw,
    secondary = LxDarkSecondaryRaw,
    onSecondary = LxDarkSurfaceRaw,
    secondaryContainer = LxDarkSecondaryContainerRaw,
    onSecondaryContainer = LxDarkSecondaryRaw,
    tertiary = LxDarkAmberRaw,
    onTertiary = LxDarkSurfaceRaw,
    tertiaryContainer = LxDarkAmberSoftRaw,
    onTertiaryContainer = LxDarkAmberRaw,
    background = LxDarkBackgroundRaw,
    onBackground = LxDarkOnBackgroundRaw,
    surface = LxDarkSurfaceRaw,
    onSurface = LxDarkOnSurfaceRaw,
    surfaceVariant = LxDarkSurfaceVariantRaw,
    onSurfaceVariant = LxDarkOnSurfaceVariantRaw,
    outline = LxDarkOutlineRaw,
    outlineVariant = LxDarkSurfaceVariantRaw,
    error = LxDarkRoseRaw,
    onError = LxDarkSurfaceRaw,
    errorContainer = LxDarkRoseRaw.copy(alpha = 0.16f),
    onErrorContainer = LxDarkRoseRaw,
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
