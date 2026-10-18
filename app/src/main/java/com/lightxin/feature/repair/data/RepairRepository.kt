package com.lightxin.feature.repair.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lightxin.core.auth.TokenManager
import com.lightxin.feature.repair.domain.RepairDraft
import com.lightxin.feature.repair.domain.RepairNode
import com.lightxin.feature.repair.domain.RepairSubmitBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class RepairSummary(
    val id: String,
    val bxdh: String,
    val bxdd: String,
    val bxlx: String,
    val gzms: String,
    val bxsj: String,
    val dqzt: String,
    val dqztMc: String,
    val wxyXm: String,
    val wxySjh: String,
    val sjh: String,
    val photoUrl: String?,
)

data class RepairStep(val clms: String, val sj: String)

data class RepairDetail(
    val id: String,
    val bxdh: String,
    val bxdd: String,
    val bxlx: String,
    val gzms: String,
    val bxsj: String,
    val dqzt: String,
    val dqztMc: String,
    val xxdz: String,
    val xqmc: String,
    val lymc: String,
    val qyfjh: String,
    val sjh: String,
    val xm: String,
    val yjfk: String,
    val wxr: String,
    val wxySjh: String,
    val wcqk: String,
    val fwpj: String,
    val completed: Boolean,
    val photoUrls: List<String>,
    val steps: List<RepairStep>,
)

