package com.lightxin.feature.leave.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lightxin.feature.leave.domain.LeaveFormField
import com.lightxin.feature.leave.domain.LeaveFormSchemaParser

data class LeaveSummary(
    val executionId: String,
    val flowTitle: String,
    val statusTxt: String,
    val receiveTime: String,
    val flowNumber: String = "",
    /** 待办节点：我的请求列表通常返回 null，销假时再从待办列表补（getRunTaskByUserCode）。 */
    val ruTaskNodeId: String = "",
)

/**
 * 列表是标准分页信封 {rows, total}；total 用来显示稳定计数，而不是数当前已加载的那一页。
 * 有的部署只回 rows，这时 total 为 null，宁可不上计数也不显示错的数字。
 */
data class LeavePage(
    val items: List<LeaveSummary>,
    val total: Int?,
)

data class LeaveDetail(
    val executionId: String,
    val flowTitle: String,
    val statusTxt: String,
    val receiveTime: String,
    val flowNumber: String,
    val fields: List<LeaveFormField>,
    val values: JsonObject,
    /** 详情偶尔也带待办节点；列表行拿不到时用它兜底销假。 */
    val ruTaskNodeId: String = "",
)

data class LeaveSchema(
    val raw: JsonElement?,
    val fields: List<LeaveFormField>,
)

object LeaveJson {

    fun findFirstObject(root: JsonElement?, predicate: (JsonObject) -> Boolean): JsonObject? {
        if (root == null) return null
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            if (predicate(obj)) return obj
            obj.entrySet().forEach { (_, value) ->
                findFirstObject(value, predicate)?.let { return it }
            }
        } else if (root.isJsonArray) {
            root.asJsonArray.forEach { value ->
                findFirstObject(value, predicate)?.let { return it }
            }
        }
        return null
    }

    fun findFirstArray(root: JsonElement?, predicate: (JsonObject) -> Boolean): JsonArray? {
        if (root == null) return null
        if (root.isJsonArray) {
            val array = root.asJsonArray
            if (array.any { it.isJsonObject && predicate(it.asJsonObject) }) return array
        }
        if (root.isJsonObject) {
            root.asJsonObject.entrySet().forEach { (_, value) ->
                findFirstArray(value, predicate)?.let { return it }
            }
        } else if (root.isJsonArray) {
            root.asJsonArray.forEach { value ->
                findFirstArray(value, predicate)?.let { return it }
            }
        }
        return null
    }

    fun collectObjectsWith(root: JsonElement?, key: String): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        fun walk(element: JsonElement?) {
            when {
                element == null -> Unit
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    if (obj.has(key)) result += obj
                    obj.entrySet().forEach { (_, value) -> walk(value) }
                }
                element.isJsonArray -> element.asJsonArray.forEach { walk(it) }
            }
        }
        walk(root)
        return result
    }

    fun isSuccess(root: JsonObject): Boolean {
        if (root.bool("flag", "success")) return true
        val result = root.string("result", "code", "status")
        return result.equals("success", ignoreCase = true) ||
            result == "0" || result == "200"
    }

    fun message(root: JsonObject, fallback: String): String =
        root.string("msg", "message", "result", "tip").ifBlank { fallback }

    fun parseSchema(root: JsonObject): LeaveSchema {
        val holder = findFirstObject(root) { obj ->
            obj.has("flowSheet") || obj.has("formJson") || obj.has("fields") || obj.has("formFields")
        }
        val rawCandidate: JsonElement? = holder?.let { obj ->
            obj.get("flowSheet") ?: obj.get("formJson")
        } ?: findFirstObject(root) { obj ->
            obj.has("fields") || obj.has("formFields") ||
                (obj.has("list") && obj.get("list")?.isJsonArray == true &&
                    obj.getAsJsonArray("list").any { it.isJsonObject && it.asJsonObject.has("type") })
        } ?: root.get("data")

        val candidate = unwrapJsonString(rawCandidate) ?: root
        return LeaveSchema(raw = candidate, fields = LeaveFormSchemaParser.parse(candidate))
    }

    /** 服务端常把 formJson / flowSheet 再包一层字符串。 */
    fun unwrapJsonString(element: JsonElement?): JsonElement? {
        if (element == null) return null
        if (!element.isJsonPrimitive) return element
        val text = runCatching { element.asString }.getOrNull() ?: return null
        if (!text.trimStart().startsWith("{") && !text.trimStart().startsWith("[")) return null
        return runCatching { com.google.gson.JsonParser.parseString(text) }.getOrNull()
    }
}


internal fun JsonObject.string(vararg keys: String): String =
    keys.firstNotNullOfOrNull { key ->
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
    }.orEmpty()

internal fun JsonObject.int(vararg keys: String): Int =
    keys.firstNotNullOfOrNull { key ->
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }
    } ?: 0

internal fun JsonObject.bool(vararg keys: String): Boolean =
    keys.any { key ->
        val primitive = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        when {
            primitive == null -> false
            primitive.isBoolean -> primitive.asBoolean
            primitive.isNumber -> primitive.asInt != 0
            else -> primitive.asString.lowercase() in setOf("1", "true", "yes")
        }
    }
