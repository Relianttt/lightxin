package com.lightxin.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分段控件指示器的连续权重：位置落在某 tab 上为 1，到相邻 tab 为 0，
 * 拖动过程中单调下降、只在过半时跨过 0.5，避免“指示器瞬切 + 内容慢动”。
 */
class TabSelectionWeightTest {
    @Test
    fun settledOnTabGivesFullWeight() {
        assertEquals(1f, tabSelectionWeight(0, 0f), 0.0001f)
        assertEquals(1f, tabSelectionWeight(1, 1f), 0.0001f)
    }

    @Test
    fun weightFallsLinearlyWithDistance() {
        assertEquals(0.5f, tabSelectionWeight(0, 0.5f), 0.0001f)
        assertEquals(0.25f, tabSelectionWeight(0, 0.75f), 0.0001f)
        assertEquals(0f, tabSelectionWeight(0, 1f), 0.0001f)
        assertEquals(0.5f, tabSelectionWeight(1, 0.5f), 0.0001f)
    }

    @Test
    fun weightDecaysBeyondBothEnds() {
        assertEquals(0.8f, tabSelectionWeight(0, -0.2f), 0.0001f)
        assertEquals(0.6f, tabSelectionWeight(1, 1.4f), 0.0001f)
        assertEquals(0f, tabSelectionWeight(0, 2f), 0.0001f)
    }

    @Test
    fun draggingCrossesMidpointOnlyOnce() {
        val weights = (0..10).map { tabSelectionWeight(0, it / 10f) }
        assertEquals(weights.sortedDescending(), weights)
        assertTrue(weights.last() == 0f)
        assertEquals(6, weights.indexOfFirst { it < 0.5f })
    }
}
