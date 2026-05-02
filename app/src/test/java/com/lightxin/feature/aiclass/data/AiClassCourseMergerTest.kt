package com.lightxin.feature.aiclass.data

import com.lightxin.feature.aiclass.domain.AiCourse
import org.junit.Assert.assertEquals
import org.junit.Test

class AiClassCourseMergerTest {

    @Test
    fun `merge exact identity and fill only missing fields`() {
        val primary = course(
            id = "course-1",
            classId = "class-1",
            courseName = " Kotlin ",
            teacherName = " Teacher ",
            studentNum = 42,
            cover = "primary-cover",
            typeName = " 理论 ",
        )
        val supplemental = course(
            id = "course-1",
            classId = "class-1",
            courseRecordId = "record-1",
            courseName = "Kotlin",
            teacherName = "Teacher",
            studentNum = 99,
            cover = "supplement-cover",
            typeName = "理论",
        )

        val result = AiClassCourseMerger.mergeCourses(
            primary = listOf(primary),
            supplemental = listOf(supplemental),
        )

        assertEquals(1, result.size)
        assertEquals("record-1", result.single().courseRecordId)
        assertEquals(42, result.single().studentNum)
        assertEquals("primary-cover", result.single().cover)
        assertEquals("Kotlin", result.single().courseName)
        assertEquals("Teacher", result.single().teacherName)
        assertEquals("理论", result.single().typeName)
    }

    @Test
    fun `merge fuzzy name match when ids are absent`() {
        val primary = course(
            courseName = "大学英语（理论）",
            teacherName = "张三",
            termYear = "2025",
            term = "1",
            typeName = "理论",
        )
        val supplemental = course(
            teachClassId = "tc-1",
            courseName = " 大学 英语 ",
            teacherName = "张三",
            termYear = "2025",
            term = "1",
            typeName = "理论",
        )

        val result = AiClassCourseMerger.mergeCourses(
            primary = listOf(primary),
            supplemental = listOf(supplemental),
        )

        assertEquals(1, result.size)
        assertEquals("tc-1", result.single().teachClassId)
    }

    @Test
    fun `keep theory and experiment courses separated`() {
        val primary = course(
            courseId = "same-course",
            courseName = "物理",
            typeName = "理论",
        )
        val supplemental = course(
            courseId = "same-course",
            courseName = "物理",
            typeName = "实验",
        )

        val result = AiClassCourseMerger.mergeCourses(
            primary = listOf(primary),
            supplemental = listOf(supplemental),
        )

        assertEquals(2, result.size)
        assertEquals(listOf("理论", "实验"), result.map { it.typeName })
    }

    private fun course(
        stableId: String = "",
        id: String = "",
        code: String = "",
        classId: String = "",
        courseId: String = "",
        courseRecordId: String = "",
        courseName: String = "课程",
        teacherName: String = "",
        studentNum: Int = 0,
        teachClassId: String = "",
        cover: String = "",
        termYear: String = "2025",
        term: String = "1",
        typeName: String = "",
    ): AiCourse {
        return AiCourse(
            stableId = stableId,
            id = id,
            code = code,
            classId = classId,
            courseId = courseId,
            courseRecordId = courseRecordId,
            courseName = courseName,
            teacherName = teacherName,
            studentNum = studentNum,
            teachClassId = teachClassId,
            cover = cover,
            termYear = termYear,
            term = term,
            typeName = typeName,
        )
    }
}
