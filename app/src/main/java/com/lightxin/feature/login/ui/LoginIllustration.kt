package com.lightxin.feature.login.ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate

// viewBox 内坐标系：390 x 220
private const val VB_W = 390f
private const val VB_H = 220f

// 色板（贴近原型值）
private val FrameBg = Color(0xFFFAF7F2)
private val CardFill = Color(0xFFFFFDF9)
private val CardBorder = Color(0xFFE8E2D8)
private val HeaderFill = Color(0xFFF0EBE1)
private val InkStrong = Color(0xFF7A5C3A)
private val InkMid = Color(0xFF4A4640)
private val InkFaint = Color(0xFFB5AFA5)
private val Terra = Color(0xFFC4704B)
private val Slate = Color(0xFF7B9EC9)
private val Sage = Color(0xFF6B8F71)
private val Amber = Color(0xFFC49A4B)
private val DotGhost = Color(0xFFD4CFC6)
private val SandFill = Color(0xFFEDE7DB)

/**
 * 登录页插画：四元素 + 暖色曲线，各元素独立浮动。
 */
@Composable
fun LoginIllustration(modifier: Modifier = Modifier) {
    val trans = rememberInfiniteTransition(label = "loginIllus")

    // 左卡浮动（上下 ±9px，周期 6.5s）
    val card1Y by trans.animateFloat(
        initialValue = 0f,
        targetValue = -9f,
        animationSpec = infiniteRepeatable(tween(6_500, easing = EaseInOut), RepeatMode.Reverse),
        label = "card1Y",
    )
    // 通知药丸浮动（-5 ↔ 5，7.4s）
    val card2Y by trans.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(7_400, easing = EaseInOut), RepeatMode.Reverse),
        label = "card2Y",
    )
    // 运动卡浮动（4 ↔ -6，7.1s）
    val card3Y by trans.animateFloat(
        initialValue = 4f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(7_100, easing = EaseInOut), RepeatMode.Reverse),
        label = "card3Y",
    )
    // 时钟浮动（-4 ↔ 6，8.2s）
    val clockY by trans.animateFloat(
        initialValue = -4f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(8_200, easing = EaseInOut), RepeatMode.Reverse),
        label = "clockY",
    )
    // 秒针旋转（60s 线性）
    val secondAngle by trans.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "sec",
    )
    // 运动环填充（一次性 + 保持，用 stroke-dashoffset 模拟）
    val ringSweep by trans.animateFloat(
        initialValue = 0f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 2_200, delayMillis = 600, easing = EaseInOut),
            RepeatMode.Reverse,
        ),
        label = "ring",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(VB_W / VB_H),
    ) {
        val scale = size.width / VB_W

        drawRect(color = FrameBg, size = size)

        // ── 左卡：课程列表，rotate -4.5°，上下浮动 ──
        translate(left = 0f, top = card1Y * scale) {
            rotate(degrees = -4.5f, pivot = Offset(80f * scale, 100f * scale)) {
                drawCourseListCard(scale)
            }
        }

        // ── 橙色有机曲线（背景层，静态）──
        val curve = Path().apply {
            moveTo(14f * scale, 178f * scale)
            cubicTo(60f * scale, 150f * scale, 110f * scale, 200f * scale, 168f * scale, 172f * scale)
            cubicTo(220f * scale, 152f * scale, 260f * scale, 150f * scale, 308f * scale, 178f * scale)
            cubicTo(340f * scale, 192f * scale, 370f * scale, 166f * scale, 384f * scale, 184f * scale)
        }
        drawPath(
            path = curve,
            color = Terra.copy(alpha = 0.32f),
            style = Stroke(width = 2f * scale, cap = StrokeCap.Round),
        )

        // ── 时钟（右上，rotate 2°，浮动 + 秒针旋转）──
        translate(left = 0f, top = clockY * scale) {
            rotate(degrees = 2f, pivot = Offset(310f * scale, 56f * scale)) {
                drawClock(scale, secondAngle)
            }
        }

        // ── 通知药丸（右中，rotate 3°，浮动）──
        translate(left = 0f, top = card2Y * scale) {
            rotate(degrees = 3f, pivot = Offset(268f * scale, 118f * scale)) {
                drawNotificationPill(scale)
            }
        }

        // ── 运动卡（右下，rotate -2°，浮动 + ring 动画）──
        translate(left = 0f, top = card3Y * scale) {
            rotate(degrees = -2f, pivot = Offset(140f * scale, 184f * scale)) {
                drawExerciseCard(scale, ringSweep)
            }
        }
    }
}

