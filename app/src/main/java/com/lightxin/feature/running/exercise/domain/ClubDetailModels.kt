package com.lightxin.feature.running.exercise.domain

/** auto/clubInfo 任务节点的活动状态。⚠️ 与 newAutoInfo 的 checkState（签到状态）语义不同，独立 enum。 */
enum class ClubTaskState { ACTIVE, FINISHED, UNKNOWN }

/** 单条历史锻炼考勤打卡记录，来自 auto/clubInfo rows[].checkList[]。 */
data class ClubCheckRecord(
    val isNormal: Boolean,
    val startDate: String,
    val endDate: String,
    val venueName: String,
    val duration: String,
)

/** 一个自主练习任务节点，来自 auto/clubInfo rows[]。不含场馆列表（UI 不展示）。 */
data class ClubTask(
    val autoId: String,
    val memberId: String,
    val name: String,
    val requiredMinutes: Int,
    val completedMinutes: Int,
    val state: ClubTaskState,
    val startDate: String,
    val endDate: String,
    val checkRecords: List<ClubCheckRecord>,
    /** 是否可发起锻炼考勤：state=ACTIVE 且当前时间在有效窗内。 */
    val canCheck: Boolean,
)
