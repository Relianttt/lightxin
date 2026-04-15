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
