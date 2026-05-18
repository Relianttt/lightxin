package com.lightxin.feature.credit.domain

data class CreditOverview(
    val totalCredit: Double,
    val pass: Boolean,
    val modules: List<CreditModule>,
)

data class CreditModule(
    val name: String,
    val type: String,
    val credit: Double,
)

data class CreditRecord(
    val id: String,
    val name: String,
    val score: Double,
    val statusName: String,
)

data class CreditRecordDetail(
    val name: String,
    val highestLevelName: String,
    val awardLevelName: String,
    val awardPrizeName: String,
    val prizeScore: Double,
    val getTime: String,
    val qualityModuleName: String,
    val qualityCategoryName: String,
    val statusName: String,
)
