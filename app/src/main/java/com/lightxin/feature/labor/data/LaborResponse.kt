package com.lightxin.feature.labor.data

/**
 * queryPersonalTimesTotal.do 响应
 */
data class HoursTotalResponse(
    val data: HoursTotalData?,
    val flag: Boolean?,
)

data class HoursTotalData(
    val voluntaryTimes: String?,
    val summerTimes: String?,
    val laborTimes: String?,
    val socialTimes: String?,
    val otherTimes: String?,
)

/**
 * queryTimesDetailsPage.do 响应
 */
data class ActivityPageResponse(
    val data: ActivityPageData?,
    val flag: Boolean?,
)

data class ActivityPageData(
    val list: List<ActivityRow>?,
    val totalPage: Int?,
    val totalCount: Int?,
)

data class ActivityRow(
    val id: String?,
    val projectTypeName: String?,
    val activityName: String?,
    val serviceTimes: String?,
    val createDate: String?,
    val type: String?,
)

/**
 * queryTimesDetails.do 响应
 */
data class ActivityDetailResponse(
    val data: ActivityDetailData?,
    val flag: Boolean?,
)

data class ActivityDetailData(
    val organizer: String?,
    val serviceTimes: String?,
    val activityName: String?,
    val activityType: String?,
    val activityLevel: String?,
    val createDate: String?,
)
