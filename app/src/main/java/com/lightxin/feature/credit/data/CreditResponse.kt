package com.lightxin.feature.credit.data

data class CreditOverviewResponse(
    val flag: Boolean?,
    val result: String?,
    val data: CreditOverviewData?,
)

data class CreditOverviewData(
    val personnelCreditVo: PersonnelCreditVo?,
)

data class PersonnelCreditVo(
    val countCredit: String?,
    val pass: Boolean?,
    val list: List<CreditModuleItem>?,
)

data class CreditModuleItem(
    val name: String?,
    val type: String?,
    val credit: String?,
)

data class CreditRecordListResponse(
    val flag: Boolean?,
    val rows: List<CreditRecordItem>?,
)

data class CreditRecordItem(
    val name: String?,
    val statusName: String?,
    val score: Double?,
    val id: String?,
    val status: String?,
)

data class CreditRecordDetailResponse(
    val flag: Boolean?,
    val rows: List<CreditDetailItem>?,
)

data class CreditDetailItem(
    val name: String?,
    val highestLevelName: String?,
    val awardLevelName: String?,
    val awardPrizeName: String?,
    val prizeScore: Double?,
    val getTime: String?,
    val qualityModuleName: String?,
    val qualityCategoryName: String?,
    val status: String?,
    val statusName: String?,
)
