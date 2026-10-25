package com.lightxin.feature.exam.domain

/** 学期选择项：展示文案、请求用组合值、按时间排序的键。 */
data class ExamTermOption(val label: String, val value: String, val sortKey: Int)

/**
 * 学年列表 → “学年 × 学期”选项，按时间倒序（最近的学期在最上面）。
 *
 * 服务端返回的学年顺序不保证从新到旧，也不能直接把第一学期排在第二学期前面，
 * 否则选项会表现为“2024 第一、2024 第二、2025 第一…”的交替顺序。
 */
internal fun examTermOptions(years: List<SchoolYear>): List<ExamTermOption> =
    years.flatMap { year ->
        SEMESTERS.map { (semester, label) ->
            ExamTermOption(
                label = "${year.display} $label",
                value = "${year.value}|$semester",
                sortKey = (examYearStart(year) ?: 0) * 10 + semester.toInt(),
            )
        }
    }.sortedByDescending { it.sortKey }

private val SEMESTERS = listOf("2" to "第二学期", "1" to "第一学期")

/** 从 value 或 display 里取学年起始年份；都取不到时返回 null，排序时沉底。 */
internal fun examYearStart(year: SchoolYear): Int? =
    YEAR_PATTERN.find(year.value)?.groupValues?.get(1)?.toIntOrNull()
        ?: YEAR_PATTERN.find(year.display)?.groupValues?.get(1)?.toIntOrNull()

private val YEAR_PATTERN = Regex("(\\d{4})")
