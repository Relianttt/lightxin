package com.lightxin.feature.checkin.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.lightxin.core.designsystem.theme.lxPressable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.lightxin.core.designsystem.component.LxLoadMoreFooter
import com.lightxin.core.designsystem.component.LxSectionLabel
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxStatusLine
import com.lightxin.core.designsystem.component.LxTabItem
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.ServiceErrorCard
import com.lightxin.core.designsystem.component.rememberLxTabPagerSelection
import com.lightxin.core.designsystem.component.LxTabPager
import com.lightxin.core.designsystem.component.rememberLxTabPagerState
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxMotion
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.lxStaggerEnter
import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.holiday.domain.HolidayTask
import kotlinx.coroutines.delay

private const val TAB_CHECKIN = 0

/**
 * 学工在线：查寝 / 离校登记 两个 tab。
 * 查寝被拒（非法访问）时只影响查寝 tab，离校登记仍可正常使用。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CheckinListScreen(
    onBack: () -> Unit,
    onTaskClick: (taskDateId: String) -> Unit,
    shouldRefresh: Boolean,
    onRefreshConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    onHolidayClick: (holidayId: String) -> Unit = {},
    onRelogin: () -> Unit = {},
    viewModel: CheckinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberLxTabPagerState()
    val selectTab = rememberLxTabPagerSelection(pagerState)
    val checkinListState = rememberLazyListState()
    val holidayListState = rememberLazyListState()

    var staggerDone by rememberSaveable { mutableStateOf(false) }
    val hasRows = uiState.tasks.isNotEmpty() || uiState.holidayTasks.isNotEmpty()
    LaunchedEffect(hasRows) {
        if (hasRows && !staggerDone) {
            delay(LxMotion.StaggerWindowMs.toLong())
            staggerDone = true
        }
    }
    val playStagger = !staggerDone

    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    val shouldLoadMore by remember(uiState) {
        derivedStateOf {
            if (pagerState.settledPage != TAB_CHECKIN) return@derivedStateOf false
            val lastVisible = checkinListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = checkinListState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3 && uiState.hasMore && !uiState.isLoadingMore && !uiState.isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            // 标题与 tab 行固定在 pager 外，切页时只有列表内容横向移动。
            Column(modifier = Modifier.background(LxCanvas)) {
                LxNavBar(onBack = onBack)
                LxPageHeader(title = "学工在线", modifier = Modifier.padding(horizontal = 24.dp))
                LxFilterTabs(
                    items = listOf(LxTabItem("查寝"), LxTabItem("离校登记")),
                    selectedIndex = pagerState.currentPage,
                    indicatorFraction = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    onSelect = selectTab,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .wrapContentWidth(Alignment.Start),
                )
            }
        },
    ) { padding ->
        LxTabPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { pageIndex ->
            LazyColumn(
                state = if (pageIndex == TAB_CHECKIN) checkinListState else holidayListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                when (pageIndex) {
                    TAB_CHECKIN -> checkinContent(
                        uiState = uiState,
                        onTaskClick = onTaskClick,
                        onRelogin = onRelogin,
                        onRetry = viewModel::retry,
                        playStagger = playStagger,
                    )
                    else -> holidayContent(
                        uiState = uiState,
                        onHolidayClick = onHolidayClick,
                        onRetry = viewModel::retryHoliday,
                        playStagger = playStagger,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.checkinContent(
    uiState: CheckinUiState,
    onTaskClick: (String) -> Unit,
    onRelogin: () -> Unit,
    onRetry: () -> Unit,
    playStagger: Boolean = false,
) {
    when {
        uiState.isLoading -> item(key = "checkin_loading") {
            LxSkeletonList(
                count = 5,
                modifier = Modifier.animateItem(
                    fadeInSpec = LxMotion.enter(),
                    fadeOutSpec = LxMotion.exit(),
                ),
            )
        }
        uiState.rejectedDetail != null -> item(key = "checkin_rejected") {
            Box(modifier = Modifier.fillMaxWidth()) {
                ServiceErrorCard(
                    title = "暂时无法加载查寝",
                    message = "可能是登录已过期，先重试一次。",
                    technicalDetail = uiState.rejectedDetail,
                    isRetrying = uiState.isLoading,
                    onRetry = onRetry,
                    onRelogin = onRelogin,
                )
            }
        }
        uiState.error != null -> item(key = "checkin_error") {
            CenteredError(message = uiState.error, onRetry = onRetry)
        }
        uiState.tasks.isEmpty() -> item(key = "checkin_empty") {
            CenteredEmpty(
                title = "没有查寝任务",
                body = "有新的查寝任务时会出现在这里。",
            )
        }
        else -> {
            val open = uiState.tasks.filter { !it.isSigned && it.isInOpenWindow }
            val openIds = open.map { it.taskDateId }.toSet()
            val rest = uiState.tasks.filterNot { it.taskDateId in openIds }
            if (open.isNotEmpty()) {
                item(key = "open_label") {
                    LxSectionLabel(
                        text = "现在可以签到",
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 2.dp),
                    )
                }
                itemsIndexed(open, key = { _, task -> "c_${task.taskDateId}" }) { index, task ->
                    CheckinRow(
                        task = task,
                        status = "可签到",
                        tone = LxTone.Accent,
                        onClick = { onTaskClick(task.taskDateId) },
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = LxMotion.enter(),
                                placementSpec = LxMotion.placement(),
                                fadeOutSpec = LxMotion.exit(),
                            )
                            .lxStaggerEnter(index = index, play = playStagger),
                    )
                }
                if (rest.isNotEmpty()) {
                    item(key = "rest_label") {
                        LxSectionLabel(
                            text = "全部记录",
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 2.dp),
                        )
                    }
                }
            }
            itemsIndexed(rest, key = { _, task -> "c_${task.taskDateId}" }) { index, task ->
                CheckinRow(
                    task = task,
                    status = if (task.isSigned) "已签到" else "未签到",
                    tone = if (task.isSigned) LxTone.Success else LxTone.Warning,
                    onClick = { if (!task.isSigned) onTaskClick(task.taskDateId) },
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = LxMotion.enter(),
                            placementSpec = LxMotion.placement(),
                            fadeOutSpec = LxMotion.exit(),
                        )
                        .lxStaggerEnter(index = open.size + index, play = playStagger),
                )
            }
            item(key = "checkin_more") {
                LxLoadMoreFooter(
                    loading = uiState.isLoadingMore,
                    hasMore = uiState.hasMore,
                    error = null,
                    onRetry = onRetry,
                    showEnd = uiState.tasks.size >= 4,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.holidayContent(
    uiState: CheckinUiState,
    onHolidayClick: (String) -> Unit,
    onRetry: () -> Unit,
    playStagger: Boolean = false,
) {
    when {
        uiState.isLoadingHoliday -> item(key = "holiday_loading") {
            LxSkeletonList(
                count = 4,
                modifier = Modifier.animateItem(
                    fadeInSpec = LxMotion.enter(),
                    fadeOutSpec = LxMotion.exit(),
                ),
            )
        }
        uiState.holidayError != null -> item(key = "holiday_error") {
            CenteredError(message = uiState.holidayError, onRetry = onRetry)
        }
        uiState.holidayTasks.isEmpty() -> item(key = "holiday_empty") {
            CenteredEmpty(
                title = "没有离校登记",
                body = "节假日需要登记时会出现在这里。",
            )
        }
        else -> itemsIndexed(uiState.holidayTasks, key = { _, task -> "h_${task.holidayId}" }) { index, task ->
            HolidayRow(
                task = task,
                onClick = { onHolidayClick(task.holidayId) },
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = LxMotion.enter(),
                        placementSpec = LxMotion.placement(),
                        fadeOutSpec = LxMotion.exit(),
                    )
                    .lxStaggerEnter(index = index, play = playStagger),
            )
        }
    }
}

@Composable
private fun CenteredError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        LxErrorState(message = message, onRetry = onRetry)
    }
}

@Composable
private fun CenteredEmpty(title: String, body: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        LxEmptyState(title = title, body = body)
    }
}

@Composable
private fun CheckinRow(
    task: CheckinTask,
    status: String,
    tone: LxTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .lxPressable(enabled = !task.isSigned, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.taskName.ifBlank { "查寝" },
                fontSize = 15.5.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            LxStatusLine(
                status = status,
                tone = tone,
                meta = listOf(task.startTime, task.endTime)
                    .filter { it.isNotBlank() }
                    .joinToString(" ~ "),
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

@Composable
private fun HolidayRow(task: HolidayTask, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .lxPressable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.name.ifBlank { "离校登记" },
                fontSize = 15.5.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            LxStatusLine(
                status = if (task.isRegistered) "已登记" else "待登记",
                tone = if (task.isRegistered) LxTone.Success else LxTone.Warning,
                meta = listOf(task.registerStartDate, task.registerEndDate)
                    .filter { it.isNotBlank() }
                    .joinToString(" ~ "),
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}
