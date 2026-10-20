package com.lightxin.feature.schedule.data

import com.lightxin.core.auth.TokenManager
import com.lightxin.core.auth.AccountContext
import com.lightxin.core.auth.AccountSession
import com.lightxin.core.database.LightXinDatabase
import com.lightxin.core.network.CshRetrofit
import com.lightxin.core.network.SingleFlight
import com.lightxin.feature.schedule.domain.ScheduleData
import com.lightxin.feature.schedule.domain.WeekInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import com.lightxin.feature.schedule.data.local.ScheduleCacheMapper
import com.lightxin.feature.schedule.data.local.ScheduleWeekSnapshot
import com.lightxin.feature.schedule.data.local.SemesterSnapshot
import com.lightxin.feature.schedule.data.local.SCHOOL_ZONE
import java.time.LocalDate
import java.io.IOException
import retrofit2.Retrofit
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val api: ScheduleApi,
    private val tokenManager: TokenManager,
    private val session: AccountSession,
    private val database: LightXinDatabase,
) {
    private val dao = database.scheduleCacheDao()
    private val semesters = SingleFlight<String, Result<SemesterSnapshot>>()
    private val weeks = SingleFlight<String, Result<ScheduleWeekSnapshot>>()

    suspend fun account(): AccountContext = tokenManager.account()

    fun observeSemester(account: AccountContext): Flow<SemesterSnapshot?> =
        dao.observeSemester(account.userCode).map { entity ->
            if (session.isCurrent(account.lease)) entity?.let(ScheduleCacheMapper::semester) else null
        }.flowOn(Dispatchers.IO)

    suspend fun cachedSemester(account: AccountContext): SemesterSnapshot? = withContext(Dispatchers.IO) {
        session.requireCurrent(account.lease)
        dao.semester(account.userCode)?.let(ScheduleCacheMapper::semester)
    }

    fun observeWeek(account: AccountContext, info: WeekInfo, week: Int): Flow<ScheduleWeekSnapshot?> =
        dao.observeWeek(account.userCode, info.schoolYear, info.schoolTerm, week).map { entity ->
            if (session.isCurrent(account.lease)) entity?.let(ScheduleCacheMapper::week) else null
        }.flowOn(Dispatchers.IO)

    suspend fun cachedWeek(account: AccountContext, info: WeekInfo, week: Int): ScheduleWeekSnapshot? = withContext(Dispatchers.IO) {
        session.requireCurrent(account.lease)
        dao.week(account.userCode, info.schoolYear, info.schoolTerm, week)?.let(ScheduleCacheMapper::week)
    }

    suspend fun cachedWeekNumbers(account: AccountContext, info: WeekInfo): Set<Int> = withContext(Dispatchers.IO) {
        session.requireCurrent(account.lease)
        dao.weeks(account.userCode, info.schoolYear, info.schoolTerm)
            .filter { ScheduleCacheMapper.week(it) != null }.map { it.weekNumber }.toSet()
    }

    suspend fun refreshSemester(account: AccountContext): Result<SemesterSnapshot> =
        semesters.run("${account.lease.generation}:${account.userCode}", account.lease.scope) {
            request(account, "获取周次") {
                val snapshot = SemesterSnapshot(api.getWeekList().toWeekInfo(), LocalDate.now(SCHOOL_ZONE), System.currentTimeMillis())
                database.withTransaction {
                    session.requireCurrent(account.lease)
                    dao.saveSemester(ScheduleCacheMapper.semester(account.userCode, snapshot))
                    session.requireCurrent(account.lease)
                }
                snapshot
            }
        }

    suspend fun refreshWeek(account: AccountContext, info: WeekInfo, week: Int): Result<ScheduleWeekSnapshot> =
        weeks.run("${account.lease.generation}:${account.userCode}:${info.schoolYear}:${info.schoolTerm}:$week", account.lease.scope) {
            request(account, "加载课表") {
                require(week in info.weekNumbers) { "周次不在当前学期列表中" }
                val response = api.getTimeTable(account.userCode, info.schoolYear, info.schoolTerm, week.toString())
                val snapshot = ScheduleWeekSnapshot(response.toScheduleData(), System.currentTimeMillis())
                database.withTransaction {
                    session.requireCurrent(account.lease)
                    dao.saveWeek(ScheduleCacheMapper.week(account.userCode, info, week, snapshot))
                    session.requireCurrent(account.lease)
                }
                snapshot
            }
        }

    suspend fun getWeekInfo(): Result<WeekInfo> = refreshSemester(account()).map { it.info }

    suspend fun getCourses(schoolYear: String, schoolTerm: String, week: Int): Result<ScheduleData> =
        refreshWeek(account(), WeekInfo(week, 1, schoolYear, schoolTerm, listOf(week)), week).map { it.data }

    private suspend fun <T> request(account: AccountContext, action: String, block: suspend () -> T): Result<T> = try {
        session.requireCurrent(account.lease)
        require(account.userCode.isNotBlank()) { "登录信息已失效，请重新登录" }
        val data = block()
        session.requireCurrent(account.lease)
        Result.success(data)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(Exception(mapErrorMessage(action, e), e))
    }

    private fun mapErrorMessage(action: String, error: Exception): String {
        return when (error) {
            is HttpException -> when {
                error.code() == 401 -> "登录已失效，请重新登录"
                error.code() >= 500 -> "${action}接口暂时异常，请稍后重试"
                else -> "${action}失败（HTTP ${error.code()}）"
            }

            is IOException -> "网络异常，请检查连接后重试"
            else -> error.message ?: "${action}失败"
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ScheduleModule {

    @Provides
    @Singleton
    fun provideScheduleApi(@CshRetrofit retrofit: Retrofit): ScheduleApi =
        retrofit.create(ScheduleApi::class.java)
}
