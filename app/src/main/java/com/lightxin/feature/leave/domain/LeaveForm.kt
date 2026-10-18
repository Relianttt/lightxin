package com.lightxin.feature.leave.domain

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class LeaveFormType {
    TEXT_INPUT,
    TEXTAREA,
    RADIO,
    SELECT,
    DATE_RANGE,
    ADDRESS,
    UPLOAD_FILE,
    UPLOAD_IMG,
    FORMULA,
    READONLY_TEXT,
    UNKNOWN,
}

data class LeaveOption(val label: String, val value: String)

data class LeaveFormField(
    val key: String,
    val label: String,
    val type: LeaveFormType,
    val required: Boolean = false,
    val readonly: Boolean = false,
    val options: List<LeaveOption> = emptyList(),
    val order: Int = 0,
    /** 服务端给的只读正文 / 默认值（说明类 text 字段的 content 也放这里）。 */
    val defaultValue: String? = null,
)

data class LeaveAttachment(
    val uid: String,
    val fileName: String,
    val fileUrl: String,
)

data class LeaveFormInput(
    val values: Map<String, String> = emptyMap(),
    val dateRanges: Map<String, Pair<String, String>> = emptyMap(),
    val attachments: Map<String, List<LeaveAttachment>> = emptyMap(),
)

object LeaveFormSchemaParser {

    private val containerTypes = setOf(
        "card", "grid", "layout", "tabs", "tab", "collapse", "table",
        "row", "col", "group", "container", "panel",
    )

    private val containerKeys = listOf(
        "list", "columns", "children", "fields", "formFields",
        "flowSheet", "sheet", "form", "data", "rows", "tabs",
    )

    fun parse(root: JsonElement?): List<LeaveFormField> {
        val fields = LinkedHashMap<String, LeaveFormField>()
        collect(root, fields, 0)
        return fields.values.sortedBy { it.order }
    }

