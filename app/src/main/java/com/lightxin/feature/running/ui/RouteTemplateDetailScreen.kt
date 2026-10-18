package com.lightxin.feature.running.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxDialog
import com.lightxin.core.designsystem.component.LxDialogConfirmTone
import com.lightxin.core.designsystem.component.LxEmpty
import com.lightxin.core.designsystem.component.LxOutlinedButton
import com.lightxin.core.designsystem.component.LxTextField
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.component.lxSharedBounds
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxSage
import com.lightxin.core.designsystem.theme.LxSandDeep
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.LxWarning
import com.lightxin.feature.running.domain.RouteQualityStatus
import com.lightxin.feature.running.domain.RouteTemplate
import com.lightxin.feature.running.domain.TrackPoint

@Composable
fun RouteTemplateDetailScreen(
    templateId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteTemplateViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val template = uiState.templates.firstOrNull { it.id == templateId }

    var renameVisible by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var deleteVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "模板详情", onBack = onBack) },
    ) { padding ->
        if (template == null) {
            LxEmpty(
                message = "模板不存在或已被删除",
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PreviewCard(
                    templateId = template.id,
                    points = template.points,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
            item { StatsCard(template) }
            item { MetaCard(template) }
            if (template.qualityStatus != RouteQualityStatus.PASS) {
                item { QualityCard(template) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!template.isDefault) {
                        LxButton(
                            text = "设为默认",
                            onClick = { viewModel.setDefault(template.id) },
                        )
                    }
                    LxOutlinedButton(
                        text = "导出模板",
                        onClick = { RouteTemplateExport.shareSingle(context, template) },
                    )
                    LxOutlinedButton(
                        text = "重命名",
                        onClick = {
                            renameInput = template.name
                            renameVisible = true
                        },
                    )
                    LxOutlinedButton(
                        text = "删除模板",
                        onClick = { deleteVisible = true },
                    )
                }
            }
        }
    }

    if (renameVisible && template != null) {
        LxDialog(
            title = "重命名模板",
            confirmText = "确定",
            dismissText = "取消",
            onDismissRequest = { renameVisible = false },
            onDismiss = { renameVisible = false },
            onConfirm = {
                viewModel.rename(template.id, renameInput)
                renameVisible = false
            },
            content = {
                LxTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = "模板名称",
                    keyboardOptions = KeyboardOptions.Default,
                )
            },
        )
    }

    if (deleteVisible && template != null) {
        LxDialog(
            title = "删除模板",
            message = "确定删除「${template.name}」？此操作不可撤销。",
            confirmText = "删除",
            dismissText = "取消",
            onDismissRequest = { deleteVisible = false },
            onDismiss = { deleteVisible = false },
            onConfirm = {
                viewModel.delete(template.id)
                deleteVisible = false
                onBack()
            },
            confirmTone = LxDialogConfirmTone.Destructive,
        )
    }
}

@Composable
private fun PreviewCard(
    templateId: String,
    points: List<TrackPoint>,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val shared = Modifier.lxSharedBounds(
        key = "route-preview-$templateId",
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
    LxCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "轨迹预览",
                style = MaterialTheme.typography.labelMedium,
                color = LxInkMuted,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .then(shared)
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LxCream),
            ) {
                if (points.size >= 2) {
                    TrackCanvas(points = points, modifier = Modifier.fillMaxSize().padding(12.dp))
                } else {
                    Text(
                        text = "点数过少，无法绘制",
                        color = LxInkMuted,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCard(template: RouteTemplate) {
    LxCard {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = template.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatCell("距离", String.format("%.2f km", template.totalDistanceMeters / 1000.0))
                StatCell("点数", "${template.pointCount}")
                StatCell("录制时长", formatSeconds(template.durationSeconds))
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LxInk)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, color = LxInkMuted)
    }
}

@Composable
private fun MetaCard(template: RouteTemplate) {
    LxCard {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            MetaRow("来源", template.source.displayName())
            Spacer(modifier = Modifier.height(10.dp))
            MetaRow("录制时间", formatDate(template.createdAtMillis))
            Spacer(modifier = Modifier.height(10.dp))
            MetaRow(
                "最近使用",
                template.lastUsedAtMillis?.let { formatDate(it) } ?: "尚未使用",
            )
            Spacer(modifier = Modifier.height(10.dp))
            MetaRow(
                "质量",
                template.qualityStatus.displayName(),
                valueColor = when (template.qualityStatus) {
                    RouteQualityStatus.PASS -> LxSage
                    RouteQualityStatus.WARNING -> LxWarning
                    RouteQualityStatus.REJECTED -> LxRose
                },
            )
            if (template.isDefault) {
                Spacer(modifier = Modifier.height(10.dp))
                MetaRow("默认", "是", valueColor = LxTerra)
            }
        }
    }
}

@Composable
private fun MetaRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = LxInk,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 14.sp, color = LxInkMuted)
        Text(text = value, fontSize = 14.sp, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QualityCard(template: RouteTemplate) {
    LxCard {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "质量提示",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxWarning,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = template.qualityMessage ?: "存在质量问题但未记录原因",
                fontSize = 13.sp,
                color = LxInkMuted,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "警告模板仍可作为默认模板使用，但建议重新录制。",
                fontSize = 12.sp,
                color = LxSandDeep,
            )
        }
    }
}

private fun formatSeconds(total: Long): String {
    val m = total / 60
    val s = total % 60
    return if (m >= 60) {
        val h = m / 60
        "${h}h${m % 60}m"
    } else {
        "${m}m${s}s"
    }
}
