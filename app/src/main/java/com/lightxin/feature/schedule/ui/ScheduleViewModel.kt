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
import com.lightxin.feature.schedule.domain.ScheduleData
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
    /** 已到手的各周课表（选中周 + 从缓存预读的相邻周），供横向翻页时相邻页直接有内容。 */
    val weekData: Map<Int, ScheduleData> = emptyMap(),
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
        // 换学期时选中周和周次列表同一帧到位，翻页器不会先落在旧周再跳
        _uiState.update { it.copy(weekInfo = info,
            selectedWeek = if (changed) info.currentWeek else it.selectedWeek,
            weekData = if (changed) emptyMap() else it.weekData,
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
            // 切周保留上一周的课表做占位，等新快照到位再整体替换——
            // 直接清空会让 UI 整屏换骨架屏，缓存命中快时就是“闪一下”。
            else it.copy(selectedWeek = week, hasLoadedWeek = false, isLoading = true,
                isRefreshing = false, error = null, refreshError = null)
        }
        weekJob = viewModelScope.launch {
            val cached = repository.cachedWeek(current, info, week)
            if (!isCurrent()) return@launch
            cached?.let { showWeek(it) }
            coroutineScope {
                launch { preloadNeighbors(current, info, week, ::isCurrent) }
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

    /** 只读本地缓存，不发请求：相邻周没缓存就让那一页等选中后再走正常加载。 */
    private suspend fun preloadNeighbors(current: AccountContext, info: WeekInfo, week: Int, isCurrent: () -> Boolean) {
        val index = info.weekNumbers.indexOf(week)
        for (neighbor in listOfNotNull(info.weekNumbers.getOrNull(index - 1), info.weekNumbers.getOrNull(index + 1))) {
            if (neighbor in _uiState.value.weekData) continue
            val snapshot = repository.cachedWeek(current, info, neighbor) ?: continue
            if (!isCurrent()) return
            _uiState.update { it.copy(weekData = it.weekData + (neighbor to snapshot.data)) }
        }
    }

    private fun showWeek(snapshot: ScheduleWeekSnapshot) {
        _uiState.update { it.copy(courses = snapshot.data.courses, weekDates = snapshot.data.weekDates,
            weekData = it.weekData + (it.selectedWeek to snapshot.data),
            hasLoadedWeek = true, lastSyncedAt = snapshot.syncedAt, isLoading = false, error = null) }
    }
}
