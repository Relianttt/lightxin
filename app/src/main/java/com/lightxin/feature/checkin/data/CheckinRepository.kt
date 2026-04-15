package com.lightxin.feature.checkin.data

import com.lightxin.core.network.CheckinRetrofit
import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.checkin.domain.TaskDetail
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.google.gson.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckinRepository @Inject constructor(
    private val api: CheckinApi,
    private val fileApi: FileUploadApi,
) {
    suspend fun getTasks(page: Int, pageSize: Int = 10): Result<List<CheckinTask>> {
        return try {
            val body = mapOf<String, Any>(
                "pageNum" to page,
                "pageSize" to pageSize,
                "type" to "1",
                "taskMajorType" to "3",
            )
            val response = api.pageStudentSignIn(body)
            if (!response.isSuccess()) {
                return Result.failure(Exception(response.msg ?: "获取签到任务失败"))
            }
            val list = response.rows ?: response.data?.list.orEmpty()

            Result.success(
                list.map { row ->
                    CheckinTask(
                        id = row.id ?: "",
                        taskName = row.title ?: row.taskName ?: "",
                        taskDateId = row.taskDateId ?: row.id ?: row.userTaskId ?: row.taskId ?: "",
                        isSigned = row.executionedStatus == "1" || row.signinStatus == "1",
                        startTime = row.timedStartTime ?: row.startTime ?: row.collectionStartTime ?: "",
                        endTime = row.timedEndTime ?: row.endTime ?: row.collectionEndTime ?: "",
                    )
                }
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取签到任务", e), e))
        }
    }

    suspend fun getTaskDetail(dateId: String): Result<TaskDetail> {
        return try {
            val body = mapOf("dateId" to dateId)
            val response = api.getTaskInfoByDateId(body)
            if (!response.isSuccess()) {
                return Result.failure(Exception(response.msg ?: "获取任务详情失败"))
            }
            val data = response.data
                ?: return Result.failure(Exception("获取任务详情失败"))
            val firstLocation = data.signinLocations?.firstOrNull()
            val (centerLng, centerLat) = firstLocation?.lngLat
                ?.split(",")
                ?.let { parts ->
                    val lng = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                    val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                    lng to lat
                } ?: (data.lng?.toDoubleOrNull() ?: 0.0) to (data.lat?.toDoubleOrNull() ?: 0.0)

            Result.success(
                TaskDetail(
                    taskName = data.title ?: data.taskName ?: "",
                    taskDateId = data.dateId ?: data.taskDateId ?: dateId,
                    startTime = data.timedStartTime ?: data.startTime ?: data.collectionStartTime ?: "",
                    endTime = data.timedEndTime ?: data.endTime ?: data.collectionEndTime ?: "",
                    needPhoto = data.photoRequire == "1" || data.signinPhoto == "1" || data.needPhoto == "1",
                    isSigned = data.signStatus == "1" || data.signinStatus == "1",
                    signinPlace = firstLocation?.signinLocation ?: data.signinPlace ?: "",
                    locationRange = firstLocation?.content?.toString()?.toDoubleOrNull()
                        ?: data.locationRange?.toDoubleOrNull()
                        ?: 0.0,
                    centerLng = centerLng,
                    centerLat = centerLat,
                    address = firstLocation?.signinLocation ?: data.address ?: "",
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取任务详情", e), e))
        }
    }

    suspend fun uploadPhoto(file: File): Result<String> {
        return try {
            val requestBody = file.asRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
            val response = fileApi.uploadFile(part)

            val url = response.data.extractUploadUrl()
            if (url.isNullOrBlank()) {
                Result.failure(Exception(response.msg ?: "照片上传失败"))
            } else {
                Result.success(url)
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("上传照片", e), e))
        }
    }

    suspend fun submitSignIn(
        taskDateId: String,
        photoUrl: String,
        place: String,
        lngLatString: String,   // "经度,纬度" BD-09格式
    ): Result<Unit> {
        return try {
            val body = mapOf(
                "taskDateId" to taskDateId,
                "signinPhoto" to photoUrl,
                "signinPlace" to place,
                "latLng" to lngLatString,
                "outSignin" to "0",
                "outSigninDesc" to "",
            )
            val response = api.signIn(body)
            if (response.code == "200" || response.code == "0") {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.msg ?: "签到失败"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("提交签到", e), e))
        }
    }

    private fun mapError(action: String, error: Exception): String {
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

    private fun SignInPageResponse.isSuccess(): Boolean = code == "0" || code == "200"

    private fun TaskInfoResponse.isSuccess(): Boolean = code == "0" || code == "200"

    private fun JsonElement?.extractUploadUrl(): String? {
        val element = this ?: return null
        if (element.isJsonNull) return null
        if (element.isJsonPrimitive) return element.asString
        if (!element.isJsonObject) return null

        val obj = element.asJsonObject
        val commonKeys = listOf("url", "fileUrl", "fullPath", "path", "filePath")
        commonKeys.forEach { key ->
            obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        }

        obj.entrySet().forEach { (_, value) ->
            if (value.isJsonPrimitive) {
                val text = value.asString
                if (text.startsWith("http://") || text.startsWith("https://") || text.contains("/group")) {
                    return text
                }
            }
        }

        return null
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CheckinModule {

    @Provides
    @Singleton
    fun provideCheckinApi(@CheckinRetrofit retrofit: Retrofit): CheckinApi =
        retrofit.create(CheckinApi::class.java)

    @Provides
    @Singleton
    fun provideFileUploadApi(@CheckinRetrofit retrofit: Retrofit): FileUploadApi =
        retrofit.create(FileUploadApi::class.java)
}
