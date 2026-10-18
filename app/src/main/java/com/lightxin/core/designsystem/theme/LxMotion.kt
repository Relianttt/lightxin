package com.lightxin.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全项目统一的动效 token。
 *
 * 基调：借「连续性」而不借「弹」——所有 spring 走临界阻尼（dampingRatio = 1f），
 * 交互反馈 ≤ 100ms、状态过渡 ≤ 200ms、页面转场 ≤ 260ms；不做回弹、视差、无限装饰动画。
 * 参数只在这里改，组件与页面不各自写数字。
 */
object LxMotion {
    // ── 时长 ──
    const val PressInMs = 80
    const val PressOutMs = 140
    const val StateMs = 160
    const val PageMs = 240
    const val PagePopExitMs = 160

    // ── 位移 / 缩放 ──
    val EnterShiftDp = 6.dp
    const val PageShiftFraction = 0.12f
    const val PagePopShiftFraction = 0.08f
    const val TabShiftFraction = 0.06f
    const val PressedScaleButton = 0.98f
    const val PressedScaleCard = 0.99f

    // ── 曲线：快出慢收，无 overshoot ──
    val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val EaseInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    // ── 弹簧：全部临界阻尼 ──
    val PressScale: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 900f)
    val Soft: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 380f)
    val SoftDp: SpringSpec<Dp> = spring(dampingRatio = 1f, stiffness = 380f)
    val Page: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 520f)
    val ColorSpring: SpringSpec<Color> = spring(dampingRatio = 1f, stiffness = 1200f)

    // ── 常用 tween ──
    fun state() = tween<Float>(durationMillis = StateMs, easing = EaseOut)
    fun pressIn() = tween<Float>(durationMillis = PressInMs, easing = EaseOut)
    fun pressOut() = tween<Float>(durationMillis = PressOutMs, easing = EaseOut)
    fun page() = tween<Float>(durationMillis = PageMs, easing = EaseOut)
}

/** 按压时连续缩放到 [pressedScale]，禁用态由调用方传 pressedScale = 1f。 */
@Composable
fun animatedPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = LxMotion.PressedScaleButton,
): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = LxMotion.PressScale,
        label = "lxPressScale",
    )
}

/** 按压时在 [rest] / [pressed] 之间连续过渡颜色。 */
@Composable
fun animatedPressColor(
    interactionSource: InteractionSource,
    rest: Color,
    pressed: Color,
): State<Color> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateColorAsState(
        targetValue = if (isPressed) pressed else rest,
        animationSpec = LxMotion.ColorSpring,
        label = "lxPressColor",
    )
}

/**
 * 列表/空态的入场：淡入 + 6dp 连续位移（不再用 it/4 之类的比例位移）。
 * 系统关闭动画时 Compose 会按 durationScale = 0 直接落到终值。
 */
@Composable
fun Modifier.lxEnterShift(visible: Boolean = true): Modifier {
    val offset by animateDpAsState(
        targetValue = if (visible) 0.dp else LxMotion.EnterShiftDp,
        animationSpec = LxMotion.SoftDp,
        label = "lxEnterShift",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = LxMotion.StateMs, easing = LxMotion.EaseOut),
        label = "lxEnterAlpha",
    )
    return this
        .offset(y = offset)
        .alpha(alpha)
}
