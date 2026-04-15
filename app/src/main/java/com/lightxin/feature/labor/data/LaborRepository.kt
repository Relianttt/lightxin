package com.lightxin.feature.labor.data

import com.lightxin.core.auth.TokenManager
import com.lightxin.core.network.LaborRetrofit
import com.lightxin.feature.labor.domain.ActivityDetail
import com.lightxin.feature.labor.domain.ActivityRecord
import com.lightxin.feature.labor.domain.HoursSummary
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaborRepository @Inject constructor(
    private val api: LaborApi,
    private val tokenManager: TokenManager,
) {
    private suspend fun authParams(): Triple<String, String, String> {
        val userCode = tokenManager.getUserCode().orEmpty()
        val accessToken = tokenManager.getAccessToken().orEmpty()
        return Triple(userCode, userCode, accessToken) // xh = userCode
    }

    suspend fun getHoursSummary(): Result<HoursSummary> {
        return try {
            val (userCode, xh, token) = authParams()
            if (userCode.isBlank()) return Result.failure(Exception("登录信息已失效，请重新登录"))

            val response = api.queryHoursTotal(userCode, xh, token)
            val data = response.data
                ?: return Result.failure(Exception("获取工时数据失败"))

            Result.success(
                HoursSummary(
                    voluntaryTimes = data.voluntaryTimes?.toDoubleOrNull() ?: 0.0,
                    summerTimes = data.summerTimes?.toDoubleOrNull() ?: 0.0,
                    laborTimes = data.laborTimes?.toDoubleOrNull() ?: 0.0,
                    socialTimes = data.socialTimes?.toDoubleOrNull() ?: 0.0,
                    otherTimes = data.otherTimes?.toDoubleOrNull() ?: 0.0,
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapErrorMessage("获取工时", e), e))
        }
    }

    suspend fun getActivities(page: Int, pageSize: Int = 10): Result<List<ActivityRecord>> {
        return try {
            val (userCode, xh, token) = authParams()
            if (userCode.isBlank()) return Result.failure(Exception("登录信息已失效，请重新登录"))

            val response = api.queryActivities(userCode, xh, token, page.toString(), pageSize.toString())
            val list = response.data?.list.orEmpty()

            Result.success(
                list.map { row ->
                    ActivityRecord(
                        id = row.id ?: "",
                        projectTypeName = row.projectTypeName ?: "",
                        activityName = row.activityName ?: "",
                        serviceTimes = row.serviceTimes?.toDoubleOrNull() ?: 0.0,
                        createDate = row.createDate ?: "",
                    )
                }
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapErrorMessage("获取活动列表", e), e))
        }
    }

    suspend fun getActivityDetail(id: String, type: String): Result<ActivityDetail> {
        return try {
            val (userCode, xh, token) = authParams()
            if (userCode.isBlank()) return Result.failure(Exception("登录信息已失效，请重新登录"))

            val response = api.queryActivityDetail(userCode, xh, token, id, type)
            val data = response.data
                ?: return Result.failure(Exception("获取活动详情失败"))

            Result.success(
                ActivityDetail(
                    activityName = data.activityName ?: "",
                    activityType = data.activityType ?: "",
                    activityLevel = data.activityLevel ?: "",
                    organizer = data.organizer ?: "",
                    serviceTimes = data.serviceTimes?.toDoubleOrNull() ?: 0.0,
                    createDate = data.createDate ?: "",
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapErrorMessage("获取活动详情", e), e))
        }
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
object LaborModule {

    @Provides
    @Singleton
    fun provideLaborApi(@LaborRetrofit retrofit: Retrofit): LaborApi =
        retrofit.create(LaborApi::class.java)
}
