package com.lightxin.feature.holiday.domain

import com.lightxin.feature.checkin.domain.CheckinTask

/**
 * 统一列表项 sealed interface
 * 供 CheckinListScreen 将查寝任务和节假日任务合并为单一列表
 */
sealed interface TaskListItem {
    data class Checkin(val task: CheckinTask) : TaskListItem
    data class Holiday(val task: HolidayTask) : TaskListItem
}

/**
 * 节假日登记任务（列表项领域模型）
 */
data class HolidayTask(
    val holidayId: String,
    val name: String,
    val registerStartDate: String,
    val registerEndDate: String,
    val isRegistered: Boolean,
    val allowStaySchool: Boolean,
    val startDate: String,
    val endDate: String,
)

/**
 * 登记表单数据
 */
data class HolidayFormData(
    val startDate: String = "",
    val endDate: String = "",
    val stroke: String = "0",   // "0"=离校 "1"=留校
    val reason: String = "",
    val destination: String = "",
    val urgentPhone: String = "",
    val registerId: String = "",  // 已有记录 ID，回填时不为空
)

/**
 * 离校/留校字典选项
 */
data class StrokeOption(
    val label: String,
    val value: String,
)
