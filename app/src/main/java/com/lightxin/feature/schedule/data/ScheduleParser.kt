package com.lightxin.feature.schedule.data

import com.lightxin.feature.schedule.domain.Course
import com.lightxin.feature.schedule.domain.ScheduleData
import com.lightxin.feature.schedule.domain.WeekInfo

internal fun WeekListResponse.toWeekInfo(): WeekInfo {
    require(flag == true) { "获取周次失败：服务端未确认成功" }
    val info = requireNotNull(data) { "周次信息缺失" }
    val weeks = requireNotNull(rows) { "周次列表缺失" }.map {
        requireNotNull(it.value?.toIntOrNull()?.takeIf { value -> value >= 0 }) { "周次编号无效" }
    }.distinct().sorted()
    val current = requireNotNull(info.week?.toIntOrNull()) { "当前周无效" }
    require(weeks.isNotEmpty() && current in weeks) { "当前周不在学期周次列表中" }
    require(!info.schoolYear.isNullOrBlank() && !info.schoolTerm.isNullOrBlank()) { "学期信息缺失" }
    return WeekInfo(current, weeks.size, info.schoolYear, info.schoolTerm, weeks)
}

internal fun TimeTableResponse.toScheduleData(): ScheduleData {
    require(flag == true) { "加载课表失败：服务端未确认成功" }
    val days = requireNotNull(rows) { "课表结构缺失" }
    val courses = mutableListOf<Course>()
    val dates = mutableMapOf<Int, String>()
    for (day in days) {
        val dow = requireNotNull(day.xq?.toIntOrNull()?.takeIf { it in 1..7 }) { "课表星期无效" }
        day.rq?.let { dates[dow] = it }
        for (vo in requireNotNull(day.kcVoList) { "每日课程结构缺失" }) {
            val start = requireNotNull(vo.ksjc?.toIntOrNull()?.takeIf { it >= 0 }) { "课程节次无效" }
            val end = requireNotNull(vo.jsjc?.toIntOrNull()?.takeIf { it >= start }) { "课程结束节次无效" }
            require(!vo.kcmc.isNullOrBlank()) { "课程名称缺失" }
            courses += Course(vo.kcmc, start, end, vo.jsmc.orEmpty(), vo.teacherName.orEmpty(), dow)
        }
    }
    return ScheduleData(courses, dates)
}
