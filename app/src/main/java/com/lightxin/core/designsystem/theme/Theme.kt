package com.lightxin.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = LxTerra,
    onPrimary = Color.White,
    primaryContainer = LxTerraSoft,
    onPrimaryContainer = LxTerra,
    secondary = LxSage,
    onSecondary = Color.White,
    secondaryContainer = LxSageSoft,
    onSecondaryContainer = LxSage,
    tertiary = LxAmber,
    onTertiary = Color.White,
    tertiaryContainer = LxAmberSoft,
    onTertiaryContainer = LxAmber,
    background = LxParchment,
    onBackground = LxInk,
    surface = LxCard,
    onSurface = LxInk,
    surfaceVariant = LxSand,
    onSurfaceVariant = LxInkMuted,
    outline = LxSandDeep,
    outlineVariant = LxSand,
    error = LxRose,
    onError = Color.White,
    errorContainer = LxRose.copy(alpha = 0.12f),
    onErrorContainer = LxRose,
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
    // 当前原型仅提供 Light 主题（暖色 token），Dark 主题待后续独立设计。
    // 保留参数签名以便后续启用时无需改调用方。
    darkTheme: Boolean = false,
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
