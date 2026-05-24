package com.lightxin.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.notification.CourseNotificationScheduler
import com.lightxin.feature.checkin.data.CheckinRepository
import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.holiday.data.HolidayRepository
import com.lightxin.feature.holiday.domain.HolidayTask
import com.lightxin.feature.home.domain.HomeBootstrap
import com.lightxin.feature.home.domain.HomeBootstrapSnapshot
import com.lightxin.feature.home.domain.HomeScene
import com.lightxin.feature.home.domain.SceneResolver
import com.lightxin.feature.home.domain.SubtitleContext
import com.lightxin.feature.home.domain.SubtitleLibrary
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class HomeDashboardData(
    val todayCourses: List<Course> = emptyList(),
    val tomorrowFirstSection: Int? = null, // 明天第一节课的起始节次，null表示无课
    val currentWeek: Int = 0,
    val nextCheckin: CheckinTask? = null,
    val holidayTask: HolidayTask? = null,
    val runningProgress: RunningDashboard? = null,
    val laborHours: HoursSummary? = null,
)

data class HomeUiState(
    val userName: String = "",
    val subtitle: String = "",
    val dashboardData: HomeDashboardData = HomeDashboardData(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeBootstrap: HomeBootstrap,
    private val scheduleRepository: ScheduleRepository,
    private val checkinRepository: CheckinRepository,
    private val holidayRepository: HolidayRepository,
    private val runningRepository: RunningRepository,
    private val laborRepository: LaborRepository,
    private val tokenManager: TokenManager,
    private val courseNotificationScheduler: CourseNotificationScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    // 副标题轮换计数器：手动刷新或后台 >30 分钟回前台时递增，驱动同桶下文案轮换
    private var subtitleRotation: Int = 0
    // 上次进入后台的时间戳
    private var lastPausedAt: Long? = null

    init {
        // 先填本地姓名，避免首次 snapshot 未到达时界面空白
        viewModelScope.launch {
            _uiState.update { it.copy(userName = tokenManager.getUserName().orEmpty()) }
        }
        // 立即应用 bootstrap 已有的 snapshot（若有）
        homeBootstrap.snapshot.value?.let { applyBootstrap(it) }
        // 持续订阅后续 snapshot 变化（超时后到达的数据）
        viewModelScope.launch {
            homeBootstrap.snapshot.filterNotNull().collect { applyBootstrap(it) }
        }
        // 自行加载 bootstrap 不覆盖的 running / labor
        viewModelScope.launch { loadExtras() }
        // 启动课程倒计时通知调度
        courseNotificationScheduler.start(viewModelScope) {
            _uiState.value.dashboardData.todayCourses
        }
    }

    fun refresh() {
        viewModelScope.launch {
            subtitleRotation++
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                loadAll()
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** 应用进入后台：记录时间戳 */
    fun onPaused() {
        lastPausedAt = System.currentTimeMillis()
    }

    /** 应用回前台：若距上次 ON_PAUSE > 30 分钟则轮换副标题，再重算场景 */
    fun onResumed() {
        val pausedAt = lastPausedAt
        if (pausedAt != null &&
            System.currentTimeMillis() - pausedAt > BACKGROUND_ROTATION_THRESHOLD_MS
        ) {
            subtitleRotation++
        }
        recomputeSubtitle()
    }

    /** 前台定时器 / 其他触发点调用：基于当前 dashboardData 重算副标题 */
    fun recomputeSubtitle() {
        _uiState.update { current ->
            current.copy(subtitle = buildSubtitle(current.dashboardData))
        }
    }

    private fun applyBootstrap(snapshot: HomeBootstrapSnapshot) {
        val mergedData = _uiState.value.dashboardData.copy(
            todayCourses = snapshot.todayCourses,
            tomorrowFirstSection = snapshot.tomorrowFirstSection,
            currentWeek = snapshot.currentWeek,
            nextCheckin = snapshot.nextUnsignedCheckin,
        )
        _uiState.update { current ->
            val merged = current.dashboardData.copy(
                todayCourses = snapshot.todayCourses,
                tomorrowFirstSection = snapshot.tomorrowFirstSection,
                currentWeek = snapshot.currentWeek,
                nextCheckin = snapshot.nextUnsignedCheckin,
            )
            val errors = current.errors.toMutableMap().apply {
                snapshot.scheduleError?.let { put("schedule", it) } ?: remove("schedule")
                snapshot.checkinError?.let { put("checkin", it) } ?: remove("checkin")
            }
            current.copy(
                userName = snapshot.userName.ifBlank { current.userName },
                dashboardData = merged,
                subtitle = buildSubtitle(merged),
                isLoading = false,
                errors = errors,
            )
        }
        courseNotificationScheduler.checkNow(mergedData.todayCourses)
    }

    private suspend fun loadExtras() = coroutineScope {
        val runningDeferred = async { loadRunning() }
        val laborDeferred = async { loadLabor() }
        val holidayDeferred = async { loadHoliday() }
        val (running, runningError) = runningDeferred.await()
        val (labor, laborError) = laborDeferred.await()
        val (holiday, holidayError) = holidayDeferred.await()

        _uiState.update { current ->
            val merged = current.dashboardData.copy(
                runningProgress = running,
                laborHours = labor,
                holidayTask = holiday,
            )
            val errors = current.errors.toMutableMap().apply {
                runningError?.let { put("running", it) } ?: remove("running")
                laborError?.let { put("labor", it) } ?: remove("labor")
                holidayError?.let { put("holiday", it) } ?: remove("holiday")
            }
            current.copy(
                dashboardData = merged,
                subtitle = buildSubtitle(merged),
                errors = errors,
            )
        }
    }

    private suspend fun loadAll() {
        _uiState.update { it.copy(isLoading = _uiState.value.dashboardData == HomeDashboardData()) }

        coroutineScope {
            val scheduleDeferred = async { loadTodayCourses() }
            val checkinDeferred = async { loadNextCheckin() }
            val runningDeferred = async { loadRunning() }
            val laborDeferred = async { loadLabor() }
            val holidayDeferred = async { loadHoliday() }

            val (courses, tomorrowFirst, currentWeek, scheduleError) = scheduleDeferred.await()
            val (checkinTask, checkinError) = checkinDeferred.await()
            val (running, runningError) = runningDeferred.await()
            val (labor, laborError) = laborDeferred.await()
            val (holiday, holidayError) = holidayDeferred.await()

            val errors = buildMap {
                scheduleError?.let { put("schedule", it) }
                checkinError?.let { put("checkin", it) }
                runningError?.let { put("running", it) }
                laborError?.let { put("labor", it) }
                holidayError?.let { put("holiday", it) }
            }

            val merged = HomeDashboardData(
                todayCourses = courses,
                tomorrowFirstSection = tomorrowFirst,
                currentWeek = currentWeek,
                nextCheckin = checkinTask,
                holidayTask = holiday,
                runningProgress = running,
                laborHours = labor,
            )
            _uiState.update {
                it.copy(
                    dashboardData = merged,
                    subtitle = buildSubtitle(merged),
                    isLoading = false,
                    errors = errors,
                )
            }
            courseNotificationScheduler.checkNow(merged.todayCourses)
        }
    }

    /** 场景优先：命中则用场景文案；否则走文案库 */
    private fun buildSubtitle(data: HomeDashboardData): String {
        val now = LocalDateTime.now()
        val scene = SceneResolver.resolve(now, data.todayCourses, data.nextCheckin)
        SubtitleLibrary.fromScene(scene)?.let { return it }
        return SubtitleLibrary.pickSubtitle(
            context = SubtitleContext(
                now = now,
                todayCourses = data.todayCourses,
                tomorrowFirstSection = data.tomorrowFirstSection,
                hasPendingCheckin = data.nextCheckin?.let { !it.isSigned } == true,
            ),
            rotation = subtitleRotation,
        )
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

    private suspend fun loadHoliday(): Pair<HolidayTask?, String?> {
        val result = holidayRepository.getFirstUnregistered()
        return Pair(result.getOrNull(), result.exceptionOrNull()?.message)
    }

    private suspend fun loadRunning(): Pair<RunningDashboard?, String?> {
        val result = runningRepository.getDashboard()
        return Pair(result.getOrNull(), result.exceptionOrNull()?.message)
    }

    private suspend fun loadLabor(): Pair<HoursSummary?, String?> {
        val result = laborRepository.getHoursSummary()
        return Pair(result.getOrNull(), result.exceptionOrNull()?.message)
    }

    private companion object {
        const val BACKGROUND_ROTATION_THRESHOLD_MS = 30 * 60_000L
    }
}
