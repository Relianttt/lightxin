package com.lightxin.feature.schedule.data.local

import androidx.room.Entity

@Entity(tableName = "semester_cache", primaryKeys = ["userCode", "schoolYear", "schoolTerm"])
data class SemesterCacheEntity(
    val userCode: String,
    val schoolYear: String,
    val schoolTerm: String,
    val weekNumbersJson: String,
    val referenceWeek: Int,
    val referenceLocalDate: String,
    val syncedAt: Long,
    val isLatestKnown: Boolean = true,
)

@Entity(tableName = "schedule_week_cache", primaryKeys = ["userCode", "schoolYear", "schoolTerm", "weekNumber"])
data class ScheduleWeekCacheEntity(
    val userCode: String,
    val schoolYear: String,
    val schoolTerm: String,
    val weekNumber: Int,
    val payloadJson: String,
    val syncedAt: Long,
    val payloadVersion: Int = 1,
)
