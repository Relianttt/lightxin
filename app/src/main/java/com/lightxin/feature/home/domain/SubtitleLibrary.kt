package com.lightxin.feature.home.domain

import com.lightxin.feature.schedule.domain.Course
import java.time.LocalDateTime
import kotlin.math.absoluteValue

/**
 * 副标题计算所需的上下文信号
 */
data class SubtitleContext(
    val now: LocalDateTime,
    val todayCourses: List<Course>,
    val tomorrowFirstSection: Int?,
    val hasPendingCheckin: Boolean,
)

/**
 * 副标题文案库。
 *
 * 按「条件桶」分组存储；同一日期 + 同一桶下文案稳定（用日期种子选 index），
 * 跨天自然轮换。避免程序感，语言偏口语、自然。
 *
 * 文档 3.3 的三类切换时机（手动刷新 / 后台回前台 >30min / 跨时段）在
 * HomeViewModel 中驱动，此处只负责根据上下文挑一条。
 */
object SubtitleLibrary {

    /**
     * @param rotation 同桶内轮换计数器，默认 0；由 HomeViewModel 在手动刷新或
     * 后台返回超过 30 分钟时递增，以驱动同日同桶下文案切换
     */
    fun pickSubtitle(context: SubtitleContext, rotation: Int = 0): String {
        val bucket = chooseBucket(context)
        val pool = pool(bucket)
        if (pool.isEmpty()) return ""
        val seed = (context.now.toLocalDate().toEpochDay().toInt() + rotation).absoluteValue
        return pool[seed % pool.size]
    }

    /**
     * 把 [HomeScene] 转成一行副标题文案。[HomeScene.None] 返回 null，
     * 表示当前无场景可说，交回 [pickSubtitle] 用通用文案兜底。
     */
    fun fromScene(scene: HomeScene): String? = when (scene) {
        HomeScene.None -> null
        is HomeScene.MorningBooks -> {
            val names = scene.morningCourses.joinToString("、") { it.name }
            "今天上午有 $names，别带错书了"
        }
        is HomeScene.PreClass -> "还有 ${scene.minutesLeft} 分钟上课 · ${scene.course.name}"
        is HomeScene.InClass -> "专心上课，下课见"
        is HomeScene.PreNextAfterClass -> {
            "下一节 ${scene.nextCourse.name}，还有 ${scene.minutesToNext} 分钟"
        }
        is HomeScene.LunchBooks -> {
            "下午还有 ${scene.afternoonCourses.size} 节课，记得换书"
        }
        is HomeScene.EveningCheckin -> "别忘了完成查寝签到"
    }

    private fun chooseBucket(ctx: SubtitleContext): SubtitleBucket {
        val hour = ctx.now.hour

        if (hour in 0..5) return SubtitleBucket.LateNight
        if (hour >= 23 && (ctx.tomorrowFirstSection ?: Int.MAX_VALUE) <= 2) {
            return SubtitleBucket.EarlyClassTomorrow
        }
        if (hour >= 19 && ctx.hasPendingCheckin) return SubtitleBucket.PendingCheckin
        if (hour >= 23) return SubtitleBucket.DeepNight

        if (ctx.todayCourses.isEmpty()) {
            return when (hour) {
                in 6..9 -> SubtitleBucket.FreeMorningEarly
                in 10..11 -> SubtitleBucket.FreeMorningLate
                in 12..13 -> SubtitleBucket.FreeNoon
                in 14..17 -> SubtitleBucket.FreeAfternoon
                in 18..20 -> SubtitleBucket.FreeEvening
                else -> SubtitleBucket.FreeNight
            }
        }

        val lastEndSection = ctx.todayCourses.maxOf { it.endSection }
        val lastEndTime = SectionSchedule.endOf(lastEndSection)
        if (lastEndTime != null && ctx.now.toLocalTime() >= lastEndTime) {
            return SubtitleBucket.DayClassOver
        }

        return when (hour) {
            in 6..8 -> SubtitleBucket.DefaultMorning
            in 9..11 -> SubtitleBucket.DefaultForenoon
            in 12..13 -> SubtitleBucket.DefaultNoon
            in 14..17 -> SubtitleBucket.DefaultAfternoon
            in 18..20 -> SubtitleBucket.DefaultDusk
            in 21..22 -> SubtitleBucket.DefaultLate
            else -> SubtitleBucket.Fallback
        }
    }

