package com.lightxin.feature.holiday.data

import com.lightxin.core.auth.TokenManager
import com.lightxin.core.network.CheckinRetrofit
import com.lightxin.feature.holiday.domain.HolidayFormData
import com.lightxin.feature.holiday.domain.HolidayTask
import com.lightxin.feature.holiday.domain.StrokeOption
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HolidayRepository @Inject constructor(
    @CheckinRetrofit private val retrofit: Retrofit,
    private val tokenManager: TokenManager,
) {
    private val api: HolidayApi = retrofit.create(HolidayApi::class.java)

    /** 获取登记列表（分页），返回领域模型列表 */
    suspend fun getRegistrationList(page: Int): Result<List<HolidayTask>> {
        return try {
            val studentId = getStudentId()
            val body = mapOf(
                "studentId" to studentId,
                "pageNum" to page,
            )
            val response = api.getRegistrationPage(body)
            if (!response.isSuccess()) {
                return Result.failure(Exception(response.msg ?: "获取节假日列表失败"))
            }
            val rows = response.rows.orEmpty()
            // 列表接口不返回个人登记状态，对每个 holiday 并发查 getHolidayRegister 补齐
            val tasks = coroutineScope {
                rows.map { row ->
                    async {
                        val holidayId = row.id.orEmpty()
                        val registered = if (holidayId.isBlank()) {
                            false
                        } else {
                            runCatching { api.getHolidayRegister(holidayId, studentId) }
                                .getOrNull()
                                ?.let { it.isSuccess() && it.data != null }
                                ?: false
                        }
                        row.toDomain(isRegistered = registered)
                    }
                }.awaitAll()
            }
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取节假日列表", e), e))
        }
    }

    /** 获取节假日详细配置 */
    suspend fun getHolidayDetail(holidayId: String): Result<HolidayDetail> {
        return try {
            val response = api.getHolidaySetById(holidayId)
            if (!response.isSuccess()) {
                return Result.failure(Exception(response.msg ?: "获取节假日详情失败"))
            }
            val data = response.data
                ?: return Result.failure(Exception("获取节假日详情失败"))
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取节假日详情", e), e))
        }
    }

    /** 获取离校/留校字典选项 */
    suspend fun getStrokeTypes(): Result<List<StrokeOption>> {
        return try {
            val body = mapOf("dictType" to "app_stroke_type")
            val response = api.listDict(body)
            if (!response.isSuccess()) {
                return Result.failure(Exception(response.msg ?: "获取字典失败"))
            }
            val items = response.data.orEmpty()
            Result.success(
                items.mapNotNull { item ->
                    val label = item.label ?: return@mapNotNull null
                    val value = item.value ?: return@mapNotNull null
                    StrokeOption(label = label, value = value)
                }
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取字典选项", e), e))
        }
    }

    /** 获取已有登记记录，返回 null 表示无记录（可新建） */
    suspend fun getExistingRegister(holidayId: String): Result<HolidayFormData?> {
        return try {
            val response = api.getHolidayRegister(holidayId, getStudentId())
            if (!response.isSuccess()) {
                return Result.failure(Exception(response.msg ?: "获取已有登记失败"))
            }
            val data = response.data
            if (data == null) {
                return Result.success(null)
            }
            val item = data.list?.firstOrNull()
            Result.success(
                HolidayFormData(
                    registerId = data.id.orEmpty(),
                    startDate = item?.startDate.orEmpty(),
                    endDate = item?.endDate.orEmpty(),
                    stroke = item?.stroke ?: "0",
                    reason = item?.reason.orEmpty(),
                    destination = item?.destination.orEmpty(),
                    urgentPhone = item?.urgentPhone.orEmpty(),
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取已有登记", e), e))
        }
    }

    /** 提交/保存登记 */
    suspend fun saveRegistration(
        holidayId: String,
        form: HolidayFormData,
    ): Result<Unit> {
        return try {
            val listItem = mutableMapOf<String, Any>(
                "startDate" to form.startDate,
                "endDate" to form.endDate,
                "stroke" to form.stroke,
                "reason" to form.reason,
                "destination" to form.destination,
                "urgentPhone" to form.urgentPhone,
                "enableAttachmentList" to emptyList<String>(),
                "requireAttachmentList" to emptyList<String>(),
            )
            val body = mapOf<String, Any>(
                "id" to form.registerId,
                "holidayId" to holidayId,
                "studentId" to getStudentId(),
                "list" to listOf(listItem),
            )
            val response = api.save(body)
            if (response.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.msg ?: "提交登记失败"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("提交登记", e), e))
        }
    }

    /** 获取第一个未登记的节假日（给首页用） */
    suspend fun getFirstUnregistered(): Result<HolidayTask?> {
        val result = getRegistrationList(page = 1)
        return result.map { list -> list.firstOrNull { !it.isRegistered } }
    }

    private suspend fun getStudentId(): String =
        tokenManager.getUserCode().orEmpty()

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

    private fun RegistrationPageResponse.isSuccess(): Boolean = code == "0"

    private fun HolidayDetailResponse.isSuccess(): Boolean = code == "0"

    private fun DictResponse.isSuccess(): Boolean = code == "0"

    private fun HolidayRegisterResponse.isSuccess(): Boolean = code == "0"

    private fun SaveResponse.isSuccess(): Boolean = code == "0"

    private fun HolidayRow.toDomain(isRegistered: Boolean): HolidayTask = HolidayTask(
        holidayId = id.orEmpty(),
        name = name.orEmpty(),
        registerStartDate = registerStartDate.orEmpty(),
        registerEndDate = registerEndDate.orEmpty(),
        isRegistered = isRegistered,
        allowStaySchool = allowStaySchool == "1",
        startDate = startDate.orEmpty(),
        endDate = endDate.orEmpty(),
    )
}
