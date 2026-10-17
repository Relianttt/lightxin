package com.lightxin.feature.running.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.settings.DeveloperPrefs
import com.lightxin.feature.running.data.RouteTemplateStore
import com.lightxin.feature.running.data.RunningRepository
import com.lightxin.feature.running.domain.RouteTemplate
import com.lightxin.feature.running.domain.RunningDashboard
import com.lightxin.feature.running.domain.RunningResult
import com.lightxin.feature.running.domain.RunningSnapshot
import com.lightxin.feature.running.domain.RunningStartInfo
import com.lightxin.feature.running.domain.RunningTrackerState
import com.lightxin.feature.running.domain.SimConfig
import com.lightxin.feature.running.domain.TrajectoryGenerator
import com.lightxin.feature.running.service.RunningTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class RunningUiState(
    val dashboard: RunningDashboard? = null,
    val isDashboardLoading: Boolean = true,
    val dashboardError: String? = null,
    val trackerState: RunningTrackerState = RunningTrackerState(),
    val isStarting: Boolean = false,
    val startError: String? = null,
    val simError: String? = null,
    val simDistance: String = "3.20",
    val simDurationMinutes: String = "24",
    val simStartTimeMillis: Long = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(24),
    val selectedSimDurationPresetMinutes: Int? = null,
    val isSubmittingSimulation: Boolean = false,
    val isUploadingRun: Boolean = false,
    val lastResult: RunningResult? = null,
    val shouldNavigateToResult: Boolean = false,
    val defaultTemplate: RouteTemplate? = null,
    val templateCount: Int = 0,
    val advancedEnabled: Boolean = false,
) {
    /** 只有申请过 exerciseId 的会话才是真实跑步；模板录制同样让 isSessionActive 为 true。 */
    val isRunSessionActive: Boolean
        get() = trackerState.isSessionActive && trackerState.startInfo != null

    val isTemplateRecordingActive: Boolean
        get() = trackerState.isSessionActive && trackerState.startInfo == null
}

