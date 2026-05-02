package com.lightxin.feature.aiclass.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lightxin.feature.aiclass.domain.AiQuiz

internal object AiClassQuizParser {
    fun extractQuizzesFromCoursePaperInfo(element: JsonElement?): List<AiQuiz> {
        val results = mutableListOf<AiQuiz>()

        fun walk(node: JsonElement?) {
            when {
                node == null || node.isJsonNull -> Unit
                node.isJsonArray -> node.asJsonArray.forEach(::walk)
                node.isJsonObject -> {
                    val obj = node.asJsonObject
                    obj.toAiQuizOrNull()?.let(results::add)
                    obj.entrySet().forEach { (_, child) -> walk(child) }
                }
            }
        }

        walk(element)
        return results
            .distinctBy { it.id.ifBlank { "${it.title}|${it.publishDateTime}|${it.publishTime}" } }
    }

    fun mergeQuizLists(
        primary: List<AiQuiz>,
        secondary: List<AiQuiz>,
    ): List<AiQuiz> {
        val merged = LinkedHashMap<String, AiQuiz>()

        primary.forEach { quiz ->
            merged[quiz.stableKey()] = quiz
        }
        secondary.forEach { quiz ->
            val key = quiz.stableKey()
            merged[key] = merged[key]?.fillMissingFrom(quiz) ?: quiz
        }

        return merged.values.toList()
    }
}

private fun AiQuiz.fillMissingFrom(fallback: AiQuiz): AiQuiz {
    return copy(
        id = id.ifBlank { fallback.id },
        title = title.ifBlank { fallback.title },
        status = status.ifBlank { fallback.status },
        publishTime = publishTime.ifBlank { fallback.publishTime },
        publishDateTime = publishDateTime.ifBlank { fallback.publishDateTime },
        publishWeek = publishWeek.ifBlank { fallback.publishWeek },
        answerDurationMinutes = answerDurationMinutes ?: fallback.answerDurationMinutes,
    )
}

private fun AiQuiz.stableKey(): String {
    return id.ifBlank { "${title}|${publishDateTime}|${publishTime}|${publishWeek}" }
}

private fun JsonObject.toAiQuizOrNull(): AiQuiz? {
    val id = stringValue("id", "paperId", "refPaperId")
    val title = stringValue("title", "paperTitle", "name", "paperName")
    if (title.isBlank()) return null

    return AiQuiz(
        id = id,
        title = title,
        isCommitted = anyValue("iscommited", "isCommitted", "committed", "submitStatus").asBoolean(),
        status = anyValue("status", "paperStatus", "submitStatus").asDisplayString(),
        publishTime = stringValue("publishTime", "startTime"),
        publishDateTime = stringValue("publishDateTime", "createTime", "publishDate"),
        publishWeek = stringValue("publishWeek", "week"),
        answerDurationMinutes = anyValue("answerDuration", "duration", "limitTime").asIntOrNull(),
    )
}

internal fun JsonObject.stringValue(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key ->
        get(key)?.takeIf { !it.isJsonNull }?.let { element ->
            if (element.isJsonPrimitive) element.asJsonPrimitive.asString else null
        }?.takeIf { it.isNotBlank() }
    }.orEmpty()
}

private fun JsonObject.anyValue(vararg keys: String): Any? {
    return keys.firstNotNullOfOrNull { key ->
        get(key)?.takeIf { !it.isJsonNull }?.toPrimitiveValue()
    }
}

internal fun Any?.asBoolean(): Boolean {
    return when (this) {
        is Boolean -> this
        is Number -> toInt() != 0
        is String -> equals("true", ignoreCase = true) || this == "1"
        else -> false
    }
}

internal fun Any?.asIntOrNull(): Int? {
    return when (this) {
        is Number -> toInt()
        is String -> toIntOrNull()
        else -> null
    }
}

internal fun Any?.asDisplayString(): String {
    return when (this) {
        null -> ""
        is String -> this
        is Number -> toInt().toString()
        is Boolean -> if (this) "1" else "0"
        else -> toString()
    }
}

private fun JsonElement.toPrimitiveValue(): Any? {
    if (!isJsonPrimitive) return null
    val primitive = asJsonPrimitive
    return when {
        primitive.isBoolean -> primitive.asBoolean
        primitive.isNumber -> primitive.asNumber
        primitive.isString -> primitive.asString
        else -> null
    }
}
