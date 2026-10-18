package com.lightxin.feature.repair.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.lightxin.core.designsystem.component.LxFloatingAction
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxStatusLine
import com.lightxin.core.designsystem.component.LxTabItem
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.component.lxSharedBounds
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextSecondary
import com.lightxin.core.designsystem.theme.LxTintStrong
import com.lightxin.feature.repair.data.RepairSummary

@OptIn(ExperimentalFoundationApi::class)
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
    // null = 用户还没手动切换：有进行中的单子就停在“进行中”，否则直接看“全部”，不先给一个空页。
    var userFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val collapsed = listState.lxHeaderCollapsed()

    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    val active = uiState.items.filter { isRepairActive(it.dqzt) }
    val filter = userFilter ?: if (!uiState.isLoading && active.isEmpty()) 1 else 0
    val shown = if (filter == 0) active else uiState.items

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = { LxNavBar(onBack = onBack, title = "报修", collapsed = collapsed) },
        floatingActionButton = {
            LxFloatingAction(text = "新建报修", icon = Icons.Outlined.Add, onClick = onCreate)
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 112.dp),
            ) {
                item(key = "header") {
                    LxPageHeader(title = "报修", modifier = Modifier.padding(horizontal = 24.dp))
                }
                stickyHeader(key = "tabs") {
                    LxFilterTabs(
                        items = listOf(
                            LxTabItem("进行中", active.size),
                            LxTabItem("全部", uiState.items.size),
                        ),
                        selectedIndex = filter,
                        onSelect = { userFilter = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LxCanvas)
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .wrapContentWidth(Alignment.Start),
                    )
                }
                when {
                    uiState.isLoading -> item(key = "loading") { LxSkeletonList(count = 6) }
                    uiState.error != null && uiState.items.isEmpty() -> item(key = "error") {
                        Box(
                            modifier = Modifier.fillParentMaxHeight(0.6f).fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LxErrorState(message = uiState.error.orEmpty(), onRetry = viewModel::retry)
                        }
                    }
                    shown.isEmpty() -> item(key = "empty") {
                        Box(
                            modifier = Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LxEmptyState(
                                title = if (filter == 0) "没有正在处理的报修" else "还没有报修记录",
                                body = "提交后会出现在这里，后勤受理后会电话联系。",
                            )
                        }
                    }
                    else -> items(shown, key = { it.bxdh }) { item ->
                        RepairRow(
                            item = item,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = {
                                viewModel.openDetail(item.id, item.bxdh) { onOpenDetail(item.bxdh) }
                            },
                        )
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
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
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
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LxTintStrong),
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
                    tint = LxTextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = item.gzms.ifBlank { item.bxdd.ifBlank { "报修单" } },
                fontSize = 15.5.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            LxStatusLine(
                status = item.dqztMc.ifBlank { "处理中" },
                tone = repairTone(item.dqzt, item.dqztMc),
                meta = listOf(
                    repairTypeLeaf(item.bxlx),
                    repairPlace(item.bxdd),
                    formatRepairTime(item.bxsj),
                ).filter { it.isNotBlank() }.joinToString("  ·  "),
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

/** “木,房门锁” → “房门锁”：一级分类对用户是噪音，只留最具体的一级。 */
internal fun repairTypeLeaf(raw: String): String =
    raw.split(',', '，').map { it.trim() }.lastOrNull { it.isNotBlank() }.orEmpty()

/** “新芜校区,A36宿舍楼,南楼” → “A36 南楼”：只有一个校区，不重复显示，楼栋名也不带“宿舍楼”。 */
internal fun repairPlace(raw: String): String {
    val parts = raw.split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
    val trimmed = if (parts.size > 1 && parts.first().endsWith("校区")) parts.drop(1) else parts
    return trimmed.mapIndexed { index, part ->
        if (index == 0) part.removeSuffix("宿舍楼").ifBlank { part } else part
    }.joinToString(" ")
}

/** “A36宿舍楼 南楼” + 详细地址 “A36M214” → “A36 南楼 M214”，不把楼栋号写两遍。 */
internal fun repairLocation(raw: String, detail: String): String {
    val place = repairPlace(raw)
    val building = place.substringBefore(' ')
    val prefix = Regex("^[A-Za-z0-9]+").find(building)?.value.orEmpty()
    val room = detail.trim().let { if (prefix.isNotBlank() && it.startsWith(prefix)) it.removePrefix(prefix) else it }
    return listOf(place, room).filter { it.isNotBlank() }.joinToString(" ")
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

/** 只有未接单/处理中/待确认/待处理/延单审核中算“进行中”；取消、废单、延单、完工都不该计入。 */
private fun isRepairActive(code: String): Boolean =
    code in setOf("1", "2", "3", "9", "11")

private fun repairTone(code: String, status: String): LxTone = when {
    code == "4" || code == "10" || status.contains("完工") || status.contains("完成") -> LxTone.Success
    code == "5" || code == "6" || code == "7" || code == "8" ||
        status.contains("取消") || status.contains("废单") || status.contains("延单") -> LxTone.Neutral
    code == "1" || code == "2" || code == "3" || code == "9" || code == "11" ||
        status.contains("待") -> LxTone.Warning
    else -> LxTone.Accent
}

private fun formatRepairTime(raw: String): String {
    val match = Regex("""(\d{4})[-/](\d{2})[-/](\d{2})[ T](\d{2}):(\d{2})""").find(raw) ?: return raw
    val year = match.groupValues[1].toIntOrNull() ?: return raw
    val month = match.groupValues[2].toIntOrNull() ?: return raw
    val day = match.groupValues[3].toIntOrNull() ?: return raw
    return if (year == java.time.LocalDate.now().year) "$month/$day" else "$year/$month/$day"
}
