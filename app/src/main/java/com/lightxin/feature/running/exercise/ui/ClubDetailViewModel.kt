package com.lightxin.feature.running.exercise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.running.data.RunningRepository
import com.lightxin.feature.running.exercise.domain.ClubTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClubDetailUiState(
    val isLoading: Boolean = true,
    val tasks: List<ClubTask> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ClubDetailViewModel @Inject constructor(
    private val repository: RunningRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClubDetailUiState())
    val uiState: StateFlow<ClubDetailUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getClubDetail().fold(
                onSuccess = { tasks -> _uiState.update { it.copy(isLoading = false, tasks = tasks) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") } },
            )
        }
    }
}
