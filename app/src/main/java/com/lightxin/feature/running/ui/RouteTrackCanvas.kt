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
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxSage
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.feature.running.domain.TrackPoint

/** 把轨迹点等比投影到给定画布；列表缩略图与详情大图共用，保证共享元素形态一致。 */
@Composable
fun TrackCanvas(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLng = points.minOf { it.longitude }
    val maxLng = points.maxOf { it.longitude }
    val latSpan = (maxLat - minLat).coerceAtLeast(1e-6)
    val lngSpan = (maxLng - minLng).coerceAtLeast(1e-6)

    val pathColor = LxTerra
    val startColor = LxSage
    val endColor = LxRose

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val scale = minOf(w / lngSpan.toFloat(), h / latSpan.toFloat())
        val offsetX = (w - lngSpan.toFloat() * scale) / 2f
        val offsetY = (h - latSpan.toFloat() * scale) / 2f

        fun project(p: TrackPoint): Offset {
            val x = offsetX + ((p.longitude - minLng) * scale).toFloat()
            val y = offsetY + ((maxLat - p.latitude) * scale).toFloat()
            return Offset(x, y)
        }

        val path = Path()
        val first = project(points.first())
        path.moveTo(first.x, first.y)
        for (i in 1 until points.size) {
            val o = project(points[i])
            path.lineTo(o.x, o.y)
        }
        drawPath(path = path, color = pathColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color = startColor, radius = 4.dp.toPx(), center = first)
        drawCircle(color = endColor, radius = 4.dp.toPx(), center = project(points.last()))
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
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(LxCream),
        contentAlignment = Alignment.Center,
    ) {
        if (points.size >= 2) {
            TrackCanvas(points = points, modifier = Modifier.fillMaxSize().padding(5.dp))
        } else {
            Text(text = "·", color = LxInkMuted)
        }
    }
}
