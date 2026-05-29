package com.lightxin.feature.running.exercise.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** 二维码矩阵生成核心：纯逻辑，仅依赖 zxing-core，可在 JVM 单测。Bitmap 转换在 UI 层做。 */
object QrCodeMatrixGenerator {
    fun encode(content: String, size: Int = 512): Array<BooleanArray> {
        require(content.isNotBlank()) { "二维码内容不能为空" }
        require(size > 0) { "尺寸必须为正" }
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        return Array(matrix.height) { y ->
            BooleanArray(matrix.width) { x -> matrix.get(x, y) }
        }
    }
}
