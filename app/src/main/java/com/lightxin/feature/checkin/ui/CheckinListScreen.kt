package com.lightxin.feature.checkin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxEmpty
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxInkSoft
import com.lightxin.core.designsystem.theme.LxSuccess
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.holiday.domain.HolidayTask

@Composable
fun CheckinListScreen(
    onBack: () -> Unit,
    onTaskClick: (taskDateId: String) -> Unit,
    onHolidayClick: (holidayId: String) -> Unit = {},
    shouldRefresh: Boolean,
    onRefreshConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckinViewModel = hiltViewModel(),
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
        topBar = { LxTopBar(title = "查寝签到", onBack = onBack) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            uiState.error != null -> LxError(
                message = uiState.error!!,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            uiState.tasks.isEmpty() && uiState.holidayTasks.isEmpty() && uiState.holidayError == null -> LxEmpty(
                message = "暂无签到与节假日任务",
                modifier = Modifier.padding(padding),
            )
            else -> TaskList(
                uiState = uiState,
                onTaskClick = onTaskClick,
                onHolidayClick = onHolidayClick,
                onLoadMore = viewModel::loadMore,
                onRetryHoliday = viewModel::retryHoliday,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun TaskList(
    uiState: CheckinUiState,
    onTaskClick: (String) -> Unit,
    onHolidayClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetryHoliday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3 && uiState.hasMore && !uiState.isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp,
            vertical = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 查寝签到 section
        if (uiState.tasks.isNotEmpty()) {
            item(key = "section_checkin") {
                SectionHeader("查寝签到")
            }
            items(uiState.tasks, key = { "c_${it.taskDateId}" }) { task ->
                TaskCard(
                    task = task,
                    onClick = { if (!task.isSigned) onTaskClick(task.taskDateId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        // 节假日登记 section
        if (uiState.holidayTasks.isNotEmpty() || uiState.holidayError != null) {
            item(key = "section_holiday") {
                SectionHeader("节假日登记")
            }
            if (uiState.holidayError != null) {
                item(key = "holiday_error") {
                    HolidayErrorRow(message = uiState.holidayError!!, onRetry = onRetryHoliday)
                }
            }
            items(uiState.holidayTasks, key = { "h_${it.holidayId}" }) { task ->
                HolidayTaskCard(
                    task = task,
                    onClick = { onHolidayClick(task.holidayId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (uiState.isLoadingMore) {
            item(key = "loading_more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: CheckinTask,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LxCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.taskName.ifBlank { "查寝签到" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = task.startTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (task.endTime.isNotBlank()) {
                        Text(
                            text = "~ ${task.endTime}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 签到状态标记
            StatusBadge(isSigned = task.isSigned)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = LxInkMuted,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun HolidayTaskCard(
    task: HolidayTask,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LxCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name.ifBlank { "节假日登记" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = task.registerStartDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (task.registerEndDate.isNotBlank()) {
                        Text(
                            text = "~ ${task.registerEndDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            HolidayStatusBadge(isRegistered = task.isRegistered)
        }
    }
}

@Composable
private fun HolidayErrorRow(message: String, onRetry: () -> Unit) {
    LxCard(onClick = onRetry) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(LxTerra),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = LxInkSoft,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Text(
                text = "重试",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = LxInkSoft,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
    }
}

@Composable
private fun HolidayStatusBadge(isRegistered: Boolean) {
    val color = if (isRegistered) LxSuccess else LxTerra
    val text = if (isRegistered) "已登记" else "待登记"

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun StatusBadge(isSigned: Boolean) {
    val color = if (isSigned) LxSuccess else MaterialTheme.colorScheme.secondary
    val text = if (isSigned) "已签到" else "未签到"

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
