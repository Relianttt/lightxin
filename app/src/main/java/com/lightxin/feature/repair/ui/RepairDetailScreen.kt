package com.lightxin.feature.repair.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.lightxin.core.designsystem.component.LxCallout
import com.lightxin.core.designsystem.component.LxConfirmSheet
import com.lightxin.core.designsystem.component.LxErrorState
import com.lightxin.core.designsystem.component.LxMetaGrid
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxSectionLabel
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.lxSharedBounds
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxDanger
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTint
import com.lightxin.feature.repair.data.RepairDetail
import com.lightxin.feature.repair.data.RepairStep

@Composable
fun RepairDetailScreen(
    bxdh: String,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RepairDetailViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var withdrawVisible by remember { mutableStateOf(false) }

    LaunchedEffect(bxdh) { viewModel.load(bxdh) }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(uiState.withdrawn) {
        if (uiState.withdrawn) {
            viewModel.consumeWithdrawn()
            onChanged()
            onBack()
        }
    }

    val detail = uiState.detail
    val canWithdraw = detail != null && (detail.dqzt == "1" || detail.dqzt == "9")

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(onBack = onBack) {
                if (canWithdraw) {
                    LxTextAction(
                        text = "撤回",
                        onClick = { withdrawVisible = true },
                        color = LxDanger,
                        enabled = !uiState.isWorking,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LxSkeletonList(count = 5, modifier = Modifier.padding(padding))
            uiState.error != null -> Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                LxErrorState(message = uiState.error.orEmpty(), onRetry = viewModel::retry)
            }
            detail != null -> RepairDetailContent(
                detail = detail,
                padding = padding,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }

    if (withdrawVisible) {
        LxConfirmSheet(
            title = "撤回这张报修单？",
            message = "撤回后维修流程会终止，需要重新发起。",
            confirmText = "撤回报修",
            destructive = true,
            onConfirm = {
                withdrawVisible = false
                viewModel.withdraw()
            },
            onDismiss = { withdrawVisible = false },
        )
    }
}

@Composable
private fun RepairDetailContent(
    detail: RepairDetail,
    padding: PaddingValues,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val tone = repairTone(detail.dqzt, detail.dqztMc)
    LazyColumn(
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 2.dp),
    ) {
        item {
            LxPageHeader(title = detail.gzms.ifBlank { "报修详情" })
            Spacer(Modifier.height(20.dp))
            LxCallout(
                tone = tone,
                title = detail.dqztMc.ifBlank { "处理中" },
                subtitle = listOf(detail.wxr, detail.wxySjh.ifBlank { detail.sjh })
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { detail.bxdd },
            )
            Spacer(Modifier.height(28.dp))
        }

        if (detail.photoUrls.isNotEmpty()) {
            item {
                LxSectionLabel("现场图片")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(detail.photoUrls) { index, url ->
                        val shared = if (index == 0) {
                            Modifier.lxSharedBounds(
                                key = "repair-photo-${detail.bxdh}",
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        } else {
                            Modifier
                        }
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = shared
                                .size(96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(LxTint),
                        )
                    }
                }
                Spacer(Modifier.height(26.dp))
            }
        }

        item {
            LxSectionLabel("故障描述")
            Text(
                text = detail.gzms,
                fontSize = 14.5.sp,
                lineHeight = 26.sp,
                color = LxText,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(26.dp))
        }

        item {
            LxSectionLabel("报修信息")
            Spacer(Modifier.height(16.dp))
            LxMetaGrid(
                listOf(
                    "地点" to listOf(detail.bxdd, detail.xxdz)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .replace(",", " ")
                        .replace("，", " "),
                    "报修时间" to formatRepairDate(detail.bxsj),
                    "报修人" to detail.xm,
                    "联系电话" to detail.sjh,
                ).filter { it.second.isNotBlank() },
            )
            Spacer(Modifier.height(10.dp))
        }

        item {
            LxSectionLabel("处理流程")
            Spacer(Modifier.height(8.dp))
            RepairSteps(detail.steps)
            Spacer(Modifier.height(26.dp))
        }

        if (detail.yjfk.isNotBlank()) {
            item {
                LxSectionLabel("我的评价")
                Text(
                    text = detail.yjfk,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    color = LxTextMuted,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = "评价已在服务端提交，此处只读展示。",
                    fontSize = 11.sp,
                    color = LxTextMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RepairSteps(steps: List<RepairStep>) {
    if (steps.isEmpty()) {
        Text(text = "暂无流程记录", fontSize = 12.sp, color = LxTextMuted)
        return
    }
    Column {
        steps.forEach { step ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(LxTextMuted),
                )
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = step.clms.trim().ifBlank { "处理中" },
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium,
                        color = LxText,
                    )
                    if (step.sj.isNotBlank()) {
                        Text(
                            text = formatStepTime(step.sj),
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp,
                            color = LxTextMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatRepairDate(raw: String): String = formatStepTime(raw)

private fun formatStepTime(raw: String): String {
    Regex("""(\d{4})[-/](\d{2})[-/](\d{2})[ T](\d{2}):(\d{2})""").find(raw)?.let { match ->
        return "${match.groupValues[2]}/${match.groupValues[3]} ${match.groupValues[4]}:${match.groupValues[5]}"
    }
    Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日\s*(\d{1,2}):(\d{2})""").find(raw)?.let { match ->
        val month = match.groupValues[2].padStart(2, '0')
        val day = match.groupValues[3].padStart(2, '0')
        return "$month/$day ${match.groupValues[4]}:${match.groupValues[5]}"
    }
    return raw
}

private fun repairTone(code: String, status: String): LxTone = when {
    code == "4" || status.contains("完工") || status.contains("完成") -> LxTone.Success
    code == "10" || code == "11" || code == "12" ||
        status.contains("取消") || status.contains("废单") -> LxTone.Neutral
    status.contains("待") || code == "1" || code == "2" || code == "3" || code == "9" -> LxTone.Warning
    else -> LxTone.Accent
}