@Singleton
class RepairRepository @Inject constructor(
    private val api: RepairApi,
    private val tokenManager: TokenManager,
) {

    /** 详情接口的状态可能过期（已完工的单仍返回 dqzt=9），列表是同一时刻的准数据。 */
    @Volatile
    private var cachedList: List<RepairSummary> = emptyList()

    fun cachedSummary(bxdh: String): RepairSummary? = cachedList.firstOrNull { it.bxdh == bxdh }

    suspend fun list(): Result<List<RepairSummary>> = runCatchingApi("获取报修列表") {
        val response = api.getWdbxList(emptyMap())
        ensureSuccess(response, "获取报修列表失败")
        val array = RepairJson.findArray(response) { it.has("bxdh") } ?: return@runCatchingApi emptyList()
        val items = array.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val obj = element.asJsonObject
            val bxdh = obj.string("bxdh")
            if (bxdh.isBlank()) return@mapNotNull null
            RepairSummary(
                id = obj.string("id", "bxdId"),
                bxdh = bxdh,
                bxdd = obj.string("bxdd"),
                bxlx = obj.string("bxlx"),
                gzms = obj.string("gzms"),
                bxsj = obj.string("bxsj"),
                dqzt = obj.string("dqzt"),
                dqztMc = obj.string("dqztMc", "dqztmc"),
                wxyXm = obj.string("wxyXm"),
                wxySjh = obj.string("wxySjh"),
                sjh = obj.string("sjh"),
                photoUrl = RepairJson.firstPhotoUrl(obj),
            )
        }.distinctBy { it.bxdh }
        cachedList = items
        items
    }

    suspend fun detail(bxdh: String): Result<RepairDetail> = runCatchingApi("获取报修详情") {
        val response = api.getBxdXq(mapOf("bxdh" to bxdh))
        ensureSuccess(response, "获取报修详情失败")
        val obj = RepairJson.findObject(response) { it.has("bxdh") && it.has("gzms") }
            ?: throw IllegalStateException("详情数据为空")
        val flow = runCatching { api.getLct(mapOf("bxdh" to bxdh)) }.getOrNull()
        val steps = flow?.let { RepairJson.parseSteps(it) }.orEmpty()
        val wcqk = obj.string("wcqk")
        val fwpj = obj.string("fwpj")
        val rawCode = obj.string("dqzt")
        val (code, name) = normalizedRepairStatus(
            code = rawCode,
            name = obj.string("dqztmc", "dqztMc"),
            wcqk = wcqk,
            fwpj = fwpj,
        )
        RepairDetail(
            id = obj.string("bxdId", "id"),
            bxdh = obj.string("bxdh").ifBlank { bxdh },
            bxdd = RepairJson.joinPath(obj),
            bxlx = obj.string("bxlx"),
            gzms = obj.string("gzms"),
            bxsj = obj.string("bxsj"),
            dqzt = code,
            dqztMc = name,
            xxdz = obj.string("xxdz"),
            xqmc = obj.string("xqmc"),
            lymc = obj.string("lymc"),
            qyfjh = obj.string("qyfjh"),
            sjh = obj.string("sjh"),
            xm = obj.string("xm"),
            yjfk = obj.string("yjfk"),
            wxr = obj.string("wxr"),
            wxySjh = obj.string("wxySjh"),
            wcqk = wcqk,
            fwpj = fwpj,
            completed = rawCode in REPAIR_ENDED_CODES || wcqk == "1" || fwpj == "1",
            photoUrls = RepairJson.photoUrls(obj),
            steps = steps,
        )
    }

    /** 进详情前的已读标记，失败静默。 */
    suspend fun markRead(id: String) {
        if (id.isBlank()) return
        runCatching { api.updateBxdById(mapOf("id" to id, "qtYqbz" to "1")) }
    }

    suspend fun withdraw(bxdh: String): Result<Unit> = runCatchingApi("撤回报修") {
        val userCode = tokenManager.getUserCode().orEmpty()
        val response = api.addLcbz(
            mapOf(
                "bxdh" to bxdh,
                "cz" to "8",
                "wxry" to "",
                "userCode" to userCode,
            ),
        )
        ensureSuccess(response, "撤回失败")
    }

    suspend fun upload(file: File): Result<String> = runCatchingApi("上传图片") {
        val base64 = withContext(Dispatchers.IO) { compressToDataUrl(file) }
            ?: throw IllegalStateException("图片处理失败")
        val response = api.upload(System.currentTimeMillis(), base64)
        val data = response.get("data")
        val fileNode = if (data != null && data.isJsonObject) {
            val file = data.asJsonObject.get("file")
            if (file != null && file.isJsonObject) file.asJsonObject else null
        } else {
            null
        }
        val node = fileNode
            ?: RepairJson.findObject(response) { it.has("id") || it.has("fileId") }
            ?: throw IllegalStateException(RepairJson.message(response, "图片上传失败"))
        node.string("id", "fileId", "uid").ifBlank {
            throw IllegalStateException("图片上传失败：没有返回文件 ID")
        }
    }

    suspend fun addressNodes(): Result<List<RepairNode>> = loadNodes("获取报修地址") {
        api.getBxdd(emptyMap())
    }

    suspend fun typeNodes(): Result<List<RepairNode>> = loadNodes("获取报修类型") {
        api.getBxlx(emptyMap())
    }

    suspend fun defaultCampus(): Result<Pair<String, String>> = runCatchingApi("获取默认校区") {
        val userCode = tokenManager.getUserCode().orEmpty()
        val response = api.getXq(mapOf("xh" to userCode))
        val obj = RepairJson.findObject(response) { it.has("xqdm") || it.has("xqmc") }
            ?: return@runCatchingApi "" to ""
        obj.string("xqdm", "id") to obj.string("xqmc", "name", "mc")
    }

    suspend fun contact(): Result<Pair<String, String>> = runCatchingApi("获取联系人") {
        val userCode = tokenManager.getUserCode().orEmpty()
        val response = api.getSjh(mapOf("userCode" to userCode))
        val obj = RepairJson.findObject(response) { it.has("sjh") }
            ?: return@runCatchingApi "" to ""
        obj.string("xm", "name") to obj.string("sjh")
    }

    suspend fun submit(draft: RepairDraft): Result<Pair<String, String>> = runCatchingApi("提交报修") {
        val errors = RepairSubmitBuilder.validate(draft)
        if (errors.isNotEmpty()) throw IllegalArgumentException(errors.values.first())
        val response = api.saveOrUpdate(RepairSubmitBuilder.build(draft))
        ensureSuccess(response, "提交报修失败")
        val obj = RepairJson.findObject(response) { it.has("bxdh") || it.has("id") }
        (obj?.string("bxdh").orEmpty()) to (obj?.string("id").orEmpty())
    }

    private suspend fun loadNodes(
        action: String,
        call: suspend () -> JsonObject,
    ): Result<List<RepairNode>> = runCatchingApi(action) {
        val response = call()
        ensureSuccess(response, "$action 失败")
        RepairJson.parseNodes(response)
    }

    private fun ensureSuccess(response: JsonObject, fallback: String) {
        if (!RepairJson.isSuccess(response)) {
            throw IllegalStateException(RepairJson.message(response, fallback))
        }
    }

    private suspend fun <T> runCatchingApi(action: String, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        Result.failure(Exception(mapError(action, e), e))
    }

    private fun mapError(action: String, error: Exception): String = when (error) {
        is HttpException -> when {
            error.code() == 401 -> "登录已失效，请重新登录"
            error.code() >= 500 -> "$action 接口暂时异常，请稍后重试"
            else -> "$action 失败（HTTP ${error.code()}）"
        }
        is IOException -> "网络异常，请检查连接后重试"
        else -> error.message ?: "$action 失败"
    }

    private fun compressToDataUrl(file: File): String? {
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val maxSide = 1600
        val scale = minOf(1f, maxSide.toFloat() / maxOf(original.width, original.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            original
        }
        var quality = 85
        var bytes: ByteArray
        while (true) {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            if (bytes.size <= 2 * 1024 * 1024 || quality <= 40) break
            quality -= 10
        }
        if (scaled !== original) scaled.recycle()
        original.recycle()
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }
}

