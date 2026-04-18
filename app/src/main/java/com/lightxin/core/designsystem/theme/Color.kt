package com.lightxin.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// 原始字面量色板（不跟随主题，供 Theme.kt 构造 ColorScheme 使用）
// 外层 `val Lx*` 才是跟随 isSystemInDarkTheme() 的 @Composable token
// ─────────────────────────────────────────────────────────────

// ── Light 基底 ──
internal val LxParchmentRaw = Color(0xFFF5F0E8)
internal val LxCreamRaw = Color(0xFFFAF7F2)
internal val LxCardRaw = Color(0xFFFFFDF9)
internal val LxSandRaw = Color(0xFFEDE7DB)
internal val LxSandDeepRaw = Color(0xFFDDD5C5)
internal val LxCardBorderRaw = Color(0x0F000000)

internal val LxInkRaw = Color(0xFF2D2A26)
internal val LxInkSoftRaw = Color(0xFF4A4640)
internal val LxInkMutedRaw = Color(0xFF8A847A)
internal val LxInkFaintRaw = Color(0xFFB5AFA5)
internal val LxInkGhostRaw = Color(0xFFD4CFC6)

internal val LxTerraRaw = Color(0xFFC4704B)
internal val LxTerraSoftRaw = Color(0x1FC4704B)
internal val LxTerraGlowRaw = Color(0x1AC4704B)
internal val LxSageRaw = Color(0xFF6B8F71)
internal val LxSageSoftRaw = Color(0x1A6B8F71)
internal val LxAmberRaw = Color(0xFFC49A4B)
internal val LxAmberSoftRaw = Color(0x1AC49A4B)
internal val LxPlumRaw = Color(0xFF8B6B8A)
internal val LxPlumSoftRaw = Color(0x1A8B6B8A)
internal val LxSlateRaw = Color(0xFF6B7C8A)
internal val LxSlateSoftRaw = Color(0x1A6B7C8A)
internal val LxRoseRaw = Color(0xFFB5645A)

internal val LxCategoryColorsLight: List<Color> = listOf(
    Color(0xFF7B9EC9), // slate-alt 蓝灰（高数）
    LxTerraRaw,        // terra 橙棕（英语）
    LxSageRaw,         // sage 暖绿（线代）
    LxPlumRaw,         // plum 紫（数据结构）
    LxAmberRaw,        // amber 黄（思政）
    LxSlateRaw,        // slate 灰蓝
    LxRoseRaw,         // rose 暗红（体育）
    Color(0xFF7A9A6E), // sage-alt 草绿
)

// ── Dark 基底 ──
internal val LxDarkBackgroundRaw = Color(0xFF121212)
internal val LxDarkSurfaceRaw = Color(0xFF1E1E1E)
internal val LxDarkSurfaceVariantRaw = Color(0xFF2A2A2A)
internal val LxDarkCreamRaw = Color(0xFF17171A)
internal val LxDarkSandRaw = Color(0xFF2A2A2A)
internal val LxDarkSandDeepRaw = Color(0xFF3A3530)
internal val LxDarkCardBorderRaw = Color(0x14FFFFFF)

internal val LxDarkOnBackgroundRaw = Color(0xFFE8E4DC)
internal val LxDarkOnSurfaceRaw = Color(0xFFE8E4DC)
internal val LxDarkOnSurfaceVariantRaw = Color(0xFF9E9688)
internal val LxDarkInkSoftRaw = Color(0xFFC9C2B4)
internal val LxDarkInkFaintRaw = Color(0xFF6A6459)
internal val LxDarkInkGhostRaw = Color(0xFF4A4640)

