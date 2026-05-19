package com.lightxin.feature.aiclass.data

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement

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
        val id: String?,
        val code: String?,
        val classId: String?,
        val courseId: String?,
        val courseRecordId: String?,
        val courseName: String?,
        val teacherName: String?,
        val studentNum: Int?,
        val teachClassId: String?,
        val className: String?,
        val classNames: String?,
        val classNameStr: String?,
        val cover: String?,
        val termYear: String?,
        val term: String?,
        val typeName: String?,
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
    val data: List<WorkingRecordData>?,
) {
    data class WorkingRecordData(
        val courseRecordId: String?,
        val courseName: String?,
        @SerializedName("courseItemName") val courseItemName: String?,
        val courseId: String?,
        val teachClassId: String?,
    )
}

/** 测验列表响应 */
data class AiClassQuizListResponse(
    val status: String?,
    val message: String?,
    val data: JsonElement?,
) {
    // data 可能是 HAR 中的 [{ paperDetail: [...] }] 分组结构，也可能是扁平数组。
    // 保留 JsonElement，由 AiClassQuizParser 递归提取真实测验条目。
}

/** 课程详情页里的试卷信息，结构暂未完全稳定，先保留原始 JSON 再做前端提取 */
data class AiClassCoursePaperInfoResponse(
    val status: String?,
    val message: String?,
    val data: JsonElement?,
)

/** AI课堂课表响应，先保留原始 JSON，由 Repository 递归提取课程条目 */
data class AiClassTimetableResponse(
    val status: String?,
    val message: String?,
    val data: JsonElement?,
)

/** 作业列表响应 */
data class AiClassHomeworkListResponse(
    val status: String?,
    val message: String?,
    val data: HomeworkListData?,
) {
    data class HomeworkListData(
        val pageConfig: PageConfig?,
        val dataList: List<HomeworkDayGroup>?,
    )

    data class PageConfig(
        val totalPage: Int?,
        val totalCount: Int?,
    )

    data class HomeworkDayGroup(
        val dayTime: String?,
        val fileList: List<HomeworkItem>?,
    )

    data class HomeworkItem(
        val id: String?,
        val jobTitle: String?,
        val typeName: String?,
        val jobState: String?,
        val endState: String?,
        val allowOvertime: String?,
        val startTime: String?,
        val endTime: String?,
        val score: String?,
    )
}

/** 作业详情URL响应 */
data class AiClassHomeworkUrlResponse(
    val status: Int?,
    val message: String?,
    val data: String?,
)
