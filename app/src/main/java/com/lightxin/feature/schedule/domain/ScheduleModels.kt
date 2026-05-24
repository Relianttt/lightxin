package com.lightxin.feature.schedule.domain

data class WeekInfo(
    val currentWeek: Int,
    val totalWeeks: Int,
    val schoolYear: String,
    val schoolTerm: String,
)

data class Course(
    val name: String,
    val startSection: Int,
    val endSection: Int,
    val room: String,
    val teacher: String,
    val dayOfWeek: Int,  // 1=周一, 7=周日
)

/**
 * getCourses() 返回值，包含课程列表与 weekDates（dayOfWeek → MM/dd）
 */
data class ScheduleData(
    val courses: List<Course>,
    val weekDates: Map<Int, String>,
)
