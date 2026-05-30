package com.lightxin.feature.running.data

import com.google.gson.JsonObject
import com.lightxin.feature.running.domain.ClubSummary

data class JuniorExtraDetail(
    val memberId: String = "",
    val mixOnceMileKm: Double = 1.0,
    val todayMileKm: Double = 0.0,
    val completedMileKm: Double = 0.0,
    val surplusMileKm: Double = 0.0,
    val maxMileKm: Double = 0.0,
    val maxMileDate: String = "",
)

/**
 * 运动模块年级感知纯函数解析器。无副作用、无网络，可 JVM 单测。
 * 约束：年级判定唯一以 checkDsStudent.result 为准；目标值只读服务端返回字段。
 */
object SportsGradeMapper {

    /** 由 checkDsStudent.do 的 result 判定是否大一大二（唯一判定来源，不按学号推断）。 */
    fun isJuniorGrade(checkDsStudentResult: String): Boolean =
        checkDsStudentResult.contains("大一大二")

    /** 解析 index/extraInfo.do → (已完成里程, 计划总里程)；缺失返回 0。仅读服务端下发值。 */
    fun parseExtraProgress(json: JsonObject?): Pair<Double, Double> {
        val extra = json?.dataObject()?.getAsJsonObjectOrNull("extraInfo") ?: return 0.0 to 0.0
        val complete = extra.string("completeMile").toDoubleOrNull() ?: 0.0
        val plan = extra.string("planMile").toDoubleOrNull() ?: 0.0
        return complete to plan
    }

    /** 解析 index/extraInfo.do → 课外跑步会话标识 extraId（大一大二开始跑步所需，无 startRunning.do）。 */
    fun parseExtraId(json: JsonObject?): String =
        json?.dataObject()?.getAsJsonObjectOrNull("extraInfo")?.string("extraId").orEmpty()

    /** 解析 extra/extraDetailInfo.do → 大一大二课外跑步详情；缺失时保守返回空 ID 与默认单次 1km。 */
    fun parseExtraDetail(json: JsonObject?): JuniorExtraDetail {
        val detail = json?.dataObject()?.getAsJsonObjectOrNull("extraDetail") ?: return JuniorExtraDetail()
        return JuniorExtraDetail(
            memberId = detail.string("memberId"),
            mixOnceMileKm = detail.string("mixOnceMile").toPositiveDoubleOrNull() ?: 1.0,
            todayMileKm = detail.string("todayMile").toDoubleOrNull() ?: 0.0,
            completedMileKm = detail.string("completeMile").toDoubleOrNull() ?: 0.0,
            surplusMileKm = detail.string("surplusMile").toDoubleOrNull() ?: 0.0,
            maxMileKm = detail.string("maxMile").toDoubleOrNull() ?: 0.0,
            maxMileDate = detail.string("maxMileDate"),
        )
    }

    /** 解析 index/clubInfo.do → ClubSummary；大三/大四 clubInfo 为 null 时返回 null。 */
    fun parseClubSummary(json: JsonObject?): ClubSummary? {
        val club = json?.dataObject()?.getAsJsonObjectOrNull("clubInfo") ?: return null
        val year = club.string("schoolYear")
        val semester = club.string("semester")
        val teacher = club.get("teacherList")
            ?.takeIf { it.isJsonArray && it.asJsonArray.size() > 0 }
            ?.asJsonArray?.get(0)?.takeIf { it.isJsonObject }
            ?.asJsonObject?.string("name").orEmpty()
        return ClubSummary(
            courseName = club.string("clubName"),
            term = if (year.isNotBlank()) "$year-$semester" else "",
            teacherName = teacher,
            memberLevel = club.string("memberLevelName"),
        )
    }

    private fun JsonObject.dataObject(): JsonObject? =
        get("data")?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.getAsJsonObjectOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun String.toPositiveDoubleOrNull(): Double? =
        toDoubleOrNull()?.takeIf { it > 0.0 }
}
