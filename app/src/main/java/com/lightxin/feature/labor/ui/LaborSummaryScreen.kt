package com.lightxin.feature.labor.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxBottomSheet
import com.lightxin.core.designsystem.component.LxDateBlock
import com.lightxin.core.designsystem.component.LxErrorState
import com.lightxin.core.designsystem.component.LxLoadMoreFooter
import com.lightxin.core.designsystem.component.LxMetaRow
import com.lightxin.core.designsystem.component.LxMonthHeader
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPanel
import com.lightxin.core.designsystem.component.LxSkeletonBlock
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxSegment
import com.lightxin.core.designsystem.component.LxSegmentBar
import com.lightxin.core.designsystem.component.LxSegmentLegend
import com.lightxin.core.designsystem.component.LxSessionBanner
import com.lightxin.core.designsystem.component.LxSectionLabel
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.RecoveryPrimary
import com.lightxin.core.designsystem.component.ServiceErrorCard
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.component.lxToneColor
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxCategoryColors
import com.lightxin.core.designsystem.theme.LxMotion
import com.lightxin.core.designsystem.theme.LxRadiusLg
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextFaint
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTextSecondary
import com.lightxin.core.designsystem.theme.lxPressable
import com.lightxin.core.designsystem.theme.lxStaggerEnter
import com.lightxin.core.designsystem.theme.rememberLxCountUp
import com.lightxin.feature.labor.domain.ActivityDetail
import com.lightxin.feature.labor.domain.ActivityRecord
import com.lightxin.feature.labor.domain.HoursSummary
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.YearMonth

@Composable
fun LaborSummaryScreen(
    onBack: () -> Unit,
    onRelogin: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LaborViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val hasContent = uiState.hoursSummary != null || uiState.activities.isNotEmpty()
    val authExpiredDetail = uiState.authExpiredDetail

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(
                onBack = onBack,
                title = "劳动教育",
                collapsed = listState.lxHeaderCollapsed(),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LxSkeletonList(count = 5, modifier = Modifier.padding(padding))
            // 有缓存时只挂横幅，列表照常显示；没缓存才整页替换。
            authExpiredDetail != null && !hasContent -> Box(
                modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                ServiceErrorCard(
                    title = "登录已失效",
                    primary = RecoveryPrimary.Relogin,
                    message = "重新登录后即可查看劳动教育。",
                    technicalDetail = authExpiredDetail,
                    isRetrying = uiState.isRefreshing,
                    onRetry = viewModel::retry,
                    onRelogin = onRelogin,
                )
            }
            uiState.error != null && !hasContent -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LxErrorState(message = uiState.error!!, onRetry = viewModel::retry)
            }
            else -> LaborContent(
                uiState = uiState,
                listState = listState,
                onActivityClick = viewModel::onRecordClick,
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
                onRelogin = onRelogin,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (uiState.showDetailSheet) {
        LxBottomSheet(onDismiss = viewModel::dismissDetail) {
            DetailSheetContent(
                record = uiState.selectedRecord,
                detail = uiState.selectedDetail,
                isLoading = uiState.isDetailLoading,
                error = uiState.detailError,
                onRetry = { uiState.selectedRecord?.let(viewModel::onRecordClick) },
            )
        }
    }
}

@Composable
private fun LaborContent(
    uiState: LaborUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onActivityClick: (ActivityRecord) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRelogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 触底加载更多
    val reachedBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(reachedBottom, uiState.hasMore, uiState.isLoadingMore, uiState.loadMoreError) {
        if (reachedBottom && uiState.hasMore && !uiState.isLoadingMore && uiState.loadMoreError == null) {
            onLoadMore()
        }
    }

    var staggerDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.activities.isNotEmpty()) {
        if (uiState.activities.isNotEmpty() && !staggerDone) {
            delay(LxMotion.StaggerWindowMs.toLong())
            staggerDone = true
        }
    }
    val playStagger = !staggerDone

    val indexByKey = remember(uiState.activities) {
        uiState.activities.withIndex().associate { it.value.key to it.index }
    }
    val grouped = remember(uiState.activities) {
        uiState.activities.groupBy { parseLaborDate(it.createDate)?.let(YearMonth::from) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "header") {
            LxPageHeader(
                title = "劳动教育",
                subtitle = uiState.fetchedAt?.let(::formatFetchedAtLabel),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        if (uiState.authExpiredDetail != null) {
            item(key = "session_banner") {
                LxSessionBanner(
                    title = "登录已失效",
                    body = "显示的是上次保存的数据",
                    onAction = onRelogin,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
                )
            }
        }

        if (uiState.refreshError != null && uiState.authExpiredDetail == null) {
            item(key = "refresh_error") {
                Text(
                    text = "更新失败 · 点此重试",
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = LxTextMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lxPressable(onClick = onRetry)
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 2.dp),
                )
            }
        }

        uiState.hoursSummary?.let { summary ->
            item(key = "hero") {
                HoursHero(
                    summary = summary,
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 18.dp)
                        .lxStaggerEnter(index = 0, play = playStagger),
                )
            }
        }

        item(key = "section") {
            LxSectionLabel(
                text = "活动记录 · ${uiState.activities.size}",
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 4.dp),
            )
        }

        grouped.forEach { (month, records) ->
            if (month != null) {
                item(key = "month-$month") { LxMonthHeader(month) }
            }
            items(records, key = { it.key }) { record ->
                ActivityRow(
                    record = record,
                    onClick = { onActivityClick(record) },
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = LxMotion.enter(),
                            placementSpec = LxMotion.placement(),
                            fadeOutSpec = LxMotion.exit(),
                        )
                        .lxStaggerEnter(
                            index = (indexByKey[record.key] ?: 0) + 1,
                            play = playStagger,
                        ),
                )
            }
        }

        item(key = "more") {
            LxLoadMoreFooter(
                loading = uiState.isLoadingMore,
                hasMore = uiState.hasMore,
                error = uiState.loadMoreError,
                onRetry = onLoadMore,
                showEnd = uiState.activities.size >= 4,
            )
        }
    }
}

