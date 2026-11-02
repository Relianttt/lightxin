package com.lightxin.feature.water.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightxin.core.designsystem.component.LxBottomSheet
import com.lightxin.core.designsystem.component.LxConfirmSheet
import com.lightxin.core.designsystem.component.LxEmptyState
import com.lightxin.core.designsystem.component.LxFloatingAction
import com.lightxin.core.designsystem.component.LxField
import com.lightxin.core.designsystem.component.LxInput
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPanel
import com.lightxin.core.designsystem.component.LxPrimaryButton
import com.lightxin.core.designsystem.component.LxSectionLabel
import com.lightxin.core.designsystem.component.LxSkeletonBlock
import com.lightxin.core.designsystem.component.LxStatus
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.component.rememberLxPullToRefreshState
import com.lightxin.core.designsystem.theme.LxAccent
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxRadiusLg
import com.lightxin.core.designsystem.theme.LxSurface
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTextSecondary
import com.lightxin.core.designsystem.theme.LxTint
import com.lightxin.core.designsystem.theme.LxMotion
import com.lightxin.core.designsystem.theme.lxStaggerEnter
import com.lightxin.core.designsystem.theme.lxPressable
import com.lightxin.feature.water.domain.RecentDevice
import com.lightxin.feature.water.domain.deviceSubtitle
import com.lightxin.feature.water.domain.deviceTitle
import com.lightxin.feature.water.domain.formatYuan
import com.lightxin.feature.water.domain.parseDeviceCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterHomeScreen(
    onBack: () -> Unit,
    onRequireLogin: () -> Unit,
    onScan: () -> Unit,
    onOpenDevice: (String) -> Unit,
    modifier: Modifier = Modifier,
    shouldRefresh: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: WaterHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showInputSheet by remember { mutableStateOf(false) }
    var showLogoutSheet by remember { mutableStateOf(false) }
    var staggerDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LxMotion.StaggerWindowMs.toLong())
        staggerDone = true
    }
    val playStagger = !staggerDone

    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                WaterEvent.RequireLogin -> onRequireLogin()
                WaterEvent.LoginSuccess -> Unit
                is WaterEvent.Message -> scope.launch { snackbarHostState.showSnackbar(event.text) }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(
                onBack = onBack,
                title = "趣智校园",
                collapsed = listState.lxHeaderCollapsed(),
                actions = {
                    LxTextAction(text = "输入设备号", onClick = { showInputSheet = true })
                },
            )
        },
        floatingActionButton = {
            LxFloatingAction(text = "扫码", icon = Icons.Outlined.QrCodeScanner, onClick = onScan)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val refreshState = rememberLxPullToRefreshState()
        PullToRefreshBox(
            state = refreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = LxSurface,
                    color = LxAccent,
                )
            },
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 112.dp),
            ) {
                item(key = "header") {
                    LxPageHeader(title = "趣智校园")
                    Spacer(Modifier.height(20.dp))
                }
                item(key = "balance") {
                    BalancePanel(
                        balance = uiState.balance,
                        loading = uiState.balanceLoading,
                        onRetry = viewModel::refresh,
                        onLogout = { showLogoutSheet = true },
                        modifier = Modifier.lxStaggerEnter(index = 0, play = playStagger),
                    )
                }
                uiState.activeOrder?.let { order ->
                    item(key = "pending") {
                        Spacer(Modifier.height(12.dp))
                        PendingPanel(
                            title = order.deviceName,
                            onClick = { onOpenDevice(order.mac) },
                            modifier = Modifier.lxStaggerEnter(index = 1, play = playStagger),
                        )
                    }
                }
                if (uiState.recentDevices.isNotEmpty()) {
                    item(key = "recentLabel") {
                        LxSectionLabel(
                            text = "最近使用",
                            modifier = Modifier
                                .padding(top = 24.dp, bottom = 4.dp)
                                .lxStaggerEnter(index = 2, play = playStagger),
                        )
                    }
                    itemsIndexed(uiState.recentDevices, key = { _, device -> device.mac }) { index, device ->
                        RecentDeviceRow(
                            device = device,
                            onClick = { onOpenDevice(device.mac) },
                            modifier = Modifier.lxStaggerEnter(index = 3 + index, play = playStagger),
                        )
                    }
                }
                if (uiState.recentDevices.isEmpty() && uiState.activeOrder == null) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillParentMaxHeight(0.45f)
                                .fillMaxWidth()
                                .lxStaggerEnter(index = 2, play = playStagger),
                            contentAlignment = Alignment.Center,
                        ) {
                            LxEmptyState(
                                title = "还没有使用过的设备",
                                body = "扫描设备上的二维码开始",
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInputSheet) {
        DeviceCodeSheet(
            onDismiss = { showInputSheet = false },
            onConfirm = { mac ->
                showInputSheet = false
                onOpenDevice(mac)
            },
        )
    }

    if (showLogoutSheet) {
        LxConfirmSheet(
            title = "退出趣智校园",
            message = "退出后需要重新登录。",
            confirmText = "退出",
            onConfirm = {
                showLogoutSheet = false
                viewModel.logout()
            },
            onDismiss = { showLogoutSheet = false },
            destructive = false,
        )
    }
}

@Composable
private fun BalancePanel(
    balance: String?,
    loading: Boolean,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LxPanel(radius = LxRadiusLg, padding = 20.dp, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LxSectionLabel("余额", modifier = Modifier.weight(1f))
            LxTextAction(text = "退出", onClick = onLogout, color = LxTextMuted)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            when {
                balance != null -> Text(
                    text = formatYuan(balance),
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LxText,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                )

                loading -> LxSkeletonBlock(height = 40.dp, widthFraction = 0.4f)

                else -> {
                    Text(
                        text = "—",
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LxText,
                    )
                    LxTextAction(text = "重试", onClick = onRetry)
                }
            }
        }
    }
}

@Composable
private fun PendingPanel(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LxPanel(padding = 16.dp, modifier = modifier.lxPressable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                LxStatus(text = "未结算", tone = LxTone.Warning)
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = LxText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            LxTextAction(text = "去结算", onClick = onClick)
        }
    }
}

@Composable
private fun RecentDeviceRow(
    device: RecentDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .lxPressable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LxTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (device.bigTypeId == 5) Icons.Outlined.WaterDrop else Icons.Outlined.Shower,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = LxTextSecondary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deviceTitle(device.bigTypeId, device.roomName).ifBlank { device.deviceName },
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                color = LxText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = deviceSubtitle(device.areaName, device.buildingName, device.floorName)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = LxTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun DeviceCodeSheet(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LxBottomSheet(onDismiss = onDismiss) {
        Text(text = "输入设备号", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LxText)
        Spacer(Modifier.height(16.dp))
        LxField(label = "设备号", error = error) {
            LxInput(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                placeholder = "12 位设备号",
                modifier = Modifier.focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
            )
        }
        Spacer(Modifier.height(20.dp))
        LxPrimaryButton(
            text = "确定",
            onClick = {
                val mac = parseDeviceCode(value)
                if (mac == null) error = "无法识别设备号" else onConfirm(mac)
            },
            enabled = value.isNotBlank(),
        )
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}
