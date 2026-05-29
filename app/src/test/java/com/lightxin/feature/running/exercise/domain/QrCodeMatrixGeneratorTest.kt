package com.lightxin.feature.running.exercise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeMatrixGeneratorTest {

    @Test
    fun `encode produces non-empty square matrix`() {
        val matrix = QrCodeMatrixGenerator.encode("""{"exerciseId":"A"}""", size = 256)

        assertEquals(256, matrix.size)
        assertEquals(256, matrix[0].size)
        // 二维码至少有黑模块
        assertTrue(matrix.any { row -> row.any { it } })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank content rejected`() {
        QrCodeMatrixGenerator.encode("", size = 256)
    }
}
