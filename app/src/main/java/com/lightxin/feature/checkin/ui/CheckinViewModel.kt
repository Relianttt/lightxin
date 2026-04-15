package com.lightxin.feature.checkin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.checkin.data.CheckinRepository
import com.lightxin.feature.checkin.domain.CheckinTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckinUiState(
    val tasks: List<CheckinTask> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
)

@HiltViewModel
class CheckinViewModel @Inject constructor(
    private val repository: CheckinRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = _uiState

    init {
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.getTasks(page = 1).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            tasks = list,
                            isLoading = false,
                            currentPage = 1,
                            hasMore = list.size >= 10,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "加载失败")
                    }
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

            repository.getTasks(page = nextPage).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            tasks = it.tasks + list,
                            isLoadingMore = false,
                            currentPage = nextPage,
                            hasMore = list.size >= 10,
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMore = false) }
                },
            )
        }
    }

    fun retry() {
        loadInitial()
    }

    fun refresh() {
        loadInitial()
    }
}
