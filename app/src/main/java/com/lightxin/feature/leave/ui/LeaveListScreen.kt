package com.lightxin.feature.leave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxChoiceChip
import com.lightxin.core.designsystem.component.LxFloatingActionButton
import com.lightxin.core.designsystem.component.LxEmpty
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxAmber
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxSage
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.feature.leave.data.LeaveSummary

@Composable
fun LeaveListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenDetail: (String) -> Unit,
    shouldRefresh: Boolean,
    onRefreshConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LeaveListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "请假", onBack = onBack) },
        floatingActionButton = {
            LxFloatingActionButton(onClick = onCreate) {
                Icon(Icons.Outlined.Add, contentDescription = "发起请假", tint = Color.White)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LxChoiceChip(
                    text = "进行中",
                    selected = uiState.status == "1",
                    onClick = { viewModel.setStatus("1") },
                )
                LxChoiceChip(
                    text = "全部",
                    selected = uiState.status == "3",
                    onClick = { viewModel.setStatus("3") },
                )
            }

            when {
                uiState.isLoading -> LxLoading()
                uiState.error != null && uiState.items.isEmpty() -> LxError(
                    message = uiState.error.orEmpty(),
                    onRetry = viewModel::retry,
                )
                uiState.items.isEmpty() -> LxEmpty(message = "还没有请假记录")
                else -> LeaveList(
                    state = uiState,
                    onRefresh = viewModel::refresh,
                    onLoadMore = viewModel::loadMore,
                    onOpenDetail = onOpenDetail,
                )
            }
        }
    }
}

@Composable
private fun LeaveList(
    state: LeaveListUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3 && state.hasMore && !state.isLoadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.items, key = { it.executionId }) { item ->
                LeaveCard(item = item, onClick = { onOpenDetail(item.executionId) })
            }
            if (state.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun LeaveCard(item: LeaveSummary, onClick: () -> Unit) {
    LxCard(onClick = onClick) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.flowTitle.ifBlank { "学生请假申请" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LxInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                LeaveStatusBadge(item.statusTxt)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.receiveTime.ifBlank { "--" },
                fontSize = 12.sp,
                color = LxInkMuted,
            )
            if (item.flowNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "编号 ${item.flowNumber}",
                    fontSize = 12.sp,
                    color = LxInkMuted,
                )
            }
        }
    }
}

@Composable
fun LeaveStatusBadge(status: String) {
    val color = when {
        status.contains("完成") || status.contains("通过") || status.contains("已销假") -> LxSage
        status.contains("驳回") || status.contains("拒绝") || status.contains("撤销") -> LxInkMuted
        status.contains("待") || status.contains("中") -> LxAmber
        else -> LxTerra
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = status.ifBlank { "处理中" },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}
