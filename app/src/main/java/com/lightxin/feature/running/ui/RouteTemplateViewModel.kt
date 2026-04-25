package com.lightxin.feature.running.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.running.data.RouteTemplateStore
import com.lightxin.feature.running.domain.RouteQualityChecker
import com.lightxin.feature.running.domain.RouteTemplate
import com.lightxin.feature.running.domain.RouteTemplateRules
import com.lightxin.feature.running.domain.RunningTrackerState
import com.lightxin.feature.running.service.RunningTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RouteTemplateUiState(
    val templates: List<RouteTemplate> = emptyList(),
    val trackerState: RunningTrackerState = RunningTrackerState(),
    val isRecording: Boolean = false,
    val isRealRunActive: Boolean = false,
    val errorMessage: String? = null,
    val pendingSaveMessage: String? = null,
) {
    val defaultTemplate: RouteTemplate? get() = templates.firstOrNull { it.isDefault }
    val lastRecordedAtMillis: Long? get() = templates.maxOfOrNull { it.createdAtMillis }
}

@HiltViewModel
class RouteTemplateViewModel @Inject constructor(
    private val store: RouteTemplateStore,
    private val tracker: RunningTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteTemplateUiState())
    val uiState: StateFlow<RouteTemplateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(store.templates, tracker.state) { list, trackerState ->
                list to trackerState
            }.collect { (list, trackerState) ->
                val isRealRun = trackerState.isSessionActive && trackerState.startInfo != null
                val isRecording = trackerState.isSessionActive && trackerState.startInfo == null
                _uiState.update {
                    it.copy(
                        templates = list,
                        trackerState = trackerState,
                        isRealRunActive = isRealRun,
                        isRecording = isRecording,
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, pendingSaveMessage = null) }
    }

    fun setError(msg: String) {
        _uiState.update { it.copy(errorMessage = msg) }
    }

    fun beginRecording(): Boolean {
        val state = _uiState.value
        if (state.isRealRunActive) {
            _uiState.update { it.copy(errorMessage = "当前有跑步会话未结束，无法录制模板") }
            return false
        }
        if (state.isRecording) return true
        tracker.beginTemplateSession()
        return true
    }

    fun stopCollecting() {
        tracker.stopCollecting()
    }

    fun cancelRecording() {
        tracker.cancelSession()
    }

    suspend fun saveRecording(name: String): Boolean {
        val current = tracker.state.value
        val points = current.points
        val distance = current.totalDistanceMeters
        val reject = RouteTemplateRules.validate(points.size, distance)
        if (reject != null) {
            _uiState.update { it.copy(errorMessage = reject) }
            return false
        }
        val trimmedName = name.trim().ifBlank {
            "模板 ${_uiState.value.templates.size + 1}"
        }
        val duration = ((System.currentTimeMillis() - current.startTimeMillis) / 1000L).coerceAtLeast(1L)
        val quality = RouteQualityChecker.evaluate(points)
        store.save(
            name = trimmedName,
            points = points,
            totalDistanceMeters = distance,
            durationSeconds = duration,
            qualityStatus = quality.status,
            qualityMessage = quality.message,
        )
        tracker.cancelSession()
        _uiState.update { it.copy(pendingSaveMessage = "模板已保存") }
        return true
    }

    fun templateById(id: String): RouteTemplate? =
        _uiState.value.templates.firstOrNull { it.id == id }

    fun rename(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { store.rename(id, trimmed) }
    }

    fun delete(id: String) {
        viewModelScope.launch { store.delete(id) }
    }

    fun setDefault(id: String) {
        viewModelScope.launch { store.setDefault(id) }
    }
}
