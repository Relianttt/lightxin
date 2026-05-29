package com.lightxin.feature.running.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lightxin.feature.running.exercise.domain.ClubCheckRecord
import com.lightxin.feature.running.exercise.domain.ClubTask
import com.lightxin.feature.running.exercise.domain.ClubTaskState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** auto/clubInfo.do 的 rows[] → List<ClubTask> 纯函数解析器，可 JVM 单测。 */
object ClubDetailMapper {

    private val FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

    fun parseTasks(json: JsonObject?, now: LocalDateTime = LocalDateTime.now()): List<ClubTask> {
        val rows = json?.get("rows")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return rows.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            val rawState = o.string("checkState")
            val state = when (rawState) {
                "1" -> ClubTaskState.ACTIVE
                "2" -> ClubTaskState.FINISHED
                else -> ClubTaskState.UNKNOWN
            }
            val start = o.string("startDate")
            val end = o.string("endDate")
            ClubTask(
                autoId = o.string("autoId"),
                memberId = o.string("memberId"),
                name = o.string("name"),
                requiredMinutes = o.string("requiredTime").toIntOrNull() ?: 0,
                completedMinutes = o.string("completeTime").toIntOrNull() ?: 0,
                state = state,
                startDate = start,
                endDate = end,
                checkRecords = parseCheckList(o.get("checkList")),
                canCheck = state == ClubTaskState.ACTIVE && withinWindow(start, end, now),
            )
        }.sortedWithStartDateDescending()
    }

    /** 按 startDate 倒序；解析失败的项保持原始相对顺序并排在末尾（稳定排序）。 */
    private fun List<ClubTask>.sortedWithStartDateDescending(): List<ClubTask> =
        withIndex().sortedWith(
            compareByDescending<IndexedValue<ClubTask>> { parse(it.value.startDate) != null }
                .thenByDescending { parse(it.value.startDate) }
                .thenBy { it.index },
        ).map { it.value }

    private fun parseCheckList(el: com.google.gson.JsonElement?): List<ClubCheckRecord> {
        val arr = el?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        return arr.mapNotNull { c ->
            if (!c.isJsonObject) return@mapNotNull null
            val o = c.asJsonObject
            ClubCheckRecord(
                isNormal = o.string("checkState") == "1",
                startDate = o.string("startDate"),
                endDate = o.string("endDate"),
                venueName = o.string("venueName"),
                duration = o.string("time"),
            )
        }
    }

    private fun withinWindow(start: String, end: String, now: LocalDateTime): Boolean {
        val s = parse(start) ?: return false
        val e = parse(end) ?: return false
        return !now.isBefore(s) && !now.isAfter(e)
    }

    private fun parse(text: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(text.trim(), FORMATTER) }.getOrNull()

    private fun JsonObject.string(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
}
