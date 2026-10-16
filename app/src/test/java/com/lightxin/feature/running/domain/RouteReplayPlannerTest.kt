package com.lightxin.feature.running.domain

import org.junit.Assert.*
import org.junit.Test

class RouteReplayPlannerTest {
    private val points = (0..4).map { RunningCampus.pointAt(it * 20.0, 0.0).copy(timestampMillis = it * 1000L) }
    private val template = RouteTemplate("id", "test", 0, 0, 80.0, 4,
        RouteTemplateSource.TEMPLATE_RECORDING, true, points)
    private val config = SimConfig(0.1, 1, 1000)

    @Test fun missingRejectedAndBrokenTemplatesNeverFallBackToRandomRoutes() {
        assertTrue(RouteReplayPlanner.prepare(null, config) is RouteReplayPreparation.Invalid)
        assertTrue(RouteReplayPlanner.prepare(template.copy(points = emptyList()), config) is RouteReplayPreparation.Invalid)
        assertTrue(RouteReplayPlanner.prepare(template.copy(qualityStatus = RouteQualityStatus.REJECTED), config, true)
            is RouteReplayPreparation.Invalid)
    }

    @Test fun normalTemplateIsDeterministic() {
        val first = RouteReplayPlanner.prepare(template, config) as RouteReplayPreparation.Ready
        val second = RouteReplayPlanner.prepare(template, config) as RouteReplayPreparation.Ready
        assertEquals(first.points, second.points)
    }

    @Test fun qualityProblemsRequireConfirmationAndDoNotModifyCoordinates() {
        val bad = template.copy(points = listOf(points[0], points[0], points[1].copy(timestampMillis = -1),
            points[2].copy(timestampMillis = 100_000)))
        val quality = RouteQualityChecker.evaluate(bad.points)
        assertTrue(quality.message!!.contains("重复时间戳"))
        assertTrue(quality.message!!.contains("倒退时间戳"))
        assertTrue(quality.message!!.contains("零距离段"))
        assertTrue(quality.message!!.contains("采样间隔"))
        assertTrue(RouteReplayPlanner.prepare(bad, config) is RouteReplayPreparation.NeedsConfirmation)
        val ready = RouteReplayPlanner.prepare(bad, config, true) as RouteReplayPreparation.Ready
        assertEquals(bad.points.map { it.latitude to it.longitude }, ready.points.take(4).map { it.latitude to it.longitude })
    }

    @Test fun jumpsAndPersistedWarningsAreNotSilentlyRepaired() {
        val jump = template.copy(points = listOf(points[0], RunningCampus.pointAt(300.0, 0.0).copy(timestampMillis = 1000)))
        val result = RouteReplayPlanner.prepare(jump, config) as RouteReplayPreparation.NeedsConfirmation
        assertTrue(result.message.contains("大跳段"))
        assertTrue(RouteReplayPlanner.prepare(template.copy(qualityStatus = RouteQualityStatus.WARNING), config)
            is RouteReplayPreparation.NeedsConfirmation)
    }
}
