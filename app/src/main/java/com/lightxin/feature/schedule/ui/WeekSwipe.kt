package com.lightxin.feature.schedule.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** 一周的横向滑动至少要划过这么远才切周，避免纵向滚动时的轻微横向抖动误触。 */
private val WEEK_SWIPE_THRESHOLD = 56.dp

/**
 * 网格上的横向滑动切周：向左滑看下一周（step = +1），向右滑看上一周（step = -1）。
 *
 * 只认横向 touch slop，纵向拖动仍归网格自己的 `verticalScroll`；
 * 回调走 `rememberUpdatedState`，手指拖动期间发生重组也不会打断手势。
 */
@Composable
internal fun Modifier.weekSwipeGesture(onSwipe: (Int) -> Unit): Modifier {
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    return pointerInput(Unit) {
        val threshold = WEEK_SWIPE_THRESHOLD.toPx()
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragCancel = { distance = 0f },
            onDragEnd = {
                if (abs(distance) >= threshold) currentOnSwipe(if (distance < 0f) 1 else -1)
                distance = 0f
            },
        ) { _, dragAmount -> distance += dragAmount }
    }
}

/** 滑动切周的目标：越界（已经是第一周 / 最后一周）返回 null。 */
internal fun weekAfterSwipe(weeks: List<Int>, selected: Int, step: Int): Int? {
    val index = weeks.indexOf(selected)
    if (index < 0 || step == 0) return null
    return weeks.getOrNull(index + step)
}
