package com.lightxin.feature.home.domain

import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.schedule.domain.Course
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 根据当前时间 + 今日课程 + 查寝任务，计算首页应展示哪一个 [HomeScene]。
 *
 * 优先级（对应 homepage-design-spec.md 第 5.3 节）：
 *   1. PreClass（上课前 10-15 分钟 —— 紧急提醒优先）
 *   2. PreNextAfterClass（下课前 5 分钟且有后续 —— 次级紧急）
 *   3. InClass（上课中 —— 安静状态）
 *   4. EveningCheckin（晚间查寝未完成）
 *   5. LunchBooks（中午换书）
 *   6. MorningBooks（早晨带书）
 *   7. None
 *
 * 纯函数实现，便于未来接入单元测试。
 */
object SceneResolver {

    /** 上课前提前展示窗口（分钟） */
    private const val PRE_CLASS_WINDOW = 15

    /** 下课前预告下节课的窗口（分钟） */
    private const val PRE_NEXT_WINDOW = 5

    /** 中午换书提醒：距下午第一节 30 分钟内 */
    private const val LUNCH_BOOKS_WINDOW = 30

    /** 早晨带书提醒时段：7:00-8:00 */
    private val MORNING_BOOKS_START = LocalTime.of(7, 0)
    private val MORNING_BOOKS_END = LocalTime.of(8, 0)

    /** 晚间查寝触发起点：22:00 */
    private val EVENING_CHECKIN_START = LocalTime.of(22, 0)

    fun resolve(
        now: LocalDateTime,
        todayCourses: List<Course>,
        nextUnsignedCheckin: CheckinTask?,
    ): HomeScene {
        val time = now.toLocalTime()
        val sorted = todayCourses.sortedBy { it.startSection }

        preClassScene(time, sorted)?.let { return it }
        preNextScene(time, sorted)?.let { return it }
        inClassScene(time, sorted)?.let { return it }
        eveningCheckinScene(time, nextUnsignedCheckin)?.let { return it }
        lunchBooksScene(time, sorted)?.let { return it }
        morningBooksScene(time, sorted)?.let { return it }

        return HomeScene.None
    }

    private fun preClassScene(now: LocalTime, courses: List<Course>): HomeScene.PreClass? {
        for (course in courses) {
            val start = SectionSchedule.startOf(course.startSection) ?: continue
            if (now >= start) continue
            val minutes = Duration.between(now, start).toMinutes().toInt()
            if (minutes in 1..PRE_CLASS_WINDOW) {
                return HomeScene.PreClass(course, minutes)
            }
        }
        return null
    }

    private fun preNextScene(now: LocalTime, courses: List<Course>): HomeScene.PreNextAfterClass? {
        val current = currentCourse(now, courses) ?: return null
        val end = SectionSchedule.endOf(current.endSection) ?: return null
        val minutesToEnd = Duration.between(now, end).toMinutes().toInt()
        if (minutesToEnd !in 0..PRE_NEXT_WINDOW) return null

        val next = courses.firstOrNull { it.startSection > current.endSection } ?: return null
        val nextStart = SectionSchedule.startOf(next.startSection) ?: return null
        val minutesToNext = Duration.between(now, nextStart).toMinutes().toInt()
        return HomeScene.PreNextAfterClass(next, minutesToNext)
    }

    private fun inClassScene(now: LocalTime, courses: List<Course>): HomeScene.InClass? {
        val current = currentCourse(now, courses) ?: return null
        return HomeScene.InClass(current)
    }

    private fun eveningCheckinScene(
        now: LocalTime,
        task: CheckinTask?,
    ): HomeScene.EveningCheckin? {
        if (task == null || task.isSigned) return null
        if (now < EVENING_CHECKIN_START) return null
        return HomeScene.EveningCheckin(task)
    }

    private fun lunchBooksScene(now: LocalTime, courses: List<Course>): HomeScene.LunchBooks? {
        val afternoonStart = SectionSchedule.startOf(SectionSchedule.AFTERNOON_FIRST_SECTION)
            ?: return null
        if (now >= afternoonStart) return null
        val minutes = Duration.between(now, afternoonStart).toMinutes().toInt()
        if (minutes !in 1..LUNCH_BOOKS_WINDOW) return null

        // 下午课程范围：第 5 节起至第 8 节（第 9 节及以后归为晚间）
        val afternoonCourses = courses.filter {
            it.startSection in SectionSchedule.AFTERNOON_FIRST_SECTION until SectionSchedule.EVENING_FIRST_SECTION
        }
        if (afternoonCourses.isEmpty()) return null
        return HomeScene.LunchBooks(afternoonCourses)
    }

    private fun morningBooksScene(now: LocalTime, courses: List<Course>): HomeScene.MorningBooks? {
        if (now < MORNING_BOOKS_START || now >= MORNING_BOOKS_END) return null
        val morningCourses = courses.filter {
            it.startSection < SectionSchedule.AFTERNOON_FIRST_SECTION
        }
        if (morningCourses.isEmpty()) return null
        return HomeScene.MorningBooks(morningCourses)
    }

    private fun currentCourse(now: LocalTime, courses: List<Course>): Course? {
        return courses.firstOrNull { course ->
            val start = SectionSchedule.startOf(course.startSection) ?: return@firstOrNull false
            val end = SectionSchedule.endOf(course.endSection) ?: return@firstOrNull false
            now >= start && now < end
        }
    }
}
