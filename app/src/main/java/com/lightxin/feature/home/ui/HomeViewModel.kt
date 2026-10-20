package com.lightxin.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.auth.AccountSession
import com.lightxin.core.network.isAuthExpired
import com.lightxin.core.notification.CourseNotificationScheduler
import com.lightxin.feature.aiclass.data.AiClassSessionCheck
import com.lightxin.feature.aiclass.data.AiClassSessionGuard
import com.lightxin.feature.aiclass.data.AiClassRepository
import com.lightxin.feature.aiclass.data.AiClassMemoryStore
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
import com.lightxin.feature.schedule.domain.Course
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class HomeDashboardData(
    val todayCourses: List<Course> = emptyList(),
    val tomorrowFirstSection: Int? = null,
    val todayKnown: Boolean = false,
    val tomorrowKnown: Boolean = false,
    val scheduleSyncedAt: Long? = null,
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
    /** 登录态降级提示；null 表示不提示（断网、超时、服务端异常都不构成证据）。 */
    val sessionAlert: SessionAlert? = null,
)

/** 首页登录态横幅的两种降级：一个说校内会话没了，一个说只有 AI课堂被顶。 */
enum class SessionAlert { LoginExpired, AiClassBlocked }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeBootstrap: HomeBootstrap,
    private val holidayRepository: HolidayRepository,
    private val runningRepository: RunningRepository,
    private val laborRepository: LaborRepository,
    private val tokenManager: TokenManager,
    private val courseNotificationScheduler: CourseNotificationScheduler,
    private val aiClassSessionGuard: AiClassSessionGuard,
    private val aiClassRepository: AiClassRepository,
    private val aiClassMemory: AiClassMemoryStore,
    private val accountSession: AccountSession,
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
        // AI课堂会话体检：官方 App 登录后 FIF 服务端映射会被顶掉，本地 Cookie 却看不出来
        viewModelScope.launch { checkAiClassSession() }
        viewModelScope.launch {
            aiClassMemory.snapshots.collect { cached ->
                if (cached.failures.isNotEmpty()) applySessionAlert(SessionAlert.AiClassBlocked)
            }
        }
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
        // 探测最坏要等 12s 超时，不塞进下拉动画里
        viewModelScope.launch { checkAiClassSession() }
    }

    /**
     * 会话体检，结果只用于决定是否在首页挂一条降级横幅。
     *
     * 只有 Blocked（服务端拒绝 FIF 映射）与 LoginExpired（本地无校内凭据）才提示 ——
     * 断网与超时归 Inconclusive，必须沉默，否则就是把网络故障说成别人顶号。
     */
    private suspend fun checkAiClassSession() {
        val lease = accountSession.capture()
        val check = aiClassSessionGuard.checkOnLaunch()
        accountSession.requireCurrent(lease)
        if (check == AiClassSessionCheck.Blocked) aiClassMemory.markBlocked(lease)
        val alert = when (check) {
            AiClassSessionCheck.Blocked -> SessionAlert.AiClassBlocked
            AiClassSessionCheck.LoginExpired -> SessionAlert.LoginExpired
            AiClassSessionCheck.Healthy,
            AiClassSessionCheck.Skipped,
            AiClassSessionCheck.Inconclusive,
            -> null
        }
        applySessionAlert(alert)
        if (check == AiClassSessionCheck.Healthy) {
            lease.scope.launch {
                try { aiClassRepository.warmUp(check) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Ordinary prewarm failures do not change login status. */ }
            }
        }
    }

    /**
     * 横幅优先级：校内登录失效 > AI课堂被顶 > 不提示。
     *
     * `LoginExpired` 是粘性的：它一旦成立，后续一次 Healthy 的会话体检不该把横幅抹掉 ——
     * 校内 token 失效时哪些接口还能通，取决于各服务端的容忍度，不能用其中一个成功来反证。
     */
    private fun applySessionAlert(incoming: SessionAlert?) {
        _uiState.update { current ->
            val next = when {
                current.sessionAlert == SessionAlert.LoginExpired -> SessionAlert.LoginExpired
                incoming == SessionAlert.LoginExpired -> SessionAlert.LoginExpired
                incoming != null -> incoming
                else -> current.sessionAlert
            }
            current.copy(sessionAlert = next)
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
        _uiState.update { current ->
            val previous = current.dashboardData
            val merged = previous.copy(
                todayCourses = if (snapshot.scheduleLoaded) snapshot.todayCourses else previous.todayCourses,
                tomorrowFirstSection = if (snapshot.scheduleLoaded) snapshot.tomorrowFirstSection else previous.tomorrowFirstSection,
                currentWeek = if (snapshot.scheduleLoaded) snapshot.currentWeek else previous.currentWeek,
                todayKnown = if (snapshot.scheduleLoaded) snapshot.todayKnown else previous.todayKnown,
                tomorrowKnown = if (snapshot.scheduleLoaded) snapshot.tomorrowKnown else previous.tomorrowKnown,
                scheduleSyncedAt = if (snapshot.scheduleLoaded) snapshot.scheduleSyncedAt else previous.scheduleSyncedAt,
                nextCheckin = if (snapshot.checkinLoaded) snapshot.nextUnsignedCheckin else previous.nextCheckin,
            )
            val errors = current.errors.toMutableMap().apply {
                if (snapshot.scheduleLoaded) snapshot.scheduleError?.let { put("schedule", it) } ?: remove("schedule")
                if (snapshot.checkinLoaded) snapshot.checkinError?.let { put("checkin", it) } ?: remove("checkin")
            }
            current.copy(userName = snapshot.userName.ifBlank { current.userName }, dashboardData = merged,
                subtitle = buildSubtitle(merged), isLoading = !snapshot.scheduleLoaded, errors = errors)
        }
        courseNotificationScheduler.checkNow(_uiState.value.dashboardData.todayCourses)
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

    private suspend fun loadAll() = coroutineScope {
        launch { homeBootstrap.refresh() }
        launch { loadExtras() }
    }

    /** 场景优先：命中则用场景文案；否则走文案库 */
    private fun buildSubtitle(data: HomeDashboardData): String {
        if (!data.todayKnown) return "课表暂未同步，联网后可更新"
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
        // 劳动教育的 token 失效是 200 + "非法访问"，不是 401；识别出来后归到“校内登录失效”
        if (result.exceptionOrNull()?.isAuthExpired() == true) {
            applySessionAlert(SessionAlert.LoginExpired)
        }
        return Pair(result.getOrNull(), result.exceptionOrNull()?.message)
    }

    private companion object {
        const val BACKGROUND_ROTATION_THRESHOLD_MS = 30 * 60_000L
    }
}
