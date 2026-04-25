package com.lightxin.feature.aiclass.domain

data class AiCourse(
    val stableId: String,
    val id: String,
    val code: String,
    val classId: String,
    val courseId: String,
    val courseRecordId: String,
    val courseName: String,
    val teacherName: String,
    val studentNum: Int,
    val teachClassId: String,
    val cover: String,
    val termYear: String,
    val term: String,
    val typeName: String,
)

data class AiSignInInfo(
    val signId: String,
    val teacherName: String,
    val hasActiveSign: Boolean,
)

data class AiWorkingRecord(
    val courseRecordId: String,
    val courseName: String,
    val courseItemName: String,
    val teachClassId: String,
)

data class AiQuiz(
    val id: String,
    val title: String,
    val isCommitted: Boolean,
    val status: String,
    val publishTime: String,
    val publishDateTime: String,
    val publishWeek: String,
    val answerDurationMinutes: Int?,
)

fun AiCourse.displayName(): String {
    if (courseName.isBlank()) return courseName
    return when {
        typeName.contains("实验") && !courseName.contains("实验") -> "${courseName}（实验）"
        typeName.contains("理论") && !courseName.contains("理论") -> "${courseName}（理论）"
        else -> courseName
    }
}

fun AiCourse.studentCountText(): String {
    return studentNum.takeIf { it > 0 }?.let { "${it}人" } ?: "人数未知"
}
