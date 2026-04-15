package com.lightxin.feature.checkin.domain

/**
 * 签到任务（列表项）
 */
data class CheckinTask(
    val id: String,
    val taskName: String,
    val taskDateId: String,
    val isSigned: Boolean,
    val startTime: String,
    val endTime: String,
)

/**
 * 任务详情
 */
data class TaskDetail(
    val taskName: String,
    val taskDateId: String,
    val startTime: String,
    val endTime: String,
    val needPhoto: Boolean,
    val isSigned: Boolean,
    val signinPlace: String,
    val locationRange: Double,   // 签到范围(米)
    val centerLng: Double,       // 签到中心经度
    val centerLat: Double,       // 签到中心纬度
    val address: String,
)
