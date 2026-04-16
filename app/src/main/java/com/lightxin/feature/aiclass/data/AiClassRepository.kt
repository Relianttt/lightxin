package com.lightxin.feature.aiclass.data

import android.util.Log
import com.lightxin.core.network.ApiConstants
import com.lightxin.core.network.FifOkHttpClient
import com.lightxin.core.network.FifRetrofit
import com.lightxin.core.network.FifSessionManager
import com.lightxin.feature.aiclass.domain.AiCourse
import com.lightxin.feature.aiclass.domain.AiSignInInfo
import com.lightxin.feature.aiclass.domain.AiWorkingRecord
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val REPOSITORY_LOG_TAG = "AiClassRepo"

@Singleton
class AiClassRepository @Inject constructor(
    private val api: AiClassApi,
    private val fifSession: FifSessionManager,
    @FifOkHttpClient private val fifClient: OkHttpClient,
) {
    /** 确保 FIF 会话有效，无效则自动 SSO */
    private suspend fun ensureSession(): String {
        if (!fifSession.isSessionValid()) {
            fifSession.performSso().getOrThrow()
        }
        return fifSession.buildAuthHeader()
    }

    /** 获取当前学期课程列表 */
    suspend fun getCourses(): Result<List<AiCourse>> {
        return try {
            val auth = ensureSession()

            // 先获取当前学期（selected=1）
            val termResp = api.getTermList(auth)
            val currentTerm = termResp.data?.dataList?.find { it.selected == "1" }
            val termYear = currentTerm?.year ?: return Result.failure(Exception("无法获取当前学期"))
            val term = currentTerm.num ?: return Result.failure(Exception("无法获取当前学期"))

            // 查询课程
            val resp = api.getCourses(termYear, term, auth)
            val list = resp.data?.dataList.orEmpty()

            Result.success(
                list.map { item ->
                    AiCourse(
                        classId = item.classId.orEmpty(),
                        courseId = item.courseId.orEmpty(),
                        courseRecordId = item.courseRecordId.orEmpty(),
                        courseName = item.courseName.orEmpty(),
                        teacherName = item.teacherName.orEmpty(),
                        studentNum = item.studentNum ?: 0,
                        teachClassId = item.teachClassId.orEmpty(),
                        cover = item.cover.orEmpty(),
                    )
                },
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取课程列表", e), e))
        }
    }

    /** 查询正在进行的课堂 */
    suspend fun getWorkingRecord(): Result<AiWorkingRecord?> {
        return try {
            val auth = ensureSession()
            val studentId = fifSession.getStudentId().orEmpty()
            val resp = api.getWorkingCourseRecord(studentId, auth)
            val data = resp.data.orEmpty().firstOrNull()

            if (data?.courseRecordId.isNullOrBlank()) {
                Result.success(null)
            } else {
                Result.success(
                    AiWorkingRecord(
                        courseRecordId = data!!.courseRecordId!!,
                        courseName = data.courseName.orEmpty(),
                        courseItemName = data.courseItemName.orEmpty(),
                        teachClassId = data.teachClassId
                            ?.takeIf { it.isNotBlank() }
                            ?: data.courseId.orEmpty(),
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取课堂状态", e), e))
        }
    }

    /** 查询签到信息 */
    suspend fun getSignInInfo(teachClassId: String, courseRecordId: String): Result<AiSignInInfo?> {
        return try {
            val auth = ensureSession()
            val studentId = fifSession.getStudentId().orEmpty()
            val resp = api.getSignInInfo(teachClassId, courseRecordId, studentId, auth)
            val data = resp.data

            if (data?.signId.isNullOrBlank()) {
                Result.success(null)
            } else {
                Result.success(
                    AiSignInInfo(
                        signId = data!!.signId!!,
                        teacherName = data.teacherName.orEmpty(),
                        hasActiveSign = true,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("查询签到", e), e))
        }
    }

    /** 数字码签到 */
    suspend fun submitSignCode(signId: String, signCode: String): Result<String> {
        return try {
            val auth = ensureSession()
            val userName = fifSession.getUserName().orEmpty()
            val resp = api.submitSignCode(signId, userName, signCode, auth)

            if (resp.status == "success") {
                Result.success(resp.message ?: "签到成功")
            } else {
                Result.failure(Exception(resp.message ?: "签到失败"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("签到", e), e))
        }
    }

    /**
     * 扫码签到：用二维码 token 调用 qrcodeHandler。
     * 该接口返回 302 跳转到成功页，不是 JSON。
     */
    suspend fun submitQrCode(token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(
                REPOSITORY_LOG_TAG,
                "submitQrCode start, token=${token.previewForLog()}, length=${token.length}",
            )
            val auth = ensureSession()
            val url = ApiConstants.BASE_FIF +
                "/coursecenter-interaction/qrcodeV2/qrcodeHandler?token=$token&openTheWay=2"

            val noRedirectClient = fifClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .header("authorization", auth)
                .header("Visit-Type", "mobile")
                .build()

            val response = noRedirectClient.newCall(request).execute()
            val code = response.code
            val location = response.header("Location").orEmpty()
            response.close()

            Log.i(
                REPOSITORY_LOG_TAG,
                "qrcodeHandler finished, httpCode=$code, location=${location.previewForLog(96)}",
            )

            if (code == 302 && location.contains("codeExpired", ignoreCase = true)) {
                Result.failure(Exception("二维码已过期"))
            } else if (code == 302 && location.contains("signSuccess", ignoreCase = true)) {
                Result.success("扫码签到成功")
            } else if (code == 302) {
                Result.failure(Exception("暂不支持该课堂二维码"))
            } else {
                Result.failure(Exception("签到失败（HTTP $code）"))
            }
        } catch (e: Exception) {
            Log.e(REPOSITORY_LOG_TAG, "submitQrCode failed", e)
            Result.failure(Exception(mapError("扫码签到", e), e))
        }
    }

    private fun mapError(action: String, error: Exception): String {
        return when (error) {
            is HttpException -> when {
                error.code() == 401 -> "FIF 登录已失效，请重试"
                error.code() >= 500 -> "${action}接口暂时异常"
                else -> "${action}失败（HTTP ${error.code()}）"
            }
            is IOException -> "网络异常，请检查连接"
            else -> error.message ?: "${action}失败"
        }
    }
}

private fun String.previewForLog(maxLen: Int = 16): String {
    if (isBlank()) return "<blank>"
    return if (length <= maxLen) this else take(maxLen) + "..."
}

@Module
@InstallIn(SingletonComponent::class)
object AiClassModule {

    @Provides
    @Singleton
    fun provideAiClassApi(@FifRetrofit retrofit: Retrofit): AiClassApi =
        retrofit.create(AiClassApi::class.java)
}
