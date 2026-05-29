package com.lightxin.feature.running.exercise.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.feature.running.exercise.domain.ClubCheckRecord
import com.lightxin.feature.running.exercise.domain.ClubTask

@Composable
fun ClubDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onStartCheck: (autoId: String, memberId: String) -> Unit = { _, _ -> },
    viewModel: ClubDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "俱乐部详情", onBack = onBack) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            uiState.error != null -> LxError(
                message = uiState.error!!,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.tasks, key = { it.autoId }) { task ->
                    TaskCard(task = task, onStartCheck = onStartCheck)
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: ClubTask,
    onStartCheck: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(task.canCheck) }
    LxCard {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(task.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = if (task.canCheck) "进行中" else "已结束",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.canCheck) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "记录时长 ${task.completedMinutes} / ${task.requiredMinutes} 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (task.checkRecords.isEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "暂无打卡记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    task.checkRecords.forEach { record ->
                        Spacer(modifier = Modifier.height(6.dp))
                        CheckRecordRow(record)
                    }
                }
                if (task.canCheck) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LxButton(
                        text = "开始锻炼",
                        onClick = { onStartCheck(task.autoId, task.memberId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckRecordRow(record: ClubCheckRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "${record.startDate} - ${record.endDate}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "${record.venueName} · ${record.duration}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (record.isNormal) "正常" else "异常",
            style = MaterialTheme.typography.labelSmall,
            color = if (record.isNormal) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
    }
}
