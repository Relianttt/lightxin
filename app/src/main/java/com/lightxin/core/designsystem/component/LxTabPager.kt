package com.lightxin.core.designsystem.component

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.lightxin.core.designsystem.theme.LxMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 每次进入默认第一页；各页的列表状态由调用方独立保留。 */
@Composable
fun rememberLxTabPagerState(pageCount: Int = 2): PagerState =
    remember(pageCount) { PagerState(pageCount = { pageCount }) }

/**
 * tab 点击：与手势吸附共用同一曲线；连续点击时取消上一次吸附，不堆叠协程。
 * tab 行固定在 pager 外，所以选择动作由这里提供给调用方。
 */
@Composable
fun rememberLxTabPagerSelection(state: PagerState): (Int) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(state, scope) {
        var job: Job? = null
        { target: Int ->
            job?.cancel()
            job = scope.launch {
                state.animateScrollToPage(target, animationSpec = LxMotion.tab())
            }
        }
    }
}

/**
 * 只负责内容分页：拖动跟手，松手吸附；邻页常驻，避免来回切换时重建列表。
 * 标题与 tab 行放在 pager 外，避免切换时被横向带走（见三个列表页）。
 */
@Composable
fun LxTabPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int) -> Unit,
) {
    HorizontalPager(
        state = state,
        modifier = modifier,
        beyondViewportPageCount = 1,
        snapPosition = SnapPosition.Start,
        flingBehavior = PagerDefaults.flingBehavior(
            state = state,
            snapAnimationSpec = LxMotion.tab(),
        ),
        key = { it },
    ) { page ->
        content(page)
    }
}
