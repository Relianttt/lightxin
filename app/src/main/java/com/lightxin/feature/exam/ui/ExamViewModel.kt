package com.lightxin.feature.exam.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.exam.data.ExamRepository
import com.lightxin.feature.exam.domain.ExamScore
import com.lightxin.feature.exam.domain.SchoolYear
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExamUiState(
    val schoolYears: List<SchoolYear> = emptyList(),
    val selectedYear: String = "",
    val selectedSemester: String = "",
    val scores: List<ExamScore> = emptyList(),
    val isLoading: Boolean = true,
    val isScoresLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ExamViewModel @Inject constructor(
    private val repository: ExamRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState: StateFlow<ExamUiState> = _uiState
    private var scoresJob: Job? = null
    private var scoresGeneration = 0

    init {
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val termDeferred = async { repository.getCurrentTerm() }
            val yearsDeferred = async { repository.getSchoolYears() }

            val termResult = termDeferred.await()
            val yearsResult = yearsDeferred.await()

            if (termResult.isFailure && yearsResult.isFailure) {
                _uiState.update {
                    it.copy(isLoading = false, error = termResult.exceptionOrNull()?.message)
                }
                return@launch
            }

            val term = termResult.getOrNull()
            val years = yearsResult.getOrDefault(emptyList())
            val year = term?.schoolYear ?: years.firstOrNull()?.value.orEmpty()
            val semester = term?.semester ?: "1"

            _uiState.update {
                it.copy(
                    schoolYears = years,
                    selectedYear = year,
                    selectedSemester = semester,
                    isLoading = false,
                )
            }

            loadScores(year, semester)
        }
    }

    fun onTermSelected(year: String, semester: String) {
        _uiState.update { it.copy(selectedYear = year, selectedSemester = semester) }
        loadScores(year, semester)
    }

    fun retry() {
        if (_uiState.value.selectedYear.isBlank()) loadInitial()
        else loadScores(_uiState.value.selectedYear, _uiState.value.selectedSemester)
    }

    private fun loadScores(year: String, semester: String) {
        val generation = ++scoresGeneration
        scoresJob?.cancel()
        scoresJob = viewModelScope.launch {
            _uiState.update { it.copy(isScoresLoading = true, scores = emptyList(), error = null) }
            val result = repository.getExamScores(year, semester)
            if (generation != scoresGeneration) return@launch
            _uiState.update {
                it.copy(
                    scores = result.getOrDefault(emptyList()),
                    isScoresLoading = false,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }
}
