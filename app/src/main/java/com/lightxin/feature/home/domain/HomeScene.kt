package com.lightxin.feature.home.domain

import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.schedule.domain.Course

/**
 * 首页智慧提醒卡片的可能场景，同一时刻仅有一个。
 * [None] 表示不显示任何卡片（首页回归居中问候语）。
 *
 * 场景优先级由 [SceneResolver] 统一计算，文档 5.3 为基准。
 */
sealed class HomeScene {
    /** 无提醒 —— 首页只显示居中问候 */
    object None : HomeScene()

    /** 上课日早晨（约 7:00-8:00）：列出当日上午课程作为带书提醒 */
    data class MorningBooks(val morningCourses: List<Course>) : HomeScene()

    /** 上课前 10-15 分钟 */
    data class PreClass(val course: Course, val minutesLeft: Int) : HomeScene()

    /** 正在上课 */
    data class InClass(val course: Course) : HomeScene()

    /** 当前节课还剩 0-5 分钟，且今日还有后续课 */
    data class PreNextAfterClass(val nextCourse: Course, val minutesToNext: Int) : HomeScene()

    /** 中午换书：下午第一节前约 30 分钟 */
    data class LunchBooks(val afternoonCourses: List<Course>) : HomeScene()

    /** 晚间查寝未完成（22:00 之后且有未签到任务） */
    data class EveningCheckin(val task: CheckinTask) : HomeScene()
}
