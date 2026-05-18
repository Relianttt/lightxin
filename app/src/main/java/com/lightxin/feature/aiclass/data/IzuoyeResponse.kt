package com.lightxin.feature.aiclass.data

data class IzuoyeBaseResponse(
    val code: Int?,
    val message: String?,
)

data class IzuoyeTeaWorkDetailResponse(
    val code: Int?,
    val message: String?,
    val data: TeaWorkDetailData?,
) {
    data class TeaWorkDetailData(
        val teaCwId: String?,
        val cwTitle: String?,
        val teaCwContent: String?,
        val showContent: String?,
        val cwStartTimeFormat: String?,
        val cwDeadlineFormat: String?,
        val sealTimeFormat: String?,
        val cwType: Int?,
        val creatorUserName: String?,
        val viewPermit: Int?,
    )
}

data class IzuoyeStuWorkListResponse(
    val code: Int?,
    val message: String?,
    val data: StuWorkListData?,
) {
    data class StuWorkListData(
        val datalist: List<StuWorkItem>?,
    )

    data class StuWorkItem(
        val uid: String?,
        val stuCwId: String?,
        val studentId: String?,
        val studentName: String?,
        val showContent: String?,
        val cwStatus: Int?,
        val correctStatus: Int?,
        val score: String?,
        val submitTimeFormat: String?,
        val comprehensiveScore: String?,
        val cwTeaScore: String?,
    )
}

data class IzuoyeStuWorkDetailResponse(
    val code: Int?,
    val message: String?,
    val data: StuWorkDetailData?,
) {
    data class StuWorkDetailData(
        val stuCwId: String?,
        val showContent: String?,
        val cwStatus: Int?,
        val correctStatus: Int?,
        val score: String?,
        val submitTimeFormat: String?,
    )
}

data class IzuoyeStorageIdResponse(
    val code: Int?,
    val message: String?,
    val data: String?,
)