// ═══════════════ 左卡：课程列表 ═══════════════
private fun DrawScope.drawCourseListCard(s: Float) {
    val r = 11f * s
    // 白底
    drawRoundRect(
        color = CardFill,
        topLeft = Offset(28f * s, 36f * s),
        size = Size(124f * s, 104f * s),
        cornerRadius = cornerRadius(r),
    )
    drawRoundRect(
        color = CardBorder,
        topLeft = Offset(28f * s, 36f * s),
        size = Size(124f * s, 104f * s),
        cornerRadius = cornerRadius(r),
        style = Stroke(width = 1f * s),
    )
    // 顶部带状区
    drawRoundRect(
        color = HeaderFill,
        topLeft = Offset(28f * s, 36f * s),
        size = Size(124f * s, 30f * s),
        cornerRadius = cornerRadius(r),
    )
    drawRect(HeaderFill, Offset(28f * s, 55f * s), Size(124f * s, 11f * s))
    // 头部小方块 + 标题占位
    drawRoundRect(Terra.copy(alpha = 0.75f), Offset(40f * s, 46f * s), Size(9f * s, 9f * s), cornerRadius = cornerRadius(2f * s))
    drawRoundRect(InkStrong, Offset(53f * s, 47f * s), Size(36f * s, 4f * s), cornerRadius = cornerRadius(2f * s))
    // 三条彩色竖条 + 对应占位文本
    data class Row(val y: Float, val color: Color)
    listOf(Row(78f, Slate), Row(100f, Terra), Row(122f, Sage)).forEachIndexed { i, row ->
        drawRoundRect(row.color, Offset(40f * s, row.y * s), Size(3f * s, 16f * s), cornerRadius = cornerRadius(1.5f * s))
        val mainWidths = listOf(48f, 40f, 54f)
        drawRoundRect(InkMid, Offset(48f * s, (row.y + 2f) * s), Size(mainWidths[i] * s, 4f * s), cornerRadius = cornerRadius(2f * s))
        val subWidths = listOf(34f, 28f, 36f)
        drawRoundRect(InkFaint, Offset(48f * s, (row.y + 9f) * s), Size(subWidths[i] * s, 3f * s), cornerRadius = cornerRadius(1.5f * s))
    }
}

// ═══════════════ 圆时钟 ═══════════════
private fun DrawScope.drawClock(s: Float, secAngle: Float) {
    val cx = 310f * s
    val cy = 56f * s
    // 外圈
    drawCircle(CardFill, radius = 32f * s, center = Offset(cx, cy))
    drawCircle(CardBorder, radius = 32f * s, center = Offset(cx, cy), style = Stroke(width = 1f * s))
    // 内装饰圈
    drawCircle(HeaderFill, radius = 28f * s, center = Offset(cx, cy), style = Stroke(width = 1f * s))
    // 主要刻度（12/6 加粗 terra/灰）
    drawRoundRect(Terra.copy(alpha = 0.8f), Offset(308.7f * s, 27f * s), Size(2.6f * s, 6f * s), cornerRadius = cornerRadius(1.2f * s))
    drawRoundRect(InkFaint, Offset(308.7f * s, 79f * s), Size(2.6f * s, 6f * s), cornerRadius = cornerRadius(1.2f * s))
    drawRoundRect(InkFaint, Offset(281f * s, 54.7f * s), Size(6f * s, 2.6f * s), cornerRadius = cornerRadius(1.2f * s))
    drawRoundRect(InkFaint, Offset(333f * s, 54.7f * s), Size(6f * s, 2.6f * s), cornerRadius = cornerRadius(1.2f * s))
    // 次要刻度小点
    listOf(
        Offset(325f * s, 31f * s), Offset(335f * s, 41f * s),
        Offset(335f * s, 71f * s), Offset(325f * s, 81f * s),
        Offset(295f * s, 81f * s), Offset(285f * s, 71f * s),
        Offset(285f * s, 41f * s), Offset(295f * s, 31f * s),
    ).forEach { drawCircle(DotGhost, radius = 1.1f * s, center = it) }
    // 时针指向 10
    drawLine(InkMid, Offset(cx, cy), Offset(298f * s, 49f * s), strokeWidth = 2.4f * s, cap = StrokeCap.Round)
    // 分针指向 2
    drawLine(InkMid, Offset(cx, cy), Offset(326f * s, 47f * s), strokeWidth = 2f * s, cap = StrokeCap.Round)
    // 秒针（绕中心旋转）
    rotate(degrees = secAngle, pivot = Offset(cx, cy)) {
        drawLine(Terra.copy(alpha = 0.85f), Offset(cx, cy), Offset(cx, cy - 24f * s), strokeWidth = 1.1f * s, cap = StrokeCap.Round)
    }
    // 中心圆帽
    drawCircle(InkMid, radius = 2.4f * s, center = Offset(cx, cy))
    drawCircle(CardFill, radius = 1.1f * s, center = Offset(cx, cy))
}

