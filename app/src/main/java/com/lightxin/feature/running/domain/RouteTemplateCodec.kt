package com.lightxin.feature.running.domain

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

/**
 * 路线模板导入导出：纯逻辑，不依赖 Android，可单测。
 *
 * 导出信封：{"schemaVersion":1,"app":"lightxin","exportedAtMillis":...,"templates":[...]}
 * 导入兼容信封与裸数组（内置种子文件就是裸数组）。
 * 距离 / 质量一律按轨迹点重算，不信任文件里的值；硬闸与录制一致。
 */
object RouteTemplateCodec {
    const val SCHEMA_VERSION = 1
    const val APP_TAG = "lightxin"

    data class ParseResult(
        val candidates: List<ImportedTemplate>,
        val rejected: List<ImportReject>,
    )

    fun encode(templates: List<RouteTemplate>, exportedAtMillis: Long = System.currentTimeMillis()): String {
        val array = JsonArray()
        templates.forEach { array.add(it.toJson()) }
        return JsonObject().apply {
            addProperty("schemaVersion", SCHEMA_VERSION)
            addProperty("app", APP_TAG)
            addProperty("exportedAtMillis", exportedAtMillis)
            add("templates", array)
        }.toString()
    }

    /**
     * @throws IllegalArgumentException 文件不是合法 JSON / 结构不认识。
     * 单条模板不合法只进 rejected，不影响其它条目。
     */
    fun parse(raw: String, nowMillis: Long = System.currentTimeMillis()): ParseResult {
        val root = try {
            JsonParser.parseString(raw)
        } catch (e: Exception) {
            throw IllegalArgumentException("文件不是有效的 JSON", e)
        }
        val array: JsonArray = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                val templates = obj.get("templates")
                if (templates != null && templates.isJsonArray) {
                    templates.asJsonArray
                } else {
                    throw IllegalArgumentException("JSON 结构不认识：缺少 templates 数组")
                }
            }
            else -> throw IllegalArgumentException("JSON 结构不认识：应为对象或数组")
        }

        val candidates = mutableListOf<ImportedTemplate>()
        val rejected = mutableListOf<ImportReject>()
        array.forEachIndexed { index, element ->
            if (!element.isJsonObject) {
                rejected += ImportReject("第 ${index + 1} 项", "不是对象")
                return@forEachIndexed
            }
            val obj = element.asJsonObject
            val fallbackName = "导入模板 ${index + 1}"
            val name = obj.stringOrNull("name")?.trim().orEmpty().ifBlank { fallbackName }
            val points = runCatching { parsePoints(obj.get("points")) }.getOrNull()
            if (points.isNullOrEmpty()) {
                rejected += ImportReject(name, "没有可用的轨迹点")
                return@forEachIndexed
            }
            val distance = totalDistanceMeters(points)
            val hardGate = RouteTemplateRules.validate(points.size, distance)
            if (hardGate != null) {
                rejected += ImportReject(name, hardGate)
                return@forEachIndexed
            }
            val quality = RouteQualityChecker.evaluate(points)
            val fileId = obj.stringOrNull("id")?.trim().orEmpty()
            val duration = obj.longOrNull("durationSeconds")
                ?: ((points.last().timestampMillis - points.first().timestampMillis) / 1000L)
                    .coerceAtLeast(1L)
            val createdAt = obj.longOrNull("createdAtMillis")
                ?: points.first().timestampMillis.takeIf { it > 0L }
                ?: nowMillis

            candidates += ImportedTemplate(
                fileId = fileId.ifBlank { UUID.randomUUID().toString() },
                name = name,
                points = points,
                totalDistanceMeters = distance,
                durationSeconds = duration.coerceAtLeast(1L),
                qualityStatus = quality.status,
                qualityMessage = quality.message,
                createdAtMillis = createdAt,
            )
        }
        return ParseResult(candidates = candidates, rejected = rejected)
    }

    fun totalDistanceMeters(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until points.size) {
            sum += GeoDistance.metersBetween(
                points[i - 1].latitude,
                points[i - 1].longitude,
                points[i].latitude,
                points[i].longitude,
            )
        }
        return sum
    }

    private fun parsePoints(element: JsonElement?): List<TrackPoint> {
        if (element == null || !element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val obj = item.asJsonObject
            val lat = obj.doubleOrNull("latitude") ?: return@mapNotNull null
            val lng = obj.doubleOrNull("longitude") ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@mapNotNull null
            TrackPoint(
                latitude = lat,
                longitude = lng,
                timestampMillis = obj.longOrNull("timestampMillis") ?: 0L,
            )
        }
    }

    private fun RouteTemplate.toJson(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        addProperty("createdAtMillis", createdAtMillis)
        addProperty("updatedAtMillis", updatedAtMillis)
        addProperty("totalDistanceMeters", totalDistanceMeters)
        addProperty("durationSeconds", durationSeconds)
        addProperty("source", source.name)
        addProperty("isDefault", isDefault)
        qualityMessage?.let { addProperty("qualityMessage", it) }
        addProperty("qualityStatus", qualityStatus.name)
        lastUsedAtMillis?.let { addProperty("lastUsedAtMillis", it) }
        add("points", JsonArray().apply {
            points.forEach { p ->
                add(JsonObject().apply {
                    addProperty("latitude", p.latitude)
                    addProperty("longitude", p.longitude)
                    addProperty("timestampMillis", p.timestampMillis)
                })
            }
        })
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.longOrNull(key: String): Long? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()

    private fun JsonObject.doubleOrNull(key: String): Double? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()
}

