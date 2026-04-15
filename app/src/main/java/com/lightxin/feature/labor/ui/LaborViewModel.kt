package com.lightxin.feature.labor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.labor.data.LaborRepository
import com.lightxin.feature.labor.domain.ActivityRecord
import com.lightxin.feature.labor.domain.HoursSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LaborUiState(
    val hoursSummary: HoursSummary? = null,
    val activities: List<ActivityRecord> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
)

@HiltViewModel
class LaborViewModel @Inject constructor(
    private val repository: LaborRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LaborUiState())
    val uiState: StateFlow<LaborUiState> = _uiState

    init {
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 并行加载工时总览和首页活动
            val hoursResult = repository.getHoursSummary()
            val activitiesResult = repository.getActivities(page = 1)

            hoursResult.fold(
                onSuccess = { summary ->
                    _uiState.update { it.copy(hoursSummary = summary) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
                    return@launch
                },
            )

            activitiesResult.fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            activities = list,
                            isLoading = false,
                            currentPage = 1,
                            hasMore = list.size >= 10,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
                },
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val nextPage = state.currentPage + 1

            repository.getActivities(page = nextPage).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            activities = it.activities + list,
                            isLoadingMore = false,
                            currentPage = nextPage,
                            hasMore = list.size >= 10,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoadingMore = false) }
                },
            )
        }
    }

    fun retry() {
        loadInitial()
    }
}
