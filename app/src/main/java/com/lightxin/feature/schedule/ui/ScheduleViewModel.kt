package com.lightxin.feature.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.schedule.data.ScheduleRepository
import com.lightxin.feature.schedule.domain.Course
import com.lightxin.feature.schedule.domain.WeekInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleUiState(
    val weekInfo: WeekInfo? = null,
    val courses: List<Course> = emptyList(),
    val weekDates: Map<Int, String> = emptyMap(),
    val selectedWeek: Int = 1,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState

    init {
        loadWeekInfo()
    }

    private fun loadWeekInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.getWeekInfo().fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(weekInfo = info, selectedWeek = info.currentWeek)
                    }
                    loadCourses(info.schoolYear, info.schoolTerm, info.currentWeek)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "加载失败")
                    }
                },
            )
        }
    }

    fun onWeekSelected(week: Int) {
        val info = _uiState.value.weekInfo ?: return
        _uiState.update { it.copy(selectedWeek = week) }
        viewModelScope.launch {
            loadCourses(info.schoolYear, info.schoolTerm, week)
        }
    }

    fun retry() {
        loadWeekInfo()
    }

    private suspend fun loadCourses(schoolYear: String, schoolTerm: String, week: Int) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        repository.getCourses(schoolYear, schoolTerm, week).fold(
            onSuccess = { data ->
                _uiState.update {
                    it.copy(courses = data.courses, weekDates = data.weekDates, isLoading = false)
                }
            },
            onFailure = { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "加载课表失败")
                }
            },
        )
    }
}
