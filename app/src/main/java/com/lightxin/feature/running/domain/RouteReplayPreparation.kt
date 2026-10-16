package com.lightxin.feature.running.domain

sealed interface RouteReplayPreparation {
    data class Ready(val points: List<TrackPoint>) : RouteReplayPreparation
    data class NeedsConfirmation(val message: String) : RouteReplayPreparation
    data class Invalid(val message: String) : RouteReplayPreparation
}

object RouteReplayPlanner {
    fun prepare(template: RouteTemplate?, config: SimConfig, qualityConfirmed: Boolean = false): RouteReplayPreparation {
        if (template == null) return RouteReplayPreparation.Invalid("请先录制路线并设置默认模板")
        if (template.qualityStatus == RouteQualityStatus.REJECTED) {
            return RouteReplayPreparation.Invalid("当前模板不可用，请重新录制或选择其他模板")
        }
        val points = try { TrajectoryGenerator.generateFromTemplate(config, template) }
        catch (e: IllegalArgumentException) { return RouteReplayPreparation.Invalid(e.message ?: "模板不可用") }
        val quality = RouteQualityChecker.evaluate(template.points)
        val warning = listOfNotNull(template.qualityMessage, quality.message).distinct().joinToString("；")
        if (!qualityConfirmed && (warning.isNotBlank() || template.qualityStatus == RouteQualityStatus.WARNING)) {
            return RouteReplayPreparation.NeedsConfirmation(warning.ifBlank { "当前模板存在质量警告" })
        }
        return RouteReplayPreparation.Ready(points)
    }
}
