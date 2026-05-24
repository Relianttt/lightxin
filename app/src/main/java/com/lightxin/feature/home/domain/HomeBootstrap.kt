package com.lightxin.feature.home.domain

import com.lightxin.core.auth.TokenManager
import com.lightxin.feature.checkin.data.CheckinRepository
import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.schedule.data.ScheduleRepository
import com.lightxin.feature.schedule.domain.Course
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 冷启动预加载：在 MainActivity onCreate 阶段并发拉取课表与查寝，
 * 通过 [ready] 状态控制 Android 12+ SplashScreen 首帧阻塞。
 *
 * - 未登录：直接置 ready=true，交由 NavGraph 跳转登录页
 * - 数据拉取失败：snapshot 填入错误信息，仍置 ready=true
 * - 多次调用 load() 只执行一次（Mutex 去重）
 *
 * HomeViewModel 订阅 [snapshot] 作为首次渲染数据源，避免重复请求。
 */
@Singleton
class HomeBootstrap @Inject constructor(
    private val tokenManager: TokenManager,
    private val scheduleRepository: ScheduleRepository,
    private val checkinRepository: CheckinRepository,
) {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _snapshot = MutableStateFlow<HomeBootstrapSnapshot?>(null)
    val snapshot: StateFlow<HomeBootstrapSnapshot?> = _snapshot.asStateFlow()

    private val loadMutex = Mutex()
    private var loaded = false

    suspend fun load() {
        loadMutex.withLock {
            if (loaded) return
            loaded = true
        }
        try {
            val token = tokenManager.getAccessToken()
            if (token.isNullOrBlank()) return
            val userName = tokenManager.getUserName().orEmpty()

            coroutineScope {
                val coursesDeferred = async { loadCourses() }
                val checkinDeferred = async { loadCheckin() }
                val courseResult = coursesDeferred.await()
                val checkinResult = checkinDeferred.await()

                _snapshot.value = HomeBootstrapSnapshot(
                    userName = userName,
                    todayCourses = courseResult.courses,
                    tomorrowFirstSection = courseResult.tomorrowFirstSection,
                    currentWeek = courseResult.currentWeek,
                    nextUnsignedCheckin = checkinResult.first,
                    scheduleError = courseResult.error,
                    checkinError = checkinResult.second,
                )
            }
        } finally {
            _ready.value = true
        }
    }

    private suspend fun loadCourses(): CourseLoadResult {
        val weekInfoResult = scheduleRepository.getWeekInfo()
        val weekInfo = weekInfoResult.getOrNull()
            ?: return CourseLoadResult(
                emptyList(),
                null,
                0,
                weekInfoResult.exceptionOrNull()?.message,
            )
        val coursesResult = scheduleRepository.getCourses(
            weekInfo.schoolYear,
            weekInfo.schoolTerm,
            weekInfo.currentWeek,
        )
        val all = coursesResult.getOrNull().orEmpty()
        val todayDow = LocalDate.now().dayOfWeek.value
        val tomorrowDow = if (todayDow == 7) 1 else todayDow + 1

        val today = all.filter { it.dayOfWeek == todayDow }
            .distinctBy { Triple(it.name, it.startSection, it.endSection) }
            .sortedBy { it.startSection }
        val tomorrowFirst = all.filter { it.dayOfWeek == tomorrowDow }
            .minByOrNull { it.startSection }
            ?.startSection

        return CourseLoadResult(
            courses = today,
            tomorrowFirstSection = tomorrowFirst,
            currentWeek = weekInfo.currentWeek,
            error = coursesResult.exceptionOrNull()?.message,
        )
    }

    private suspend fun loadCheckin(): Pair<CheckinTask?, String?> {
        val result = checkinRepository.getTasks(page = 1, pageSize = 5)
        val task = result.getOrNull()?.firstOrNull { !it.isSigned && it.isInOpenWindow }
        return task to result.exceptionOrNull()?.message
    }

    private data class CourseLoadResult(
        val courses: List<Course>,
        val tomorrowFirstSection: Int?,
        val currentWeek: Int,
        val error: String?,
    )
}

/** 首页冷启动数据快照（单次一次性填充） */
data class HomeBootstrapSnapshot(
    val userName: String,
    val todayCourses: List<Course>,
    val tomorrowFirstSection: Int?,
    val currentWeek: Int,
    val nextUnsignedCheckin: CheckinTask?,
    val scheduleError: String? = null,
    val checkinError: String? = null,
)