@HiltViewModel
class RunningViewModel @Inject constructor(
    private val repository: RunningRepository,
    private val tracker: RunningTracker,
    private val templateStore: RouteTemplateStore,
    private val developerPrefs: DeveloperPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunningUiState())
    val uiState: StateFlow<RunningUiState> = _uiState

    init {
        observeTracker()
        observeTemplates()
        observeDeveloperPrefs()
        refreshDashboard()
    }

    private fun observeDeveloperPrefs() {
        viewModelScope.launch {
            developerPrefs.isAdvancedEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(advancedEnabled = enabled) }
            }
        }
    }

    private fun observeTemplates() {
        viewModelScope.launch {
            templateStore.templates.collectLatest { list ->
                _uiState.update {
                    it.copy(
                        defaultTemplate = list.firstOrNull { t -> t.isDefault },
                        templateCount = list.size,
                    )
                }
            }
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch { applyDashboard() }
    }

    private suspend fun applyDashboard(): RunningDashboard? {
        _uiState.update { it.copy(isDashboardLoading = true, dashboardError = null) }
        return repository.getDashboard().fold(
            onSuccess = { dashboard ->
                _uiState.update {
                    it.copy(
                        dashboard = dashboard,
                        isDashboardLoading = false,
                        dashboardError = null,
                    )
                }
                dashboard
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isDashboardLoading = false,
                        dashboardError = error.message ?: "加载跑步首页失败",
                    )
                }
                null
            },
        )
    }

    /**
     * 刷新首页并算出本次上传真正计入的里程增量。
     * 为 0 表示服务端建了记录但没计里程（例如已达每日上限）；无法判定时为 null。
     */
    private suspend fun refreshAndComputeCreditedKm(beforeTodayKm: Double?): Double? {
        val afterTodayKm = applyDashboard()?.todayKm
        if (beforeTodayKm == null || afterTodayKm == null) return null
        return (afterTodayKm - beforeTodayKm).coerceAtLeast(0.0)
    }

    suspend fun startRealRun(): Result<RunningStartInfo> {
        if (_uiState.value.isStarting) {
            return Result.failure(IllegalStateException("正在启动中"))
        }

        _uiState.update {
            it.copy(
                isStarting = true,
                startError = null,
                lastResult = null,
                shouldNavigateToResult = false,
            )
        }

        return repository.startRunning().fold(
            onSuccess = { startInfo ->
                tracker.beginSession(startInfo)
                _uiState.update { it.copy(isStarting = false) }
                Result.success(startInfo)
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isStarting = false,
                        startError = error.message ?: "开始跑步失败",
                    )
                }
                Result.failure(error)
            },
        )
    }

    suspend fun finishRealRun() {
        if (_uiState.value.isUploadingRun) return
        val snapshot = tracker.snapshotForUpload()
        if (snapshot == null) {
            _uiState.update { it.copy(startError = "当前没有可上传的跑步记录") }
            return
        }

        tracker.stopCollecting()
        uploadSnapshot(snapshot)
    }

    fun abandonRealRun() {
        tracker.cancelSession()
    }

    fun updateSimDistance(value: String) {
        _uiState.update { state ->
            val next = state.copy(simDistance = value.filterAllowedDecimal())
            next.withRealtimeSimulationError()
        }
    }

    fun updateSimDurationMinutes(value: String) {
        val filtered = value.filter(Char::isDigit)
        val minutes = filtered.toIntOrNull()
        _uiState.update { state ->
            val next = state.copy(
                simDurationMinutes = filtered,
                simStartTimeMillis = if (minutes != null && minutes > 0) {
                    System.currentTimeMillis() - minutes * 60_000L
                } else {
                    state.simStartTimeMillis
                },
                selectedSimDurationPresetMinutes = minutes?.takeIf { it in SIM_DURATION_PRESETS },
            )
            next.withRealtimeSimulationError()
        }
    }

    fun selectSimDurationPreset(minutes: Int) {
        _uiState.update { state ->
            val next = state.copy(
                simDurationMinutes = minutes.toString(),
                simStartTimeMillis = System.currentTimeMillis() - minutes * 60_000L,
                selectedSimDurationPresetMinutes = minutes,
            )
            next.withRealtimeSimulationError()
        }
    }

    fun updateSimStartTime(timeMillis: Long) {
        _uiState.update { state ->
            state.copy(
                simStartTimeMillis = timeMillis,
                selectedSimDurationPresetMinutes = null,
            ).withRealtimeSimulationError()
        }
    }

    suspend fun submitSimulation() {
        if (_uiState.value.isSubmittingSimulation) return
        val config = validateSimulation() ?: return
        val template = _uiState.value.defaultTemplate
        val templateTrajectory = template
            ?.let { TrajectoryGenerator.generateFromTemplate(config, it) }
            ?.takeIf { it.points.size >= 2 }
        val usedTemplateId = templateTrajectory?.let { template?.id }
        val templateIgnored = template != null && templateTrajectory == null
        val trajectorySize = templateTrajectory?.points?.size
            ?: TrajectoryGenerator.generate(config).points.size

        _uiState.update {
            it.copy(
                isSubmittingSimulation = true,
                simError = null,
                lastResult = null,
                shouldNavigateToResult = false,
            )
        }

        val beforeTodayKm = _uiState.value.dashboard?.todayKm

        repository.submitSimulation(config, templateTrajectory).fold(
            onSuccess = { result ->
                usedTemplateId?.let { templateStore.markUsed(it) }
                // 模板采样为空时仓库会退回内置路线；不说明的话，用户以为传的是自己录的线
                val effective = if (templateIgnored) {
                    result.copy(message = result.message + "（模板未生成有效轨迹，本次改用内置路线）")
                } else {
                    result
                }
                _uiState.update {
                    it.copy(
                        isSubmittingSimulation = false,
                        lastResult = effective,
                        shouldNavigateToResult = true,
                    )
                }
                // 同上：跳转后调用方的 scope 会被取消，收尾走 viewModelScope
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(lastResult = buildFinalResult(effective, beforeTodayKm))
                    }
                }
            },
            onFailure = { error ->
                val failureResult = buildFailedResult(
                    message = error.message ?: "模拟提交失败",
                    config = config,
                    pointCount = trajectorySize,
                )
                _uiState.update {
                    it.copy(
                        isSubmittingSimulation = false,
                        lastResult = failureResult,
                        shouldNavigateToResult = true,
                    )
                }
            },
        )
    }

    fun consumeResultNavigation() {
        _uiState.update { it.copy(shouldNavigateToResult = false) }
    }

    fun clearStartError() {
        _uiState.update { it.copy(startError = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(lastResult = null, shouldNavigateToResult = false) }
    }

    fun simulationSpeedKmh(): Double? {
        val distance = _uiState.value.simDistance.toDoubleOrNull() ?: return null
        val durationMinutes = _uiState.value.simDurationMinutes.toIntOrNull() ?: return null
        if (distance <= 0.0 || durationMinutes <= 0) return null
        return distance / durationMinutes * 60.0
    }

    /** 有效速度区间以服务端下发为准（extraDetailInfo 的 minSpeed/maxSpeed），未下发时用兜底值。 */
    fun simulationSpeedWindow(): Pair<Double, Double> {
        val dashboard = _uiState.value.dashboard
        return (dashboard?.minSpeedKmh ?: FALLBACK_MIN_SPEED_KMH) to
            (dashboard?.maxSpeedKmh ?: FALLBACK_MAX_SPEED_KMH)
    }

    fun isSimulationSpeedValid(): Boolean {
        val speedKmh = simulationSpeedKmh() ?: return false
        val (minSpeed, maxSpeed) = simulationSpeedWindow()
        return speedKmh in minSpeed..maxSpeed
    }

    /** 供 UI 展示的有效区间文案，如 "2-15 km/h"。 */
    fun simulationSpeedRangeLabel(): String {
        val (minSpeed, maxSpeed) = simulationSpeedWindow()
        return "${minSpeed.formatSpeedLimit()}-${maxSpeed.formatSpeedLimit()} km/h"
    }

    private fun speedRangeErrorMessage(): String = "模拟速度需要在 ${simulationSpeedRangeLabel()} 之间"

    private fun observeTracker() {
        viewModelScope.launch {
            tracker.state.collectLatest { trackerState ->
                _uiState.update { it.copy(trackerState = trackerState) }
            }
        }
    }

    /**
     * 上传成功后回读服务端：本次真计入了多少里程、判有效还是无效、轨迹存下几个点。
     * `result=OK` 只代表记录建了，这三件事它都不保证。
     */
    private suspend fun buildFinalResult(base: RunningResult, beforeTodayKm: Double?): RunningResult {
        val creditedKm = refreshAndComputeCreditedKm(beforeTodayKm)
        val verdict = base.uploadId.takeIf { it.isNotBlank() }
            ?.let { repository.getRunRecord(it).getOrNull() }
        val suffix = verdict?.let {
            "（服务端判定：${it.describe()}；轨迹已存：${if (it.hasStoredRoute) "是" else "否"}）"
        }.orEmpty()
        return base.copy(creditedKm = creditedKm, message = base.message + suffix)
    }

    private suspend fun uploadSnapshot(snapshot: RunningSnapshot) {
        val beforeTodayKm = _uiState.value.dashboard?.todayKm
        _uiState.update { it.copy(isUploadingRun = true, startError = null) }

        repository.uploadTrackedRun(snapshot).fold(
            onSuccess = { result ->
                tracker.completeSession()
                _uiState.update {
                    it.copy(
                        isUploadingRun = false,
                        lastResult = result,
                        shouldNavigateToResult = true,
                    )
                }
                // 跳转后调用方的 rememberCoroutineScope 会随界面销毁而取消，
                // 收尾必须挂到 viewModelScope，否则回读请求会被 Canceled
                viewModelScope.launch {
                    _uiState.update { it.copy(lastResult = buildFinalResult(result, beforeTodayKm)) }
                }
            },
            onFailure = { error ->
                // 不 clear session：轨迹与 exerciseId 要留着，回跑步页还能重试上传
                _uiState.update {
                    it.copy(
                        isUploadingRun = false,
                        lastResult = buildFailedResult(
                            message = error.message ?: "上传失败",
                            distanceKm = snapshot.distanceMeters / 1000.0,
                            durationSeconds = snapshot.durationSeconds,
                            startTimeMillis = snapshot.startTimeMillis,
                            pointCount = snapshot.points.size,
                        ),
                        shouldNavigateToResult = true,
                    )
                }
            },
        )
    }

    private fun validateSimulation(): SimConfig? {
        val uiState = _uiState.value
        val distanceKm = uiState.simDistance.toDoubleOrNull()
        val durationMinutes = uiState.simDurationMinutes.toIntOrNull()
        val startTimeMillis = uiState.simStartTimeMillis

        if (distanceKm == null || distanceKm <= 0.0) {
            _uiState.update { it.copy(simError = "请输入有效距离") }
            return null
        }
        if (durationMinutes == null || durationMinutes <= 0) {
            _uiState.update { it.copy(simError = "请输入有效时长") }
            return null
        }
        if (startTimeMillis >= System.currentTimeMillis()) {
            _uiState.update { it.copy(simError = "开始时间需要早于当前时间") }
            return null
        }

        val speedKmh = distanceKm / durationMinutes * 60.0
        val (minSpeed, maxSpeed) = simulationSpeedWindow()
        if (speedKmh !in minSpeed..maxSpeed) {
            _uiState.update { it.copy(simError = speedRangeErrorMessage()) }
            return null
        }

        _uiState.update { it.copy(simError = null) }
        return SimConfig(distanceKm = distanceKm, durationMinutes = durationMinutes, startTimeMillis = startTimeMillis)
    }

    private fun RunningUiState.withRealtimeSimulationError(): RunningUiState {
        return copy(simError = realtimeSimulationError())
    }

    private fun RunningUiState.realtimeSimulationError(): String? {
        val distanceKm = simDistance.toDoubleOrNull()
        val durationMinutes = simDurationMinutes.toIntOrNull()
        if (distanceKm == null || distanceKm <= 0.0 || durationMinutes == null || durationMinutes <= 0) {
            return null
        }
        if (simStartTimeMillis >= System.currentTimeMillis()) {
            return "开始时间需要早于当前时间"
        }
        val speedKmh = distanceKm / durationMinutes * 60.0
        val (minSpeed, maxSpeed) = simulationSpeedWindow()
        return if (speedKmh in minSpeed..maxSpeed) null else speedRangeErrorMessage()
    }

    private fun buildFailedResult(
        message: String,
        config: SimConfig,
        pointCount: Int = 0,
    ): RunningResult =
        buildFailedResult(
            message = message,
            distanceKm = config.distanceKm,
            durationSeconds = config.durationMinutes * 60L,
            startTimeMillis = config.startTimeMillis,
            pointCount = pointCount,
        )

    private fun buildFailedResult(
        message: String,
        distanceKm: Double,
        durationSeconds: Long,
        startTimeMillis: Long,
        pointCount: Int,
    ): RunningResult {
        val speedKmh = if (durationSeconds > 0) distanceKm / durationSeconds * 3600.0 else 0.0
        val startDate = Instant.ofEpochMilli(startTimeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(FORMATTER)
        val endDate = Instant.ofEpochMilli(startTimeMillis + durationSeconds * 1000L)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(FORMATTER)
        return RunningResult(
            success = false,
            message = message,
            startDate = startDate,
            endDate = endDate,
            distanceKm = distanceKm,
            durationSeconds = durationSeconds,
            speedKmh = speedKmh,
            pointCount = pointCount,
        )
    }

    private fun Double.formatSpeedLimit(): String =
        if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.1f", this)

    private fun String.filterAllowedDecimal(): String {
        val filtered = filter { it.isDigit() || it == '.' }
        val dotIndex = filtered.indexOf('.')
        return if (dotIndex == -1) {
            filtered
        } else {
            filtered.substring(0, dotIndex + 1) + filtered.substring(dotIndex + 1).replace(".", "")
        }
    }

    companion object {
        private val SIM_DURATION_PRESETS = setOf(20, 30, 45)
        private const val FALLBACK_MIN_SPEED_KMH = 2.0
        private const val FALLBACK_MAX_SPEED_KMH = 15.0
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    }
}
