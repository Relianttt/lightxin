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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxDetailRow
import com.lightxin.core.designsystem.component.LxDialog
import com.lightxin.core.designsystem.component.LxDialogConfirmTone
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxOutlinedButton
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.component.lxSharedBounds
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxSage
import com.lightxin.core.designsystem.theme.LxTerra
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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "报修详情", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            uiState.error != null -> LxError(
                message = uiState.error.orEmpty(),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            uiState.detail != null -> {
                val detail = uiState.detail!!
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        HeroPhoto(
                            detail = detail,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                    item { BaseInfoCard(detail) }
                    if (detail.photoUrls.isNotEmpty()) {
                        item { PhotoStrip(detail.photoUrls) }
                    }
                    item { FlowCard(detail.steps) }
                    if (detail.yjfk.isNotBlank()) {
                        item { EvaluationCard(detail.yjfk) }
                    }
                    item {
                        // 1=未接单、9=待处理 才允许撤回；4=维修完成已不可撤回
                        if (detail.dqzt == "1" || detail.dqzt == "9") {
                            LxOutlinedButton(
                                text = "撤回报修",
                                onClick = { withdrawVisible = true },
                                enabled = !uiState.isWorking,
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (withdrawVisible) {
        LxDialog(
            title = "撤回报修",
            message = "确定撤回这张报修单？撤回后维修流程会终止。",
            confirmText = "撤回",
            dismissText = "取消",
            confirmTone = LxDialogConfirmTone.Destructive,
            onDismissRequest = { withdrawVisible = false },
            onDismiss = { withdrawVisible = false },
            onConfirm = {
                withdrawVisible = false
                viewModel.withdraw()
            },
        )
    }
}

@Composable
private fun HeroPhoto(
    detail: RepairDetail,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val first = detail.photoUrls.firstOrNull()
    Box(
        modifier = Modifier
            .then(
                Modifier.lxSharedBounds(
                    key = "repair-photo-${detail.bxdh}",
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
            )
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(16.dp))
            .background(LxCream),
        contentAlignment = Alignment.Center,
    ) {
        if (first != null) {
            AsyncImage(
                model = first,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(text = "没有现场图片", fontSize = 13.sp, color = LxInkMuted)
        }
    }
}

@Composable
private fun BaseInfoCard(detail: RepairDetail) {
    LxCard {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = detail.bxdh,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LxInk,
                    modifier = Modifier.weight(1f),
                )
                RepairStatusBadge(detail.dqztMc, detail.dqzt)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = detail.bxdd, fontSize = 13.sp, color = LxInkMuted)
            Spacer(modifier = Modifier.height(8.dp))
            LxDetailRow(label = "报修类型", value = detail.bxlx)
            LxDetailRow(label = "详细地址", value = detail.xxdz)
            LxDetailRow(label = "报修时间", value = detail.bxsj)
            LxDetailRow(label = "联系电话", value = detail.sjh)
            LxDetailRow(label = "维修人员", value = listOf(detail.wxr, detail.wxySjh).filter { it.isNotBlank() }.joinToString(" "))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "故障描述",
                fontSize = 12.sp,
                color = LxInkMuted,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = detail.gzms, fontSize = 14.sp, color = LxInk)
        }
    }
}

@Composable
private fun PhotoStrip(urls: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(urls) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LxCream),
            )
        }
    }
}

@Composable
private fun FlowCard(steps: List<RepairStep>) {
    LxCard {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = "维修流程",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (steps.isEmpty()) {
                Text(text = "暂无流程记录", fontSize = 12.sp, color = LxInkMuted)
            } else {
                steps.forEachIndexed { index, step ->
                    Row {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (index == steps.lastIndex) LxTerra else LxSage),
                            )
                            if (index != steps.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 1.dp, height = 34.dp)
                                        .background(LxInkMuted.copy(alpha = 0.3f)),
                                )
                            }
                        }
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(text = step.clms, fontSize = 13.sp, color = LxInk)
                            Text(text = step.sj, fontSize = 11.sp, color = LxInkMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvaluationCard(feedback: String) {
    LxCard {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = "我的评价",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = feedback, fontSize = 13.sp, color = LxInkMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "评价已在服务端提交，此处只读展示。", fontSize = 11.sp, color = LxInkMuted)
        }
    }
}
