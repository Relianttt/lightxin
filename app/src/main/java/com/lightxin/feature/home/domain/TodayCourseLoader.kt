package com.lightxin.feature.home.domain

import com.lightxin.core.auth.AccountContext
import com.lightxin.core.auth.AccountSession
import com.lightxin.feature.schedule.data.ScheduleRepository
import com.lightxin.feature.schedule.data.ScheduleSyncCoordinator
import com.lightxin.feature.schedule.data.local.SCHOOL_ZONE
import com.lightxin.feature.schedule.data.local.ScheduleWeekSnapshot
import com.lightxin.feature.schedule.domain.Course
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class HomeCourseSummary(
    val todayCourses: List<Course> = emptyList(),
    val tomorrowFirstSection: Int? = null,
    val currentWeek: Int = 0,
    val todayKnown: Boolean = false,
    val tomorrowKnown: Boolean = false,
    val lastSyncedAt: Long? = null,
    val error: String? = null,
)

internal fun summarizeCourses(
    today: LocalDate,
    week: Int,
    current: ScheduleWeekSnapshot?,
    following: ScheduleWeekSnapshot?,
): HomeCourseSummary {
    val dow = today.dayOfWeek.value
    val tomorrow = if (dow == 7) following else current
    val tomorrowDow = if (dow == 7) 1 else dow + 1
    return HomeCourseSummary(
        todayCourses = current?.data?.courses.orEmpty().filter { it.dayOfWeek == dow }
            .distinctBy { Triple(it.name, it.startSection, it.endSection) }.sortedBy { it.startSection },
        tomorrowFirstSection = tomorrow?.data?.courses?.filter { it.dayOfWeek == tomorrowDow }?.minOfOrNull { it.startSection },
        currentWeek = week, todayKnown = current != null, tomorrowKnown = tomorrow != null, lastSyncedAt = current?.syncedAt,
    )
}

@Singleton
class TodayCourseLoader @Inject constructor(
    private val repository: ScheduleRepository,
    private val sync: ScheduleSyncCoordinator,
    private val session: AccountSession,
) {
    private val refreshErrors = MutableStateFlow<Pair<Long, String?>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(account: AccountContext): Flow<HomeCourseSummary> = repository.observeSemester(account).flatMapLatest { semester ->
        val errors = refreshErrors.map { if (it?.first == account.lease.generation) it.second else null }
        if (semester == null) errors.map { HomeCourseSummary(error = it) }
        else {
            val info = semester.estimatedInfo()
            val next = (info.currentWeek + 1).takeIf { it in info.weekNumbers }
            val following = if (next == null) flowOf(null) else repository.observeWeek(account, info, next)
            combine(repository.observeWeek(account, info, info.currentWeek), following, errors) { current, after, error ->
                summarizeCourses(LocalDate.now(SCHOOL_ZONE), info.currentWeek, current, after).copy(error = error)
            }
        }
    }.distinctUntilChanged()

    suspend fun refresh(account: AccountContext) {
        val semester = repository.refreshSemester(account)
        val error = if (semester.isFailure) semester.exceptionOrNull() else {
            val info = semester.getOrThrow().info
            val current = repository.refreshWeek(account, info, info.currentWeek)
            if (current.isSuccess) sync.start(account, info)
            current.exceptionOrNull()
        }
        session.publish(account.lease) { refreshErrors.value = account.lease.generation to error?.message }
    }
}
