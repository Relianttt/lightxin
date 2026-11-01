package com.lightxin.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** 动效 v3：参数与原则见 .agents/notes/2026-10-25-decision-motion-v3.md。 */
object LxMotion {
    // ── 曲线 ──
    /** 进场 / 落定：起步快、减速段很长。 */
    val EaseEnter = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    /** 退场：加速离开，不拖尾。 */
    val EaseExit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    /** 屏内往返（展开 / 收起、颜色）。 */
    val EaseInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    // ── 时长 ──
    const val PressInMs = 90
    const val PressOutMs = 200
    const val StateMs = 220
    const val StateExitMs = 160
    const val EnterMs = 400          // 内容 / 列表项入场
    const val OverlayEnterMs = 320   // 对话框、菜单
    const val OverlayExitMs = 180
    /** 返回手势期间页面按进度线性跟手；松手后按剩余比例走完这段时长。 */
    const val PageGestureMs = 350
    const val TabFadeOutMs = 90      // 底部 tab 淡出淡入
    const val TabFadeInMs = 220
    const val ContentDelayMs = 60
    const val StaggerMs = 30
    const val StaggerMaxItems = 5
    const val StaggerWindowMs = 600  // 首屏错峰窗口：StaggerMs * StaggerMaxItems + EnterMs 再留余量
    const val CountUpMs = 700
    const val QuickFadeMs = 160
    const val CollapseMs = 180
    const val BannerMs = 200
    const val BannerDelayMs = 200
    /** 首页卡片入场：用户 2026-10 指定的手感，数值不改，只换曲线。 */
    const val CardEnterMs = 350
    const val CardStaggerMs = 60
    const val CardShiftFraction = 0.3333f

    // ── 加载 / 引导（持续动画例外）──
    const val SkeletonMs = 2200
    const val ShimmerMs = 1200
    const val PulseMs = 2600
    const val OnboardingEnterMs = 450
    const val OnboardingStaggerBaseMs = 80
    const val OnboardingStaggerStepMs = 90
    const val OnboardingShiftFraction = 0.25f

    // ── 位移 / 缩放 ──
    val EnterShiftDp = 10.dp
    const val PageUnderShiftFraction = 0.25f  // 覆盖式转场：下层页视差位移
    const val TabInitialScale = 0.98f
    const val OverlayInitialScale = 0.96f
    const val OverlayExitScale = 0.98f
    const val PressedScaleButton = 0.97f
    const val PressedScaleCard = 0.985f
    const val PressOverlayAlpha = 0.05f

    // ── 弹簧 ──
    const val MoveStiffness = 380f
    const val SharedBoundsStiffness = 300f
    /** 页面覆盖转场：临界阻尼，整屏位移约 400ms 视觉落定。 */
    const val PageStiffness = 380f
    val PressIn: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 1500f)
    /** 唯一允许的极轻回弹：按压松开。觉得多余时把 dampingRatio 改回 1f 即可整体回退。 */
    val PressRelease: SpringSpec<Float> = spring(dampingRatio = 0.75f, stiffness = 500f)
    val ColorSpring: SpringSpec<Color> = spring(dampingRatio = 1f, stiffness = 600f)
    fun pagerSnap(): SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = MoveStiffness)
    fun placement(): SpringSpec<IntOffset> =
        spring(dampingRatio = 1f, stiffness = MoveStiffness, visibilityThreshold = IntOffset.VisibilityThreshold)
    fun pageSlide(): SpringSpec<IntOffset> =
        spring(dampingRatio = 1f, stiffness = PageStiffness, visibilityThreshold = IntOffset.VisibilityThreshold)

    // ── 常用 tween ──
    fun <T> enter(durationMs: Int = EnterMs, delayMs: Int = 0) = tween<T>(durationMs, delayMs, EaseEnter)
    fun <T> exit(durationMs: Int = StateExitMs) = tween<T>(durationMs, easing = EaseExit)
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
        animationSpec = if (pressed) LxMotion.PressIn else LxMotion.PressRelease,
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
 * 列表/空态的入场：淡入 + 10dp 连续位移（不再用 it/4 之类的比例位移）。
 * 系统关闭动画时 Compose 会按 durationScale = 0 直接落到终值。
 */
@Composable
fun Modifier.lxEnterShift(visible: Boolean = true): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { entered = visible }
    val offset by animateDpAsState(
        targetValue = if (entered) 0.dp else LxMotion.EnterShiftDp,
        animationSpec = LxMotion.enter(delayMs = LxMotion.ContentDelayMs),
        label = "lxEnterShift",
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = LxMotion.enter(delayMs = LxMotion.ContentDelayMs),
        label = "lxEnterAlpha",
    )
    return this
        .offset(y = offset)
        .alpha(alpha)
}

/**
 * 列表项首屏错峰入场：淡入 + 10dp 上浮，每项比上一项晚 [LxMotion.StaggerMs]。
 * [play] 为 false 或序号超出 [LxMotion.StaggerMaxItems] 时直接落到终值，不参与错峰。
 */
@Composable
fun Modifier.lxStaggerEnter(index: Int, play: Boolean): Modifier {
    val start = play && index < LxMotion.StaggerMaxItems
    val progress = remember { Animatable(if (start) 0f else 1f) }
    LaunchedEffect(start) {
        if (start && progress.value < 1f) {
            progress.animateTo(1f, tween(LxMotion.EnterMs, index * LxMotion.StaggerMs, LxMotion.EaseEnter))
        }
    }
    val shift = with(LocalDensity.current) { LxMotion.EnterShiftDp.toPx() }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * shift
    }
}

/**
 * 数值滚动：首次从 0 走到 [target] 用 [LxMotion.CountUpMs]，之后的变化用更短的 [LxMotion.StateMs]，
 * 避免刷新时数字又从头跑一遍。
 */
@Composable
fun rememberLxCountUp(target: Float, delayMs: Int = 0): State<Float> {
    val anim = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(target) {
        val durationMs = if (first) LxMotion.CountUpMs else LxMotion.StateMs
        anim.animateTo(target, tween(durationMs, delayMs, LxMotion.EaseEnter))
        first = false
    }
    return remember(anim) { derivedStateOf { anim.value } }
}