// ═══════════════ 通知药丸卡 ═══════════════
private fun DrawScope.drawNotificationPill(s: Float) {
    val r = 14f * s
    drawRoundRect(CardFill, Offset(194f * s, 94f * s), Size(148f * s, 52f * s), cornerRadius = cornerRadius(r))
    drawRoundRect(CardBorder, Offset(194f * s, 94f * s), Size(148f * s, 52f * s), cornerRadius = cornerRadius(r), style = Stroke(width = 1f * s))
    // 图标 tile
    drawRoundRect(HeaderFill, Offset(206f * s, 106f * s), Size(28f * s, 28f * s), cornerRadius = cornerRadius(8f * s))
    drawRoundRect(Amber.copy(alpha = 0.85f), Offset(214f * s, 114f * s), Size(12f * s, 12f * s), cornerRadius = cornerRadius(3f * s))
    // 文本占位
    drawRoundRect(InkMid, Offset(244f * s, 108f * s), Size(70f * s, 4f * s), cornerRadius = cornerRadius(2f * s))
    drawRoundRect(InkFaint, Offset(244f * s, 118f * s), Size(54f * s, 3f * s), cornerRadius = cornerRadius(1.5f * s))
    drawRoundRect(InkFaint.copy(alpha = 0.55f), Offset(244f * s, 127f * s), Size(40f * s, 3f * s), cornerRadius = cornerRadius(1.5f * s))
}

// ═══════════════ 运动卡（带环形进度 + 总进度条） ═══════════════
private fun DrawScope.drawExerciseCard(s: Float, sweep: Float) {
    val r = 11f * s
    drawRoundRect(CardFill, Offset(64f * s, 152f * s), Size(152f * s, 64f * s), cornerRadius = cornerRadius(r))
    drawRoundRect(CardBorder, Offset(64f * s, 152f * s), Size(152f * s, 64f * s), cornerRadius = cornerRadius(r), style = Stroke(width = 1f * s))
    // 顶部带
    drawRoundRect(HeaderFill, Offset(64f * s, 152f * s), Size(152f * s, 18f * s), cornerRadius = cornerRadius(r))
    drawRect(HeaderFill, Offset(64f * s, 163f * s), Size(152f * s, 7f * s))
    // 标题小块 + 占位
    drawRoundRect(Sage.copy(alpha = 0.8f), Offset(74f * s, 157f * s), Size(8f * s, 8f * s), cornerRadius = cornerRadius(2f * s))
    drawRoundRect(InkStrong, Offset(87f * s, 158.5f * s), Size(34f * s, 4f * s), cornerRadius = cornerRadius(2f * s))
    // Ring（12 半径，stroke 3.2）
    val ringC = Offset(90f * s, 194f * s)
    val ringR = 12f * s
    val ringStroke = 3.2f * s
    drawCircle(SandFill, radius = ringR, center = ringC, style = Stroke(width = ringStroke))
    // 进度弧（sweep * 360 度）
    val arcTl = Offset(ringC.x - ringR, ringC.y - ringR)
    val arcSize = Size(ringR * 2, ringR * 2)
    drawArc(
        color = Sage,
        startAngle = -90f,
        sweepAngle = sweep * 360f,
        useCenter = false,
        topLeft = arcTl,
        size = arcSize,
        style = Stroke(width = ringStroke, cap = StrokeCap.Round),
    )
    // 右侧文本占位
    drawRoundRect(InkMid, Offset(114f * s, 182f * s), Size(50f * s, 4f * s), cornerRadius = cornerRadius(2f * s))
    drawRoundRect(InkFaint, Offset(114f * s, 190f * s), Size(34f * s, 3f * s), cornerRadius = cornerRadius(1.5f * s))
    // 迷你总进度条
    drawRoundRect(SandFill, Offset(114f * s, 201f * s), Size(86f * s, 3.5f * s), cornerRadius = cornerRadius(1.7f * s))
    drawRoundRect(Sage.copy(alpha = 0.75f), Offset(114f * s, 201f * s), Size(60f * s, 3.5f * s), cornerRadius = cornerRadius(1.7f * s))
}

private fun cornerRadius(r: Float) = androidx.compose.ui.geometry.CornerRadius(r, r)
