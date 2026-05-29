package com.lightxin.feature.running.exercise.domain

/** 锻炼考勤轮询状态机的纯决策逻辑，可 JVM 单测。 */
enum class PollOutcome { CONTINUE, SUCCESS, TIMEOUT }

object ExerciseCheckPoller {
    /**
     * 单步决策：
     * - 打卡成功 → SUCCESS
     * - 未成功但已过截止时间 → TIMEOUT
     * - 否则继续轮询 → CONTINUE
     */
    fun decide(checkSucceeded: Boolean, nowMs: Long, deadlineMs: Long): PollOutcome = when {
        checkSucceeded -> PollOutcome.SUCCESS
        nowMs >= deadlineMs -> PollOutcome.TIMEOUT
        else -> PollOutcome.CONTINUE
    }
}
