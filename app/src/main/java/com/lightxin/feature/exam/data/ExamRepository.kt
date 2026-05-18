package com.lightxin.feature.exam.data

import com.lightxin.core.auth.TokenManager
import com.lightxin.core.network.CshRetrofit
import com.lightxin.feature.exam.domain.CurrentTerm
import com.lightxin.feature.exam.domain.ExamScore
import com.lightxin.feature.exam.domain.SchoolYear
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
class ExamRepository @Inject constructor(
    private val api: ExamApi,
    private val tokenManager: TokenManager,
) {
    suspend fun getCurrentTerm(): Result<CurrentTerm> {
        return try {
            val resp = api.getCurrentTerm()
            val vo = resp.data?.xlVo ?: return Result.failure(Exception("获取当前学期失败"))
            Result.success(CurrentTerm(schoolYear = vo.xn.orEmpty(), semester = vo.xq.orEmpty()))
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取当前学期", e), e))
        }
    }

    suspend fun getSchoolYears(): Result<List<SchoolYear>> {
        return try {
            val resp = api.getSchoolYears()
            val list = resp.rows.orEmpty().map {
                SchoolYear(display = it.text.orEmpty(), value = it.value.orEmpty())
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取学年列表", e), e))
        }
    }

    suspend fun getExamScores(schoolYear: String, semester: String): Result<List<ExamScore>> {
        return try {
            val userCode = tokenManager.getUserCode().orEmpty()
            if (userCode.isBlank()) return Result.failure(Exception("登录信息已失效"))
            val resp = api.getExamScores(userCode, schoolYear, semester)
            val list = resp.rows.orEmpty().map {
                ExamScore(
                    courseCode = it.kcdm.orEmpty(),
                    courseName = it.kcmc.orEmpty(),
                    department = it.kkbm.orEmpty(),
                    credit = it.xf.orEmpty(),
                    score = it.cj.orEmpty(),
                    examType = it.ksxz.orEmpty(),
                    gpa = it.hdjd.orEmpty(),
                    category = it.kclb.orEmpty(),
                    teacher = it.rkjs.orEmpty(),
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取考试成绩", e), e))
        }
    }

    private fun mapError(action: String, error: Exception): String {
        return when (error) {
            is HttpException -> when {
                error.code() == 401 -> "登录已失效，请重新登录"
                error.code() >= 500 -> "${action}接口暂时异常"
                else -> "${action}失败（HTTP ${error.code()}）"
            }
            is IOException -> "网络异常，请检查连接"
            else -> error.message ?: "${action}失败"
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ExamModule {

    @Provides
    @Singleton
    fun provideExamApi(@CshRetrofit retrofit: Retrofit): ExamApi =
        retrofit.create(ExamApi::class.java)
}
