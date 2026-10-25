package com.lightxin.feature.credit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxBottomSheet
import com.lightxin.core.designsystem.component.LxErrorState
import com.lightxin.core.designsystem.component.LxMetaRow
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPanel
import com.lightxin.core.designsystem.component.LxSegment
import com.lightxin.core.designsystem.component.LxSegmentBar
import com.lightxin.core.designsystem.component.LxSegmentLegend
import com.lightxin.core.designsystem.component.LxSectionLabel
import com.lightxin.core.designsystem.component.LxSkeletonBlock
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxStatus
import com.lightxin.core.designsystem.component.LxStatusLine
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxCategoryColors
import com.lightxin.core.designsystem.theme.LxMotion
import com.lightxin.core.designsystem.theme.LxRadiusLg
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextFaint
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.lxPressable
import com.lightxin.core.designsystem.theme.lxStaggerEnter
import com.lightxin.core.designsystem.theme.rememberLxCountUp
import com.lightxin.feature.credit.domain.CreditOverview
import com.lightxin.feature.credit.domain.CreditRecord
import com.lightxin.feature.credit.domain.CreditRecordDetail
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun CreditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(
                onBack = onBack,
                title = "素质学分",
                collapsed = listState.lxHeaderCollapsed(),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LxSkeletonList(count = 5, modifier = Modifier.padding(padding))
            uiState.error != null -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LxErrorState(message = uiState.error!!, onRetry = viewModel::retry)
            }
            else -> CreditContent(
                uiState = uiState,
                listState = listState,
                onRecordClick = viewModel::onRecordClick,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (uiState.showDetailSheet) {
        LxBottomSheet(onDismiss = viewModel::dismissDetail) {
            DetailSheetContent(
                detail = uiState.selectedDetail,
                isLoading = uiState.isDetailLoading,
                error = uiState.detailError,
                onRetry = { uiState.selectedRecord?.let(viewModel::onRecordClick) },
            )
        }
    }
}

@Composable
private fun CreditContent(
    uiState: CreditUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRecordClick: (CreditRecord) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var staggerDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.overview, uiState.records.isNotEmpty()) {
        if ((uiState.overview != null || uiState.records.isNotEmpty()) && !staggerDone) {
            delay(LxMotion.StaggerWindowMs.toLong())
            staggerDone = true
        }
    }
    val playStagger = !staggerDone
    val indexByKey = remember(uiState.records) {
        uiState.records.withIndex().associate { it.value.id to it.index }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "header") {
            LxPageHeader(
                title = "素质学分",
                subtitle = uiState.fetchedAt?.let(::formatFetchedAtLabel),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        if (uiState.refreshError != null) {
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

        uiState.overview?.let { overview ->
            item(key = "hero") {
                CreditHero(
                    overview = overview,
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 18.dp)
                        .lxStaggerEnter(index = 0, play = playStagger),
                )
            }
        }

        item(key = "section") {
            LxSectionLabel(
                text = "学分记录 · ${uiState.records.size}",
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 4.dp),
            )
        }

        when {
            uiState.recordsError != null -> item(key = "records_error") {
                LxErrorState(
                    message = uiState.recordsError!!,
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                )
            }
            uiState.records.isEmpty() -> item(key = "empty") {
                Text(
                    text = "暂无学分记录",
                    fontSize = 13.sp,
                    color = LxTextMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                )
            }
            else -> items(uiState.records, key = { it.id }) { record ->
                CreditRow(
                    record = record,
                    onClick = { onRecordClick(record) },
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = LxMotion.enter(),
                            placementSpec = LxMotion.placement(),
                            fadeOutSpec = LxMotion.exit(),
                        )
                        .lxStaggerEnter(
                            index = (indexByKey[record.id] ?: 0) + 1,
                            play = playStagger,
                        ),
                )
            }
        }
    }
}

@Composable
private fun CreditHero(overview: CreditOverview, modifier: Modifier = Modifier) {
    val segments = overview.modules.mapIndexed { index, module ->
        LxSegment(
            label = module.name,
            value = module.credit,
            color = LxCategoryColors[index % LxCategoryColors.size],
        )
    }
    val total by rememberLxCountUp(overview.totalCredit.toFloat())
    val fill by rememberLxCountUp(1f)

    LxPanel(modifier = modifier.fillMaxWidth(), radius = LxRadiusLg, padding = 20.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LxSectionLabel("素质学分")
            Spacer(modifier = Modifier.weight(1f))
            LxStatus(
                text = if (overview.pass) "已达标" else "未达标",
                tone = if (overview.pass) LxTone.Success else LxTone.Warning,
            )
        }
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
                text = " 学分",
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
private fun CreditRow(
    record: CreditRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .lxPressable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.name,
                fontSize = 15.5.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            LxStatusLine(
                status = record.statusName,
                tone = creditTone(record.statusName),
                meta = "",
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        Column(
            modifier = Modifier.padding(start = 12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "+%.1f".format(record.score),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Text(
                text = "学分",
                fontSize = 11.sp,
                color = LxTextFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun DetailSheetContent(
    detail: CreditRecordDetail?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    Text(
        text = detail?.name ?: "学分记录",
        fontSize = 18.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        color = LxText,
    )
    Spacer(modifier = Modifier.height(10.dp))
    when {
        isLoading -> Column {
            repeat(3) {
                LxSkeletonBlock(height = 14.dp, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
        error != null -> LxErrorState(message = error, onRetry = onRetry, modifier = Modifier.padding(top = 8.dp))
        detail != null -> Column {
            LxMetaRow(label = "获奖级别", value = detail.awardLevelName)
            LxMetaRow(label = "获奖等级", value = detail.awardPrizeName)
            LxMetaRow(label = "最高级别", value = detail.highestLevelName)
            LxMetaRow(label = "获得学分", value = "%.1f".format(detail.prizeScore))
            LxMetaRow(label = "获得时间", value = detail.getTime)
            LxMetaRow(label = "所属模块", value = detail.qualityModuleName)
            LxMetaRow(label = "子类别", value = detail.qualityCategoryName)
            LxMetaRow(label = "审核状态", value = detail.statusName)
        }
    }
}

internal fun creditTone(status: String): LxTone = when {
    status.contains("有效") || status.contains("通过") -> LxTone.Success
    status.contains("驳回") || status.contains("无效") || status.contains("不通过") -> LxTone.Danger
    status.contains("审核") || status.contains("待") -> LxTone.Warning
    else -> LxTone.Neutral
}

private fun formatFetchedAtLabel(millis: Long): String {
    val dateTime: LocalDateTime = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    return "更新于 ${dateTime.monthValue}月${dateTime.dayOfMonth}日 %02d:%02d".format(dateTime.hour, dateTime.minute)
}
