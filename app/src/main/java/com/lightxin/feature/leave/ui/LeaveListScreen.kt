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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.lightxin.core.designsystem.component.rememberLxTabPagerSelection
import com.lightxin.core.designsystem.component.LxTabPager
import com.lightxin.core.designsystem.component.rememberLxTabPagerState
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
    val lists by viewModel.uiState.collectAsState()
    val pagerState = rememberLxTabPagerState()
    val selectTab = rememberLxTabPagerSelection(pagerState)
    val activeListState = rememberLazyListState()
    val allListState = rememberLazyListState()

    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            // 标题与 tab 行固定在 pager 外：切页时只有列表内容横向移动，
            // 避免标题跟着一起被带走、也避免两页各画一条 tab 行。
            Column(modifier = Modifier.background(LxCanvas)) {
                LxNavBar(onBack = onBack)
                LxPageHeader(title = "请假", modifier = Modifier.padding(horizontal = 24.dp))
                LxFilterTabs(
                    items = listOf(
                        LxTabItem("进行中", lists.active.total),
                        LxTabItem("全部", lists.all.total),
                    ),
                    selectedIndex = pagerState.currentPage,
                    indicatorFraction = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    onSelect = selectTab,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .wrapContentWidth(Alignment.Start),
                )
            }
        },
        floatingActionButton = {
            LxFloatingAction(text = "发起请假", icon = Icons.Outlined.Add, onClick = onCreate)
        },
    ) { padding ->
        LxTabPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { pageIndex ->
            val status = if (pageIndex == 0) "1" else "3"
            val uiState = lists.forStatus(status)
            val listState = if (pageIndex == 0) activeListState else allListState
            val shouldLoadMore by remember(uiState, listState) {
                derivedStateOf {
                    val layout = listState.layoutInfo
                    val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                    last >= layout.totalItemsCount - 3 && uiState.hasMore &&
                        !uiState.isLoading && !uiState.isRefreshing && !uiState.isLoadingMore && uiState.error == null
                }
            }
            LaunchedEffect(shouldLoadMore, pagerState.settledPage) {
                if (shouldLoadMore && pagerState.settledPage == pageIndex) viewModel.loadMore(status)
            }
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 112.dp),
                ) {
                    when {
                        uiState.isLoading -> item(key = "loading") {
                            LxSkeletonList(count = 6)
                        }
                        uiState.error != null && uiState.items.isEmpty() -> item(key = "error") {
                            Box(
                                modifier = Modifier.fillParentMaxHeight(0.6f).fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LxErrorState(message = uiState.error.orEmpty(), onRetry = { viewModel.retry(status) })
                            }
                        }
                        uiState.items.isEmpty() -> item(key = "empty") {
                            Box(
                                modifier = Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LxEmptyState(
                                    title = if (status == "1") "没有进行中的请假" else "还没有请假记录",
                                    body = if (status == "1") {
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
