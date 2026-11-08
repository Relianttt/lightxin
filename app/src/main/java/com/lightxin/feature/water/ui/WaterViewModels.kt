package com.lightxin.feature.water.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.water.data.QzxyException
import com.lightxin.feature.water.data.QzxyRepository
import com.lightxin.feature.water.data.QzxySessionExpired
import com.lightxin.feature.water.data.WaterLocalStore
import com.lightxin.feature.water.data.bt.NearbyDevice
import com.lightxin.feature.water.data.bt.NearbyScanner
import com.lightxin.feature.water.data.bt.QzxyFrame
import com.lightxin.feature.water.data.bt.toHexLower
import com.lightxin.feature.water.domain.ActiveOrder
import com.lightxin.feature.water.domain.RecentDevice
import com.lightxin.feature.water.domain.SettleResult
import com.lightxin.feature.water.domain.StartOutcome
import com.lightxin.feature.water.domain.WaterDevice
import com.lightxin.feature.water.domain.WaterOrderRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** 一次性事件：snackbar 文案与「回登录页」。 */
sealed interface WaterEvent {
    /** 无会话或会话失效：回登录页。 */
    data object RequireLogin : WaterEvent

    /** 登录成功：进首页。 */
    data object LoginSuccess : WaterEvent

    data class Message(val text: String) : WaterEvent
}

// ─── 登录 ───

enum class LoginMode { Password, Sms }

data class LoginUiState(
    val phone: String = "",
    val password: String = "",
    val smsCode: String = "",
    val mode: LoginMode = LoginMode.Password,
    val countdown: Int = 0,
    val sending: Boolean = false,
    val loading: Boolean = false,
    val phoneError: String? = null,
) {
    val phoneValid: Boolean get() = phone.length == 11 && phone.startsWith("1")

    val canSubmit: Boolean
        get() = phoneValid && when (mode) {
            LoginMode.Password -> password.isNotEmpty()
            LoginMode.Sms -> smsCode.length in 4..6
        }
}

