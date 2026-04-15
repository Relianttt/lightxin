package com.lightxin.feature.schedule.data

import com.google.gson.annotations.SerializedName

/**
 * selectZcList.do 响应
 */
data class WeekListResponse(
    val data: WeekListData?,
    val rows: List<WeekRow>?,
    val flag: Boolean?,
)

data class WeekListData(
    val week: String?,
    val schoolYear: String?,
    val schoolTerm: String?,
)

data class WeekRow(
    val zc: String?,
    @SerializedName("zcName") val name: String?,
)

/**
 * selectStuSelfTimeTable.do 响应
 */
data class TimeTableResponse(
    val rows: List<DaySchedule>?,
    val flag: Boolean?,
)

data class DaySchedule(
    val xq: String?,
    val kcVoList: List<CourseVo>?,
)

data class CourseVo(
    val kcmc: String?,       // 课程名称
    val ksjc: String?,       // 开始节次
    val jsjc: String?,       // 结束节次
    val jsmc: String?,       // 教室名称
    val teacherName: String?, // 教师姓名
    val kcbh: String?,       // 课程编号
)
