package com.lightxin.feature.exam.data

import com.google.gson.annotations.SerializedName

data class ExamScoreResponse(
    val flag: Boolean?,
    val result: String?,
    val rows: List<ExamScoreItem>?,
)

data class ExamScoreItem(
    val kcdm: String?,
    val kcmc: String?,
    val kkbm: String?,
    val xf: String?,
    val cj: String?,
    val ksxz: String?,
    val hdjd: String?,
    val kclb: String?,
    val rkjs: String?,
)

data class SchoolYearResponse(
    val flag: Boolean?,
    val rows: List<SchoolYearItem>?,
)

data class SchoolYearItem(
    val text: String?,
    val value: String?,
)

data class CurrentTermResponse(
    val flag: Boolean?,
    val data: CurrentTermData?,
)

data class CurrentTermData(
    val xlVo: XlVo?,
)

data class XlVo(
    val xn: String?,
    val xq: String?,
    val zc: String?,
)
