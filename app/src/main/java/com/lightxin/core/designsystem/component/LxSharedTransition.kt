package com.lightxin.core.designsystem.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

/**
 * 共享元素只做“位置 + 尺寸”的连续性，使用临界阻尼，不做缩放文字/圆角 morph。
 * 若导航层未提供 scope（或系统关闭动画），直接返回原 Modifier，等价位移动画。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LxSharedBoundsTransform: BoundsTransform = BoundsTransform { _, _ ->
    spring<Rect>(dampingRatio = 1f, stiffness = 380f)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.lxSharedBounds(
    key: Any,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return this
    val state = sharedTransitionScope.rememberSharedContentState(key = key)
    return with(sharedTransitionScope) {
        this@lxSharedBounds.sharedElement(
            sharedContentState = state,
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = LxSharedBoundsTransform,
        )
    }
}