internal val LxDarkPrimaryRaw = Color(0xFFE59975)
internal val LxDarkPrimaryContainerRaw = Color(0xFF7A4530)
internal val LxDarkTerraSoftRaw = Color(0x33E59975)
internal val LxDarkTerraGlowRaw = Color(0x1FE59975)
internal val LxDarkSecondaryRaw = Color(0xFF96B79B)
internal val LxDarkSecondaryContainerRaw = Color(0xFF3E5A43)
internal val LxDarkSageSoftRaw = Color(0x3396B79B)
internal val LxDarkAmberRaw = Color(0xFFE5C178)
internal val LxDarkAmberSoftRaw = Color(0x33E5C178)
internal val LxDarkPlumRaw = Color(0xFFB593B4)
internal val LxDarkPlumSoftRaw = Color(0x33B593B4)
internal val LxDarkSlateRaw = Color(0xFF9AAABB)
internal val LxDarkSlateSoftRaw = Color(0x339AAABB)
internal val LxDarkRoseRaw = Color(0xFFD98A80)
internal val LxDarkOutlineRaw = Color(0xFF3A3530)

internal val LxCategoryColorsDark: List<Color> = listOf(
    Color(0xFFA8C4D8),  // slate-alt 提亮
    LxDarkPrimaryRaw,   // terra 提亮
    LxDarkSecondaryRaw, // sage 提亮
    LxDarkPlumRaw,      // plum 提亮
    LxDarkAmberRaw,     // amber 提亮
    LxDarkSlateRaw,     // slate 提亮
    LxDarkRoseRaw,      // rose 提亮
    Color(0xFF96C08B),  // sage-alt 提亮
)

// ─────────────────────────────────────────────────────────────
// 对外暴露的语义 token —— 随系统 Dark/Light 自动切换
// 所有 UI 层 import 仍走这些名字，调用点位于 Composable 作用域即可
// ─────────────────────────────────────────────────────────────

// ── 羊皮纸暖色背景 ──
val LxParchment: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkBackgroundRaw else LxParchmentRaw
val LxCream: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkCreamRaw else LxCreamRaw
val LxCard: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkSurfaceRaw else LxCardRaw
val LxSand: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkSandRaw else LxSandRaw
val LxSandDeep: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkSandDeepRaw else LxSandDeepRaw
val LxCardBorder: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkCardBorderRaw else LxCardBorderRaw

// ── 文本四级阶梯 ──
val LxInk: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkOnBackgroundRaw else LxInkRaw
val LxInkSoft: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkInkSoftRaw else LxInkSoftRaw
val LxInkMuted: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkOnSurfaceVariantRaw else LxInkMutedRaw
val LxInkFaint: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkInkFaintRaw else LxInkFaintRaw
val LxInkGhost: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkInkGhostRaw else LxInkGhostRaw

// ── 品牌语义色 ──
val LxTerra: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkPrimaryRaw else LxTerraRaw
val LxTerraSoft: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkTerraSoftRaw else LxTerraSoftRaw
val LxTerraGlow: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkTerraGlowRaw else LxTerraGlowRaw

val LxSage: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkSecondaryRaw else LxSageRaw
val LxSageSoft: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkSageSoftRaw else LxSageSoftRaw

val LxAmber: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkAmberRaw else LxAmberRaw
val LxAmberSoft: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkAmberSoftRaw else LxAmberSoftRaw

val LxPlum: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkPlumRaw else LxPlumRaw
val LxPlumSoft: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkPlumSoftRaw else LxPlumSoftRaw

val LxSlate: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkSlateRaw else LxSlateRaw
val LxSlateSoft: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkSlateSoftRaw else LxSlateSoftRaw

val LxRose: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) LxDarkRoseRaw else LxRoseRaw

// ── 历史别名 ──
val LxSuccess: Color @Composable @ReadOnlyComposable get() = LxSage
val LxWarning: Color @Composable @ReadOnlyComposable get() = LxAmber
val LxError: Color @Composable @ReadOnlyComposable get() = LxRose

// ── 课表 / 劳动图表 / 课程小圆点的八色分类色板 ──
val LxCategoryColors: List<Color>
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) LxCategoryColorsDark else LxCategoryColorsLight
