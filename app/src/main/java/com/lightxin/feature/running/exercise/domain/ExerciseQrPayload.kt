package com.lightxin.feature.running.exercise.domain

/**
 * 锻炼考勤二维码编码的本地载荷。
 * 字段来自 auto/clubInfo 活动任务（exerciseId=autoId, memberId）+ 本地生成（studentCode, timestamp）。
 * checkType/qrCodeType 按原 app 实机扫码确认的常量值。
 */
data class ExerciseQrPayload(
    val exerciseId: String,
    val memberId: String,
    val studentCode: String,
    val timestamp: Long,
    val checkType: String = "1",
    val qrCodeType: Int = 1,
) {
    /** 字段顺序对齐原 app 实机扫码内容：exerciseId/memberId/checkType/qrCodeType/studentCode/timestamp。 */
    fun toJson(): String =
        """{"exerciseId":"$exerciseId","memberId":"$memberId","checkType":"$checkType",""" +
            """"qrCodeType":$qrCodeType,"studentCode":"$studentCode","timestamp":$timestamp}"""
}