@Composable
private fun HoursHero(summary: HoursSummary, modifier: Modifier = Modifier) {
    val segments = listOf(
        LxSegment("志愿服务", summary.voluntaryTimes, LxCategoryColors[0]),
        LxSegment("暑期实践", summary.summerTimes, LxCategoryColors[1]),
        LxSegment("劳动实践", summary.laborTimes, LxCategoryColors[2]),
        LxSegment("社区服务", summary.socialTimes, LxCategoryColors[3]),
        LxSegment("其他", summary.otherTimes, LxCategoryColors[4]),
    )
    val total by rememberLxCountUp(summary.totalTimes.toFloat())
    val fill by rememberLxCountUp(1f, delayMs = LxMotion.ContentDelayMs)

    LxPanel(modifier = modifier.fillMaxWidth(), radius = LxRadiusLg, padding = 20.dp) {
        LxSectionLabel("累计时长")
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text(
                text = "%.1f".format(total),
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Text(
                text = " 小时",
                fontSize = 15.sp,
                color = LxTextMuted,
                modifier = Modifier.padding(bottom = 5.dp, start = 2.dp),
            )
        }
        LxSegmentBar(
            segments = segments,
            fill = fill,
            modifier = Modifier.padding(top = 16.dp),
        )
        LxSegmentLegend(
            segments = segments,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun ActivityRow(
    record: ActivityRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = parseLaborDate(record.createDate)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .lxPressable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LxDateBlock(parsed)
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = record.activityName,
                fontSize = 15.5.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (record.projectTypeName.isNotBlank()) append(record.projectTypeName)
                    parsed?.let {
                        if (isNotEmpty()) append(" · ")
                        append("%02d:%02d".format(it.hour, it.minute))
                    }
                },
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = LxTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        Column(
            modifier = Modifier.padding(start = 12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "+%.1f".format(record.serviceTimes),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Text(
                text = "小时",
                fontSize = 11.sp,
                color = LxTextFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun DetailSheetContent(
    record: ActivityRecord?,
    detail: ActivityDetail?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    Text(
        text = detail?.activityName ?: record?.activityName.orEmpty(),
        fontSize = 18.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        color = LxText,
    )
    Spacer(modifier = Modifier.height(10.dp))
    when {
        isLoading -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { LxSkeletonBlock(height = 14.dp) }
        }
        error != null -> LxErrorState(message = error, onRetry = onRetry, modifier = Modifier.padding(top = 8.dp))
        detail != null -> Column {
            LxMetaRow(label = "活动类型", value = detail.activityType)
            LxMetaRow(label = "活动级别", value = detail.activityLevel)
            LxMetaRow(label = "主办方", value = detail.organizer)
            LxMetaRow(label = "志愿时长", value = "%.1f 小时".format(detail.serviceTimes))
            LxMetaRow(label = "日期", value = detail.createDate)
        }
    }
}

private fun formatFetchedAtLabel(millis: Long): String {
    val dateTime: LocalDateTime = java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    return "更新于 ${dateTime.monthValue}月${dateTime.dayOfMonth}日 %02d:%02d".format(dateTime.hour, dateTime.minute)
}
