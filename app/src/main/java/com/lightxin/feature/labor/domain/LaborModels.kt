package com.lightxin.feature.labor.domain

data class HoursSummary(
    val voluntaryTimes: Double,  // 志愿
    val summerTimes: Double,     // 暑期
    val laborTimes: Double,      // 劳动
    val socialTimes: Double,     // 社区
    val otherTimes: Double,      // 其他
) {
    val totalTimes: Double
        get() = voluntaryTimes + summerTimes + laborTimes + socialTimes + otherTimes
}

data class ActivityRecord(
    val id: String,
    val projectTypeName: String,
    val type: String,
    val activityName: String,
    val serviceTimes: Double,
    val createDate: String,
)

data class ActivityDetail(
    val activityName: String,
    val activityType: String,
    val activityLevel: String,
    val organizer: String,
    val serviceTimes: Double,
    val createDate: String,
)