    private fun pool(bucket: SubtitleBucket): List<String> = when (bucket) {
        SubtitleBucket.LateNight -> listOf(
            "这么晚了还没睡？注意身体哦",
            "凌晨了，早点休息",
            "夜深了，要好好照顾自己",
        )
        SubtitleBucket.EarlyClassTomorrow -> listOf(
            "明天有早课，记得早点休息哦",
            "早课别迟到，今晚早点睡",
        )
        SubtitleBucket.PendingCheckin -> listOf(
            "别忘了完成查寝签到哦",
            "查寝还没签，记得处理一下",
        )
        SubtitleBucket.DeepNight -> listOf(
            "夜深了，早点休息",
            "晚安，明天继续",
        )
        SubtitleBucket.FreeMorningEarly -> listOf(
            "今天没课，可以多睡一会儿",
            "没课的早晨，舒服一点",
        )
        SubtitleBucket.FreeMorningLate -> listOf(
            "今天没课，享受悠闲的上午",
            "空闲的上午，慢慢来",
        )
        SubtitleBucket.FreeNoon -> listOf(
            "今天没课，午饭后休息一下",
            "无课的午间，轻松一点",
        )
        SubtitleBucket.FreeAfternoon -> listOf(
            "今天没课，下午自由安排",
            "没课的下午，好好放松",
        )
        SubtitleBucket.FreeEvening -> listOf(
            "今天没课的一天，轻松惬意",
            "悠闲的夜晚，做些自己喜欢的事",
        )
        SubtitleBucket.FreeNight -> listOf(
            "今天没课，好好放松",
            "轻松的一天，辛苦啦",
        )
        SubtitleBucket.DayClassOver -> listOf(
            "今天的课结束了，辛苦啦",
            "今天的课都上完了",
            "课上完了，该休息啦",
        )
        SubtitleBucket.DefaultMorning -> listOf(
            "新的一天，元气满满",
            "早上好，加油哦",
            "新的一天，加油",
        )
        SubtitleBucket.DefaultForenoon -> listOf(
            "上午加油，效率拉满",
            "上午时光，认真一点",
        )
        SubtitleBucket.DefaultNoon -> listOf(
            "午间时光，记得吃饭",
            "中午了，别忘记午饭",
        )
        SubtitleBucket.DefaultAfternoon -> listOf(
            "下午继续，保持状态",
            "下午也要加油",
        )
        SubtitleBucket.DefaultDusk -> listOf(
            "傍晚了，今天辛苦了",
            "夕阳西下，放缓节奏",
        )
        SubtitleBucket.DefaultLate -> listOf(
            "晚间时光，放松一下",
            "忙了一天，放松一下",
        )
        SubtitleBucket.Fallback -> listOf(
            "轻小信，陪你度过每一天",
        )
    }
}

private enum class SubtitleBucket {
    LateNight,
    EarlyClassTomorrow,
    PendingCheckin,
    DeepNight,
    FreeMorningEarly,
    FreeMorningLate,
    FreeNoon,
    FreeAfternoon,
    FreeEvening,
    FreeNight,
    DayClassOver,
    DefaultMorning,
    DefaultForenoon,
    DefaultNoon,
    DefaultAfternoon,
    DefaultDusk,
    DefaultLate,
    Fallback,
}

/**
 * 时段问候（大标题用）：如「下午好」「晚上好」。
 * 主标题文案不参与动画切换，稳定。
 */
object GreetingPhrase {
    fun of(hour: Int): String = when (hour) {
        in 0..5 -> "夜深了"
        in 6..11 -> "早上好"
        in 12..17 -> "下午好"
        else -> "晚上好"
    }
}
