package com.lightxin.feature.credit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.credit.data.CreditRepository
import com.lightxin.feature.credit.domain.CreditOverview
import com.lightxin.feature.credit.domain.CreditRecord
import com.lightxin.feature.credit.domain.CreditRecordDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreditUiState(
    val overview: CreditOverview? = null,
    val records: List<CreditRecord> = emptyList(),
    val selectedDetail: CreditRecordDetail? = null,
    val isLoading: Boolean = true,
    val isDetailLoading: Boolean = false,
    val showDetailSheet: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CreditViewModel @Inject constructor(
    private val repository: CreditRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreditUiState())
    val uiState: StateFlow<CreditUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val overviewDeferred = async { repository.getOverview() }
            val recordsDeferred = async { repository.getRecords() }

            val overviewResult = overviewDeferred.await()
            val recordsResult = recordsDeferred.await()

            _uiState.update {
                it.copy(
                    overview = overviewResult.getOrNull(),
                    records = recordsResult.getOrDefault(emptyList()),
                    isLoading = false,
                    error = overviewResult.exceptionOrNull()?.message
                        ?: recordsResult.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun onRecordClick(record: CreditRecord) {
        _uiState.update { it.copy(showDetailSheet = true, isDetailLoading = true, selectedDetail = null) }
        viewModelScope.launch {
            val result = repository.getRecordDetail(record.id)
            _uiState.update {
                it.copy(
                    selectedDetail = result.getOrNull(),
                    isDetailLoading = false,
                )
            }
        }
    }

    fun dismissDetail() {
        _uiState.update { it.copy(showDetailSheet = false, selectedDetail = null) }
    }

    fun retry() {
        load()
    }
}
