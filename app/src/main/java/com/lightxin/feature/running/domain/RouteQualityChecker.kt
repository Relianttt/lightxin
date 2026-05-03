package com.lightxin.feature.running.domain

/**
 * 轻量路线质量校验：硬闸由 RouteTemplateRules 负责；本校验仅对已通过硬闸的轨迹做"警告级"检测。
 * 规则（任一触发即 WARNING）：
 *   - 校园边界框外点占比 > 30%
 *   - 存在 >80m 的大跳段且数量 > 总段数的 5%
 *   - 有效移动段占比 < 60%（表示长时间原地停留）
 */
object RouteQualityChecker {

    private const val JUMP_SEGMENT_METERS = 80.0
    private const val MIN_MOVING_SEGMENT_METERS = 1.5

    data class Result(val status: RouteQualityStatus, val message: String?)

    fun evaluate(points: List<TrackPoint>): Result {
        if (points.size < 2) return Result(RouteQualityStatus.PASS, null)

        val outOfBounds = points.count { !inCampus(it) }
        val outRatio = outOfBounds.toDouble() / points.size

        var jumpCount = 0
        var movingSegments = 0
        val totalSegments = points.size - 1
        for (i in 1 until points.size) {
            val d = GeoDistance.metersBetween(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
            )
            if (d > JUMP_SEGMENT_METERS) jumpCount++
            if (d >= MIN_MOVING_SEGMENT_METERS) movingSegments++
        }
        val jumpRatio = jumpCount.toDouble() / totalSegments
        val movingRatio = movingSegments.toDouble() / totalSegments

        val warnings = mutableListOf<String>()
        if (outRatio > 0.30) {
            warnings += "约 ${(outRatio * 100).toInt()}% 的点超出校园范围"
        }
        if (jumpRatio > 0.05) {
            warnings += "存在 $jumpCount 个大跳段（>${JUMP_SEGMENT_METERS.toInt()}m）"
        }
        if (movingRatio < 0.60) {
            warnings += "有效移动段占比仅 ${(movingRatio * 100).toInt()}%"
        }

        return if (warnings.isEmpty()) Result(RouteQualityStatus.PASS, null)
        else Result(RouteQualityStatus.WARNING, warnings.joinToString("；"))
    }

    private fun inCampus(p: TrackPoint): Boolean =
        p.latitude in RunningCampus.minLatitude..RunningCampus.maxLatitude &&
        p.longitude in RunningCampus.minLongitude..RunningCampus.maxLongitude
}
