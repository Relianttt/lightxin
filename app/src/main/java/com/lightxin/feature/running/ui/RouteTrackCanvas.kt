package com.lightxin.feature.running.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lightxin.core.designsystem.component.lxSharedBounds
import com.lightxin.core.designsystem.theme.LxAccent
import com.lightxin.core.designsystem.theme.LxDanger
import com.lightxin.core.designsystem.theme.LxStateSuccess
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTint
import com.lightxin.feature.running.domain.TrackPoint
import kotlin.math.cos
import kotlin.math.min

/**
 * 经纬度 → 画布坐标。经度按中心纬度的 cos 修正，否则北纬 31° 附近形状会被横向拉宽约 17%。
 * 只做等比缩放和居中，不旋转、不翻转。
 */
internal fun projectTrack(points: List<TrackPoint>, width: Float, height: Float): List<Offset> {
    if (points.isEmpty() || width <= 0f || height <= 0f) return emptyList()
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLng = points.minOf { it.longitude }
    val maxLng = points.maxOf { it.longitude }
    val latSpan = (maxLat - minLat).coerceAtLeast(1e-6)
    val k = cos(Math.toRadians((minLat + maxLat) / 2))
    val lngSpanAdj = ((maxLng - minLng) * k).coerceAtLeast(1e-6)
    val scale = min(width / lngSpanAdj.toFloat(), height / latSpan.toFloat())
    val offsetX = (width - lngSpanAdj.toFloat() * scale) / 2f
    val offsetY = (height - latSpan.toFloat() * scale) / 2f
    return points.map { point ->
        Offset(
            x = offsetX + ((point.longitude - minLng) * k * scale).toFloat(),
            y = offsetY + ((maxLat - point.latitude) * scale).toFloat(),
        )
    }
}

/**
 * 把轨迹点等比投影到给定画布；列表缩略图与详情大图共用，保证共享元素形态一致。
 * [live] = true 用于跑步进行中：终点画成带光晕的强调色圆点，而不是结果页的红色终点。
 */
@Composable
fun TrackCanvas(points: List<TrackPoint>, modifier: Modifier = Modifier, live: Boolean = false) {
    if (points.size < 2) return
    val pathColor = LxAccent
    val startColor = LxStateSuccess
    val endColor = LxDanger

    Canvas(modifier = modifier) {
        val projected = projectTrack(points, size.width, size.height)
        if (projected.size < 2) return@Canvas
        val path = Path()
        val first = projected.first()
        path.moveTo(first.x, first.y)
        for (index in 1 until projected.size) {
            val point = projected[index]
            path.lineTo(point.x, point.y)
        }
        drawPath(path = path, color = pathColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color = startColor, radius = 4.dp.toPx(), center = first)
        val last = projected.last()
        if (live) {
            drawCircle(color = pathColor.copy(alpha = 0.2f), radius = 9.dp.toPx(), center = last)
            drawCircle(color = pathColor, radius = 4.dp.toPx(), center = last)
        } else {
            drawCircle(color = endColor, radius = 4.dp.toPx(), center = last)
        }
    }
}

/**
 * 列表卡片左侧 44dp 轨迹缩略图；key 与详情预览一致，共享元素只做 bounds 连续。
 * 无 scope 时退化为普通缩略图。
 */
@Composable
fun TemplateThumb(
    templateId: String,
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val shared = Modifier.lxSharedBounds(
        key = "route-preview-$templateId",
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
    Box(
        modifier = modifier
            .then(shared)
            .size(46.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(LxTint),
        contentAlignment = Alignment.Center,
    ) {
        if (points.size >= 2) {
            TrackCanvas(points = points, modifier = Modifier.fillMaxSize().padding(5.dp))
        } else {
            Text(text = "·", color = LxTextMuted)
        }
    }
}
