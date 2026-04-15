package com.lightxin.feature.labor.data

/**
 * queryPersonalTimesTotal.do 响应
 */
data class HoursTotalResponse(
    val data: NestedData<HoursTotalData>?,
    val flag: Boolean?,
    val result: String?,
)

data class NestedData<T>(
    val data: T?,
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
    val rows: List<ActivityRow>?,
    val total: Int?,
    val flag: Boolean?,
    val result: String?,
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
    val data: NestedData<ActivityDetailData>?,
    val flag: Boolean?,
    val result: String?,
)

data class ActivityDetailData(
    val organizer: String?,
    val serviceTimes: String?,
    val activityName: String?,
    val activityType: String?,
    val activityLevel: String?,
    val createDate: String?,
)
