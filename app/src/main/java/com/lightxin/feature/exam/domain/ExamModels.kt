package com.lightxin.feature.exam.domain

data class ExamScore(
    val courseCode: String,
    val courseName: String,
    val department: String,
    val credit: String,
    val score: String,
    val examType: String,
    val gpa: String,
    val category: String,
    val teacher: String,
)

data class SchoolYear(
    val display: String,
    val value: String,
)

data class CurrentTerm(
    val schoolYear: String,
    val semester: String,
)
