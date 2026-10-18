package com.lightxin.feature.repair.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.lightxin.core.designsystem.component.LxCallout
import com.lightxin.core.designsystem.component.LxConfirmSheet
import com.lightxin.core.designsystem.component.LxErrorState
import com.lightxin.core.designsystem.component.LxGhostButton
import com.lightxin.core.designsystem.component.LxMetaGrid
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxSectionLabel
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.component.lxSharedBounds
import com.lightxin.core.designsystem.theme.LxAccent
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxDanger
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextFaint
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTextSecondary
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
    // 已完工的单不能撤回：状态在仓库层已按 wcqk/fwpj 修正，不再单信详情里的 dqzt。
    val canWithdraw = detail != null && !detail.completed && (detail.dqzt == "1" || detail.dqzt == "9")
    val listState = rememberLazyListState()
    val collapsed = listState.lxHeaderCollapsed()

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(
                onBack = onBack,
                title = detail?.let { repairDetailTitle(it) },
                collapsed = collapsed,
            )
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
                listState = listState,
                canWithdraw = canWithdraw,
                isWorking = uiState.isWorking,
                onWithdraw = { withdrawVisible = true },
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

/** 描述够短就直接当标题（不再在下面重复一遍）；太长则用具体故障类型做标题。 */
private fun repairDetailTitle(detail: RepairDetail): String =
    if (detail.gzms.isNotBlank() && detail.gzms.length <= SHORT_DESCRIPTION) {
        detail.gzms
    } else {
        repairTypeLeaf(detail.bxlx).takeIf { it.isNotBlank() }?.let { "${it}报修" } ?: "报修详情"
    }

private const val SHORT_DESCRIPTION = 24

@Composable
private fun RepairDetailContent(
    detail: RepairDetail,
    padding: PaddingValues,
    listState: LazyListState,
    canWithdraw: Boolean,
    isWorking: Boolean,
    onWithdraw: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val tone = repairTone(detail.dqzt, detail.dqztMc)
    val descriptionIsTitle = detail.gzms.isNotBlank() && detail.gzms.length <= SHORT_DESCRIPTION
    LazyColumn(
        state = listState,
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 2.dp),
    ) {
        item {
            LxPageHeader(title = repairDetailTitle(detail))
            Spacer(Modifier.height(16.dp))
            LxCallout(
                tone = tone,
                title = detail.dqztMc.ifBlank { "处理中" },
                subtitle = listOf(
                    detail.wxr.takeIf { it.isNotBlank() }?.let { "维修员 $it" }.orEmpty(),
                    detail.wxySjh,
                ).filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "提交于 ${formatRepairDate(detail.bxsj)}" },
            )
            Spacer(Modifier.height(26.dp))
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
                                .size(104.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LxTint),
                        )
                    }
                }
                Spacer(Modifier.height(26.dp))
            }
        }

        if (!descriptionIsTitle && detail.gzms.isNotBlank()) {
            item {
                LxSectionLabel("故障描述")
                Text(
                    text = detail.gzms,
                    fontSize = 15.sp,
                    lineHeight = 25.sp,
                    color = LxText,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(26.dp))
            }
        }

        item {
            LxSectionLabel("报修信息")
            Spacer(Modifier.height(12.dp))
            LxMetaGrid(
                listOf(
                    "地点" to repairLocation(detail.bxdd, detail.xxdz),
                    "报修时间" to formatRepairDate(detail.bxsj),
                    "类型" to repairTypeLeaf(detail.bxlx),
                    "报修人" to detail.xm,
                    "联系电话" to detail.sjh,
                ).filter { it.second.isNotBlank() },
            )
            Spacer(Modifier.height(10.dp))
        }

        item {
            LxSectionLabel("处理流程")
            Spacer(Modifier.height(6.dp))
            RepairSteps(detail.steps)
            Spacer(Modifier.height(22.dp))
        }

        if (detail.yjfk.isNotBlank()) {
            item {
                LxSectionLabel("我的评价")
                Text(
                    text = detail.yjfk,
                    fontSize = 14.5.sp,
                    lineHeight = 23.sp,
                    color = LxText,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        if (canWithdraw) {
            item {
                LxGhostButton(
                    text = "撤回报修",
                    onClick = onWithdraw,
                    enabled = !isWorking,
                    contentColor = LxDanger,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

/** 时间顺序展示，最后一步是当前进度：陶土圆点 + 加粗。 */
@Composable
private fun RepairSteps(steps: List<RepairStep>) {
    if (steps.isEmpty()) {
        Text(text = "暂无流程记录", fontSize = 13.sp, color = LxTextMuted)
        return
    }
    Column {
        steps.forEachIndexed { index, step ->
            val current = index == steps.lastIndex
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(if (current) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (current) LxAccent else LxTextFaint),
                )
                Column(modifier = Modifier.weight(1f).padding(start = if (current) 12.dp else 14.dp)) {
                    Text(
                        text = step.clms.trim().ifBlank { "处理中" },
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (current) LxText else LxTextSecondary,
                    )
                    if (step.sj.isNotBlank()) {
                        Text(
                            text = formatStepTime(step.sj),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = LxTextMuted,
                            modifier = Modifier.padding(top = 1.dp),
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
        val year = match.groupValues[1]
        val prefix = if (year == java.time.LocalDate.now().year.toString()) "" else "$year/"
        return "$prefix${match.groupValues[2]}/${match.groupValues[3]} ${match.groupValues[4]}:${match.groupValues[5]}"
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
