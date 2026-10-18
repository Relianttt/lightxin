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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.HomeRepairService
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxEmpty
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxFloatingActionButton
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.component.lxSharedBounds
import com.lightxin.core.designsystem.theme.LxAmber
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxSage
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.feature.repair.data.RepairSummary

@Composable
fun RepairListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenDetail: (String) -> Unit,
    shouldRefresh: Boolean,
    onRefreshConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RepairListViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    androidx.compose.runtime.LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "报修", onBack = onBack) },
        floatingActionButton = {
            LxFloatingActionButton(onClick = onCreate) {
                Icon(Icons.Outlined.Add, contentDescription = "新建报修", tint = Color.White)
            }
        },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            uiState.error != null && uiState.items.isEmpty() -> LxError(
                message = uiState.error.orEmpty(),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            uiState.items.isEmpty() -> LxEmpty(
                message = "还没有报修记录",
                icon = Icons.Outlined.HomeRepairService,
                modifier = Modifier.padding(padding),
            )
            else -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.items, key = { it.bxdh }) { item ->
                        RepairCard(
                            item = item,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = {
                                viewModel.openDetail(item.id, item.bxdh) { onOpenDetail(item.bxdh) }
                            },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RepairCard(
    item: RepairSummary,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit,
) {
    LxCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .then(
                        Modifier.lxSharedBounds(
                            key = "repair-photo-${item.bxdh}",
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        ),
                    )
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LxCream),
                contentAlignment = Alignment.Center,
            ) {
                if (item.photoUrl != null) {
                    AsyncImage(
                        model = item.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Outlined.HomeRepairService,
                        contentDescription = null,
                        tint = LxInkMuted,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.bxdd.ifBlank { "报修单" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LxInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    RepairStatusBadge(item.dqztMc, item.dqzt)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.bxlx,
                    fontSize = 12.sp,
                    color = LxTerra,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.gzms,
                    fontSize = 13.sp,
                    color = LxInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = item.bxsj, fontSize = 11.sp, color = LxInkMuted)
            }
        }
    }
}

@Composable
fun RepairStatusBadge(status: String, code: String) {
    val color = when {
        code == "4" -> LxSage
        code == "1" || code == "2" || code == "3" || code == "9" || status.contains("待") -> LxAmber
        code == "10" || code == "11" || code == "12" || status.contains("取消") ||
            status.contains("废单") -> LxInkMuted
        else -> LxTerra
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = status.ifBlank { "处理中" },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}
