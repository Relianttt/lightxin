package com.lightxin.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// ─── 羊皮纸暖色背景 ───
val LxParchment = Color(0xFFF5F0E8)   // 主背景
val LxCream = Color(0xFFFAF7F2)       // 次级背景 / 插画框底 / 按压态
val LxCard = Color(0xFFFFFDF9)        // 卡片底色（偏暖白）
val LxSand = Color(0xFFEDE7DB)        // 分隔 / 浅填充
val LxSandDeep = Color(0xFFDDD5C5)    // 建筑 / 深色填充 / 输入框边框

// 卡片细边框（透明黑 6%，贴近任意底色都有轻微阴影感）
val LxCardBorder = Color(0x0F000000)

// ─── 文本四级阶梯 ───
val LxInk = Color(0xFF2D2A26)         // 主文本
val LxInkSoft = Color(0xFF4A4640)     // 次文本 / 强调副文本
val LxInkMuted = Color(0xFF8A847A)    // 弱化文本 / 副标题
val LxInkFaint = Color(0xFFB5AFA5)    // 占位 / 未选中
val LxInkGhost = Color(0xFFD4CFC6)    // 装饰符号（`›` 箭头等）

// ─── 品牌语义色 ───
val LxTerra = Color(0xFFC4704B)       // 主品牌（课程 / 链接 / 主按钮）
val LxTerraSoft = Color(0x1FC4704B)   // alpha .12，胶囊 / 方块图标底
val LxTerraGlow = Color(0x0FC4704B)   // alpha .06，今日列高亮

val LxSage = Color(0xFF6B8F71)        // 运动 / 成功
val LxSageSoft = Color(0x1A6B8F71)    // alpha .10

val LxAmber = Color(0xFFC49A4B)       // 查寝 / 提醒
val LxAmberSoft = Color(0x1AC49A4B)

val LxPlum = Color(0xFF8B6B8A)        // 劳动
val LxPlumSoft = Color(0x1A8B6B8A)

val LxSlate = Color(0xFF6B7C8A)       // 中性辅助
val LxSlateSoft = Color(0x1A6B7C8A)

val LxRose = Color(0xFFB5645A)        // 错误 / 退出登录

// 历史别名（保持向后兼容，指向新语义色）
val LxSuccess = LxSage
val LxWarning = LxAmber
val LxError = LxRose

// ─── Material colorScheme 映射值 ───
// 这些变量在 Theme.kt 中被引用；命名保留旧名以减少下游改动面
val LxBackground = LxParchment
val LxSurface = LxCard
val LxSurfaceVariant = LxSand
val LxPrimary = LxTerra
val LxPrimaryContainer = LxTerraSoft
val LxSecondary = LxSage
val LxSecondaryContainer = LxSageSoft
val LxOnBackground = LxInk
val LxOnSurface = LxInk
val LxOnSurfaceVariant = LxInkMuted
val LxOutline = LxSandDeep

// ─── 八色分类色板（课表 / 劳动图表 / 课程小圆点共享）───
// 顺序与 prototype/anthropic-redesign.html 的 courses 色值一一对应
val LxCategoryColors = listOf(
    Color(0xFF7B9EC9), // slate-alt 蓝灰（高数）
    LxTerra,           // terra 橙棕（英语）
    LxSage,            // sage 暖绿（线代）
    LxPlum,            // plum 紫（数据结构）
    LxAmber,           // amber 黄（思政）
    LxSlate,           // slate 灰蓝
    LxRose,            // rose 暗红（体育）
    Color(0xFF7A9A6E), // sage-alt 草绿
)

// ─── 暗色主题（本阶段不改，占位保持编译通过）───
val LxDarkBackground = Color(0xFF121212)
val LxDarkSurface = Color(0xFF1E1E1E)
val LxDarkSurfaceVariant = Color(0xFF2A2A2A)
val LxDarkPrimary = Color(0xFFE59975)
val LxDarkPrimaryContainer = Color(0xFF7A4530)
val LxDarkSecondary = Color(0xFF96B79B)
val LxDarkSecondaryContainer = Color(0xFF3E5A43)
val LxDarkOnBackground = Color(0xFFE8E4DC)
val LxDarkOnSurface = Color(0xFFE8E4DC)
val LxDarkOnSurfaceVariant = Color(0xFF9E9688)
val LxDarkOutline = Color(0xFF3A3530)
val LxDarkError = Color(0xFFD98A80)
val LxDarkSuccess = Color(0xFF8FB396)
val LxDarkWarning = Color(0xFFE5C178)
