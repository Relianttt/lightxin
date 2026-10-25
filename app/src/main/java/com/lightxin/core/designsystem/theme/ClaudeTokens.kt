package com.lightxin.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Claude.ai 应用 CSS（2026-02 归档）里的真实色阶，独立于旧暖色 token。
// 全局 ColorScheme 与旧语义 token 共用此色板。
// 来源与取舍见 .agents/notes/2026-10-18-decision-claude-ui-language.md

internal val CanvasLight = Color(0xFFFAF9F5)
internal val CanvasDark = Color(0xFF262624)
internal val SurfaceLight = Color(0xFFFFFFFF)
internal val SurfaceDark = Color(0xFF30302E)
internal val TintLight = Color(0xFFF5F4ED)
internal val TintDark = Color(0xFF1F1E1D)
internal val TintStrongLight = Color(0xFFF0EEE6)
internal val TintStrongDark = Color(0xFF141413)
internal val TextLight = Color(0xFF141413)
internal val TextDark = Color(0xFFFAF9F5)
internal val TextSecondaryLight = Color(0xFF3D3D3A)
internal val TextSecondaryDark = Color(0xFFC2C0B6)
internal val TextMutedLight = Color(0xFF73726C)
internal val TextMutedDark = Color(0xFF9C9A92)
internal val TextFaintLight = Color(0xFF9C9A92)
internal val TextFaintDark = Color(0xFF73726C)
internal val HairlineLight = Color(0x1F1F1E1D)
internal val HairlineDark = Color(0x24DEDCD1)
internal val AccentLight = Color(0xFFC6613F)
internal val AccentDark = Color(0xFFD97757)
internal val AccentTextLight = Color(0xFFB4562F)
internal val AccentTextDark = Color(0xFFE08A6A)
internal val SuccessLight = Color(0xFF2F7613)
internal val SuccessDark = Color(0xFF459315)
internal val WarningLight = Color(0xFF875A08)
internal val WarningDark = Color(0xFFB17506)
internal val DangerLight = Color(0xFFB53333)
internal val DangerDark = Color(0xFFDD5353)
internal val DangerSurfaceLight = Color(0xFFF9ECEC)
internal val DangerSurfaceDark = Color(0xFF3A2624)
internal val DangerOnSurfaceLight = Color(0xFFB53333)
internal val DangerOnSurfaceDark = Color(0xFFE08A8A)

private val isDark: Boolean @Composable @ReadOnlyComposable get() = isSystemInDarkTheme()

val LxCanvas: Color @Composable @ReadOnlyComposable get() = if (isDark) CanvasDark else CanvasLight
val LxSurface: Color @Composable @ReadOnlyComposable get() = if (isDark) SurfaceDark else SurfaceLight
val LxTint: Color @Composable @ReadOnlyComposable get() = if (isDark) TintDark else TintLight
val LxTintStrong: Color @Composable @ReadOnlyComposable get() = if (isDark) TintStrongDark else TintStrongLight
val LxText: Color @Composable @ReadOnlyComposable get() = if (isDark) TextDark else TextLight
val LxTextSecondary: Color @Composable @ReadOnlyComposable get() = if (isDark) TextSecondaryDark else TextSecondaryLight
val LxTextMuted: Color @Composable @ReadOnlyComposable get() = if (isDark) TextMutedDark else TextMutedLight
val LxTextFaint: Color @Composable @ReadOnlyComposable get() = if (isDark) TextFaintDark else TextFaintLight
val LxHairline: Color @Composable @ReadOnlyComposable get() = if (isDark) HairlineDark else HairlineLight
val LxAccent: Color @Composable @ReadOnlyComposable get() = if (isDark) AccentDark else AccentLight
val LxAccentText: Color @Composable @ReadOnlyComposable get() = if (isDark) AccentTextDark else AccentTextLight
val LxOnAccent: Color @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF1F1E1D) else Color(0xFFFFFDF9)
val LxStateSuccess: Color @Composable @ReadOnlyComposable get() = if (isDark) SuccessDark else SuccessLight
val LxStateWarning: Color @Composable @ReadOnlyComposable get() = if (isDark) WarningDark else WarningLight
val LxDanger: Color @Composable @ReadOnlyComposable get() = if (isDark) DangerDark else DangerLight
val LxDangerSurface: Color @Composable @ReadOnlyComposable get() = if (isDark) DangerSurfaceDark else DangerSurfaceLight
val LxDangerOnSurface: Color @Composable @ReadOnlyComposable get() = if (isDark) DangerOnSurfaceDark else DangerOnSurfaceLight

/** 实心危险按钮：两种模式都用砖红，保证白字对比度。 */
val LxDangerFill: Color = Color(0xFFB53333)

val LxRadiusSm = 8.dp
val LxRadiusMd = 12.dp
val LxRadiusLg = 16.dp
val LxRadiusSheet = 24.dp
