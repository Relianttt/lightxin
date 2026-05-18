package com.lightxin.feature.aiclass.domain

data class AiHomework(
    val id: String,
    val title: String,
    val endTime: String,
    val jobState: String,
    val score: String,
) {
    val statusText: String
        get() = when {
            score.isNotBlank() && score != "null" -> "${score}分"
            jobState == "2" -> "已截止"
            else -> "进行中"
        }
}

data class AiHomeworkDetail(
    val teaCwId: String,
    val title: String,
    val htmlContent: String,
    val startTime: String,
    val deadline: String,
    val teacherName: String,
    val cwDeadlineFormat: String,
)

data class AiStudentWork(
    val stuCwId: String,
    val studentName: String,
    val showContent: String,
    val cwStatus: Int,
    val correctStatus: Int,
    val score: String,
    val submitTime: String,
)
