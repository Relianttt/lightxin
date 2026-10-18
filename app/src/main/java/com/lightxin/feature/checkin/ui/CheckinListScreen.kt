package com.lightxin.feature.checkin.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.lightxin.core.designsystem.component.LxStatusLine
import com.lightxin.core.designsystem.component.LxTabItem
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.ServiceErrorCard
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.component.lxHorizontalSwipe
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.holiday.domain.HolidayTask

private const val TAB_CHECKIN = 0
private const val TAB_HOLIDAY = 1

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
    var tab by remember { mutableIntStateOf(TAB_CHECKIN) }
    val checkinListState = rememberLazyListState()
    val holidayListState = rememberLazyListState()
    val checkinCollapsed = checkinListState.lxHeaderCollapsed()
    val holidayCollapsed = holidayListState.lxHeaderCollapsed()
    val collapsed = if (tab == TAB_CHECKIN) checkinCollapsed else holidayCollapsed
    val listState = if (tab == TAB_CHECKIN) checkinListState else holidayListState

    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            if (tab != TAB_CHECKIN) return@derivedStateOf false
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
        topBar = { LxNavBar(onBack = onBack, title = "学工在线", collapsed = collapsed) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .lxHorizontalSwipe(
                    onSwipeLeft = { if (tab == TAB_CHECKIN) tab = TAB_HOLIDAY },
                    onSwipeRight = { if (tab == TAB_HOLIDAY) tab = TAB_CHECKIN },
                ),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "header") {
                LxPageHeader(title = "学工在线", modifier = Modifier.padding(horizontal = 24.dp))
            }
            stickyHeader(key = "tabs") {
                LxFilterTabs(
                    items = listOf(LxTabItem("查寝"), LxTabItem("离校登记")),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LxCanvas)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .wrapContentWidth(Alignment.Start),
                )
            }

            when (tab) {
                TAB_CHECKIN -> checkinContent(
                    uiState = uiState,
                    onTaskClick = onTaskClick,
                    onRelogin = onRelogin,
                    onRetry = viewModel::retry,
                )
                else -> holidayContent(
                    uiState = uiState,
                    onHolidayClick = onHolidayClick,
                    onRetry = viewModel::retryHoliday,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.checkinContent(
    uiState: CheckinUiState,
    onTaskClick: (String) -> Unit,
    onRelogin: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        uiState.isLoading -> item(key = "checkin_loading") { LxSkeletonList(count = 5) }
        uiState.rejectedDetail != null -> item(key = "checkin_rejected") {
            Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                ServiceErrorCard(
                    title = "暂时无法加载查寝信息",
                    message = "服务端这次没有认可你的身份信息。常见原因是登录已过期；也可能只是这一下没对上。\n\n" +
                        "可以先重试一次；如果一直这样，重新登录通常能恢复。",
                    technicalDetail = uiState.rejectedDetail,
                    isRetrying = uiState.isLoading,
                    onRetry = onRetry,
                    onRelogin = onRelogin,
                    retryLabel = "重新加载查寝",
                    retryingLabel = "正在重新加载…",
                    footer = "离校登记仍可使用。",
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
            items(uiState.tasks, key = { "c_${it.taskDateId}" }) { task ->
                CheckinRow(task = task, onClick = { if (!task.isSigned) onTaskClick(task.taskDateId) })
            }
            if (uiState.isLoadingMore) {
                item(key = "checkin_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.holidayContent(
    uiState: CheckinUiState,
    onHolidayClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        uiState.isLoadingHoliday -> item(key = "holiday_loading") { LxSkeletonList(count = 4) }
        uiState.holidayError != null -> item(key = "holiday_error") {
            CenteredError(message = uiState.holidayError, onRetry = onRetry)
        }
        uiState.holidayTasks.isEmpty() -> item(key = "holiday_empty") {
            CenteredEmpty(
                title = "没有离校登记",
                body = "节假日需要登记时会出现在这里。",
            )
        }
        else -> items(uiState.holidayTasks, key = { "h_${it.holidayId}" }) { task ->
            HolidayRow(task = task, onClick = { onHolidayClick(task.holidayId) })
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
private fun CheckinRow(task: CheckinTask, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !task.isSigned, role = Role.Button, onClick = onClick)
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
                status = if (task.isSigned) "已签到" else "未签到",
                tone = if (task.isSigned) LxTone.Success else LxTone.Warning,
                meta = listOf(task.startTime, task.endTime)
                    .filter { it.isNotBlank() }
                    .joinToString(" ~ "),
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

@Composable
private fun HolidayRow(task: HolidayTask, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
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
