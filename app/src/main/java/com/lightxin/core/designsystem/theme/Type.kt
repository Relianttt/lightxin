package com.lightxin.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lightxin.R

/**
 * Newsreader 小号光学尺寸（14pt）— 正文级标题（14~18sp）。
 * 字形在小字下更粗、字距更宽，利于卡片标题阅读。
 */
val NewsreaderSmall = FontFamily(
    Font(R.font.newsreader_14_regular, FontWeight.Normal),
    Font(R.font.newsreader_14_medium, FontWeight.Medium),
)

/**
 * Newsreader 大号光学尺寸（24pt）— 大标题（20~30sp）。
 */
val NewsreaderLarge = FontFamily(
    Font(R.font.newsreader_24_regular, FontWeight.Normal),
    Font(R.font.newsreader_24_medium, FontWeight.Medium),
)

/**
 * Newsreader 超大光学尺寸（36pt）— 显示字号（30sp 以上）。
 */
val NewsreaderDisplay = FontFamily(
    Font(R.font.newsreader_36_regular, FontWeight.Normal),
    Font(R.font.newsreader_36_medium, FontWeight.Medium),
)

/**
 * Outfit — 正文、按钮、数字。三字重覆盖全部 sans 场景。
 */
val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
)

val LightXinTypography = Typography(
    // 品牌级显示字 — 当前仅保留，阶段 1 无使用
    displayLarge = TextStyle(
        fontFamily = NewsreaderDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.3).sp,
    ),
    // 首页问候语、我的页标题
    headlineLarge = TextStyle(
        fontFamily = NewsreaderDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
    ),
    // 欢迎页 tagline、较大标题
    headlineMedium = TextStyle(
        fontFamily = NewsreaderLarge,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = NewsreaderLarge,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    // BottomSheet 标题、详情页标题
    titleLarge = TextStyle(
        fontFamily = NewsreaderLarge,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    // 卡片标题（今日课程 / 查寝 / 运动 / 劳动）
    titleMedium = TextStyle(
        fontFamily = NewsreaderSmall,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = NewsreaderSmall,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // 以下全部 Outfit
    bodyLarge = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)

/**
 * 数字场景的等宽 feature。使用方式：
 *   Text(..., style = MaterialTheme.typography.bodyMedium.merge(LxTabularNums))
 * 或在 Text 处直接加 fontFeatureSettings = "tnum"。
 */
val LxTabularNums = TextStyle(fontFeatureSettings = "tnum")
