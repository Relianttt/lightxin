package com.lightxin.feature.water.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.lightxin.core.designsystem.component.LxGhostButton
import com.lightxin.core.designsystem.component.LxMetaGrid
import com.lightxin.core.designsystem.component.LxMetaRow
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPanel
import com.lightxin.core.designsystem.component.LxPrimaryButton
import com.lightxin.core.designsystem.component.LxSectionLabel
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxStatus
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.theme.LxAccent
import com.lightxin.core.designsystem.theme.LxAccentText
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxHairline
import com.lightxin.core.designsystem.theme.LxMotion
import com.lightxin.core.designsystem.theme.LxRadiusLg
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextSecondary
import com.lightxin.feature.water.domain.WaterDevice
import com.lightxin.feature.water.domain.deviceSubtitle
import com.lightxin.feature.water.domain.deviceTitle
import com.lightxin.feature.water.domain.formatLi
import com.lightxin.feature.water.domain.formatYuan
import com.lightxin.feature.water.domain.useTimeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** 连接、开阀、出水、结算：整页切到「水位」舞台，和静止的详情页一眼可分。 */
private fun DevicePhase.isStaged(): Boolean =
    this == DevicePhase.Connecting || this == DevicePhase.Starting ||
        this == DevicePhase.Running || this == DevicePhase.Settling

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
    val staged = phase.isStaged()
    val listState = rememberLazyListState()
    val headerCollapsed = listState.lxHeaderCollapsed()
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

    // 开阀成功时用户多半在看水龙头，用触感告诉他「开了」；重进页面恢复使用中不震
    val haptic = LocalHapticFeedback.current
    var lastPhase by remember { mutableStateOf(phase) }
    LaunchedEffect(phase) {
        if (phase == DevicePhase.Running && lastPhase == DevicePhase.Starting) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        lastPhase = phase
    }

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
    val actionButton: @Composable () -> Unit = {
        when (phase) {
            // 结束会结算扣钱：换成和「开始」完全不同的按钮，文案说清后果
            DevicePhase.Running, DevicePhase.Settling -> LxGhostButton(
                text = "结束并结算",
                onClick = { ensureBluetoothThen { viewModel.settle() } },
                enabled = phase == DevicePhase.Running,
            )

            else -> LxPrimaryButton(
                text = when (phase) {
                    DevicePhase.NeedSettle -> "结算"
                    DevicePhase.BusyByOther -> "设备使用中"
                    else -> if (device?.bigTypeId == WaterDevice.TYPE_DRINKING) "开始接水" else "开始洗澡"
                },
                onClick = {
                    when (phase) {
                        DevicePhase.Idle -> ensureBluetoothThen { viewModel.start() }
                        DevicePhase.NeedSettle -> ensureBluetoothThen { viewModel.settle() }
                        else -> Unit
                    }
                },
                enabled = phase == DevicePhase.Idle || phase == DevicePhase.NeedSettle,
            )
        }
    }

    Box(modifier = modifier.fillMaxSize().background(LxCanvas)) {
        WaterLevel(
            level = when (phase) {
                DevicePhase.Connecting, DevicePhase.Starting -> 0.1f
                DevicePhase.Running -> 0.42f
                else -> 0f
            },
            modifier = Modifier.fillMaxSize(),
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LxNavBar(
                    onBack = backAction,
                    title = device?.let { deviceTitle(it) }.orEmpty(),
                    collapsed = !staged && headerCollapsed,
                )
            },
            bottomBar = {
                if (showBottomBar) {
                    if (staged) {
                        // 水位要漫到按钮后面，不用带底色的 LxBottomBar
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        ) { actionButton() }
                    } else {
                        LxBottomBar { actionButton() }
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

                device != null -> AnimatedContent(
                    targetState = phase,
                    contentKey = { it.isStaged() },
                    transitionSpec = {
                        fadeIn(tween(LxMotion.StateMs, easing = LxMotion.EaseEnter)) togetherWith
                            fadeOut(tween(LxMotion.StateExitMs, easing = LxMotion.EaseExit))
                    },
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    label = "waterStage",
                ) { target ->
                    if (target.isStaged()) {
                        UsageStage(
                            device = device,
                            phase = target,
                            elapsedMs = uiState.runningSince?.let { now - it } ?: 0L,
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                        ) {
                            item(key = "header") {
                                LxPageHeader(title = deviceTitle(device), subtitle = deviceSubtitle(device))
                                Spacer(Modifier.height(20.dp))
                            }
                            if (target == DevicePhase.NeedSettle) {
                                item(key = "needSettle") {
                                    LxCallout(tone = LxTone.Warning, title = "上次使用未结算")
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                            item(key = "panel") {
                                DeviceInfoPanel(device = device, balance = uiState.balance)
                            }
                            if (target == DevicePhase.BusyByOther) {
                                item(key = "busy") {
                                    LxStatus(
                                        text = "设备使用中",
                                        tone = LxTone.Warning,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                }
                            }
                        }
                    }
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

/** 余额、预扣与可用时段合成一张卡。 */
@Composable
private fun DeviceInfoPanel(device: WaterDevice, balance: String?) {
    LxPanel(radius = LxRadiusLg, padding = 20.dp, modifier = Modifier.fillMaxWidth()) {
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
        Box(
            Modifier
                .padding(top = 16.dp, bottom = 4.dp)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(LxHairline),
        )
        LxMetaRow(label = "预扣", value = formatYuan(device.withholdMoney))
        useTimeText(device)?.let { LxMetaRow(label = "可用时段", value = it) }
    }
}

@Composable
private fun UsageStage(device: WaterDevice, phase: DevicePhase, elapsedMs: Long) {
    val running = phase == DevicePhase.Running
    val timerAlpha by animateFloatAsState(
        targetValue = if (running) 1f else 0f,
        animationSpec = tween(LxMotion.StateMs, easing = LxMotion.EaseInOut),
        label = "waterTimerAlpha",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LxPageHeader(title = deviceTitle(device), subtitle = deviceSubtitle(device))
        Spacer(Modifier.weight(0.7f))
        AnimatedContent(
            targetState = when (phase) {
                DevicePhase.Connecting -> "连接中"
                DevicePhase.Starting -> "开阀中"
                DevicePhase.Running -> if (device.isDrinking) "可以接水了" else "热水已开"
                else -> "结算中"
            },
            transitionSpec = {
                fadeIn(tween(LxMotion.StateMs, easing = LxMotion.EaseEnter)) togetherWith
                    fadeOut(tween(LxMotion.StateExitMs, easing = LxMotion.EaseExit))
            },
            label = "waterStageLabel",
        ) { label ->
            Text(
                text = label,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                color = if (label == "可以接水了" || label == "热水已开") LxAccentText else LxTextSecondary,
            )
        }
        Text(
            text = formatElapsed(elapsedMs),
            fontSize = 64.sp,
            lineHeight = 72.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(top = 4.dp).alpha(timerAlpha),
        )
        Spacer(Modifier.weight(1.3f))
    }
}

/** 从底部升起的两层水波；[level] 是占整屏高度的比例，0 时完全退下。 */
@Composable
private fun WaterLevel(level: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(LxMotion.CountUpMs, easing = LxMotion.EaseInOut),
        label = "waterLevel",
    )
    val transition = rememberInfiniteTransition(label = "waterWave")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(LxMotion.PulseMs, easing = LinearEasing)),
        label = "waterWavePhase",
    )
    val back = LxAccent.copy(alpha = 0.07f)
    val front = LxAccent.copy(alpha = 0.11f)
    Canvas(modifier) {
        if (animated <= 0.001f) return@Canvas
        val top = size.height * (1f - animated)
        // 水位很低时压扁波峰，避免波浪在屏幕底边露头
        val amp = 7.dp.toPx() * (animated * 10f).coerceAtMost(1f)
        drawWave(top, amp, size.width * 1.3f, wave, back)
        drawWave(top + 5.dp.toPx(), amp * 0.8f, size.width * 0.9f, -wave * 2f + 1f, front)
    }
}

private fun DrawScope.drawWave(top: Float, amplitude: Float, wavelength: Float, phase: Float, color: Color) {
    val path = Path().apply {
        moveTo(0f, size.height)
        var x = 0f
        val step = 6.dp.toPx()
        while (x <= size.width + step) {
            lineTo(x, top + amplitude * sin(2f * PI.toFloat() * x / wavelength + phase))
            x += step
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
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
