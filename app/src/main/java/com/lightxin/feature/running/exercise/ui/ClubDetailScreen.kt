package com.lightxin.feature.running.exercise.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxCardBorder
import com.lightxin.core.designsystem.theme.LxInkFaint
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxInkSoft
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxSage
import com.lightxin.core.designsystem.theme.LxSand
import com.lightxin.core.designsystem.theme.LxTerra
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
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    LxCard {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = !expanded },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusBadge(active = task.canCheck)
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = LxInkFaint,
                    modifier = Modifier.size(20.dp).rotate(chevronRotation),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DurationBar(completed = task.completedMinutes, required = task.requiredMinutes)
                    if (task.checkRecords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = LxCardBorder)
                        task.checkRecords.forEach { record ->
                            Spacer(modifier = Modifier.height(14.dp))
                            CheckRecordRow(record)
                        }
                    }
                    if (task.canCheck) {
                        Spacer(modifier = Modifier.height(20.dp))
                        LxButton(
                            text = "开始锻炼",
                            onClick = { onStartCheck(task.autoId, task.memberId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(active: Boolean) {
    val color = if (active) LxTerra else LxInkMuted
    val bg = if (active) LxTerra.copy(alpha = 0.12f) else LxSand
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (active) "进行中" else "已结束",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun DurationBar(completed: Int, required: Int) {
    val fraction = if (required > 0) (completed.toFloat() / required).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("记录时长", style = MaterialTheme.typography.bodySmall, color = LxInkMuted)
            Text(
                text = "$completed / $required 分钟",
                style = MaterialTheme.typography.labelMedium,
                color = LxInkSoft,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(LxSand),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(LxTerra),
            )
        }
    }
}

@Composable
private fun CheckRecordRow(record: ClubCheckRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${record.startDate} - ${record.endDate}",
                style = MaterialTheme.typography.bodySmall,
                color = LxInkSoft,
            )
            Text(
                "${record.venueName} · ${record.duration}",
                style = MaterialTheme.typography.labelSmall,
                color = LxInkMuted,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (record.isNormal) LxSage else LxRose),
            )
            Text(
                text = if (record.isNormal) "正常" else "异常",
                style = MaterialTheme.typography.labelSmall,
                color = LxInkMuted,
            )
        }
    }
}
