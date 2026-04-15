package com.lightxin.feature.aiclass.domain

data class AiCourse(
    val classId: String,
    val courseId: String,
    val courseRecordId: String,
    val courseName: String,
    val teacherName: String,
    val studentNum: Int,
    val teachClassId: String,
    val cover: String,
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
