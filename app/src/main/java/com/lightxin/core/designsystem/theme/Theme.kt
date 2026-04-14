package com.lightxin.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = LxPrimary,
    onPrimary = LxSurface,
    primaryContainer = LxPrimaryContainer,
    secondary = LxSecondary,
    onSecondary = LxSurface,
    secondaryContainer = LxSecondaryContainer,
    background = LxBackground,
    onBackground = LxOnBackground,
    surface = LxSurface,
    onSurface = LxOnSurface,
    surfaceVariant = LxSurfaceVariant,
    onSurfaceVariant = LxOnSurfaceVariant,
    outline = LxOutline,
    error = LxError,
)

private val DarkColorScheme = darkColorScheme(
    primary = LxDarkPrimary,
    onPrimary = LxDarkSurface,
    primaryContainer = LxDarkPrimaryContainer,
    secondary = LxDarkSecondary,
    onSecondary = LxDarkSurface,
    secondaryContainer = LxDarkSecondaryContainer,
    background = LxDarkBackground,
    onBackground = LxDarkOnBackground,
    surface = LxDarkSurface,
    onSurface = LxDarkOnSurface,
    surfaceVariant = LxDarkSurfaceVariant,
    onSurfaceVariant = LxDarkOnSurfaceVariant,
    outline = LxDarkOutline,
    error = LxDarkError,
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
        content = content,
    )
}
