package com.lightxin.feature.schedule.ui

/** 选中胶囊在两枚周次胶囊之间的左右边界（px）。 */
internal data class IndicatorSpan(val start: Float, val end: Float)

/**
 * 选中胶囊从 [from] 滑向 [to] 时的形态，[fraction] ∈ [0, 1]。
 *
 * 右边用 ease-out、左边用 ease-in：往右滑时右边先冲出去、左边后跟上，往左滑（fraction 递减）
 * 反过来左边领先——胶囊在半路被拉长，到站时收回原宽，方向无关。
 */
internal fun stretchedSpan(from: IndicatorSpan, to: IndicatorSpan, fraction: Float): IndicatorSpan {
    val f = fraction.coerceIn(0f, 1f)
    val lead = 1f - (1f - f) * (1f - f)
    val trail = f * f
    return IndicatorSpan(
        start = from.start + (to.start - from.start) * trail,
        end = from.end + (to.end - from.end) * lead,
    )
}

/** 半路的拉伸程度：两端为 0、正中为 1，用来让胶囊被拉长时同步变细一点。 */
internal fun stretchAmount(fraction: Float): Float {
    val f = fraction.coerceIn(0f, 1f)
    return 4f * f * (1f - f)
}

/** 某个周次胶囊被选中胶囊覆盖的程度（文字颜色据此渐变）：正中为 1，相距一格及以上为 0。 */
internal fun chipEmphasis(position: Float, index: Int): Float =
    (1f - kotlin.math.abs(position - index)).coerceIn(0f, 1f)
