package com.lightxin.feature.leave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxEmptyState
import com.lightxin.core.designsystem.component.LxErrorState
import com.lightxin.core.designsystem.component.LxFilterTabs
import com.lightxin.core.designsystem.component.LxFloatingAction
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxStatusLine
import com.lightxin.core.designsystem.component.LxTabItem
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.component.lxHorizontalSwipe
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextFaint
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTextSecondary
import com.lightxin.feature.leave.data.LeaveSummary
import java.time.LocalDateTime
import java.time.YearMonth

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
    val listState = rememberLazyListState()
    val collapsed = listState.lxHeaderCollapsed()

    // 每次进入列表都从“进行中”开始，不记忆上次停留的 tab。
    LaunchedEffect(Unit) { viewModel.setStatus("1") }

    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = { LxNavBar(onBack = onBack, title = "请假", collapsed = collapsed) },
        floatingActionButton = {
            LxFloatingAction(text = "发起请假", icon = Icons.Outlined.Add, onClick = onCreate)
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .lxHorizontalSwipe(
                        onSwipeLeft = { if (uiState.status == "1") viewModel.setStatus("3") },
                        onSwipeRight = { if (uiState.status == "3") viewModel.setStatus("1") },
                    ),
                contentPadding = PaddingValues(bottom = 112.dp),
            ) {
                item(key = "header") {
                    LxPageHeader(title = "请假", modifier = Modifier.padding(horizontal = 24.dp))
                }
                stickyHeader(key = "tabs") {
                    LxFilterTabs(
                        items = listOf(
                            LxTabItem("进行中", uiState.activeTotal),
                            LxTabItem("全部", uiState.allTotal),
                        ),
                        selectedIndex = if (uiState.status == "1") 0 else 1,
                        onSelect = { index -> viewModel.setStatus(if (index == 0) "1" else "3") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LxCanvas)
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .wrapContentWidth(Alignment.Start),
                    )
                }

                when {
                    uiState.isLoading -> item(key = "loading") {
                        LxSkeletonList(count = 6)
                    }
                    uiState.error != null && uiState.items.isEmpty() -> item(key = "error") {
                        Box(
                            modifier = Modifier.fillParentMaxHeight(0.6f).fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LxErrorState(message = uiState.error.orEmpty(), onRetry = viewModel::retry)
                        }
                    }
                    uiState.items.isEmpty() -> item(key = "empty") {
                        Box(
                            modifier = Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LxEmptyState(
                                title = if (uiState.status == "1") "没有进行中的请假" else "还没有请假记录",
                                body = if (uiState.status == "1") {
                                    "已结束的申请都在“全部”里。"
                                } else {
                                    "提交后会出现在这里，通过后记得按时销假。"
                                },
                            )
                        }
                    }
                    else -> {
                        var lastMonth: YearMonth? = null
                        uiState.items.forEach { item ->
                            val month = parseLeaveTime(item.receiveTime)?.let { YearMonth.from(it) }
                            if (month != null && month != lastMonth) {
                                item(key = "month-${month}") { MonthHeader(month) }
                                lastMonth = month
                            }
                            item(key = item.executionId) {
                                LeaveRow(item = item, onClick = { onOpenDetail(item.executionId) })
                            }
                        }
                    }
                }

                if (uiState.isLoadingMore) {
                    item(key = "more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "正在加载…", fontSize = 12.sp, color = LxTextMuted)
                        }
                    }
                } else if (!uiState.hasMore && uiState.items.size >= 4) {
                    item(key = "end") {
                        Text(
                            text = "没有更多了",
                            fontSize = 11.5.sp,
                            color = LxTextFaint,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth) {
    Text(
        text = "${month.year} 年 ${month.monthValue} 月",
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        color = LxTextMuted,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 2.dp),
    )
}

@Composable
private fun LeaveRow(item: LeaveSummary, onClick: () -> Unit) {
    val parsed = parseLeaveTime(item.receiveTime)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DateBlock(parsed)
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = leaveTitle(item),
                fontSize = 15.5.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LxStatusLine(
                status = item.statusTxt.ifBlank { "处理中" },
                tone = leaveStatusTone(item.statusTxt),
                meta = buildList {
                    parsed?.let { add("%02d:%02d 提交".format(it.hour, it.minute)) }
                    item.flowNumber.takeLast(6).takeIf { it.isNotBlank() }?.let { add("编号 …$it") }
                }.joinToString("  ·  "),
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

/** 左侧日期块：日 + 周几；月份交给分组标题，避免“01 / 6 月”被读成序号。 */
@Composable
private fun DateBlock(parsed: LocalDateTime?) {
    Column(
        modifier = Modifier.width(44.dp).padding(top = 1.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = parsed?.dayOfMonth?.toString()?.padStart(2, '0') ?: "—",
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxTextSecondary,
        )
        Text(
            text = parsed?.let { WeekLabels[it.dayOfWeek.value - 1] } ?: "",
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            color = LxTextFaint,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private val WeekLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

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
