package com.lightxin.feature.water.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightxin.core.designsystem.component.LxBottomBar
import com.lightxin.core.designsystem.component.LxBottomSheet
import com.lightxin.core.designsystem.component.LxCallout
import com.lightxin.core.designsystem.component.LxConfirmSheet
import com.lightxin.core.designsystem.component.LxEmptyState
import com.lightxin.core.designsystem.component.LxErrorState
import com.lightxin.core.designsystem.component.LxMetaGrid
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPanel
import com.lightxin.core.designsystem.component.LxPrimaryButton
import com.lightxin.core.designsystem.component.LxSectionLabel
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxStatus
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxMotion
import com.lightxin.core.designsystem.theme.LxRadiusLg
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.feature.water.domain.WaterDevice
import com.lightxin.feature.water.domain.deviceSubtitle
import com.lightxin.feature.water.domain.deviceTitle
import com.lightxin.feature.water.domain.formatLi
import com.lightxin.feature.water.domain.formatYuan
import com.lightxin.feature.water.domain.useTimeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class DevicePanel { Balance, Timer, NeedSettle }

@Composable
fun WaterDeviceScreen(
    onBack: () -> Unit,
    onRequireLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WaterDeviceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val phase = uiState.phase
    val device = uiState.device
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showLeaveSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                WaterEvent.RequireLogin -> onRequireLogin()
                WaterEvent.LoginSuccess -> Unit
                is WaterEvent.Message -> scope.launch { snackbarHostState.showSnackbar(event.text) }
            }
        }
    }

    val blocked = phase == DevicePhase.Connecting || phase == DevicePhase.Starting || phase == DevicePhase.Settling
    val backAction: () -> Unit = {
        when {
            blocked -> Unit
            phase == DevicePhase.Running -> showLeaveSheet = true
            else -> onBack()
        }
    }
    BackHandler(enabled = true, onBack = backAction)

    val ensureBluetoothThen = rememberBluetoothGate(BluetoothUse.Connect, snackbarHostState)

    // Running 期间屏幕常亮
    val view = LocalView.current
    DisposableEffect(phase == DevicePhase.Running) {
        view.keepScreenOn = phase == DevicePhase.Running
        onDispose { view.keepScreenOn = false }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.runningSince, phase) {
        if (phase == DevicePhase.Running && uiState.runningSince != null) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    val showBottomBar = phase != DevicePhase.Loading && phase !is DevicePhase.LoadFailed &&
        phase !is DevicePhase.Unsupported
    val buttonText = when (phase) {
        DevicePhase.Idle -> if (device?.bigTypeId == WaterDevice.TYPE_DRINKING) "开始接水" else "开始洗澡"
        DevicePhase.Connecting -> "连接中…"
        DevicePhase.Starting -> "开阀中…"
        DevicePhase.Running -> "结束"
        DevicePhase.Settling -> "结算中…"
        DevicePhase.NeedSettle -> "结算"
        DevicePhase.BusyByOther -> "设备使用中"
        else -> ""
    }
    val buttonEnabled = phase == DevicePhase.Idle || phase == DevicePhase.Running || phase == DevicePhase.NeedSettle

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(
                onBack = backAction,
                title = device?.let { deviceTitle(it) }.orEmpty(),
                collapsed = listState.lxHeaderCollapsed(),
            )
        },
        bottomBar = {
            if (showBottomBar) {
                LxBottomBar {
                    LxPrimaryButton(
                        text = buttonText,
                        onClick = {
                            when (phase) {
                                DevicePhase.Idle -> ensureBluetoothThen { viewModel.start() }
                                DevicePhase.Running, DevicePhase.NeedSettle ->
                                    ensureBluetoothThen { viewModel.settle() }

                                else -> Unit
                            }
                        },
                        enabled = buttonEnabled,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            phase == DevicePhase.Loading -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                LxSkeletonList(count = 4)
            }

            phase is DevicePhase.LoadFailed -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LxErrorState(message = phase.message, onRetry = viewModel::load)
            }

            phase is DevicePhase.Unsupported -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LxEmptyState(title = phase.title, body = device?.deviceName.orEmpty())
            }

            device != null -> LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                item(key = "header") {
                    LxPageHeader(title = deviceTitle(device), subtitle = deviceSubtitle(device))
                }
                item(key = "panel") {
                    Spacer(Modifier.height(8.dp))
                    val panel = when (phase) {
                        DevicePhase.Running, DevicePhase.Settling -> DevicePanel.Timer
                        DevicePhase.NeedSettle -> DevicePanel.NeedSettle
                        else -> DevicePanel.Balance
                    }
                    AnimatedContent(
                        targetState = panel,
                        transitionSpec = {
                            fadeIn(tween(LxMotion.StateMs, easing = LxMotion.EaseEnter)) togetherWith
                                fadeOut(tween(LxMotion.StateExitMs, easing = LxMotion.EaseExit))
                        },
                        label = "waterPanel",
                    ) { target ->
                        when (target) {
                            DevicePanel.Balance -> BalancePanel(
                                balance = uiState.balance,
                                showBusy = phase == DevicePhase.BusyByOther,
                            )

                            DevicePanel.Timer -> TimerPanel(
                                elapsedMs = (uiState.runningSince?.let { now - it } ?: 0L),
                            )

                            DevicePanel.NeedSettle -> LxCallout(
                                tone = LxTone.Warning,
                                title = "上次使用未结算",
                            )
                        }
                    }
                }
                item(key = "meta") {
                    Spacer(Modifier.height(24.dp))
                    LxMetaGrid(
                        items = buildList {
                            add("预扣" to formatYuan(device.withholdMoney))
                            useTimeText(device)?.let { add("可用时段" to it) }
                        },
                    )
                }
            }
        }
    }

    if (showLeaveSheet) {
        LxConfirmSheet(
            title = "还在使用中",
            message = "离开后回到这里结束并结算。",
            confirmText = "离开",
            onConfirm = {
                showLeaveSheet = false
                onBack()
            },
            onDismiss = { showLeaveSheet = false },
            destructive = false,
        )
    }

    uiState.result?.let { result ->
        LxBottomSheet(onDismiss = viewModel::dismissResult) {
            LxSectionLabel("本次消费")
            Text(
                text = formatLi(result.consumeMoneyLi),
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(20.dp))
            LxMetaGrid(
                items = listOf(
                    "余额" to (uiState.balance?.let { formatYuan(it) } ?: "—"),
                    "订单号" to result.orderNo,
                ),
            )
            Spacer(Modifier.height(8.dp))
            LxPrimaryButton(
                text = "完成",
                onClick = { hide { viewModel.dismissResult() } },
            )
        }
    }
}

@Composable
private fun BalancePanel(
    balance: String?,
    showBusy: Boolean,
) {
    Column {
        LxPanel(radius = LxRadiusLg, padding = 20.dp) {
            LxSectionLabel("余额")
            Text(
                text = balance?.let { formatYuan(it) } ?: "—",
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (showBusy) {
            LxStatus(
                text = "设备使用中",
                tone = LxTone.Warning,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun TimerPanel(elapsedMs: Long) {
    LxPanel(radius = LxRadiusLg, padding = 20.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LxSectionLabel("已用时")
            LxStatus(text = "使用中", tone = LxTone.Accent)
        }
        Text(
            text = formatElapsed(elapsedMs),
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

internal fun formatElapsed(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
