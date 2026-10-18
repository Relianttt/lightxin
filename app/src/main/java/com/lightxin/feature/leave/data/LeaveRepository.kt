package com.lightxin.feature.leave.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lightxin.core.auth.TokenManager
import com.lightxin.feature.leave.domain.LeaveAttachment
import com.lightxin.feature.leave.domain.LeaveFormSchemaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaveRepository @Inject constructor(
    private val api: LeaveApi,
    private val tokenManager: TokenManager,
) {
    @Volatile
    private var cachedDefinitionId: String? = null

    /** 列表行按 executionId 缓存：详情页据此补 ruTaskNodeId / statusTxt，避免再拉一遍列表。 */
    private val summaryCache = java.util.concurrent.ConcurrentHashMap<String, LeaveSummary>()

    /** executionId → 待办节点 id（销假用）。 */
    private val taskNodeCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun getDefinitionId(): Result<String> {
        cachedDefinitionId?.let { return Result.success(it) }
        return try {
            val response = api.getFlowDefinitions(emptyMap())
            val match = LeaveJson.collectObjectsWith(response, "definitionId")
                .firstOrNull { obj ->
                    val name = obj.string("name", "title", "flowName", "processName")
                    name.contains("学生请假")
                }
            val id = match?.string("definitionId", "id")
                ?.takeIf { it.isNotBlank() }
                ?: FALLBACK_DEFINITION_ID
            cachedDefinitionId = id
            Result.success(id)
        } catch (e: Exception) {
            // 流程定义接口失败不阻塞发起：直接落到已知 definitionId
            cachedDefinitionId = FALLBACK_DEFINITION_ID
            Result.success(FALLBACK_DEFINITION_ID)
        }
    }

    suspend fun getSchema(definitionId: String): Result<LeaveSchema> = runCatchingApi("获取请假表单") {
        val response = api.getFlowSheet(mapOf("processId" to definitionId))
        ensureSuccess(response, "获取请假表单失败")
        LeaveJson.parseSchema(response)
    }

    suspend fun getDefaults(definitionId: String): Result<JsonObject> = runCatchingApi("获取表单默认值") {
        val response = api.findDefinitionBase(mapOf("definitionId" to definitionId))
        LeaveJson.findFirstObject(response) { it.has("dataJson") || it.has("flowTitle") }
            ?.let { obj ->
                val dataJson = obj.get("dataJson")
                if (dataJson?.isJsonPrimitive == true) {
                    runCatching { JsonParser.parseString(dataJson.asString).asJsonObject }.getOrNull()
                } else if (dataJson?.isJsonObject == true) {
                    dataJson.asJsonObject
                } else {
                    obj
                }
            }
            ?.deepCopy()
            ?: JsonObject()
    }

    suspend fun submit(
        definitionId: String,
        dataJson: JsonObject,
        executionId: String?,
        draft: Boolean,
    ): Result<Unit> = runCatchingApi(if (draft) "暂存请假" else "提交请假") {
        val fields = mutableMapOf(
            "definitionId" to definitionId,
            "dataJson" to dataJson.toString(),
        )
        if (!executionId.isNullOrBlank()) fields["executionId"] = executionId
        val response = if (draft) api.saveForm(fields) else api.submitForm(fields)
        ensureSuccess(response, if (draft) "暂存失败" else "提交失败")
    }

    suspend fun myExecutions(
        page: Int,
        pageSize: Int = 20,
        status: String,
    ): Result<LeavePage> = runCatchingApi("获取请假列表") {
        val response = api.myExecution(
            mapOf(
                "currentPageNo" to page.toString(),
                "pageSize" to pageSize.toString(),
                "timeType" to "4",
                "status" to status,
            ),
        )
        ensureSuccess(response, "获取请假列表失败")
        val array = LeaveJson.findFirstArray(response) { it.has("executionId") }
            ?: com.google.gson.JsonArray()
        val items = array.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val obj = element.asJsonObject
            val id = obj.string("executionId", "id")
            if (id.isBlank()) return@mapNotNull null
            LeaveSummary(
                executionId = id,
                flowTitle = obj.string("flowTitle", "title", "processName"),
                statusTxt = obj.string("statusTxt", "statusName", "taskName", "status"),
                receiveTime = obj.string("receiveTime", "createTime", "createDate", "startTime", "beginTime", "gmtCreate"),
                flowNumber = obj.string("flowNumber", "code", "processNumber"),
                ruTaskNodeId = obj.string("ruTaskNodeId"),
            )
        }.distinctBy { it.executionId }
        items.forEach { summaryCache[it.executionId] = it }
        LeavePage(
            items = items,
            total = response.int("total").takeIf { it > 0 }
                ?: LeaveJson.collectObjectsWith(response, "total")
                    .firstOrNull()?.int("total")?.takeIf { it > 0 }
                ?: items.size.takeIf { page == 1 && items.size < pageSize },
        )
    }

    /**
     * 销假需要「待销假」这条待办任务节点：我的请求列表行不带它，待办列表才带。
     * H5 也是从待办点进去，路由里同时带 executionId 和 ruTaskNodeId。
     */
    suspend fun findTaskNodeId(executionId: String): String {
        taskNodeCache[executionId]?.let { return it }
        return runCatching {
            val response = api.getRunTask(
                mapOf(
                    "currentPageNo" to "1",
                    "pageSize" to "50",
                    "approvedType" to "3",
                ),
            )
            val array = LeaveJson.findFirstArray(response) { it.has("ruTaskNodeId") }
                ?: return@runCatching ""
            val row = array.firstOrNull { item ->
                item.isJsonObject && item.asJsonObject.string("executionId") == executionId
            }?.asJsonObject ?: return@runCatching ""
            row.string("ruTaskNodeId").also { node ->
                if (node.isNotBlank()) taskNodeCache[executionId] = node
            }
        }.getOrDefault("")
    }

    /** 只取计数：pageSize=1 拉一页，total 是服务端算的，不会随已加载页数变化。 */
    suspend fun count(status: String): Int? =
        myExecutions(page = 1, pageSize = 1, status = status).getOrNull()?.total

    /** 销假：H5 XjDialog 确认后调用 agree.do；附件可选，先按空列表提交。 */
    suspend fun submitCheckIn(
        executionId: String,
        ruTaskNodeId: String,
        dataJson: JsonObject,
    ): Result<Unit> =
        runCatchingApi("销假") {
            if (ruTaskNodeId.isBlank()) throw IllegalStateException("缺少销假待办节点，请返回列表重新进入")
            val stuQjsq = com.google.gson.JsonObject().apply {
                add("xjfjList", com.google.gson.JsonArray())
            }
            val response = api.agree(
                mapOf(
                    "dataJson" to dataJson.toString(),
                    "executionId" to executionId,
                    "ruTaskNodeId" to ruTaskNodeId,
                    "stuQjsq" to stuQjsq.toString(),
                    "_t" to System.currentTimeMillis().toString(),
                ),
            )
            ensureSuccess(response, "销假失败")
        }

    suspend fun getForm(executionId: String): Result<LeaveDetail> = runCatchingApi("获取请假详情") {
        val response = api.getForm(
            mapOf(
                "executionId" to executionId,
                // 抓包/实测：type=1 且 ruTaskNodeId 为空时服务端会挂起直到超时；type=0 正常返回
                "type" to "0",
                "ruTaskNodeId" to "",
            ),
        )
        ensureSuccess(response, "获取请假详情失败")
        val obj = LeaveJson.findFirstObject(response) { it.has("dataJson") || it.has("executionId") }
            ?: throw IllegalStateException("详情数据为空")
        val values = parseDataJson(obj.get("dataJson")) ?: obj
        val schema = findSchemaInDetail(obj)
        LeaveDetail(
            executionId = obj.string("executionId").ifBlank { executionId },
            flowTitle = obj.string("flowTitle", "title").ifBlank {
                values.string("flowTitle", "title")
            },
            statusTxt = obj.string("statusTxt", "statusName", "status").ifBlank {
                values.string("statusTxt", "statusName", "status")
            },
            receiveTime = obj.string("receiveTime", "createTime", "startTime").ifBlank {
                values.string("receiveTime", "createTime", "startTime")
            },
            flowNumber = obj.string("flowNumber", "code").ifBlank {
                values.string("flowNumber", "code")
            },
            fields = schema.ifEmpty { inferFieldsFromValues(values) },
            values = values,
            ruTaskNodeId = obj.string("ruTaskNodeId"),
        )
    }

    suspend fun findSummary(executionId: String): LeaveSummary? {
        summaryCache[executionId]?.let { return it }
        return runCatching {
            myExecutions(page = 1, pageSize = 50, status = "3").getOrNull()
                ?.items?.firstOrNull { it.executionId == executionId }
        }.getOrNull()
    }

    suspend fun canWithdraw(executionId: String): Boolean = runCatching {
        val response = api.getMyRequestButtons(
            mapOf("executionId" to executionId, "type" to "1"),
        )
        LeaveJson.findFirstObject(response) { obj ->
            obj.has("withdraw") || obj.has("canWithdraw")
        }?.let { obj ->
            obj.bool("withdraw", "canWithdraw")
        } ?: false
    }.getOrDefault(false)

    suspend fun withdraw(executionId: String): Result<Unit> = runCatchingApi("撤回请假") {
        val response = api.withdrawExecution(mapOf("executionId" to executionId))
        ensureSuccess(response, "撤回失败")
    }

    suspend fun getXjxx(executionId: String): Result<JsonObject?> = runCatchingApi("获取销假信息") {
        val response = api.getXjxx(mapOf("id" to executionId))
        if (!LeaveJson.isSuccess(response)) {
            throw IllegalStateException(LeaveJson.message(response, "获取销假信息失败"))
        }
        val obj = LeaveJson.findFirstObject(response) { candidate ->
            candidate.has("dataJson") || candidate.has("cjScStr") ||
                candidate.has("isCj") || candidate.has("executionId")
        } ?: throw IllegalStateException("销假信息为空")
        parseDataJson(obj.get("dataJson")) ?: obj
    }

    suspend fun uploadAttachment(file: File): Result<LeaveAttachment> = runCatchingApi("上传附件") {
        val part = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("application/octet-stream".toMediaType()),
        )
        val credentials = tokenManager.snapshot()
        val response = withContext(Dispatchers.IO) {
            api.uploadFile(
                file = part,
                userCode = credentials.userCode.orEmpty().toRequestBody("text/plain".toMediaType()),
                accessToken = credentials.accessToken.orEmpty().toRequestBody("text/plain".toMediaType()),
            )
        }
        val node = LeaveJson.findFirstObject(response) { obj ->
            obj.has("fileUrl") || obj.has("url")
        } ?: throw IllegalStateException(LeaveJson.message(response, "附件上传失败"))
        val url = node.string("fileUrl", "url")
        if (url.isBlank()) throw IllegalStateException("附件上传失败：没有返回地址")
        LeaveAttachment(
            uid = node.string("uid", "fileId", "id").ifBlank { url },
            fileName = node.string("fileName", "name").ifBlank { file.name },
            fileUrl = url,
        )
    }

    private fun findSchemaInDetail(obj: JsonObject): List<com.lightxin.feature.leave.domain.LeaveFormField> {
        val element = LeaveJson.unwrapJsonString(obj.get("formJson")) ?: obj
        return LeaveFormSchemaParser.parse(element)
    }

    private fun inferFieldsFromValues(values: JsonObject) = LeaveFormSchemaParser.parse(values)

    private fun parseDataJson(element: com.google.gson.JsonElement?): JsonObject? {
        if (element == null) return null
        if (element.isJsonObject) return element.asJsonObject
        if (element.isJsonPrimitive) {
            return runCatching {
                JsonParser.parseString(element.asString).takeIf { it.isJsonObject }?.asJsonObject
            }.getOrNull()
        }
        return null
    }

    private fun ensureSuccess(response: JsonObject, fallback: String) {
        if (!LeaveJson.isSuccess(response)) {
            throw IllegalStateException(LeaveJson.message(response, fallback))
        }
    }

    private suspend fun <T> runCatchingApi(action: String, block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(Exception(mapError(action, e), e))
        }
    }

    private fun mapError(action: String, error: Exception): String = when (error) {
        is HttpException -> when {
            error.code() == 401 -> "登录已失效，请重新登录"
            error.code() >= 500 -> "${action}接口暂时异常，请稍后重试"
            else -> "$action 失败（HTTP ${error.code()}）"
        }
        is IOException -> "网络异常，请检查连接后重试"
        else -> error.message ?: "$action 失败"
    }

    companion object {
        const val FALLBACK_DEFINITION_ID = "e77db6c3ae0a4911a42280f98ce45ebc"
    }
}
