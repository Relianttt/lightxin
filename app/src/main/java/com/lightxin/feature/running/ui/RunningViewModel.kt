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
import com.lightxin.feature.running.domain.RouteReplayPlanner
import com.lightxin.feature.running.domain.RouteReplayPreparation
import com.lightxin.feature.running.domain.TrackPoint
import com.lightxin.feature.running.service.RunningTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong
import javax.inject.Inject

data class RunningUiState(
    val dashboard: RunningDashboard? = null,
    val isDashboardLoading: Boolean = true,
    val dashboardError: String? = null,
    val trackerState: RunningTrackerState = RunningTrackerState(),
    val isStarting: Boolean = false,
    val startError: String? = null,
    val simError: String? = null,
    val simulationWarning: String? = null,
    val simDistance: String = "3.20",
    /** 允许小数分钟（如 24.5 = 24 分 30 秒），这样不必为了几秒去加一个输入框。 */
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
    /** 只有向服务端申请过 exerciseId 的会话才是真实跑步：模板录制同样让 isSessionActive 为 true。 */
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
     * 为 0 表示服务端建了记录但没计里程（例如已达每日上限），无法判定时为 null。
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
        val filtered = value.filterAllowedDecimal()
        val minutes = filtered.toDoubleOrNull()
        _uiState.update { state ->
            val next = state.copy(
                simDurationMinutes = filtered,
                simStartTimeMillis = if (minutes != null && minutes > 0.0) {
                    System.currentTimeMillis() - (minutes * 60_000.0).toLong()
                } else {
                    state.simStartTimeMillis
                },
                // 只有整数分钟且正好等于预设时才高亮，小数分钟不套预设
                selectedSimDurationPresetMinutes = minutes
                    ?.takeIf { it > 0.0 && it == kotlin.math.floor(it) }
                    ?.toInt()
                    ?.takeIf { it in SIM_DURATION_PRESETS },
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

    private data class SimulationDraft(val config: SimConfig, val template: RouteTemplate?)
    private var pendingSimulation: SimulationDraft? = null

    suspend fun submitSimulation() {
        if (_uiState.value.isSubmittingSimulation || pendingSimulation != null) return
        val config = validateSimulation() ?: return
        prepareSimulation(SimulationDraft(config, _uiState.value.defaultTemplate), qualityConfirmed = false)
    }

    fun dismissSimulationWarning() {
        pendingSimulation = null
        _uiState.update { it.copy(simulationWarning = null) }
    }

    suspend fun confirmSimulationWarning() {
        val draft = pendingSimulation ?: return
        dismissSimulationWarning()
        if (draft.template != _uiState.value.defaultTemplate) {
            _uiState.update { it.copy(simError = "默认模板已变化，请重新生成轨迹") }
            return
        }
        prepareSimulation(draft, qualityConfirmed = true)
    }

    private suspend fun prepareSimulation(draft: SimulationDraft, qualityConfirmed: Boolean) {
        if (_uiState.value.isSubmittingSimulation) return
        _uiState.update { it.copy(isSubmittingSimulation = true, simError = null, lastResult = null, shouldNavigateToResult = false) }
        try {
            when (val prepared = withContext(Dispatchers.Default) {
                RouteReplayPlanner.prepare(draft.template, draft.config, qualityConfirmed)
            }) {
                is RouteReplayPreparation.Invalid -> _uiState.update { it.copy(simError = prepared.message) }
                is RouteReplayPreparation.NeedsConfirmation -> {
                    pendingSimulation = draft
                    _uiState.update { it.copy(simulationWarning = prepared.message) }
                }
                is RouteReplayPreparation.Ready -> uploadSimulation(draft.config, draft.template!!, prepared.points)
            }
        } finally {
            _uiState.update { it.copy(isSubmittingSimulation = false) }
        }
    }

    private suspend fun uploadSimulation(config: SimConfig, template: RouteTemplate, templatePoints: List<TrackPoint>) {
        val trajectorySize = templatePoints.size
        val beforeTodayKm = _uiState.value.dashboard?.todayKm
        val submitCall = repository.submitSimulation(config, templatePoints, overrideIsBd09 = false)

        submitCall.fold(
            onSuccess = { result ->
                templateStore.markUsed(template.id)
                _uiState.update {
                    it.copy(
                        isSubmittingSimulation = false,
                        lastResult = result,
                        shouldNavigateToResult = true,
                    )
                }
                finalizeResult(result, beforeTodayKm)
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

    /**
     * 回读服务端对本次上传的判定，回填到结果页。
     *
     * 必须挂 `viewModelScope`：上传由界面侧 `rememberCoroutineScope` 调起，
     * 一跳转结果页那个作用域就取消，挂在上面的回读会被 Canceled 掉（历史上就这么静默丢过数据）。
     * 回读失败一律静默降级 —— 不影响上传结果本身。
     */
    private fun finalizeResult(result: RunningResult, beforeTodayKm: Double?) {
        val checkId = result.uploadId.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            val verdict = repository.getRunRecord(checkId).getOrNull()
            val creditedKm = refreshAndComputeCreditedKm(beforeTodayKm)
            if (verdict == null && creditedKm == null) return@launch
            _uiState.update { state ->
                val current = state.lastResult?.takeIf { it.uploadId == checkId } ?: return@update state
                state.copy(
                    lastResult = current.copy(
                        verdict = verdict?.summary().orEmpty(),
                        creditedKm = creditedKm,
                    ),
                )
            }
        }
    }

    fun clearStartError() {
        _uiState.update { it.copy(startError = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(lastResult = null, shouldNavigateToResult = false) }
    }

    fun simulationSpeedKmh(): Double? {
        val distance = _uiState.value.simDistance.toDoubleOrNull() ?: return null
        val durationMinutes = _uiState.value.simDurationMinutes.toDoubleOrNull() ?: return null
        if (distance <= 0.0 || durationMinutes <= 0.0) return null
        return distance / durationMinutes * 60.0
    }

    private fun observeTracker() {
        viewModelScope.launch {
            tracker.state.collectLatest { trackerState ->
                _uiState.update { it.copy(trackerState = trackerState) }
            }
        }
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
                finalizeResult(result, beforeTodayKm)
            },
            onFailure = { error ->
                tracker.completeSession()
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
        val durationMinutes = uiState.simDurationMinutes.toDoubleOrNull()
        val startTimeMillis = uiState.simStartTimeMillis

        if (distanceKm == null || distanceKm <= 0.0) {
            _uiState.update { it.copy(simError = "请输入有效距离") }
            return null
        }
        if (durationMinutes == null || durationMinutes <= 0.0) {
            _uiState.update { it.copy(simError = "请输入有效时长") }
            return null
        }
        if (startTimeMillis >= System.currentTimeMillis()) {
            _uiState.update { it.copy(simError = "开始时间需要早于当前时间") }
            return null
        }

        val speedKmh = distanceKm / durationMinutes * 60.0
        if (speedKmh !in SIM_MIN_SPEED_KMH..SIM_MAX_SPEED_KMH) {
            _uiState.update { it.copy(simError = speedRangeErrorMessage()) }
            return null
        }

        // 分钟带小数，换算成秒后四舍五入；至少 1 秒，避免 0 时长
        val durationSeconds = (durationMinutes * 60.0).roundToLong().coerceAtLeast(1L)
        _uiState.update { it.copy(simError = null) }
        return SimConfig(distanceKm = distanceKm, durationSeconds = durationSeconds, startTimeMillis = startTimeMillis)
    }

    private fun RunningUiState.withRealtimeSimulationError(): RunningUiState {
        return copy(simError = realtimeSimulationError())
    }

    private fun RunningUiState.realtimeSimulationError(): String? {
        val distanceKm = simDistance.toDoubleOrNull()
        val durationMinutes = simDurationMinutes.toDoubleOrNull()
        if (distanceKm == null || distanceKm <= 0.0 || durationMinutes == null || durationMinutes <= 0.0) {
            return null
        }
        if (simStartTimeMillis >= System.currentTimeMillis()) {
            return "开始时间需要早于当前时间"
        }
        val speedKmh = distanceKm / durationMinutes * 60.0
        return if (speedKmh in SIM_MIN_SPEED_KMH..SIM_MAX_SPEED_KMH) null else speedRangeErrorMessage()
    }

    /** 服务端 extraDetailInfo.do 实测下发 minSpeed=2 / maxSpeed=15；这里硬编码对齐，避免自己比服务端更严。 */
    private fun speedRangeErrorMessage(): String =
        "模拟速度需要在 ${SIM_MIN_SPEED_KMH.toInt()}-${SIM_MAX_SPEED_KMH.toInt()} km/h 之间"

    private fun buildFailedResult(
        message: String,
        config: SimConfig,
        pointCount: Int = 0,
    ): RunningResult =
        buildFailedResult(
            message = message,
            distanceKm = config.distanceKm,
            durationSeconds = config.durationSeconds,
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
        private const val SIM_MIN_SPEED_KMH = 2.0
        private const val SIM_MAX_SPEED_KMH = 15.0
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    }
}
