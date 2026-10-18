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
 * 基调：借“连续性”而不借“弹”——所有 spring 走临界阻尼（dampingRatio = 1f），
 * 按下及时响应，状态与页面舒缓收尾；不做回弹、视差、无限装饰动画。
 * 参数只在这里改，组件与页面不各自写数字；`MotionTokenGateTest` 会拦截字面量。
 *
 * 节奏：按下即时（spring）→ 状态 280ms → 分页吸附 420ms → 页面进入 / 退出 420ms。
 * 高频的分页切换不再与页面导航共用同一个慢时长。
 */
object LxMotion {
    // ── 时长 ──
    const val StateMs = 280
    const val PageMs = 420
    const val PagePopExitMs = 350
    const val TabMs = 420
    const val DialogExitMs = 220
    /** 小范围淡入淡出：顶栏小标题、折叠淡出。 */
    const val QuickFadeMs = 160
    /** 收起短于展开，避免退出拖沓。 */
    const val CollapseMs = 180
    /** 首页横幅 / 副标切换。 */
    const val BannerMs = 200
    const val BannerDelayMs = 200
    /** 首页卡片入场：恢复 7 月手感（350ms、上移 1/3 卡高、60ms 错峰）。 */
    const val CardEnterMs = 350
    const val CardStaggerMs = 60
    const val CardShiftFraction = 0.3333f
    const val SharedBoundsStiffness = 240f

    // ── 加载 / 首次入场（持续动画与 onboarding 例外）──
    const val SkeletonMs = 1800
    const val ShimmerMs = 1200
    const val PulseMs = 2600
    const val OnboardingEnterMs = 450
    const val OnboardingStaggerBaseMs = 80
    const val OnboardingStaggerStepMs = 90

    // ── 位移 / 缩放 ──
    val EnterShiftDp = 6.dp
    const val PageShiftFraction = 0.25f
    const val PagePopShiftFraction = 0.25f
    const val TabShiftFraction = 0.25f
    /** onboarding 插画式入场：屏高 1/4。显式例外，不用于列表与状态过渡。 */
    const val OnboardingShiftFraction = 0.25f
    const val PressedScaleButton = 0.98f
    const val PressedScaleCard = 0.99f

    // ── 曲线：快出慢收，无 overshoot ──
    val EaseOut = CubicBezierEasing(0.25f, 0.8f, 0.25f, 1f)
    val EaseInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    // ── 弹簧：全部临界阻尼 ──
    val PressScale: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 700f)
    val SoftDp: SpringSpec<Dp> = spring(dampingRatio = 1f, stiffness = 240f)
    val ColorSpring: SpringSpec<Color> = spring(dampingRatio = 1f, stiffness = 600f)

    // ── 常用 tween ──
    fun tab() = tween<Float>(durationMillis = TabMs, easing = EaseOut)
    fun dialogExit() = tween<Float>(durationMillis = DialogExitMs, easing = EaseOut)
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
