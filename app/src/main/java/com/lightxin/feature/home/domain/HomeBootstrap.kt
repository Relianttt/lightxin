package com.lightxin.feature.home.domain

import com.lightxin.core.auth.AccountContext
import com.lightxin.core.auth.AccountSession
import com.lightxin.core.auth.TokenManager
import com.lightxin.feature.checkin.data.CheckinRepository
import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.schedule.domain.Course
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Source results are merged separately; cached courses never wait for check-in. */
@Singleton
class HomeBootstrap @Inject constructor(
    private val tokenManager: TokenManager,
    private val courseLoader: TodayCourseLoader,
    private val checkinRepository: CheckinRepository,
    private val session: AccountSession,
) {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _snapshot = MutableStateFlow<HomeBootstrapSnapshot?>(null)
    val snapshot: StateFlow<HomeBootstrapSnapshot?> = _snapshot.asStateFlow()
    private val loadMutex = Mutex()
    private var loaded = false
    private var generation = 0L
    private var courseJob: Job? = null

    suspend fun resetAfterRelogin() = loadMutex.withLock {
        generation++
        loaded = false
        courseJob?.cancel()
        _snapshot.value = null
    }

    suspend fun load() {
        loadMutex.withLock {
            if (loaded) return
            val account = tokenManager.account()
            if (account.userCode.isBlank() || tokenManager.getAccessToken().isNullOrBlank()) {
                _ready.value = true
                return
            }
            loaded = true
            val currentGeneration = generation
            _snapshot.value = HomeBootstrapSnapshot(userName = tokenManager.getUserName().orEmpty())
            courseJob = account.lease.scope.launch {
                courseLoader.observe(account).collect { courses ->
                    update(account, currentGeneration) { it.copy(
                        todayCourses = courses.todayCourses, tomorrowFirstSection = courses.tomorrowFirstSection,
                        currentWeek = courses.currentWeek, todayKnown = courses.todayKnown, tomorrowKnown = courses.tomorrowKnown,
                        scheduleError = courses.error, scheduleLoaded = courses.todayKnown || courses.error != null,
                        scheduleSyncedAt = courses.lastSyncedAt,
                    ) }
                }
            }
            account.lease.scope.launch { refresh() }
        }
    }

    suspend fun refresh() = coroutineScope {
        val account = tokenManager.account()
        val currentGeneration = loadMutex.withLock { generation }
        launch { courseLoader.refresh(account) }
        launch {
            val result = checkinRepository.getTasks(page = 1, pageSize = 5)
            val task = result.getOrNull()?.firstOrNull { !it.isSigned && it.isInOpenWindow }
            update(account, currentGeneration) { it.copy(nextUnsignedCheckin = task,
                checkinError = result.exceptionOrNull()?.message, checkinLoaded = true) }
        }
    }

    private suspend fun update(account: AccountContext, version: Long, transform: (HomeBootstrapSnapshot) -> HomeBootstrapSnapshot) {
        loadMutex.withLock {
            if (version != generation || !session.isCurrent(account.lease)) return
            _snapshot.value = _snapshot.value?.let(transform)
            if (_snapshot.value?.scheduleLoaded == true) _ready.value = true
        }
    }
}

data class HomeBootstrapSnapshot(
    val userName: String,
    val todayCourses: List<Course> = emptyList(),
    val tomorrowFirstSection: Int? = null,
    val currentWeek: Int = 0,
    val nextUnsignedCheckin: CheckinTask? = null,
    val scheduleError: String? = null,
    val checkinError: String? = null,
    val scheduleLoaded: Boolean = false,
    val checkinLoaded: Boolean = false,
    val todayKnown: Boolean = false,
    val tomorrowKnown: Boolean = false,
    val scheduleSyncedAt: Long? = null,
)
