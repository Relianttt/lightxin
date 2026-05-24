package com.lightxin.feature.checkin.domain

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

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
) {
    /** 当前时间是否在任务的开放时间窗口内 */
    val isInOpenWindow: Boolean
        get() {
            val now = LocalDateTime.now()
            return isInTimeWindow(startTime, endTime, now)
        }

    companion object {
        private val fmtDatetimeSec = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val fmtDatetime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val fmtTimeOnly = DateTimeFormatter.ofPattern("HH:mm")

        private val formatters = listOf(fmtDatetimeSec, fmtDatetime, fmtTimeOnly)

        fun isInTimeWindow(startTimeStr: String, endTimeStr: String, now: LocalDateTime = LocalDateTime.now()): Boolean {
            val start = parseTime(startTimeStr, now) ?: return false
            val end = parseTime(endTimeStr, now) ?: return false
            return !now.isBefore(start) && now.isBefore(end)
        }

        private fun parseTime(timeStr: String, now: LocalDateTime): LocalDateTime? {
            if (timeStr.isBlank()) return null
            for (formatter in formatters) {
                try {
                    return when (formatter) {
                        fmtTimeOnly -> {
                            // "HH:mm" 格式：用传入的 now 日期补充
                            val time = LocalTime.parse(timeStr, formatter)
                            LocalDateTime.of(now.toLocalDate(), time)
                        }
                        else -> LocalDateTime.parse(timeStr, formatter)
                    }
                } catch (_: DateTimeParseException) {
                    continue
                }
            }
            return null
        }
    }
}

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
