package com.lightxin.feature.aiclass.data

import com.lightxin.feature.aiclass.domain.AiCourse

internal object AiClassCourseMerger {
    fun mergeCourses(
        primary: List<AiCourse>,
        supplemental: List<AiCourse>,
        log: (String) -> Unit = {},
    ): List<AiCourse> {
        val merged = primary.map { it.normalize() }.toMutableList()

        supplemental.forEach { course ->
            val normalized = course.normalize()
            val exactMatchIndex = merged.indexOfFirst { it.matchesByIdentity(normalized) }
            if (exactMatchIndex >= 0) {
                log("Course merge: exact match \"${normalized.courseName}\" (typeName=${normalized.typeName}) with index $exactMatchIndex")
                merged[exactMatchIndex] = merged[exactMatchIndex].mergeWithSupplement(normalized)
                return@forEach
            }

            val fuzzyMatchIndexes = merged.withIndex()
                .filter { (_, existing) -> existing.matchesByName(normalized) }
                .map { it.index }

            if (fuzzyMatchIndexes.size == 1) {
                val index = fuzzyMatchIndexes.first()
                log("Course merge: fuzzy match \"${normalized.courseName}\" (typeName=${normalized.typeName}) with index $index")
                merged[index] = merged[index].mergeWithSupplement(normalized)
            } else {
                log("Course merge: new entry \"${normalized.courseName}\" (typeName=${normalized.typeName}, fuzzyMatches=${fuzzyMatchIndexes.size})")
                merged += normalized
            }
        }

        log("Course merge result: primary=${primary.size}, supplemental=${supplemental.size}, merged=${merged.size}")
        return merged
    }
}

internal fun buildCourseStableId(course: AiCourse): String {
    return listOf(
        course.id,
        course.classId,
        course.teachClassId,
        course.courseId,
        course.courseRecordId,
        course.termYear,
        course.term,
        course.courseName,
        course.teacherName,
        course.typeName,
    ).filter { it.isNotBlank() }
        .joinToString("|")
        .ifBlank { "course:${course.hashCode()}" }
}

internal fun parseTypeName(rawClassName: String): String {
    return when {
        rawClassName.contains("(实验)") || rawClassName.contains("实验") -> "实验"
        rawClassName.contains("(理论)") || rawClassName.contains("理论") -> "理论"
        else -> ""
    }
}

private fun AiCourse.normalize(): AiCourse {
    val normalized = copy(
        termYear = termYear.trim(),
        term = term.trim(),
        courseName = courseName.trim(),
        teacherName = teacherName.trim(),
        typeName = typeName.trim(),
    )
    return normalized.copy(stableId = buildCourseStableId(normalized))
}

private fun AiCourse.matchesByIdentity(other: AiCourse): Boolean {
    val thisIds = listOf(id, classId, teachClassId, courseId)
        .filter { it.isNotBlank() }
        .toSet()
    val otherIds = listOf(other.id, other.classId, other.teachClassId, other.courseId)
        .filter { it.isNotBlank() }
        .toSet()
    if (thisIds.isEmpty() || otherIds.isEmpty()) return false
    if (thisIds.intersect(otherIds).isEmpty()) return false
    if (typeName.isNotBlank() && other.typeName.isNotBlank() && typeName != other.typeName) {
        return false
    }
    return true
}

private fun AiCourse.matchesByName(other: AiCourse): Boolean {
    if (termYear != other.termYear || term != other.term) return false
    if (courseName.normalizedCourseName() != other.courseName.normalizedCourseName()) return false
    if (teacherName.isNotBlank() && other.teacherName.isNotBlank() && teacherName != other.teacherName) {
        return false
    }
    if (typeName.isNotBlank() && other.typeName.isNotBlank() && typeName != other.typeName) {
        return false
    }
    return true
}

private fun AiCourse.mergeWithSupplement(supplement: AiCourse): AiCourse {
    val merged = copy(
        id = id.ifBlank { supplement.id },
        code = code.ifBlank { supplement.code },
        classId = classId.ifBlank { supplement.classId },
        courseId = courseId.ifBlank { supplement.courseId },
        courseRecordId = courseRecordId.ifBlank { supplement.courseRecordId },
        courseName = courseName.ifBlank { supplement.courseName },
        teacherName = teacherName.ifBlank { supplement.teacherName },
        studentNum = if (studentNum > 0) studentNum else supplement.studentNum,
        teachClassId = teachClassId.ifBlank { supplement.teachClassId },
        cover = cover.ifBlank { supplement.cover },
        termYear = termYear.ifBlank { supplement.termYear },
        term = term.ifBlank { supplement.term },
        typeName = typeName.ifBlank { supplement.typeName },
    )
    return merged.copy(stableId = buildCourseStableId(merged))
}

private fun String.normalizedCourseName(): String {
    return replace(Regex("\\s+"), "")
        .replace(Regex("[（(]实验[）)]|[（(]理论[）)]"), "")
        .trim()
}