object RepairJson {

    fun isSuccess(root: JsonObject): Boolean {
        if (root.get("flag")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) return true
        val code = root.string("code", "result")
        return code == "0" || code == "200" || code.equals("success", ignoreCase = true)
    }

    fun message(root: JsonObject, fallback: String): String =
        root.string("msg", "message", "result").ifBlank { fallback }

    fun findObject(root: JsonElement?, predicate: (JsonObject) -> Boolean): JsonObject? {
        if (root == null) return null
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            if (predicate(obj)) return obj
            obj.entrySet().forEach { (_, value) ->
                findObject(value, predicate)?.let { return it }
            }
        } else if (root.isJsonArray) {
            root.asJsonArray.forEach { value ->
                findObject(value, predicate)?.let { return it }
            }
        }
        return null
    }

    fun findArray(root: JsonElement?, predicate: (JsonObject) -> Boolean): JsonArray? {
        if (root == null) return null
        if (root.isJsonArray) {
            val array = root.asJsonArray
            if (array.any { it.isJsonObject && predicate(it.asJsonObject) }) return array
        }
        if (root.isJsonObject) {
            root.asJsonObject.entrySet().forEach { (_, value) ->
                findArray(value, predicate)?.let { return it }
            }
        } else if (root.isJsonArray) {
            root.asJsonArray.forEach { value ->
                findArray(value, predicate)?.let { return it }
            }
        }
        return null
    }

    fun parseNodes(root: JsonObject): List<RepairNode> {
        val array = findArray(root) { obj ->
            (obj.has("name") || obj.has("mc")) &&
                (obj.has("value") || obj.has("id") || obj.has("key"))
        } ?: return emptyList()
        return array.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val obj = item.asJsonObject
            val name = obj.string("name", "mc", "label")
            val value = obj.string("value", "id", "key", "code")
            if (name.isBlank() || value.isBlank()) return@mapNotNull null
            RepairNode(
                name = name,
                value = value,
                parent = obj.string("parent", "pId", "pid"),
            )
        }.distinctBy { it.value }
    }

    fun parseSteps(root: JsonObject): List<RepairStep> {
        val array = findArray(root) { it.has("clms") || it.has("sj") } ?: return emptyList()
        return array.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val obj = item.asJsonObject
            val text = obj.string("clms")
            val time = obj.string("sj")
            if (text.isBlank() && time.isBlank()) null else RepairStep(text, time)
        }
    }

    fun joinPath(obj: JsonObject): String {
        val direct = obj.string("bxdd")
        if (direct.isNotBlank()) return direct
        return listOf(obj.string("xqmc"), obj.string("lymc"), obj.string("qyfjh"))
            .filter { it.isNotBlank() }
            .joinToString(",")
    }

    fun photoUrls(obj: JsonObject): List<String> {
        val array = obj.get("fileList")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val file = item.asJsonObject
            fullUrl(file.string("fileFullName")) ?: fullUrl(file.string("fileUrl", "url", "path"))
        }
    }

    /** 服务端 fileUrl 常是相对路径（group2/M00/...），补全成可加载 URL。 */
    private fun fullUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) return null
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("group") -> "https://fs.aiit.edu.cn/$value"
            else -> value
        }
    }

    fun firstPhotoUrl(obj: JsonObject): String? = photoUrls(obj).firstOrNull()

}

internal fun JsonObject.string(vararg keys: String): String =
    keys.firstNotNullOfOrNull { key ->
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
    }.orEmpty()

/** 已结束的 dqzt：4 维修完成、5 已取消、6 废单审核中、7 已废单、8 已延单、10 已登记耗材。 */
internal val REPAIR_ENDED_CODES = setOf("4", "5", "6", "7", "8", "10")

/**
 * 详情接口的状态会过期：HAR 已核对，20251225024 在列表里是 dqzt=4 维修完成，详情仍返回 dqzt=9 待处理，
 * 但同时给了 wcqk=1 / fwpj=1。以完成标志为准修正状态，避免已完工的单在详情里显示“待处理”还能撤回。
 */
internal fun normalizedRepairStatus(
    code: String,
    name: String,
    wcqk: String,
    fwpj: String,
): Pair<String, String> {
    val finished = wcqk == "1" || fwpj == "1"
    if (!finished || code in REPAIR_ENDED_CODES) return code to name.ifBlank { "处理中" }
    return "4" to "维修完成"
}
