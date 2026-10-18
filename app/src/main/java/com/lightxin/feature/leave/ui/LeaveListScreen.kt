package com.lightxin.feature.leave.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxEmptyState
import com.lightxin.core.designsystem.component.LxErrorState
import com.lightxin.core.designsystem.component.LxFilterTabs
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxStatus
import com.lightxin.core.designsystem.component.LxTabItem
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxTextFaint
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTextSecondary
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

    val activeCount = uiState.items.count { isLeaveActive(it.statusTxt) }
    val shown = if (uiState.status == "1") uiState.items.filter { isLeaveActive(it.statusTxt) } else uiState.items
    val subtitle = when {
        uiState.status == "1" -> "${shown.size} 条进行中"
        activeCount > 0 -> "${uiState.items.size} 条申请 · $activeCount 条进行中"
        else -> "${uiState.items.size} 条申请"
    }

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(onBack = onBack) {
                LxTextAction(text = "发起", onClick = onCreate)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LxPageHeader(title = "请假", subtitle = subtitle, modifier = Modifier.padding(horizontal = 24.dp))
            LxFilterTabs(
                items = listOf(
                    LxTabItem("进行中", activeCount),
                    LxTabItem("全部", uiState.items.size),
                ),
                selectedIndex = if (uiState.status == "1") 0 else 1,
                onSelect = { index -> viewModel.setStatus(if (index == 0) "1" else "3") },
            )

            when {
                uiState.isLoading -> LxSkeletonList(count = 6)
                uiState.error != null && uiState.items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LxErrorState(message = uiState.error.orEmpty(), onRetry = viewModel::retry)
                }
                uiState.items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LxEmptyState(
                        title = "还没有请假记录",
                        body = "提交后会出现在这里。辅导员通常 1 个工作日内处理，通过后记得按时销假。",
                        actionText = "发起第一条申请",
                        onAction = onCreate,
                    )
                }
                else -> LeaveList(
                    state = uiState.copy(items = shown),
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
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
        ) {
            items(state.items, key = { it.executionId }) { item ->
                LeaveRow(item = item, onClick = { onOpenDetail(item.executionId) })
            }
            if (state.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "正在加载…", fontSize = 12.sp, color = LxTextMuted)
                    }
                }
            }
            if (!state.hasMore) {
                item {
                    Text(
                        text = "没有更多了",
                        fontSize = 11.5.sp,
                        color = LxTextFaint,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LeaveRow(item: LeaveSummary, onClick: () -> Unit) {
    val parsed = parseReceiveTime(item.receiveTime)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(44.dp)) {
            Text(
                text = parsed?.day ?: "—",
                fontSize = 19.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxTextSecondary,
            )
            Text(
                text = parsed?.month ?: "",
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = LxTextFaint,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = leaveTitle(item),
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = LxTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildString {
                if (parsed != null) append("${parsed.time} 提交")
                val number = item.flowNumber.takeLast(6)
                if (number.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("编号 …$number")
                }
                if (isEmpty()) append(item.receiveTime)
            }
            Text(
                text = meta,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = LxTextMuted,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LxStatus(
            text = item.statusTxt.ifBlank { "处理中" },
            tone = leaveStatusTone(item.statusTxt),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private data class ReceiveTimeParts(val day: String, val month: String, val time: String)

private fun parseReceiveTime(raw: String): ReceiveTimeParts? {
    val match = Regex("""(\d{4})[-/](\d{2})[-/](\d{2})[ T](\d{2}):(\d{2})""").find(raw) ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    return ReceiveTimeParts(
        day = match.groupValues[3],
        month = "${month} 月",
        time = "${match.groupValues[4]}:${match.groupValues[5]}",
    )
}

private fun isLeaveActive(status: String): Boolean {
    if (status.contains("待")) return true
    return !(status.contains("完成") || status.contains("通过") || status.contains("已销假") ||
        status.contains("驳回") || status.contains("拒绝") || status.contains("撤销") ||
        status.contains("撤回"))
}

private fun leaveTitle(item: LeaveSummary): String = leaveDisplayTitle(item.flowTitle)

internal fun leaveDisplayTitle(raw: String): String {
    val type = listOf("病假", "事假", "公假", "丧假", "婚假", "产假", "护理假", "探亲假")
        .firstOrNull { raw.contains(it) }
    return type?.let { "${it}申请" } ?: "学生请假申请"
}

private fun leaveStatusTone(status: String): LxTone = when {
    status.contains("驳回") || status.contains("拒绝") -> LxTone.Danger
    status.contains("待") || status.contains("中") -> LxTone.Warning
    status.contains("完成") || status.contains("通过") || status.contains("销假") -> LxTone.Success
    else -> LxTone.Neutral
}
