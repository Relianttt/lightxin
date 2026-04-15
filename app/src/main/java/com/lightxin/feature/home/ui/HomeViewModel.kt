package com.lightxin.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.auth.TokenManager
import com.lightxin.feature.checkin.data.CheckinRepository
import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.labor.data.LaborRepository
import com.lightxin.feature.labor.domain.HoursSummary
import com.lightxin.feature.running.data.RunningRepository
import com.lightxin.feature.running.domain.RunningDashboard
import com.lightxin.feature.schedule.data.ScheduleRepository
import com.lightxin.feature.schedule.domain.Course
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeDashboardData(
    val todayCourses: List<Course> = emptyList(),
    val tomorrowFirstSection: Int? = null, // 明天第一节课的起始节次，null表示无课
    val currentWeek: Int = 0,
    val nextCheckin: CheckinTask? = null,
    val runningProgress: RunningDashboard? = null,
    val laborHours: HoursSummary? = null,
)

data class HomeUiState(
    val userName: String = "",
    val dashboardData: HomeDashboardData = HomeDashboardData(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val checkinRepository: CheckinRepository,
    private val runningRepository: RunningRepository,
    private val laborRepository: LaborRepository,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(userName = tokenManager.getUserName().orEmpty()) }
            loadAll()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadAll()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun loadAll() {
        _uiState.update { it.copy(isLoading = _uiState.value.dashboardData == HomeDashboardData()) }

        coroutineScope {
            val scheduleDeferred = async { loadTodayCourses() }
            val checkinDeferred = async { loadNextCheckin() }
            val runningDeferred = async { loadRunning() }
            val laborDeferred = async { loadLabor() }

            val (courses, tomorrowFirst, currentWeek, scheduleError) = scheduleDeferred.await()
            val (checkinTask, checkinError) = checkinDeferred.await()
            val (running, runningError) = runningDeferred.await()
            val (labor, laborError) = laborDeferred.await()

            val errors = buildMap {
                scheduleError?.let { put("schedule", it) }
                checkinError?.let { put("checkin", it) }
                runningError?.let { put("running", it) }
                laborError?.let { put("labor", it) }
            }

            _uiState.update {
                it.copy(
                    dashboardData = HomeDashboardData(
                        todayCourses = courses,
                        tomorrowFirstSection = tomorrowFirst,
                        currentWeek = currentWeek,
                        nextCheckin = checkinTask,
                        runningProgress = running,
                        laborHours = labor,
                    ),
                    isLoading = false,
                    errors = errors,
                )
            }
        }
    }

    private data class ScheduleResult(
        val todayCourses: List<Course>,
        val tomorrowFirstSection: Int?,
        val currentWeek: Int,
        val error: String?,
    )

    private suspend fun loadTodayCourses(): ScheduleResult {
        val weekInfoResult = scheduleRepository.getWeekInfo()
        val weekInfo = weekInfoResult.getOrNull()
            ?: return ScheduleResult(emptyList(), null, 0, weekInfoResult.exceptionOrNull()?.message)

        val coursesResult = scheduleRepository.getCourses(
            weekInfo.schoolYear,
            weekInfo.schoolTerm,
            weekInfo.currentWeek,
        )
        val allCourses = coursesResult.getOrNull().orEmpty()

        val todayDow = LocalDate.now().dayOfWeek.value
        val todayCourses = allCourses
            .filter { it.dayOfWeek == todayDow }
            .distinctBy { Triple(it.name, it.startSection, it.endSection) }
            .sortedBy { it.startSection }

        val tomorrowDow = if (todayDow == 7) 1 else todayDow + 1
        val tomorrowFirst = allCourses
            .filter { it.dayOfWeek == tomorrowDow }
            .minByOrNull { it.startSection }
            ?.startSection

        return ScheduleResult(todayCourses, tomorrowFirst, weekInfo.currentWeek, coursesResult.exceptionOrNull()?.message)
    }

    private suspend fun loadNextCheckin(): Pair<CheckinTask?, String?> {
        val result = checkinRepository.getTasks(page = 1, pageSize = 5)
        val task = result.getOrNull()?.firstOrNull { !it.isSigned }
        return Pair(task, result.exceptionOrNull()?.message)
    }

    private suspend fun loadRunning(): Pair<RunningDashboard?, String?> {
        val result = runningRepository.getDashboard()
        return Pair(result.getOrNull(), result.exceptionOrNull()?.message)
    }

    private suspend fun loadLabor(): Pair<HoursSummary?, String?> {
        val result = laborRepository.getHoursSummary()
        return Pair(result.getOrNull(), result.exceptionOrNull()?.message)
    }
}
