package com.lightxin.feature.repair.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.HomeRepairService
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.lightxin.core.designsystem.component.LxEmptyState
import com.lightxin.core.designsystem.component.LxErrorState
import com.lightxin.core.designsystem.component.LxFilterTabs
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxStatus
import com.lightxin.core.designsystem.component.LxTabItem
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.lxSharedBounds
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTint
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
    var filter by remember { mutableIntStateOf(0) }

    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    val active = uiState.items.filter { isRepairActive(it.dqzt) }
    val shown = if (filter == 0) active else uiState.items

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(onBack = onBack) {
                LxTextAction(text = "新建", onClick = onCreate)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LxPageHeader(
                title = "报修",
                subtitle = if (active.isEmpty()) "${uiState.items.size} 条记录" else "${active.size} 条正在处理",
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            LxFilterTabs(
                items = listOf(
                    LxTabItem("进行中", active.size),
                    LxTabItem("全部", uiState.items.size),
                ),
                selectedIndex = filter,
                onSelect = { filter = it },
            )

            when {
                uiState.isLoading -> LxSkeletonList(count = 6)
                uiState.error != null && uiState.items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LxErrorState(message = uiState.error.orEmpty(), onRetry = viewModel::retry)
                }
                shown.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LxEmptyState(
                        title = if (filter == 0) "没有正在处理的报修" else "还没有报修记录",
                        body = "提交后会出现在这里，后勤受理后会电话联系你。",
                        actionText = "新建报修",
                        onAction = onCreate,
                    )
                }
                else -> PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        items(shown, key = { it.bxdh }) { item ->
                            RepairRow(
                                item = item,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onClick = {
                                    viewModel.openDetail(item.id, item.bxdh) { onOpenDetail(item.bxdh) }
                                },
                            )
                        }
                        item { Spacer(Modifier.height(28.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepairRow(
    item: RepairSummary,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                .size(46.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(LxTint),
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
                    imageVector = repairTypeIcon(item.bxlx),
                    contentDescription = null,
                    tint = LxTextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = item.gzms.ifBlank { item.bxdd.ifBlank { "报修单" } },
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOf(
                item.bxlx.replace(",", " / ").replace("，", " / "),
                item.bxdd.replace(",", " ").replace("，", " "),
                formatRepairTime(item.bxsj),
            ).filter { it.isNotBlank() }.joinToString(" · ")
            Text(
                text = meta,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = LxTextMuted,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LxStatus(
            text = item.dqztMc.ifBlank { "处理中" },
            tone = repairTone(item.dqzt, item.dqztMc),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun repairTypeIcon(type: String): ImageVector = when {
    type.contains("水") -> Icons.Outlined.WaterDrop
    type.contains("电") || type.contains("空调") -> Icons.Outlined.Bolt
    type.contains("多媒体") || type.contains("投影") -> Icons.Outlined.DesktopWindows
    type.contains("门") || type.contains("窗") -> Icons.Outlined.DoorFront
    type.contains("网络") -> Icons.Outlined.Wifi
    type.contains("家具") -> Icons.Outlined.Chair
    else -> Icons.Outlined.HomeRepairService
}

private fun isRepairActive(code: String): Boolean =
    code !in setOf("4", "10", "11", "12")

private fun repairTone(code: String, status: String): LxTone = when {
    code == "4" || status.contains("完工") || status.contains("完成") -> LxTone.Success
    code == "10" || code == "11" || code == "12" ||
        status.contains("取消") || status.contains("废单") -> LxTone.Neutral
    status.contains("待") || code == "1" || code == "2" || code == "3" || code == "9" -> LxTone.Warning
    else -> LxTone.Accent
}

private fun formatRepairTime(raw: String): String {
    val match = Regex("""(\d{4})[-/](\d{2})[-/](\d{2})[ T](\d{2}):(\d{2})""").find(raw)
    if (match != null) {
        val month = match.groupValues[2].toIntOrNull() ?: return raw
        val day = match.groupValues[3]
        val hour = match.groupValues[4]
        val minute = match.groupValues[5]
        return "${month}/${day} $hour:$minute"
    }
    return raw
}
