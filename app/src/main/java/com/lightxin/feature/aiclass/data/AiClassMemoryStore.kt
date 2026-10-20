package com.lightxin.feature.aiclass.data

import com.lightxin.core.auth.AccountContext
import com.lightxin.core.auth.AccountLease
import com.lightxin.core.auth.AccountSession
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.network.*
import com.lightxin.feature.aiclass.domain.AiCourse
import com.lightxin.feature.aiclass.domain.AiWorkingRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

data class AiCourseSnapshot(val termYear: String, val term: String, val courses: List<AiCourse>)

data class AiClassMemorySnapshot(
    val generation: Long = -1,
    val courses: AiCourseSnapshot? = null,
    val coursesAt: Long? = null,
    val workingRecord: AiWorkingRecord? = null,
    val workingAt: Long? = null,
    val failures: Map<FifOperation, FifMappingFailure> = emptyMap(),
)

internal fun Throwable.definitiveFifFailure(): FifUserMappingException? {
    val causes = rootCauseChain(this)
    val mapping = causes.filterIsInstance<FifUserMappingException>().firstOrNull()
    if (mapping != null && (mapping.reason == FifMappingReason.ACCOUNT_INFO || mapping.httpStatus in listOf(401, 403))) return mapping
    val http = causes.filterIsInstance<HttpException>().firstOrNull { it.code() in listOf(401, 403) }
    return http?.let { FifUserMappingException(httpStatus = it.code(), cause = it) }
}

@Singleton
class AiClassMemoryStore @Inject constructor(
    private val session: AccountSession,
    private val tokens: TokenManager,
) {
    private class Owner(val account: AccountContext) {
        val scope = CoroutineScope(account.lease.scope.coroutineContext + SupervisorJob(account.lease.scope.coroutineContext[Job]))
    }

    private val lock = Any()
    private var owner: Owner? = null
    private val mutable = MutableStateFlow(AiClassMemorySnapshot())
    val snapshots: Flow<AiClassMemorySnapshot> = combine(mutable, session.leases) { value, lease ->
        if (value.generation == lease.generation) value else AiClassMemorySnapshot()
    }
    private val coursesFlight = SingleFlight<Any, Result<AiCourseSnapshot>>()
    private val workingFlight = SingleFlight<Any, Result<AiWorkingRecord?>>()

    fun snapshot(): AiClassMemorySnapshot = mutable.value.takeIf { it.generation == session.capture().generation } ?: AiClassMemorySnapshot()

    fun markBlocked(lease: AccountLease) = synchronized(lock) {
        session.publish(lease) {
            val cached = snapshot().copy(generation = lease.generation)
            val failures = listOf(FifOperation.COURSES, FifOperation.WORKING_RECORD).associateWith {
                FifMappingFailure(FifMappingReason.ACCOUNT_INFO, it)
            }
            mutable.value = cached.copy(failures = cached.failures + failures)
        }
    }

    fun clear() = synchronized(lock) {
        owner?.scope?.cancel()
        owner = null
        mutable.value = AiClassMemorySnapshot()
    }

    private suspend fun owner(): Owner {
        val account = tokens.account()
        require(account.userCode.isNotBlank()) { "校内登录信息已失效，请重新登录" }
        return synchronized(lock) {
            session.requireCurrent(account.lease)
            owner?.takeIf { it.account == account } ?: Owner(account).also {
                owner?.scope?.cancel()
                owner = it
                if (mutable.value.generation != account.lease.generation) {
                    mutable.value = AiClassMemorySnapshot(generation = account.lease.generation)
                }
            }
        }
    }

    suspend fun courses(maxAgeMs: Long = 0, fetch: suspend () -> Result<AiCourseSnapshot>): Result<AiCourseSnapshot> {
        val current = owner()
        return coursesFlight.run(current, current.scope) {
            val cached = snapshot()
            if (cached.courses != null && cached.coursesAt != null && FifOperation.COURSES !in cached.failures &&
                System.currentTimeMillis() - cached.coursesAt < maxAgeMs) return@run Result.success(cached.courses)
            val result = timed("获取课程列表", 18_000, fetch)
            publish(current, FifOperation.COURSES, result) { value -> copy(courses = value, coursesAt = System.currentTimeMillis()) }
            result
        }
    }

    suspend fun working(maxAgeMs: Long = 0, fetch: suspend () -> Result<AiWorkingRecord?>): Result<AiWorkingRecord?> {
        val current = owner()
        return workingFlight.run(current, current.scope) {
            val cached = snapshot()
            if (cached.workingAt != null && FifOperation.WORKING_RECORD !in cached.failures &&
                System.currentTimeMillis() - cached.workingAt < maxAgeMs) return@run Result.success(cached.workingRecord)
            val result = timed("获取课堂状态", 8_000, fetch)
            publish(current, FifOperation.WORKING_RECORD, result) { value -> copy(workingRecord = value, workingAt = System.currentTimeMillis()) }
            result
        }
    }

    private fun <T> publish(current: Owner, operation: FifOperation, result: Result<T>, success: AiClassMemorySnapshot.(T) -> AiClassMemorySnapshot) {
        synchronized(lock) {
            if (owner !== current) throw CancellationException("AI session reset")
            session.requireCurrent(current.account.lease)
            session.publish(current.account.lease) {
                val failures = mutable.value.failures.toMutableMap()
                val error = result.exceptionOrNull()?.definitiveFifFailure()
                if (error != null) failures[operation] = error.fifMappingFailure(operation)!!
                else if (result.isSuccess) failures.remove(operation)
                mutable.value = (if (result.isSuccess) mutable.value.success(result.getOrThrow()) else mutable.value).copy(failures = failures)
            }
        }
    }

    private suspend fun <T> timed(action: String, timeout: Long, fetch: suspend () -> Result<T>): Result<T> = try {
        val result = withTimeout(timeout) { fetch() }
        val rejection = result.exceptionOrNull()?.definitiveFifFailure()
        if (rejection == null) result else Result.failure(rejection)
    } catch (e: TimeoutCancellationException) {
        Result.failure(Exception("${action}超时，请稍后重试", e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.definitiveFifFailure() ?: e)
    }
}
