package com.lightxin.feature.onboarding.ui

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate

// viewBox 内坐标系：390 x 280
private const val VB_W = 390f
private const val VB_H = 280f

// 色板（与原型 SVG 的十六进制值一一对应）
private val SkyCream = Color(0xFFFAF7F2)
private val HorizonGlow = Color(0xFFFAC775)
private val GroundSand = Color(0xFFEDE7DB)
private val PathFill = Color(0xFFD4CEC4)
private val PathEdge = Color(0xFFC4B99A)
private val BuildingBase = Color(0xFFDDD5C5)
private val BuildingTop = Color(0xFFC4BDB0)
private val WindowBlue = Color(0xFFA8C4D8)
private val WindowAmber = Color(0xFFFAC775)
private val TrunkBrown = Color(0xFFB4A48A)
private val LeavesDark = Color(0xFF6B8F71)
private val LeavesLight = Color(0xFF7A9A6E)
private val BirdInk = Color(0xFFB4A48A)
private val FigureHair = Color(0xFF5A4832)
private val FigureBodyTerra = Color(0xFFC4704B)
private val FigureBodySage = Color(0xFF6B8F71)
private val PillarShade = Color(0x0A000000)
private val DoorLine = Color(0x14000000)

/**
 * 欢迎页上半屏插画：校园晨景，含多组独立动效。
 * 调用方设置宽度即可，高度按 viewBox 比例锁定。
 */
