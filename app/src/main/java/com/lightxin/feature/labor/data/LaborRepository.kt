package com.lightxin.feature.labor.data

import com.lightxin.core.auth.TokenManager
import com.lightxin.core.network.AuthExpiredException
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
            if (isLaborAuthRejection(response.flag, response.result)) {
                return Result.failure(laborAuthRejected("获取工时总览", response.flag, response.result))
            }
            val data = response.data?.data
                ?: return Result.failure(Exception(response.result ?: "获取工时数据失败"))

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
            if (isLaborAuthRejection(response.flag, response.result)) {
                return Result.failure(laborAuthRejected("获取活动列表", response.flag, response.result))
            }
            val list = response.rows.orEmpty()

            Result.success(
                list.map { row ->
                    ActivityRecord(
                        id = row.id ?: "",
                        projectTypeName = row.projectTypeName ?: "",
                        type = row.type ?: "",
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
            if (isLaborAuthRejection(response.flag, response.result)) {
                return Result.failure(laborAuthRejected("获取活动详情", response.flag, response.result))
            }
            val data = response.data?.data
                ?: return Result.failure(Exception(response.result ?: "获取活动详情失败"))

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

/**
 * `ldjy` 用 200 + `{"flag":false,"result":"非法访问"}` 表达「token 无效」。
 * 不转成登录失效，界面就会把它原样显示成「非法访问」—— 用户看不懂，也没法据此重登。
 *
 * 注意：查寝 `fdygl` 的 `-100 非法访问` 是另一回事（7 个身份字段没带全），不要在那边套这个判定。
 */
internal fun isLaborAuthRejection(flag: Boolean?, result: String?): Boolean =
    flag == false && result?.contains("非法访问") == true

/** 把拒绝包装成带结构化详情的登录失效异常，供界面出「服务不可用」卡片。 */
internal fun laborAuthRejected(action: String, flag: Boolean?, result: String?): AuthExpiredException =
    AuthExpiredException(
        technicalDetail = buildString {
            append("LABOR_REJECTED\n")
            append("操作：$action\n")
            append("阶段：ldjy 身份校验\n")
            append("响应：flag=${flag ?: "null"} / result=${result.orEmpty().ifBlank { "无" }}")
        },
    )

@Module
@InstallIn(SingletonComponent::class)
object LaborModule {

    @Provides
    @Singleton
    fun provideLaborApi(@LaborRetrofit retrofit: Retrofit): LaborApi =
        retrofit.create(LaborApi::class.java)
}
