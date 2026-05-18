package com.lightxin.feature.credit.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxDetailRow
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxCategoryColors
import com.lightxin.core.designsystem.theme.LxSuccess
import com.lightxin.feature.credit.domain.CreditModule
import com.lightxin.feature.credit.domain.CreditOverview
import com.lightxin.feature.credit.domain.CreditRecord
import com.lightxin.feature.credit.domain.CreditRecordDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "素质学分", onBack = onBack) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            uiState.error != null && uiState.overview == null -> LxError(
                message = uiState.error!!,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            else -> CreditContent(
                uiState = uiState,
                onRecordClick = viewModel::onRecordClick,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (uiState.showDetailSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissDetail,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            DetailSheetContent(
                detail = uiState.selectedDetail,
                isLoading = uiState.isDetailLoading,
                error = uiState.detailError,
            )
        }
    }
}

@Composable
private fun CreditContent(
    uiState: CreditUiState,
    onRecordClick: (CreditRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        uiState.overview?.let { overview ->
            item(key = "overview") {
                OverviewCard(overview)
            }
        }

        item(key = "records_title") {
            Text(
                text = "学分记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        if (uiState.records.isEmpty() && uiState.error != null) {
            item(key = "records_error") {
                LxCard {
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        } else if (uiState.records.isEmpty()) {
            item(key = "empty") {
                LxCard {
                    Text(
                        text = "暂无学分记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        } else {
            items(uiState.records, key = { it.id }) { record ->
                RecordCard(record = record, onClick = { onRecordClick(record) })
            }
        }
    }
}

@Composable
private fun OverviewCard(overview: CreditOverview) {
    val maxCredit = overview.modules.maxOfOrNull { it.credit }?.coerceAtLeast(1.0) ?: 1.0

    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "学分总览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "%.1f 学分".format(overview.totalCredit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val statusColor = if (overview.pass) LxSuccess else MaterialTheme.colorScheme.error
                    Text(
                        text = if (overview.pass) "已达标" else "未达标",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            overview.modules.forEachIndexed { index, module ->
                CreditBarRow(
                    label = module.name,
                    credit = module.credit,
                    maxCredit = maxCredit,
                    color = LxCategoryColors[index % LxCategoryColors.size],
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun CreditBarRow(
    label: String,
    credit: Double,
    maxCredit: Double,
    color: androidx.compose.ui.graphics.Color,
) {
    val fraction = (credit / maxCredit).toFloat().coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "%.1f".format(credit),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun RecordCard(record: CreditRecord, onClick: () -> Unit) {
    LxCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${record.score} 学分 · ${record.statusName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailSheetContent(
    detail: CreditRecordDetail?,
    isLoading: Boolean,
    error: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        if (isLoading) {
            LxLoading()
        } else if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (detail != null) {
            Text(
                text = detail.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LxDetailRow(label = "获奖级别", value = detail.awardLevelName)
            LxDetailRow(label = "获奖等级", value = detail.awardPrizeName)
            LxDetailRow(label = "最高级别", value = detail.highestLevelName)
            LxDetailRow(label = "获得学分", value = "%.1f".format(detail.prizeScore))
            LxDetailRow(label = "获得时间", value = detail.getTime)
            LxDetailRow(label = "所属模块", value = detail.qualityModuleName)
            LxDetailRow(label = "子类别", value = detail.qualityCategoryName)
            LxDetailRow(label = "审核状态", value = detail.statusName, showDivider = false)
        }
    }
}
