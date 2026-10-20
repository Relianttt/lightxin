package com.lightxin.feature.schedule.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lightxin.feature.schedule.domain.ScheduleData
import com.lightxin.feature.schedule.domain.WeekInfo
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

val SCHOOL_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

data class ScheduleWeekSnapshot(val data: ScheduleData, val syncedAt: Long)

data class SemesterSnapshot(val info: WeekInfo, val referenceDate: LocalDate, val syncedAt: Long) {
    fun estimatedInfo(today: LocalDate = LocalDate.now(SCHOOL_ZONE)): WeekInfo {
        val anchorMonday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val target = info.currentWeek + ChronoUnit.WEEKS.between(anchorMonday, currentMonday).toInt()
        val week = info.weekNumbers.minByOrNull { kotlin.math.abs(it.toLong() - target) } ?: info.currentWeek
        return info.copy(currentWeek = week)
    }
}

internal object ScheduleCacheMapper {
    private val gson = Gson()
    private val weekType = object : TypeToken<List<Int>>() {}.type

    fun semester(entity: SemesterCacheEntity): SemesterSnapshot? = runCatching {
        val weeks: List<Int> = gson.fromJson(entity.weekNumbersJson, weekType)
        require(weeks.isNotEmpty() && weeks.all { it >= 0 } && entity.referenceWeek in weeks)
        require(entity.schoolYear.isNotBlank() && entity.schoolTerm.isNotBlank())
        SemesterSnapshot(
            WeekInfo(entity.referenceWeek, weeks.size, entity.schoolYear, entity.schoolTerm, weeks),
            LocalDate.parse(entity.referenceLocalDate), entity.syncedAt,
        )
    }.getOrNull()

    fun semester(user: String, snapshot: SemesterSnapshot) = SemesterCacheEntity(
        user, snapshot.info.schoolYear, snapshot.info.schoolTerm, gson.toJson(snapshot.info.weekNumbers),
        snapshot.info.currentWeek, snapshot.referenceDate.toString(), snapshot.syncedAt,
    )

    fun week(entity: ScheduleWeekCacheEntity): ScheduleWeekSnapshot? = runCatching {
        require(entity.payloadVersion == 1)
        val data = requireNotNull(gson.fromJson(entity.payloadJson, ScheduleData::class.java))
        requireNotNull(data.courses)
        requireNotNull(data.weekDates)
        require(data.courses.all { it.name.isNotBlank() && it.dayOfWeek in 1..7 && it.startSection >= 0 && it.endSection >= it.startSection })
        ScheduleWeekSnapshot(data, entity.syncedAt)
    }.getOrNull()

    fun week(user: String, info: WeekInfo, week: Int, snapshot: ScheduleWeekSnapshot) = ScheduleWeekCacheEntity(
        user, info.schoolYear, info.schoolTerm, week, gson.toJson(snapshot.data), snapshot.syncedAt,
    )
}
