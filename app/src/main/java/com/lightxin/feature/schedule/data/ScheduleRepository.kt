package com.lightxin.feature.schedule.data

import com.lightxin.core.auth.TokenManager
import com.lightxin.core.network.CshRetrofit
import com.lightxin.feature.schedule.domain.Course
import com.lightxin.feature.schedule.domain.ScheduleData
import com.lightxin.feature.schedule.domain.WeekInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import retrofit2.Retrofit
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val api: ScheduleApi,
    private val tokenManager: TokenManager,
) {
    suspend fun getWeekInfo(): Result<WeekInfo> {
        return try {
            val response = api.getWeekList()
            val data = response.data
            val rows = response.rows

            if (data == null) {
                Result.failure(Exception("获取周次信息失败"))
            } else {
                Result.success(
                    WeekInfo(
                        currentWeek = data.week?.toIntOrNull() ?: 1,
                        totalWeeks = rows?.size ?: 18,
                        schoolYear = data.schoolYear ?: "",
                        schoolTerm = data.schoolTerm ?: "",
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapErrorMessage("获取周次", e), e))
        }
    }

    suspend fun getCourses(
        schoolYear: String,
        schoolTerm: String,
        week: Int,
    ): Result<ScheduleData> {
        return try {
            val userCode = tokenManager.getUserCode().orEmpty()
            if (userCode.isBlank()) {
                return Result.failure(Exception("登录信息已失效，请重新登录"))
            }

            val response = api.getTimeTable(
                userCode = userCode,
                schoolYear = schoolYear,
                schoolTerm = schoolTerm,
                week = week.toString(),
            )

            val courses = mutableListOf<Course>()
            val weekDates = mutableMapOf<Int, String>()
            response.rows?.forEach { day ->
                val dayOfWeek = day.xq?.toIntOrNull() ?: return@forEach
                if (day.rq != null) weekDates[dayOfWeek] = day.rq
                day.kcVoList?.forEach { vo ->
                    val startSection = vo.ksjc?.toIntOrNull() ?: return@forEach
                    val endSection = vo.jsjc?.toIntOrNull() ?: startSection
                    courses.add(
                        Course(
                            name = vo.kcmc ?: "",
                            startSection = startSection,
                            endSection = endSection,
                            room = vo.jsmc ?: "",
                            teacher = vo.teacherName ?: "",
                            dayOfWeek = dayOfWeek,
                        )
                    )
                }
            }

            Result.success(ScheduleData(courses = courses, weekDates = weekDates))
        } catch (e: Exception) {
            Result.failure(Exception(mapErrorMessage("加载课表", e), e))
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
object ScheduleModule {

    @Provides
    @Singleton
    fun provideScheduleApi(@CshRetrofit retrofit: Retrofit): ScheduleApi =
        retrofit.create(ScheduleApi::class.java)
}
