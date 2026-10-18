package com.lightxin.feature.running.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxEmptyState
import com.lightxin.core.designsystem.component.LxFloatingAction
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxStatusLine
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.theme.LxAccentText
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.feature.running.domain.RouteQualityStatus
import com.lightxin.feature.running.domain.RouteTemplate

@Composable
fun RouteTemplateListScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteTemplateViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val systemCount = uiState.templates.count { it.source == com.lightxin.feature.running.domain.RouteTemplateSource.SYSTEM }
    val listState = rememberLazyListState()
    val collapsed = listState.lxHeaderCollapsed()

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(onBack = onBack, title = "路线模板", collapsed = collapsed) {
                if (uiState.templates.isNotEmpty()) {
                    LxTextAction(
                        text = "导出",
                        onClick = { RouteTemplateExport.shareAll(context, uiState.templates) },
                        color = LxTextMuted,
                    )
                }
            }
        },
        floatingActionButton = {
            LxFloatingAction(text = "导入模板", icon = Icons.Outlined.UploadFile, onClick = onImport)
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item(key = "header") {
                LxPageHeader(
                    title = "路线模板",
                    subtitle = buildString {
                        append("${uiState.templates.size} 个模板")
                        if (systemCount > 0) append(" · 系统内置 $systemCount 个")
                    },
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            if (uiState.templates.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillParentMaxHeight(0.6f).fillMaxWidth().padding(top = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LxEmptyState(
                            title = "还没有路线模板",
                            body = "去跑步页录制一条，或点右下角导入下载好的模板文件。",
                        )
                    }
                }
            } else {
                items(uiState.templates, key = { it.id }) { template ->
                    TemplateRow(
                        template = template,
                        onClick = { onOpenDetail(template.id) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(
    template: RouteTemplate,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TemplateThumb(
            templateId = template.id,
            points = template.points,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = template.name,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LxText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (template.isDefault) {
                    Text(
                        text = "默认",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = LxAccentText,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            val (label, tone) = when (template.qualityStatus) {
                RouteQualityStatus.PASS -> "已校验" to LxTone.Neutral
                RouteQualityStatus.WARNING -> "待校验" to LxTone.Warning
                RouteQualityStatus.REJECTED -> "已拒绝" to LxTone.Danger
            }
            LxStatusLine(
                status = label,
                tone = tone,
                meta = buildString {
                    append(String.format("%.2f km", template.totalDistanceMeters / 1000.0))
                    append("  ·  ${template.pointCount} 个点")
                    append("  ·  ${formatDate(template.createdAtMillis)}")
                },
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}
