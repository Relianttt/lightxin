package com.lightxin.feature.running.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxBottomSheet
import com.lightxin.core.designsystem.component.LxCallout
import com.lightxin.core.designsystem.component.LxConfirmSheet
import com.lightxin.core.designsystem.component.LxDangerButton
import com.lightxin.core.designsystem.component.LxEmptyState
import com.lightxin.core.designsystem.component.LxInput
import com.lightxin.core.designsystem.component.LxMetaRow
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPanel
import com.lightxin.core.designsystem.component.LxPrimaryButton
import com.lightxin.core.designsystem.component.LxGhostButton
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.theme.LxAccentText
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.feature.running.domain.RouteQualityStatus

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
    val listState = rememberLazyListState()
    val collapsed = listState.lxHeaderCollapsed()

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(
                onBack = onBack,
                title = template?.name ?: "路线模板",
                collapsed = collapsed,
            )
        },
    ) { padding ->
        if (template == null) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                LxEmptyState(
                    title = "模板不存在",
                    body = "可能已被删除，返回列表看看其它模板。",
                )
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 2.dp),
        ) {
            item {
                LxPageHeader(title = template.name)
                if (template.isDefault) {
                    Text(
                        text = "默认模板",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = LxAccentText,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            item {
                LxPanel(radius = 16.dp, padding = 16.dp, shadow = true) {
                    TrackCanvas(
                        points = template.points,
                        modifier = Modifier.fillMaxWidth().height(146.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatCell(
                            value = String.format("%.2f", template.totalDistanceMeters / 1000.0),
                            label = "公里",
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            value = template.pointCount.toString(),
                            label = "轨迹点",
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            value = formatDuration(template.durationSeconds),
                            label = "录制时长",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            item {
                LxMetaRow(label = "来源", value = template.source.displayName())
                LxMetaRow(label = "录制时间", value = formatDate(template.createdAtMillis))
                LxMetaRow(
                    label = "最近使用",
                    value = template.lastUsedAtMillis?.let(::formatDate) ?: "尚未使用",
                )
                val (qualityLabel, qualityTone) = when (template.qualityStatus) {
                    RouteQualityStatus.PASS -> "正常" to LxTone.Success
                    RouteQualityStatus.WARNING -> "警告" to LxTone.Warning
                    RouteQualityStatus.REJECTED -> "已拒绝" to LxTone.Danger
                }
                LxMetaRow(label = "质量", value = qualityLabel, tone = qualityTone)
                Spacer(Modifier.height(6.dp))
            }

            if (template.qualityStatus != RouteQualityStatus.PASS) {
                item {
                    val warning = template.qualityStatus == RouteQualityStatus.WARNING
                    LxCallout(
                        tone = if (warning) LxTone.Warning else LxTone.Danger,
                        title = if (warning) "质量警告" else "质量已拒绝",
                        subtitle = buildString {
                            append(template.qualityMessage ?: "存在质量问题但未记录原因。")
                            if (warning) append("\n警告模板仍可作为默认模板使用，但建议重新录制。")
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Spacer(Modifier.height(22.dp))
                }
            }

            item {
                if (!template.isDefault) {
                    LxPrimaryButton(
                        text = "设为默认",
                        onClick = { viewModel.setDefault(template.id) },
                    )
                    Text(
                        text = "设为默认后，跑步页会优先选中此模板。",
                        fontSize = 11.5.sp,
                        lineHeight = 19.sp,
                        color = LxTextMuted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                } else {
                    Text(
                        text = "跑步页会优先选中这个模板。",
                        fontSize = 11.5.sp,
                        lineHeight = 19.sp,
                        color = LxTextMuted,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LxGhostButton(
                        text = "导出模板",
                        onClick = { RouteTemplateExport.shareSingle(context, template) },
                        modifier = Modifier.weight(1f),
                    )
                    LxGhostButton(
                        text = "重命名",
                        onClick = {
                            renameInput = template.name
                            renameVisible = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(36.dp))
                LxDangerButton(
                    text = "删除模板",
                    onClick = { deleteVisible = true },
                )
                Text(
                    text = "删除后不可恢复，建议先导出备份。",
                    fontSize = 11.sp,
                    color = LxTextMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (renameVisible) {
        LxBottomSheet(onDismiss = { renameVisible = false }) {
            Text(
                text = "重命名模板",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
            )
            Spacer(Modifier.height(14.dp))
            LxInput(
                value = renameInput,
                onValueChange = { renameInput = it },
                placeholder = "模板名称",
            )
            LxPrimaryButton(
                text = "保存",
                onClick = {
                    viewModel.rename(template?.id.orEmpty(), renameInput)
                    renameVisible = false
                },
                enabled = renameInput.isNotBlank(),
                modifier = Modifier.padding(top = 16.dp),
            )
            LxTextAction(
                text = "取消",
                onClick = { renameVisible = false },
                color = LxTextMuted,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }

    if (deleteVisible) {
        LxConfirmSheet(
            title = "删除这个模板？",
            message = "确定删除“${template?.name.orEmpty()}”？此操作不可撤销，建议先导出备份。",
            confirmText = "删除模板",
            destructive = true,
            onConfirm = {
                deleteVisible = false
                template?.let {
                    viewModel.delete(it.id)
                    onBack()
                }
            },
            onDismiss = { deleteVisible = false },
        )
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            fontSize = 21.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = LxTextMuted,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

private fun formatDuration(total: Long): String {
    val minutes = total / 60
    val seconds = total % 60
    return "%02d:%02d".format(minutes, seconds)
}
