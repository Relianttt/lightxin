package com.lightxin.core.notification

import android.os.Bundle
import com.lightxin.R
import com.lightxin.feature.home.domain.SectionSchedule
import com.lightxin.feature.schedule.domain.Course
import com.lightxin.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseNotificationScheduler @Inject constructor(
    private val notifier: LiveActivityNotifier,
) {
    companion object {
        private const val KEY = "course_countdown"
        private const val TRIGGER_MINUTES = 25L
        private const val DISMISS_AFTER_START_MINUTES = 5L
    }

    private var job: Job? = null

    fun start(scope: CoroutineScope, coursesProvider: () -> List<Course>) {
        job?.cancel()
        job = scope.launch {
            while (true) {
                check(coursesProvider())
                delay(60_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        notifier.cancel(KEY)
    }

    fun checkNow(courses: List<Course>) {
        check(courses)
    }

    private fun check(courses: List<Course>) {
        val now = LocalTime.now()
        val target = findTargetCourse(courses, now)

        if (target == null) {
            notifier.cancel(KEY)
            return
        }

        val startTime = SectionSchedule.startOf(target.startSection) ?: return
        val minutesUntilStart = ChronoUnit.MINUTES.between(now, startTime)

        val (title, statusText) = if (minutesUntilStart > 0) {
            target.name to "即将上课 | 还有 ${minutesUntilStart} 分钟"
        } else {
            target.name to "已上课"
        }

        val capsuleText = shortenRoom(target.room)
        val content = "$statusText\n地点：${target.room}"
        val bigText = "$statusText\n地点：${target.room}"

        val extras = Bundle().apply {
            putString("courseName", target.name)
            putString("statusText", statusText)
            putString("room", target.room)
            putString("bigText", bigText)
        }

        val request = LiveActivityRequest(
            key = KEY,
            channelId = KEY,
            title = title,
            content = statusText,
            capsuleText = capsuleText,
            route = NotificationRoute(destination = Routes.AICLASS_HOME),
            extras = extras,
        )
        notifier.show(request)
    }

    /**
     * 简化地点名称用于胶囊显示。
     * "信息楼（A1）S204" → "A1S204"
     * 提取括号内容 + 括号后内容，去掉中文楼名。
     */
    private fun shortenRoom(room: String): String {
        val match = Regex("[（(]([^）)]+)[）)](.*)").find(room)
        if (match != null) {
            val prefix = match.groupValues[1]
            val suffix = match.groupValues[2].trim()
            return (prefix + suffix).take(7)
        }
        return room.take(7)
    }

    /**
     * 找到当前应该显示通知的课程：
     * - 距开始 ≤25 分钟且尚未开始超过 5 分钟的课程
     */
    private fun findTargetCourse(courses: List<Course>, now: LocalTime): Course? {
        return courses
            .sortedBy { it.startSection }
            .firstOrNull { course ->
                val startTime = SectionSchedule.startOf(course.startSection) ?: return@firstOrNull false
                val minutesUntilStart = ChronoUnit.MINUTES.between(now, startTime)
                // 课前 25 分钟 到 课后 5 分钟
                minutesUntilStart in -DISMISS_AFTER_START_MINUTES..TRIGGER_MINUTES
            }
    }
}