@HiltViewModel
class QzxyLoginViewModel @Inject constructor(
    private val repository: QzxyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<WaterEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var countdownJob: Job? = null

    fun onPhoneChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(11)
        _uiState.update { it.copy(phone = digits, phoneError = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun onSmsCodeChange(value: String) {
        _uiState.update { it.copy(smsCode = value.filter { c -> c.isDigit() }.take(6)) }
    }

    fun setMode(mode: LoginMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    /** 失焦且非空且无效时才提示，避免用户刚输第一位就报错。 */
    fun validatePhone() {
        _uiState.update {
            it.copy(phoneError = if (it.phone.isNotEmpty() && !it.phoneValid) "手机号格式不对" else null)
        }
    }

    fun sendSmsCode() {
        val state = _uiState.value
        if (!state.phoneValid || state.countdown > 0 || state.sending) return
        _uiState.update { it.copy(sending = true) }
        viewModelScope.launch {
            repository.requestSmsCode(state.phone)
                .onSuccess { startCountdown() }
                .onFailure { _events.send(WaterEvent.Message(it.message.orEmpty())) }
            _uiState.update { it.copy(sending = false) }
        }
    }

    fun login() {
        val state = _uiState.value
        if (!state.canSubmit || state.loading) return
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = when (state.mode) {
                LoginMode.Password -> repository.loginByPassword(state.phone, state.password)
                LoginMode.Sms -> repository.loginBySmsCode(state.phone, state.smsCode)
            }
            _uiState.update { it.copy(loading = false) }
            if (result.isSuccess) {
                _events.send(WaterEvent.LoginSuccess)
            } else {
                result.onFailure { _events.send(WaterEvent.Message(it.message.orEmpty())) }
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (second in 60 downTo 1) {
                _uiState.update { it.copy(countdown = second) }
                delay(1_000)
            }
            _uiState.update { it.copy(countdown = 0) }
        }
    }
}

// ─── 首页 ───

data class WaterHomeUiState(
    val balance: String? = null,
    val balanceLoading: Boolean = false,
    val recentDevices: List<RecentDevice> = emptyList(),
    /** 本地「最近使用」读出来之前不显示空状态，避免进页时闪一下「还没有使用过的设备」。 */
    val localLoaded: Boolean = false,
    val activeOrder: ActiveOrder? = null,
    // 仅下拉刷新时为 true；首次加载只显示骨架，不出刷新圈
    val isRefreshing: Boolean = false,
    val nearby: List<NearbyEntry> = emptyList(),
    val nearbyScan: NearbyScan = NearbyScan.Idle,
)

enum class NearbyScan { Idle, Scanning, Done }

/** 搜到且查到设备信息的附近设备，按信号强弱排序。 */
data class NearbyEntry(val device: RecentDevice, val rssi: Int?)

@HiltViewModel
class WaterHomeViewModel @Inject constructor(
    private val repository: QzxyRepository,
    private val store: WaterLocalStore,
    private val scanner: NearbyScanner,
) : ViewModel() {

    private var scanJob: Job? = null

    private val _uiState = MutableStateFlow(WaterHomeUiState())
    val uiState: StateFlow<WaterHomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<WaterEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (repository.session() == null) {
                _events.send(WaterEvent.RequireLogin)
                return@launch
            }
            observeLocal()
            loadBalance()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadBalance()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /** 搜索 [SCAN_MS] 毫秒；每个新 MAC 查一次设备信息，查不到的不展示。 */
    fun scanNearby() {
        if (scanJob?.isActive == true) return
        _uiState.update { it.copy(nearby = emptyList(), nearbyScan = NearbyScan.Scanning) }
        scanJob = viewModelScope.launch {
            val seen = mutableSetOf<String>()
            val lookups = mutableListOf<Job>()
            try {
                withTimeoutOrNull(SCAN_MS) {
                    scanner.scan().collect { found ->
                        if (seen.add(found.mac)) {
                            // 挂在外层协程上，扫描超时不打断进行中的查询
                            lookups += this@launch.launch { resolveNearby(found) }
                        } else {
                            updateRssi(found)
                        }
                    }
                }
                lookups.joinAll()
            } catch (e: QzxyException) {
                _events.send(WaterEvent.Message(e.message.orEmpty()))
            } finally {
                _uiState.update { it.copy(nearbyScan = NearbyScan.Done) }
            }
        }
    }

    /** 连接设备前停止搜索：经典蓝牙查询会拖慢连接。 */
    fun stopScan() {
        scanJob?.cancel()
    }

    private suspend fun resolveNearby(found: NearbyDevice) {
        val device = repository.deviceByMac(found.mac).getOrNull() ?: return
        val entry = NearbyEntry(
            device = RecentDevice(
                mac = device.macWithColon,
                deviceName = device.deviceName,
                bigTypeId = device.bigTypeId,
                roomName = device.roomName,
                areaName = device.areaName,
                buildingName = device.buildingName,
                floorName = device.floorName,
            ),
            rssi = found.rssi,
        )
        _uiState.update { state -> state.copy(nearby = (state.nearby + entry).sortedByRssi()) }
    }

    private fun updateRssi(found: NearbyDevice) {
        val rssi = found.rssi ?: return
        _uiState.update { state ->
            state.copy(
                nearby = state.nearby
                    .map { if (it.device.mac == found.mac) it.copy(rssi = rssi) else it }
                    .sortedByRssi(),
            )
        }
    }

    private fun List<NearbyEntry>.sortedByRssi() = sortedByDescending { it.rssi ?: Int.MIN_VALUE }

    fun logout() {
        viewModelScope.launch {
            repository.clearSession()
            _events.send(WaterEvent.RequireLogin)
        }
    }

    private fun observeLocal() {
        viewModelScope.launch {
            store.activeOrder.collect { order -> _uiState.update { it.copy(activeOrder = order) } }
        }
        viewModelScope.launch {
            store.recentDevices.collect { devices -> _uiState.update { it.copy(recentDevices = devices, localLoaded = true) } }
        }
    }

    private suspend fun loadBalance() {
        _uiState.update { it.copy(balanceLoading = true) }
        repository.wallet()
            .onSuccess { money -> _uiState.update { it.copy(balance = money) } }
            .onFailure { error ->
                if (error is QzxySessionExpired) {
                    _events.send(WaterEvent.RequireLogin)
                } else {
                    _events.send(WaterEvent.Message(error.message.orEmpty()))
                }
            }
        _uiState.update { it.copy(balanceLoading = false) }
    }

    private companion object {
        const val SCAN_MS = 8_000L
    }
}

// ─── 设备页 ───

sealed interface DevicePhase {
    data object Loading : DevicePhase

    data class LoadFailed(val message: String) : DevicePhase

    /** 「暂不支持此设备」/「设备已停用」。 */
    data class Unsupported(val title: String) : DevicePhase

    data object Idle : DevicePhase

    data object Connecting : DevicePhase

    data object Starting : DevicePhase

    data object Running : DevicePhase

    data object Settling : DevicePhase

    data object NeedSettle : DevicePhase

    data object BusyByOther : DevicePhase
}

data class WaterDeviceUiState(
    val device: WaterDevice? = null,
    val balance: String? = null,
    val phase: DevicePhase = DevicePhase.Loading,
    val result: SettleResult? = null,
    val runningSince: Long? = null,
    /** 「暂不支持」或开阀/结算失败时的可复制诊断文本。 */
    val diagnostic: String? = null,
)

@HiltViewModel
class WaterDeviceViewModel @Inject constructor(
    private val repository: QzxyRepository,
    private val store: WaterLocalStore,
    private val runner: WaterOrderRunner,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mac: String = Uri.decode(savedStateHandle.get<String>("mac").orEmpty())

    private val _uiState = MutableStateFlow(WaterDeviceUiState())
    val uiState: StateFlow<WaterDeviceUiState> = _uiState.asStateFlow()

    private val _events = Channel<WaterEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(phase = DevicePhase.Loading) }
        viewModelScope.launch {
            if (repository.session() == null) {
                _events.send(WaterEvent.RequireLogin)
                return@launch
            }
            val device = repository.deviceByMac(mac).getOrElse { error ->
                if (error is QzxySessionExpired) {
                    _events.send(WaterEvent.RequireLogin)
                } else {
                    _uiState.update { it.copy(phase = DevicePhase.LoadFailed(error.message.orEmpty())) }
                }
                return@launch
            }
            val balance = repository.wallet().getOrNull()
            val active = store.activeOrder.first()
            val phase = when {
                !device.isSupported -> DevicePhase.Unsupported("暂不支持此设备")
                !device.isEnabled -> DevicePhase.Unsupported("设备已停用")
                active?.mac == mac -> if (active?.opened == true) DevicePhase.Running else DevicePhase.NeedSettle
                else -> DevicePhase.Idle
            }
            _uiState.update {
                it.copy(
                    device = device,
                    balance = balance ?: it.balance,
                    phase = phase,
                    runningSince = if (active?.mac == mac) active?.startedAt else null,
                    diagnostic = if (phase is DevicePhase.Unsupported) device.diagnostic else null,
                )
            }
        }
    }

    fun start() {
        val device = _uiState.value.device ?: return
        if (_uiState.value.phase != DevicePhase.Idle) return
        _uiState.update { it.copy(phase = DevicePhase.Connecting, diagnostic = null) }
        viewModelScope.launch {
            runner.start(device) { _uiState.update { it.copy(phase = DevicePhase.Starting) } }
                .onSuccess { outcome ->
                    when (outcome) {
                        StartOutcome.Running -> {
                            val since = store.activeOrder.first()?.startedAt ?: System.currentTimeMillis()
                            _uiState.update { it.copy(phase = DevicePhase.Running, runningSince = since) }
                        }

                        StartOutcome.NeedSettle -> _uiState.update { it.copy(phase = DevicePhase.NeedSettle) }
                    }
                }
                .onFailure { error -> fail(error) }
        }
    }

    fun settle() {
        val device = _uiState.value.device ?: return
        val phase = _uiState.value.phase
        if (phase != DevicePhase.NeedSettle && phase != DevicePhase.Idle && phase != DevicePhase.Running) return
        _uiState.update { it.copy(phase = DevicePhase.Settling, diagnostic = null) }
        viewModelScope.launch {
            runner.settle(device)
                .onSuccess { result ->
                    val balance = repository.wallet().getOrNull()
                    _uiState.update {
                        it.copy(
                            phase = DevicePhase.Idle,
                            result = result,
                            balance = balance ?: it.balance,
                            runningSince = null,
                        )
                    }
                }
                .onFailure { error -> fail(error) }
        }
    }

    fun dismissResult() {
        _uiState.update { it.copy(result = null) }
    }

    override fun onCleared() {
        runner.close()
        super.onCleared()
    }

    private suspend fun fail(error: Throwable) {
        if (error is QzxySessionExpired) {
            _events.send(WaterEvent.RequireLogin)
            return
        }
        _events.send(WaterEvent.Message(error.message.orEmpty()))
        var active = store.activeOrder.first()
        // 结算失败后阀门状态未知，按「未结算」处理，重进页面不再显示使用中
        if (active != null && active.mac == mac && active.opened) {
            active = active.copy(opened = false)
            store.setActiveOrder(active)
        }
        val next = when {
            active != null -> DevicePhase.NeedSettle
            error.message == "设备使用中" -> DevicePhase.BusyByOther
            else -> DevicePhase.Idle
        }
        _uiState.update {
            it.copy(
                phase = next,
                runningSince = if (next == DevicePhase.NeedSettle) active?.startedAt else null,
                diagnostic = it.device?.let { device -> failureDiagnostic(device, error, runner.lastStatusPayload) },
            )
        }
    }
}

/** 设备字段 + 失败原因 + 最近一次 `0x23` 原始载荷及其解析结果。 */
internal fun failureDiagnostic(device: WaterDevice, error: Throwable, statusPayload: ByteArray?): String =
    buildString {
        append(device.diagnostic)
        append("\nerror=").append(error.message.orEmpty())
        generateSequence(error.cause) { it.cause }.forEach { append(" <- ").append(it.message.orEmpty()) }
        if (statusPayload != null) {
            append("\n0x23 len=${statusPayload.size} hex=${statusPayload.toHexLower()}")
            runCatching { QzxyFrame.parseStatus(statusPayload) }.getOrNull()?.let { s ->
                append("\nstate=${s.state} protocolType=${s.protocolType.ifEmpty { "空" }}")
                append(" randomNumber=${s.randomNumber} macType=${s.macType}")
            }
        }
    }
