package com.lightxin.feature.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.auth.AccountContext
import com.lightxin.core.auth.AccountSession
import com.lightxin.feature.schedule.data.ScheduleRepository
import com.lightxin.feature.schedule.data.ScheduleSyncCoordinator
import com.lightxin.feature.schedule.data.local.ScheduleWeekSnapshot
import com.lightxin.feature.schedule.data.local.SemesterSnapshot
import com.lightxin.feature.schedule.domain.Course
import com.lightxin.feature.schedule.domain.WeekInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val hasLoadedWeek: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshError: String? = null,
    val lastSyncedAt: Long? = null,
    val isEstimatedWeek: Boolean = false,
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val session: AccountSession,
    private val sync: ScheduleSyncCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState
    private var account: AccountContext? = null
    private var weekJob: Job? = null
    private var selectionVersion = 0L
    private var onlineSemesterAt: Long? = null

    init {
        viewModelScope.launch {
            session.leases.collectLatest {
                weekJob?.cancel()
                selectionVersion++
                onlineSemesterAt = null
                _uiState.value = ScheduleUiState()
                val current = repository.account()
                account = current
                if (current.userCode.isBlank()) {
                    _uiState.value = ScheduleUiState(isLoading = false, error = "登录信息已失效，请重新登录")
                    return@collectLatest
                }
                coroutineScope {
                    launch {
                        repository.observeSemester(current).collect { snapshot ->
                            if (snapshot != null && session.isCurrent(current.lease)) applySemester(snapshot)
                        }
                    }
                    launch { refreshSemester(current) }
                }
            }
        }
    }

    private fun applySemester(snapshot: SemesterSnapshot) {
        val estimated = onlineSemesterAt != snapshot.syncedAt
        val info = if (estimated) snapshot.estimatedInfo() else snapshot.info
        val previous = _uiState.value.weekInfo
        val changed = previous == null || previous.schoolYear != info.schoolYear || previous.schoolTerm != info.schoolTerm
        _uiState.update { it.copy(weekInfo = info, isEstimatedWeek = estimated,
            hasLoadedWeek = if (changed) false else it.hasLoadedWeek) }
        if (changed || _uiState.value.selectedWeek !in info.weekNumbers) selectWeek(info.currentWeek, force = true)
    }

    private suspend fun refreshSemester(current: AccountContext) {
        repository.refreshSemester(current).fold(
            onSuccess = { snapshot ->
                if (!session.isCurrent(current.lease)) return@fold
                onlineSemesterAt = snapshot.syncedAt
                applySemester(snapshot)
                sync.start(current, snapshot.info)
            },
            onFailure = { error ->
                if (session.isCurrent(current.lease)) _uiState.update {
                    it.copy(isLoading = if (it.weekInfo == null) false else it.isLoading,
                        error = if (it.weekInfo == null) error.message else it.error, refreshError = error.message)
                }
            },
        )
    }

    fun onWeekSelected(week: Int) { selectWeek(week, force = false) }

    fun retry() {
        val current = account ?: return
        if (_uiState.value.weekInfo == null) viewModelScope.launch { refreshSemester(current) }
        else selectWeek(_uiState.value.selectedWeek, force = true)
    }

    private fun selectWeek(week: Int, force: Boolean) {
        val current = account ?: return
        val info = _uiState.value.weekInfo ?: return
        if (week !in info.weekNumbers) return
        weekJob?.cancel()
        val version = ++selectionVersion
        fun isCurrent() = version == selectionVersion && session.isCurrent(current.lease)
        _uiState.update {
            if (it.selectedWeek == week && it.hasLoadedWeek) it.copy(isRefreshing = true, error = null, refreshError = null)
            else it.copy(selectedWeek = week, courses = emptyList(), weekDates = emptyMap(),
                hasLoadedWeek = false, lastSyncedAt = null, isLoading = true, isRefreshing = false, error = null, refreshError = null)
        }
        weekJob = viewModelScope.launch {
            val cached = repository.cachedWeek(current, info, week)
            if (!isCurrent()) return@launch
            cached?.let { showWeek(it) }
            coroutineScope {
                launch {
                    repository.observeWeek(current, info, week).collect { snapshot ->
                        if (isCurrent() && snapshot != null) showWeek(snapshot)
                    }
                }
                launch {
                    val maxAge = if (week == info.currentWeek || week == info.currentWeek + 1) 12L else 24L
                    if (!force && cached != null && System.currentTimeMillis() - cached.syncedAt < maxAge * 3_600_000) {
                        if (isCurrent()) _uiState.update { it.copy(isRefreshing = false) }
                        return@launch
                    }
                    _uiState.update { it.copy(isRefreshing = true) }
                    repository.refreshWeek(current, info, week).fold(
                        onSuccess = { if (isCurrent()) {
                            showWeek(it)
                            _uiState.update { state -> state.copy(refreshError = null) }
                        } },
                        onFailure = { error -> if (isCurrent()) _uiState.update {
                            it.copy(isLoading = false, error = if (it.hasLoadedWeek) null else "该周尚未缓存，联网后可获取\n${error.message.orEmpty()}",
                                refreshError = error.message)
                        } },
                    )
                    if (isCurrent()) _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    private fun showWeek(snapshot: ScheduleWeekSnapshot) {
        _uiState.update { it.copy(courses = snapshot.data.courses, weekDates = snapshot.data.weekDates,
            hasLoadedWeek = true, lastSyncedAt = snapshot.syncedAt, isLoading = false, error = null) }
    }
}