    private fun collect(element: JsonElement?, out: MutableMap<String, LeaveFormField>, depth: Int) {
        if (element == null || depth > 12) return
        when {
            element.isJsonArray -> element.asJsonArray.forEach { collect(it, out, depth + 1) }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val rawType = obj.firstString("type", "fieldType", "componentType")
                val normalizedType = rawType?.lowercase()?.replace("_", "")?.replace("-", "")
                if (normalizedType != null && normalizedType in containerTypes) {
                    containerKeys.forEach { key -> collect(obj.get(key), out, depth + 1) }
                    return
                }
                val field = parseField(obj, out.size, rawType)
                if (field != null) {
                    out.putIfAbsent(field.key, field)
                    return
                }
                // 非叶子节点（根对象 / 布局列 / 未识别容器）：继续向下找
                obj.entrySet().forEach { (key, value) ->
                    if (key !in setOf("component", "properties", "rules", "condition", "formula")) {
                        collect(value, out, depth + 1)
                    }
                }
            }
        }
    }

    private fun parseField(obj: JsonObject, fallbackOrder: Int, rawType: String?): LeaveFormField? {
        val key = obj.firstString("model", "name", "key", "fieldName", "field", "id") ?: return null
        if (rawType.isNullOrBlank()) return null
        val properties = obj.get("properties")?.takeIf { it.isJsonObject }?.asJsonObject
        val hidden = properties?.firstBoolean("hidden") == true || obj.firstBoolean("hidden")
        if (hidden) return null

        val label = obj.firstString("label", "title", "text", "fieldTitle", "typeName") ?: key
        val rules = obj.get("rules")?.takeIf { it.isJsonArray }?.asJsonArray
        val requiredFromRules = rules?.any { rule ->
            rule.isJsonObject && rule.asJsonObject.firstBoolean("required")
        } == true
        val disabled = properties?.firstBoolean("disabled") == true

        return LeaveFormField(
            key = key,
            label = label,
            type = mapType(rawType),
            required = requiredFromRules ||
                obj.firstBoolean("required", "isRequired", "must", "require") ||
                obj.firstString("required", "isRequired", "must", "require") == "1",
            readonly = disabled ||
                obj.firstBoolean("readonly", "readOnly", "disabled") ||
                obj.firstString("readonly", "readOnly", "disabled") == "1" ||
                obj.firstString("editable") == "false",
            options = parseOptions(obj, properties),
            order = obj.firstString("order", "sort", "dispOrder", "index", "sequence")?.toIntOrNull()
                ?: fallbackOrder,
            defaultValue = properties?.firstString("defaultValue")
                ?: obj.firstString("content")
                ?: obj.firstString("defaultValue"),
        )
    }

    private fun parseOptions(obj: JsonObject, properties: JsonObject?): List<LeaveOption> {
        val array = listOf("options", "optionList", "dicts", "items", "choices")
            .firstNotNullOfOrNull { key ->
                obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: properties?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            }
            ?: return emptyList()
        return array.mapNotNull { item ->
            when {
                item.isJsonObject -> {
                    val option = item.asJsonObject
                    val label = option.firstString("label", "text", "name", "mc") ?: return@mapNotNull null
                    val value = option.firstString("value", "id", "code", "key") ?: label
                    LeaveOption(label, value)
                }
                item.isJsonPrimitive -> {
                    val text = item.asString
                    LeaveOption(text, text)
                }
                else -> null
            }
        }
    }

    private fun mapType(raw: String): LeaveFormType {
        val normalized = raw.lowercase().replace("_", "").replace("-", "")
        return when {
            normalized.startsWith("textarea") -> LeaveFormType.TEXTAREA
            normalized == "radio" || normalized == "radiogroup" -> LeaveFormType.RADIO
            normalized.startsWith("select") || normalized == "dropdown" || normalized == "picker" ->
                LeaveFormType.SELECT
            normalized.startsWith("daterange") || normalized == "datetime" || normalized == "date" ->
                LeaveFormType.DATE_RANGE
            normalized.startsWith("address") -> LeaveFormType.ADDRESS
            normalized.startsWith("uploadfile") || normalized == "file" || normalized == "attachment" ->
                LeaveFormType.UPLOAD_FILE
            normalized.startsWith("uploadimg") || normalized.startsWith("uploadimage") ||
                normalized == "image" -> LeaveFormType.UPLOAD_IMG
            normalized == "formula" || normalized == "number" || normalized == "calculate" ->
                LeaveFormType.FORMULA
            normalized == "text" || normalized == "label" || normalized == "readonlytext" ||
                normalized == "readonly" || normalized == "divider" -> LeaveFormType.READONLY_TEXT
            normalized == "input" || normalized == "textinput" || normalized == "textfield" ||
                normalized == "string" -> LeaveFormType.TEXT_INPUT
            else -> LeaveFormType.UNKNOWN
        }
    }

    private fun JsonObject.firstString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        }

    private fun JsonObject.firstBoolean(vararg keys: String): Boolean =
        keys.any { key ->
            val primitive = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            when {
                primitive == null -> false
                primitive.isBoolean -> primitive.asBoolean
                primitive.isNumber -> primitive.asInt != 0
                else -> primitive.asString.lowercase() in setOf("1", "true", "yes")
            }
        }
}

data class LeaveIdentity(
    val userCode: String,
    val userName: String,
    val userPhone: String = "",
    val yxdm: String = "",
    val yxmc: String = "",
    val bjdm: String = "",
)

object LeaveFormBuilder {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun build(
        defaults: JsonObject?,
        fields: List<LeaveFormField>,
        input: LeaveFormInput,
        identity: LeaveIdentity,
    ): JsonObject {
        val result = defaults?.deepCopy() ?: JsonObject()

        fields.forEach { field ->
            when (field.type) {
                LeaveFormType.DATE_RANGE -> {
                    input.dateRanges[field.key]?.let { (start, end) ->
                        result.add(field.key, JsonArray().apply {
                            add(JsonPrimitive(start))
                            add(JsonPrimitive(end))
                        })
                    }
                }
                LeaveFormType.UPLOAD_FILE, LeaveFormType.UPLOAD_IMG -> {
                    val files = input.attachments[field.key].orEmpty()
                    result.add(field.key, attachmentsJson(files))
                }
                LeaveFormType.FORMULA, LeaveFormType.READONLY_TEXT, LeaveFormType.UNKNOWN -> Unit
                else -> input.values[field.key]?.let { result.addProperty(field.key, it) }
            }
        }

        // 表单定义缺字段也不能丢用户输入
        input.values.forEach { (key, value) ->
            if (!result.has(key)) result.addProperty(key, value)
        }
        input.dateRanges.forEach { (key, range) ->
            if (!result.has(key)) {
                result.add(key, JsonArray().apply {
                    add(JsonPrimitive(range.first))
                    add(JsonPrimitive(range.second))
                })
            }
        }
        input.attachments.forEach { (key, files) ->
            if (!result.has(key)) result.add(key, attachmentsJson(files))
        }

        // 附件字段为空时提交空字符串（与原 App 一致），而不是空数组
        fields.filter { it.type == LeaveFormType.UPLOAD_FILE || it.type == LeaveFormType.UPLOAD_IMG }
            .forEach { field ->
                if (input.attachments[field.key].isNullOrEmpty()) {
                    result.addProperty(field.key, "")
                }
            }

        computeLeaveHours(result)

        if (identity.userCode.isNotBlank()) result.addProperty("userCode", identity.userCode)
        if (identity.userName.isNotBlank()) result.addProperty("userName", identity.userName)
        if (identity.userPhone.isNotBlank()) result.addProperty("userPhone", identity.userPhone)
        if (identity.yxdm.isNotBlank()) result.addProperty("yxdm", identity.yxdm)
        if (identity.yxmc.isNotBlank()) result.addProperty("yxmc", identity.yxmc)
        if (identity.bjdm.isNotBlank()) result.addProperty("bjdm", identity.bjdm)

        return result
    }