@Composable
fun OnboardingIllustration(modifier: Modifier = Modifier) {
    val trans = rememberInfiniteTransition(label = "onboardingIllus")

    // 地平线晨光呼吸（14s ease-in-out）
    val dawnAlpha by trans.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = EaseInOut), RepeatMode.Reverse),
        label = "dawn",
    )

    // 太阳浮动 + 光芒脉动
    val sunY by trans.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(7_000, easing = EaseInOut), RepeatMode.Reverse),
        label = "sunY",
    )
    val rayOpacity by trans.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(3_500, easing = EaseInOut), RepeatMode.Reverse),
        label = "rayOp",
    )

    // 云飘
    val cloud1X by trans.animateFloat(
        initialValue = 0f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = EaseInOut), RepeatMode.Reverse),
        label = "cloud1",
    )
    val cloud2X by trans.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(11_000, easing = EaseInOut), RepeatMode.Reverse),
        label = "cloud2",
    )

    // 树摇摆（两棵树各自周期，第二棵延迟 700ms）
    val treeAngle1 by trans.animateFloat(
        initialValue = -1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(5_800, easing = EaseInOut), RepeatMode.Reverse),
        label = "tree1",
    )
    val treeAngle2 by trans.animateFloat(
        initialValue = -0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(6_400, easing = EaseInOut), RepeatMode.Reverse),
        label = "tree2",
    )

    // 窗户辉光（6 组独立错相，覆盖主楼/宿舍/右楼代表性窗户）
    val winGlow1 by trans.animateFloat(0.5f, 0.9f, infiniteRepeatable(tween(5_200, easing = EaseInOut), RepeatMode.Reverse), "w1")
    val winGlow2 by trans.animateFloat(0.9f, 0.5f, infiniteRepeatable(tween(4_600, easing = EaseInOut), RepeatMode.Reverse), "w2")
    val winGlow3 by trans.animateFloat(0.5f, 0.85f, infiniteRepeatable(tween(6_000, easing = EaseInOut), RepeatMode.Reverse), "w3")
    val winGlow4 by trans.animateFloat(0.9f, 0.55f, infiniteRepeatable(tween(4_100, easing = EaseInOut), RepeatMode.Reverse), "w4")
    val winGlow5 by trans.animateFloat(0.55f, 0.9f, infiniteRepeatable(tween(5_700, easing = EaseInOut), RepeatMode.Reverse), "w5")
    val winGlow6 by trans.animateFloat(0.9f, 0.5f, infiniteRepeatable(tween(4_900, easing = EaseInOut), RepeatMode.Reverse), "w6")

    // 两个走路小人（18s / 22s 线性循环，walk 沿路径上移 + 两端淡入淡出）
    val walk1 by trans.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing)),
        label = "walk1",
    )
    val walk2 by trans.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing)),
        label = "walk2",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(VB_W / VB_H),
    ) {
        val scale = size.width / VB_W
        val sx = { x: Float -> x * scale }
        val sy = { y: Float -> y * scale }

        // ── 天空底 ──
        drawRect(color = SkyCream, size = size)

        // ── 地平线晨光椭圆 ──
        val glowCenter = Offset(sx(195f), sy(192f))
        val glowSize = Size(sx(280f * 2), sy(30f * 2))
        translate(left = glowCenter.x - glowSize.width / 2, top = glowCenter.y - glowSize.height / 2) {
            drawOval(color = HorizonGlow.copy(alpha = dawnAlpha), size = glowSize)
        }

        // ── 地面 ──
        drawRect(color = GroundSand, topLeft = Offset(0f, sy(192f)), size = Size(size.width, sy(88f)))

        // ── 小径（梯形） ──
        val pathTrap = Path().apply {
            moveTo(sx(177f), sy(192f))
            lineTo(sx(213f), sy(192f))
            lineTo(sx(220f), sy(280f))
            lineTo(sx(170f), sy(280f))
            close()
        }
        drawPath(path = pathTrap, color = PathFill.copy(alpha = 0.55f))
        drawLine(PathEdge.copy(alpha = 0.4f), Offset(sx(177f), sy(192f)), Offset(sx(170f), sy(280f)), strokeWidth = 0.5f * scale)
        drawLine(PathEdge.copy(alpha = 0.4f), Offset(sx(213f), sy(192f)), Offset(sx(220f), sy(280f)), strokeWidth = 0.5f * scale)

        // ── 云 1（左上）──
        translate(left = cloud1X * scale, top = 0f) {
            drawOval(
                color = GroundSand.copy(alpha = 0.75f),
                topLeft = Offset(sx(248f - 38f), sy(58f - 15f)),
                size = Size(sx(76f), sy(30f)),
            )
            drawOval(
                color = GroundSand.copy(alpha = 0.75f),
                topLeft = Offset(sx(270f - 24f), sy(48f - 13f)),
                size = Size(sx(48f), sy(26f)),
            )
            drawOval(
                color = GroundSand.copy(alpha = 0.65f),
                topLeft = Offset(sx(228f - 18f), sy(55f - 11f)),
                size = Size(sx(36f), sy(22f)),
            )
        }

        // ── 云 2（右上）──
        translate(left = cloud2X * scale, top = 0f) {
            drawOval(
                color = GroundSand.copy(alpha = 0.55f),
                topLeft = Offset(sx(340f - 26f), sy(82f - 11f)),
                size = Size(sx(52f), sy(22f)),
            )
            drawOval(
                color = GroundSand.copy(alpha = 0.5f),
                topLeft = Offset(sx(357f - 16f), sy(75f - 9f)),
                size = Size(sx(32f), sy(18f)),
            )
        }

        // ── 太阳组（整体上下浮动）──
        translate(left = 0f, top = sunY * scale) {
            // 8 条主射线 + 4 条斜射线（透明度随 rayOpacity）
            val rayColor = HorizonGlow.copy(alpha = rayOpacity)
            val rayStroke = 2.2f * scale
            val rayStrokeThin = 1.6f * scale
            val rayLines = listOf(
                Pair(Offset(sx(64f), sy(70f)), Offset(sx(64f), sy(57f))),
                Pair(Offset(sx(64f), sy(114f)), Offset(sx(64f), sy(127f))),
                Pair(Offset(sx(20f), sy(92f)), Offset(sx(7f), sy(92f))),
                Pair(Offset(sx(108f), sy(92f)), Offset(sx(121f), sy(92f))),
            )
            rayLines.forEach { (a, b) ->
                drawLine(rayColor, a, b, strokeWidth = rayStroke, cap = StrokeCap.Round)
            }
            val rayLinesThin = listOf(
                Pair(Offset(sx(33f), sy(79f)), Offset(sx(24f), sy(70f))),
                Pair(Offset(sx(95f), sy(79f)), Offset(sx(104f), sy(70f))),
                Pair(Offset(sx(33f), sy(105f)), Offset(sx(24f), sy(114f))),
                Pair(Offset(sx(95f), sy(105f)), Offset(sx(104f), sy(114f))),
            )
            rayLinesThin.forEach { (a, b) ->
                drawLine(rayColor, a, b, strokeWidth = rayStrokeThin, cap = StrokeCap.Round)
            }
            // 太阳本体
            drawCircle(color = HorizonGlow.copy(alpha = 0.88f), radius = sx(22f), center = Offset(sx(64f), sy(92f)))
            drawCircle(color = HorizonGlow.copy(alpha = 0.3f), radius = sx(16f), center = Offset(sx(64f), sy(92f)))
        }

        // ── 小鸟剪影（三条曲线）──
        val birdStroke = Stroke(width = 1.5f * scale, cap = StrokeCap.Round)
        listOf(
            Triple(138f, 46f, 0.55f),
            Triple(150f, 39f, 0.5f),
            Triple(165f, 52f, 0.4f),
        ).forEach { (x, y, a) ->
            val bird = Path().apply {
                moveTo(sx(x), sy(y))
                quadraticTo(sx(x + 3f), sy(y - 3f), sx(x + 6f), sy(y))
            }
            drawPath(bird, color = BirdInk.copy(alpha = a), style = birdStroke)
        }

        // ── 左侧宿舍楼 ──
        drawRect(BuildingBase, Offset(sx(36f), sy(126f)), Size(sx(76f), sy(66f)))
        drawRect(BuildingTop, Offset(sx(30f), sy(118f)), Size(sx(88f), sy(11f)))
        // 宿舍窗户两行（6 扇，复用 3 个动画值，错相）
        val dormWin = listOf(
            Triple(44f, 135f, WindowBlue.copy(alpha = winGlow1 * 0.6f)),
            Triple(63f, 135f, WindowAmber.copy(alpha = winGlow2 * 0.68f)),
            Triple(82f, 135f, WindowBlue.copy(alpha = winGlow3 * 0.55f)),
            Triple(44f, 153f, WindowAmber.copy(alpha = winGlow2 * 0.7f)),
            Triple(63f, 153f, WindowBlue.copy(alpha = winGlow4 * 0.5f)),
            Triple(82f, 153f, WindowAmber.copy(alpha = winGlow5 * 0.62f)),
        )
        dormWin.forEach { (x, y, c) ->
            drawRect(c, Offset(sx(x), sy(y)), Size(sx(13f), sy(11f)))
        }
        // 宿舍门
        drawRect(PathEdge, Offset(sx(63f), sy(172f)), Size(sx(16f), sy(20f)))

        // ── 中央主楼 ──
        drawRect(BuildingBase, Offset(sx(148f), sy(82f)), Size(sx(94f), sy(110f)))
        drawRect(BuildingTop, Offset(sx(141f), sy(73f)), Size(sx(108f), sy(13f)))
        drawRect(PillarShade, Offset(sx(156f), sy(86f)), Size(sx(3.5f), sy(106f)))
        drawRect(PillarShade, Offset(sx(230f), sy(86f)), Size(sx(3.5f), sy(106f)))
        // 主楼窗户 3 行 x 3 列 = 9 扇（复用 6 个动画值）
        val mainWin = listOf(
            Triple(158f, 95f, WindowBlue.copy(alpha = winGlow3 * 0.62f)),
            Triple(181f, 95f, WindowAmber.copy(alpha = winGlow2 * 0.7f)),
            Triple(204f, 95f, WindowBlue.copy(alpha = winGlow5 * 0.55f)),
            Triple(158f, 117f, WindowAmber.copy(alpha = winGlow6 * 0.65f)),
            Triple(181f, 117f, WindowBlue.copy(alpha = winGlow1 * 0.6f)),
            Triple(204f, 117f, WindowAmber.copy(alpha = winGlow4 * 0.7f)),
            Triple(158f, 139f, WindowBlue.copy(alpha = winGlow2 * 0.5f)),
            Triple(181f, 139f, WindowAmber.copy(alpha = winGlow5 * 0.62f)),
            Triple(204f, 139f, WindowBlue.copy(alpha = winGlow3 * 0.55f)),
        )
        mainWin.forEach { (x, y, c) ->
            drawRect(c, Offset(sx(x), sy(y)), Size(sx(17f), sy(14f)))
        }
        // 主楼大门（带中缝）
        drawRect(PathEdge, Offset(sx(178f), sy(164f)), Size(sx(24f), sy(28f)))
        drawLine(DoorLine, Offset(sx(190f), sy(164f)), Offset(sx(190f), sy(192f)), strokeWidth = 1f * scale)

        // ── 右楼 ──
        drawRect(BuildingBase, Offset(sx(284f), sy(104f)), Size(sx(78f), sy(88f)))
        drawRect(BuildingTop, Offset(sx(278f), sy(96f)), Size(sx(90f), sy(11f)))
        val rightWin = listOf(
            Triple(292f, 114f, WindowAmber.copy(alpha = winGlow4 * 0.66f)),
            Triple(312f, 114f, WindowBlue.copy(alpha = winGlow6 * 0.55f)),
            Triple(332f, 114f, WindowAmber.copy(alpha = winGlow3 * 0.6f)),
            Triple(292f, 133f, WindowBlue.copy(alpha = winGlow1 * 0.5f)),
            Triple(312f, 133f, WindowAmber.copy(alpha = winGlow2 * 0.65f)),
            Triple(332f, 133f, WindowBlue.copy(alpha = winGlow5 * 0.55f)),
        )
        rightWin.forEach { (x, y, c) ->
            drawRect(c, Offset(sx(x), sy(y)), Size(sx(14f), sy(12f)))
        }
        drawRect(PathEdge, Offset(sx(310f), sy(158f)), Size(sx(18f), sy(34f)))

        // ── 左树（从根部旋转） ──
        rotate(degrees = treeAngle1, pivot = Offset(sx(121.5f), sy(192f))) {
            drawRect(TrunkBrown, Offset(sx(119f), sy(157f)), Size(sx(5f), sy(35f)))
            drawOval(LeavesLight.copy(alpha = 0.82f), Offset(sx(121.5f - 16f), sy(150f - 17f)), Size(sx(32f), sy(34f)))
            drawOval(LeavesDark.copy(alpha = 0.4f), Offset(sx(121.5f - 10f), sy(142f - 11f)), Size(sx(20f), sy(22f)))
        }
        // ── 右树 ──
        rotate(degrees = treeAngle2, pivot = Offset(sx(254f), sy(192f))) {
            drawRect(TrunkBrown, Offset(sx(252f), sy(162f)), Size(sx(4f), sy(30f)))
            drawOval(LeavesDark.copy(alpha = 0.78f), Offset(sx(254f - 14f), sy(156f - 15f)), Size(sx(28f), sy(30f)))
        }

        // ── 灌木（静态） ──
        drawOval(LeavesLight.copy(alpha = 0.45f), Offset(sx(138f - 18f), sy(193f - 10f)), Size(sx(36f), sy(20f)))
        drawOval(LeavesDark.copy(alpha = 0.4f), Offset(sx(252f - 16f), sy(193f - 9f)), Size(sx(32f), sy(18f)))
        drawOval(LeavesLight.copy(alpha = 0.35f), Offset(sx(280f - 12f), sy(193f - 8f)), Size(sx(24f), sy(16f)))

        // ── 小人 1（terra 衣）── 沿路径上移 0 → -78px
        drawFigure(
            baseX = 198f,
            baseY = 270f,
            translateY = -78f * walk1,
            translateX = -6f * walk1,
            alpha = walkAlpha(walk1, edgeFade = 0.08f, maxA = 0.85f),
            headR = 2.2f,
            bodyRx = 2.4f,
            bodyRy = 3.4f,
            bodyColor = FigureBodyTerra,
            scale = scale,
        )
        // ── 小人 2（sage 衣），路径略不同 ──
        drawFigure(
            baseX = 190f,
            baseY = 274f,
            translateY = -70f * walk2,
            translateX = 4f * walk2,
            alpha = walkAlpha(walk2, edgeFade = 0.10f, maxA = 0.75f),
            headR = 2f,
            bodyRx = 2.2f,
            bodyRy = 3.1f,
            bodyColor = FigureBodySage,
            scale = scale,
        )
    }
}

/** 走路小人两端淡入淡出：8% / 10% 边缘曲线，中段保持 maxA */
private fun walkAlpha(t: Float, edgeFade: Float, maxA: Float): Float = when {
    t < edgeFade -> (t / edgeFade) * maxA
    t > 1f - edgeFade -> ((1f - t) / edgeFade) * maxA
    else -> maxA
}

/** 画小人：头圆 + 身体椭圆 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFigure(
    baseX: Float,
    baseY: Float,
    translateX: Float,
    translateY: Float,
    alpha: Float,
    headR: Float,
    bodyRx: Float,
    bodyRy: Float,
    bodyColor: Color,
    scale: Float,
) {
    val cx = (baseX + translateX) * scale
    val cy = (baseY + translateY) * scale
    drawCircle(
        color = FigureHair.copy(alpha = alpha),
        radius = headR * scale,
        center = Offset(cx, cy),
    )
    drawOval(
        color = bodyColor.copy(alpha = alpha * 0.95f),
        topLeft = Offset(cx - bodyRx * scale, cy + (6f - bodyRy) * scale),
        size = Size(bodyRx * 2 * scale, bodyRy * 2 * scale),
    )
}
