package com.lightxin.feature.schedule.data

import com.lightxin.core.auth.AccountContext
import com.lightxin.core.auth.AccountSession
import com.lightxin.feature.schedule.domain.WeekInfo
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleSyncCoordinator @Inject constructor(
    private val repository: ScheduleRepository,
    private val session: AccountSession,
) {
    private val lock = Any()
    private var foreground = false
    private val pending = mutableMapOf<String, Pair<AccountContext, WeekInfo>>()
    private val jobs = mutableMapOf<String, Job>()

    fun onForeground(active: Boolean) = synchronized(lock) {
        foreground = active
        if (!active) jobs.values.toList().forEach { it.cancel() }
        else {
            pending.entries.removeAll { !session.isCurrent(it.value.first.lease) }
            pending.values.toList().forEach { (account, info) -> start(account, info) }
        }
    }

    fun start(account: AccountContext, info: WeekInfo) = synchronized(lock) {
        if (!session.isCurrent(account.lease)) return@synchronized
        val key = "${account.lease.generation}:${account.userCode}:${info.schoolYear}:${info.schoolTerm}"
        pending[key] = account to info
        if (!foreground || jobs[key]?.isActive == true) return@synchronized
        val job = account.lease.scope.launch(start = CoroutineStart.LAZY) {
            sync(account, info)
            synchronized(lock) { pending.remove(key) }
        }
        jobs[key] = job
        job.invokeOnCompletion { synchronized(lock) { if (jobs[key] === job) jobs.remove(key) } }
        job.start()
    }

    internal suspend fun sync(account: AccountContext, info: WeekInfo): Result<Unit> {
        val next = (info.currentWeek + 1).takeIf { it in info.weekNumbers }
        val priority = listOfNotNull(info.currentWeek, next)
        val cached = repository.cachedWeekNumbers(account, info)
        val ordered = (priority + info.weekNumbers.filter { it !in cached }).distinct()
        for (week in ordered) {
            session.requireCurrent(account.lease)
            val snapshot = repository.cachedWeek(account, info, week)
            if (snapshot != null && (week !in priority || System.currentTimeMillis() - snapshot.syncedAt < 12 * 3_600_000L)) continue
            val result = repository.refreshWeek(account, info, week)
            if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)
            delay(150)
        }
        return Result.success(Unit)
    }
}