    /** QJSC（请假时长，小时）：由 QJRQ 起止计算，整点去小数，否则保留 1 位。 */
    fun computeLeaveHours(target: JsonObject) {
        val range = target.get("QJRQ")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        if (range.size() < 2) return
        val start = parseDate(range[0]) ?: return
        val end = parseDate(range[1]) ?: return
        if (!end.isAfter(start)) return
        val minutes = Duration.between(start, end).toMinutes()
        val hours = minutes / 60.0
        if (hours % 1.0 == 0.0) {
            target.addProperty("QJSC", hours.toInt())
        } else {
            target.addProperty("QJSC", (hours * 10).toInt() / 10.0)
        }
    }

    fun parseDate(element: JsonElement?): LocalDateTime? {
        val text = element?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: return null
        return runCatching { LocalDateTime.parse(text.replace('/', '-'), dateFormatter) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text.replace('/', '-'), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }.getOrNull()
    }

    private fun attachmentsJson(files: List<LeaveAttachment>): JsonArray = JsonArray().apply {
        files.forEach { file ->
            add(JsonObject().apply {
                addProperty("uid", file.uid)
                addProperty("status", "done")
                addProperty("fileName", file.fileName)
                addProperty("name", file.fileName)
                addProperty("fileUrl", file.fileUrl)
                addProperty("url", file.fileUrl)
            })
        }
    }
}

data class LeaveValidationError(val key: String, val message: String)

object LeaveFormValidator {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun validate(fields: List<LeaveFormField>, input: LeaveFormInput): List<LeaveValidationError> {
        val errors = mutableListOf<LeaveValidationError>()
        fields.forEach { field ->
            if (!field.required || field.readonly) return@forEach
            val filled = when (field.type) {
                LeaveFormType.DATE_RANGE -> {
                    val range = input.dateRanges[field.key]
                    !range?.first.isNullOrBlank() && !range?.second.isNullOrBlank()
                }
                LeaveFormType.UPLOAD_FILE, LeaveFormType.UPLOAD_IMG ->
                    !input.attachments[field.key].isNullOrEmpty()
                LeaveFormType.FORMULA, LeaveFormType.READONLY_TEXT, LeaveFormType.UNKNOWN -> true
                else -> !input.values[field.key].isNullOrBlank()
            }
            if (!filled) errors += LeaveValidationError(field.key, "请填写${field.label}")
        }

        val reasonKey = fields.firstOrNull {
            it.key == "QJYY" || it.label.contains("原因")
        }?.key ?: "QJYY"
        val reason = input.values[reasonKey].orEmpty()
        if (reason.isNotBlank() && reason.trim().length < 10) {
            errors += LeaveValidationError(reasonKey, "请假原因至少 10 个字")
        }

        val range = input.dateRanges["QJRQ"]
        if (range != null) {
            val start = runCatching { LocalDateTime.parse(range.first, dateFormatter) }.getOrNull()
            val end = runCatching { LocalDateTime.parse(range.second, dateFormatter) }.getOrNull()
            if (start == null) errors += LeaveValidationError("QJRQ", "请选择开始时间")
            if (end == null) errors += LeaveValidationError("QJRQ", "请选择结束时间")
            if (start != null && end != null && !end.isAfter(start)) {
                errors += LeaveValidationError("QJRQ", "结束时间需晚于开始时间")
            }
        }
        return errors
    }

    fun errorFor(errors: List<LeaveValidationError>, key: String): String? =
        errors.firstOrNull { it.key == key }?.message
}
