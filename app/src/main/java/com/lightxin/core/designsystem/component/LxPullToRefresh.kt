package com.lightxin.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow

/**
 * 下拉刷新状态 + 越过触发阈值时的触感。只在“刚越过阈值”那一刻震一次，
 * 继续下拉或回弹到阈值以下都不会重复触发。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberLxPullToRefreshState(): PullToRefreshState {
    val state = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }
            .distinctUntilChanged()
            .filter { it }
            .collect { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    }
    return state
}
