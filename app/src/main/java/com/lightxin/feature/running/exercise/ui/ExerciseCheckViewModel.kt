package com.lightxin.feature.running.exercise.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.running.data.RunningRepository
import com.lightxin.feature.running.exercise.domain.ExerciseQrPayload
import com.lightxin.feature.running.exercise.domain.ExerciseCheckPoller
import com.lightxin.feature.running.exercise.domain.PollOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CheckPhase { POLLING, SUCCESS, TIMEOUT }

data class ExerciseCheckUiState(
    val qrContent: String = "",
    val phase: CheckPhase = CheckPhase.POLLING,
)

@HiltViewModel
class ExerciseCheckViewModel @Inject constructor(
    private val repository: RunningRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val autoId: String = savedStateHandle["autoId"] ?: ""
    private val memberId: String = savedStateHandle["memberId"] ?: ""

    private val _uiState = MutableStateFlow(ExerciseCheckUiState())
    val uiState: StateFlow<ExerciseCheckUiState> = _uiState

    private var sessionJob: Job? = null

    init {
        startSession()
    }

    /** 一轮打卡会话：取消上一轮轮询 → 生成新 timestamp + 二维码 → 轮询。首次进入与重试共用。 */
    private fun startSession() {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            // timestamp 本轮生成一次，二维码与轮询共用同一值（服务端匹配键）
            val timestamp = System.currentTimeMillis()
            val deadline = timestamp + TIMEOUT_MS
            val studentCode = repository.currentStudentCode()
            _uiState.update {
                it.copy(
                    phase = CheckPhase.POLLING,
                    qrContent = ExerciseQrPayload(
                        exerciseId = autoId,
                        memberId = memberId,
                        studentCode = studentCode,
                        timestamp = timestamp,
                    ).toJson(),
                )
            }
            poll(timestamp, deadline)
        }
    }

    private suspend fun poll(timestamp: Long, deadline: Long) {
        while (true) {
            val success = repository.pollQrcodeResult(timestamp).getOrDefault(false)
            when (ExerciseCheckPoller.decide(success, System.currentTimeMillis(), deadline)) {
                PollOutcome.SUCCESS -> {
                    _uiState.update { it.copy(phase = CheckPhase.SUCCESS) }
                    return
                }
                PollOutcome.TIMEOUT -> {
                    _uiState.update { it.copy(phase = CheckPhase.TIMEOUT) }
                    return
                }
                PollOutcome.CONTINUE -> delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun retry() {
        if (_uiState.value.phase == CheckPhase.SUCCESS) return
        startSession()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1500L
        private const val TIMEOUT_MS = 60_000L
    }
}