data class ImportedTemplate(
    val fileId: String,
    val name: String,
    val points: List<TrackPoint>,
    val totalDistanceMeters: Double,
    val durationSeconds: Long,
    val qualityStatus: RouteQualityStatus,
    val qualityMessage: String?,
    val createdAtMillis: Long,
)

data class ImportReject(
    val name: String,
    val reason: String,
)

enum class ConflictChoice {
    OVERWRITE,
    KEEP_COPY,
    SKIP,
}

data class ImportPreview(
    val valid: List<ImportedTemplate>,
    val rejected: List<ImportReject>,
    /** 与现有模板 id 或 name 冲突的条目 fileId */
    val conflicts: Set<String>,
) {
    val importableCount: Int get() = valid.size
    val totalCount: Int get() = valid.size + rejected.size

    fun isConflict(item: ImportedTemplate): Boolean = item.fileId in conflicts
}

data class ImportReport(
    val added: Int,
    val overwritten: Int,
    val skipped: Int,
) {
    val total: Int get() = added + overwritten
}

/**
 * 冲突解析：id 相同或 name（trim）相同即冲突。
 * 覆盖保留本地 isDefault / createdAt / lastUsedAt，只替换内容。
 */
object TemplateImportResolver {

    fun preview(parsed: RouteTemplateCodec.ParseResult, existing: List<RouteTemplate>): ImportPreview {
        val existingIds = existing.map { it.id }.toSet()
        val existingNames = existing.map { it.name.trim() }.toSet()
        val conflicts = parsed.candidates
            .filter { it.fileId in existingIds || it.name.trim() in existingNames }
            .map { it.fileId }
            .toSet()
        return ImportPreview(
            valid = parsed.candidates,
            rejected = parsed.rejected,
            conflicts = conflicts,
        )
    }

    fun resolve(
        preview: ImportPreview,
        decisions: Map<String, ConflictChoice>,
        existing: List<RouteTemplate>,
        nowMillis: Long = System.currentTimeMillis(),
    ): ImportResolution {
        val result = existing.toMutableList()
        var added = 0
        var overwritten = 0
        var skipped = 0

        preview.valid.forEach { imported ->
            val targetIndex = result.indexOfFirst {
                it.id == imported.fileId || it.name.trim() == imported.name.trim()
            }
            if (targetIndex >= 0) {
                when (decisions[imported.fileId] ?: ConflictChoice.KEEP_COPY) {
                    ConflictChoice.SKIP -> skipped++
                    ConflictChoice.OVERWRITE -> {
                        val target = result[targetIndex]
                        result[targetIndex] = target.copy(
                            name = imported.name,
                            updatedAtMillis = nowMillis,
                            totalDistanceMeters = imported.totalDistanceMeters,
                            durationSeconds = imported.durationSeconds,
                            source = RouteTemplateSource.IMPORTED,
                            points = imported.points,
                            qualityStatus = imported.qualityStatus,
                            qualityMessage = imported.qualityMessage,
                        )
                        overwritten++
                    }
                    ConflictChoice.KEEP_COPY -> {
                        val newTemplate = imported.toRouteTemplate(
                            id = UUID.randomUUID().toString(),
                            name = uniqueName(imported.name, result),
                            isDefault = result.isEmpty(),
                            nowMillis = nowMillis,
                        )
                        result += newTemplate
                        added++
                    }
                }
            } else {
                val id = imported.fileId.takeIf { candidate ->
                    candidate.isNotBlank() && result.none { it.id == candidate }
                } ?: UUID.randomUUID().toString()
                result += imported.toRouteTemplate(
                    id = id,
                    name = uniqueName(imported.name, result),
                    isDefault = result.isEmpty(),
                    nowMillis = nowMillis,
                )
                added++
            }
        }

        return ImportResolution(
            templates = if (added + overwritten == 0) existing else result,
            report = ImportReport(added = added, overwritten = overwritten, skipped = skipped),
        )
    }

    private fun ImportedTemplate.toRouteTemplate(
        id: String,
        name: String,
        isDefault: Boolean,
        nowMillis: Long,
    ): RouteTemplate = RouteTemplate(
        id = id,
        name = name,
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis,
        totalDistanceMeters = totalDistanceMeters,
        durationSeconds = durationSeconds,
        source = RouteTemplateSource.IMPORTED,
        isDefault = isDefault,
        points = points,
        qualityStatus = qualityStatus,
        qualityMessage = qualityMessage,
        lastUsedAtMillis = null,
    )

    private fun uniqueName(base: String, existing: List<RouteTemplate>): String {
        val names = existing.map { it.name.trim() }.toSet()
        if (base !in names) return base
        var n = 1
        while (true) {
            val suffix = if (n == 1) " (导入)" else " (导入 $n)"
            val candidate = base + suffix
            if (candidate !in names) return candidate
            n++
        }
    }
}

data class ImportResolution(
    val templates: List<RouteTemplate>,
    val report: ImportReport,
)
