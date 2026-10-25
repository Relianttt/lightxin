package com.lightxin.feature.exam.domain

data class ExamSummary(val weightedGpa: Double?, val totalCredit: Double, val courseCount: Int)

fun summarize(scores: List<ExamScore>): ExamSummary {
    val totalCredit = scores.sumOf { it.credit.toDoubleOrNull()?.takeIf(Double::isFinite) ?: 0.0 }
    var weighted = 0.0
    var weight = 0.0
    for (score in scores) {
        val credit = score.credit.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: continue
        val gpa = score.gpa.toDoubleOrNull()?.takeIf(Double::isFinite) ?: continue
        weighted += credit * gpa
        weight += credit
    }
    return ExamSummary(if (weight > 0) weighted / weight else null, totalCredit, scores.size)
}
