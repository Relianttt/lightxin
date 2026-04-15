package com.lightxin.feature.labor.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.labor.data.LaborRepository
import com.lightxin.feature.labor.domain.ActivityDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LaborDetailUiState(
    val detail: ActivityDetail? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class LaborDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LaborRepository,
) : ViewModel() {

    private val id: String = savedStateHandle["id"] ?: ""
    private val type: String = savedStateHandle["type"] ?: ""

    private val _uiState = MutableStateFlow(LaborDetailUiState())
    val uiState: StateFlow<LaborDetailUiState> = _uiState

    init {
        loadDetail()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.getActivityDetail(id, type).fold(
                onSuccess = { detail ->
                    _uiState.update { it.copy(detail = detail, isLoading = false) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "加载失败")
                    }
                },
            )
        }
    }

    fun retry() {
        loadDetail()
    }
}
