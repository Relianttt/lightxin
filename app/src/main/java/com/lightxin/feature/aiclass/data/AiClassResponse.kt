package com.lightxin.feature.aiclass.data

import com.google.gson.annotations.SerializedName

/** 通用响应 */
data class AiClassBaseResponse(
    val status: String?,
    val message: String?,
)

/** 学期列表响应 */
data class AiClassTermResponse(
    val status: String?,
    val message: String?,
    val data: TermListData?,
) {
    data class TermListData(
        val dataList: List<TermItem>?,
    )

    data class TermItem(
        val year: String?,
        val num: String?,
        val name: String?,
        val id: String?,
        val selected: String?,
    )
}

/** 课程列表响应 */
data class AiClassCourseResponse(
    val status: String?,
    val message: String?,
    val data: CourseListData?,
) {
    data class CourseListData(
        val dataList: List<CourseItem>?,
    )

    data class CourseItem(
        val classId: String?,
        val courseId: String?,
        val courseRecordId: String?,
        val courseName: String?,
        val teacherName: String?,
        val studentNum: Int?,
        val teachClassId: String?,
        val cover: String?,
    )
}

/** 签到信息响应 */
data class AiClassSignInInfoResponse(
    val status: String?,
    val message: String?,
    val data: SignInData?,
) {
    data class SignInData(
        val signId: String?,
        val teacherName: String?,
        val signStatus: String?,
    )
}

/** 正在上课记录响应 */
data class AiClassWorkingRecordResponse(
    val status: String?,
    val message: String?,
    val data: WorkingRecordData?,
) {
    data class WorkingRecordData(
        val courseRecordId: String?,
        val courseName: String?,
        @SerializedName("courseItemName") val courseItemName: String?,
        val teachClassId: String?,
    )
}
