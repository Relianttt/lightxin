package com.lightxin.feature.home.domain

import java.time.LocalTime

/** 第 n 节的起止时间 */
data class SectionTime(val start: LocalTime, val end: LocalTime)

/**
 * 校园节次作息表（11 节，固定作息）
 *
 * 数据来源：用户确认的学校实际作息
 */
object SectionSchedule {
    val sections: Map<Int, SectionTime> = mapOf(
        1 to SectionTime(LocalTime.of(8, 30), LocalTime.of(9, 15)),
        2 to SectionTime(LocalTime.of(9, 20), LocalTime.of(10, 5)),
        3 to SectionTime(LocalTime.of(10, 25), LocalTime.of(11, 10)),
        4 to SectionTime(LocalTime.of(11, 15), LocalTime.of(12, 0)),
        5 to SectionTime(LocalTime.of(14, 0), LocalTime.of(14, 45)),
        6 to SectionTime(LocalTime.of(14, 50), LocalTime.of(15, 35)),
        7 to SectionTime(LocalTime.of(15, 55), LocalTime.of(16, 40)),
        8 to SectionTime(LocalTime.of(16, 45), LocalTime.of(17, 30)),
        9 to SectionTime(LocalTime.of(19, 0), LocalTime.of(19, 45)),
        10 to SectionTime(LocalTime.of(19, 55), LocalTime.of(20, 40)),
        11 to SectionTime(LocalTime.of(20, 50), LocalTime.of(21, 35)),
    )

    fun startOf(section: Int): LocalTime? = sections[section]?.start
    fun endOf(section: Int): LocalTime? = sections[section]?.end

    /** 下午第一节节次（用于中午换书提醒判断） */
    const val AFTERNOON_FIRST_SECTION = 5

    /** 晚间节次起点（19 点后第一节） */
    const val EVENING_FIRST_SECTION = 9
}
