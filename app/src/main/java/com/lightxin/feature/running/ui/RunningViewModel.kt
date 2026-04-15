package com.lightxin.feature.running.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.running.data.RunningRepository
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
    val simDistance: String = "3.20",
    val simDurationMinutes: String = "24",
    val simStartTimeMillis: Long = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(24),
    val isSubmittingSimulation: Boolean = false,
    val isUploadingRun: Boolean = false,
    val lastResult: RunningResult? = null,
    val shouldNavigateToResult: Boolean = false,
)

@HiltViewModel
class RunningViewModel @Inject constructor(
    private val repository: RunningRepository,
    private val tracker: RunningTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunningUiState())
    val uiState: StateFlow<RunningUiState> = _uiState

    init {
        observeTracker()
        refreshDashboard()
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDashboardLoading = true, dashboardError = null) }
            repository.getDashboard().fold(
                onSuccess = { dashboard ->
                    _uiState.update {
                        it.copy(
                            dashboard = dashboard,
                            isDashboardLoading = false,
                            dashboardError = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDashboardLoading = false,
                            dashboardError = error.message ?: "加载跑步首页失败",
                        )
                    }
                },
            )
        }
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
        _uiState.update { it.copy(simDistance = value.filterAllowedDecimal()) }
    }

    fun updateSimDurationMinutes(value: String) {
        _uiState.update { it.copy(simDurationMinutes = value.filter(Char::isDigit)) }
    }

    fun updateSimStartTime(timeMillis: Long) {
        _uiState.update { it.copy(simStartTimeMillis = timeMillis) }
    }

    suspend fun submitSimulation() {
        if (_uiState.value.isSubmittingSimulation) return
        val config = validateSimulation() ?: return
        val simulatedPointCount = TrajectoryGenerator.generate(config).size

        _uiState.update {
            it.copy(
                isSubmittingSimulation = true,
                startError = null,
                lastResult = null,
                shouldNavigateToResult = false,
            )
        }

        repository.submitSimulation(config).fold(
            onSuccess = { result ->
                _uiState.update {
                    it.copy(
                        isSubmittingSimulation = false,
                        lastResult = result,
                        shouldNavigateToResult = true,
                    )
                }
                refreshDashboard()
            },
            onFailure = { error ->
                val failureResult = buildFailedResult(
                    message = error.message ?: "模拟提交失败",
                    config = config,
                    pointCount = simulatedPointCount,
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

    private fun observeTracker() {
        viewModelScope.launch {
            tracker.state.collectLatest { trackerState ->
                _uiState.update { it.copy(trackerState = trackerState) }
            }
        }
    }

    private suspend fun uploadSnapshot(snapshot: RunningSnapshot) {
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
                refreshDashboard()
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
        val distanceKm = _uiState.value.simDistance.toDoubleOrNull()
        val durationMinutes = _uiState.value.simDurationMinutes.toIntOrNull()
        val startTimeMillis = _uiState.value.simStartTimeMillis

        if (distanceKm == null || distanceKm <= 0.0) {
            _uiState.update { it.copy(startError = "请输入有效距离") }
            return null
        }
        if (durationMinutes == null || durationMinutes <= 0) {
            _uiState.update { it.copy(startError = "请输入有效时长") }
            return null
        }
        if (startTimeMillis >= System.currentTimeMillis()) {
            _uiState.update { it.copy(startError = "开始时间需要早于当前时间") }
            return null
        }

        val speedKmh = distanceKm / durationMinutes * 60.0
        if (speedKmh !in 6.0..15.0) {
            _uiState.update { it.copy(startError = "模拟速度需要在 6-15 km/h 之间") }
            return null
        }

        _uiState.update { it.copy(startError = null) }
        return SimConfig(distanceKm = distanceKm, durationMinutes = durationMinutes, startTimeMillis = startTimeMillis)
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
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    }
}
