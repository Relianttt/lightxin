package com.lightxin.feature.running.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxStatus
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.LxTone
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

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(onBack = onBack) {
                if (uiState.templates.isNotEmpty()) {
                    LxTextAction(
                        text = "导出",
                        onClick = { RouteTemplateExport.shareAll(context, uiState.templates) },
                        color = LxTextMuted,
                    )
                }
                LxTextAction(text = "导入", onClick = onImport)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LxPageHeader(
                title = "路线模板",
                subtitle = buildString {
                    append("${uiState.templates.size} 个模板")
                    if (systemCount > 0) append(" · 系统内置 $systemCount 个")
                    uiState.templates.maxOfOrNull { it.lastUsedAtMillis ?: 0L }?.takeIf { it > 0 }?.let {
                        append(" · 最近使用 ${formatDate(it)}")
                    }
                },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (uiState.templates.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LxEmptyState(
                        title = "还没有路线模板",
                        body = "去跑步页录制一条，或从右上角导入导出的 JSON 文件。",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                ) {
                    items(uiState.templates, key = { it.id }) { template ->
                        TemplateRow(
                            template = template,
                            onClick = { onOpenDetail(template.id) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                    item { Spacer(Modifier.height(28.dp)) }
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
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                Box(Modifier.width(0.dp))
            }
            Text(
                text = buildString {
                    append(String.format("%.2f km", template.totalDistanceMeters / 1000.0))
                    append(" · ${template.pointCount} 个点")
                    append(" · ${formatDate(template.createdAtMillis)}")
                },
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = LxTextMuted,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val (label, tone) = when (template.qualityStatus) {
            RouteQualityStatus.PASS -> "已校验" to LxTone.Neutral
            RouteQualityStatus.WARNING -> "待校验" to LxTone.Warning
            RouteQualityStatus.REJECTED -> "已拒绝" to LxTone.Danger
        }
        LxStatus(
            text = label,
            tone = tone,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
