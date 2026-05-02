package com.lightxin.feature.aiclass.data

import com.google.gson.JsonParser
import com.lightxin.feature.aiclass.domain.AiQuiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiClassQuizParserTest {

    @Test
    fun `extract nested quizzes with aliases and deduplicate`() {
        val json = JsonParser.parseString(
            """
            {
              "course": {
                "papers": [
                  {
                    "paperId": "paper-1",
                    "paperTitle": "第一章测验",
                    "isCommitted": true,
                    "paperStatus": 2,
                    "startTime": "08:00",
                    "createTime": "2026-04-01",
                    "week": "第1周",
                    "limitTime": "30"
                  },
                  {
                    "paperId": "paper-1",
                    "paperTitle": "第一章测验",
                    "isCommitted": false
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val result = AiClassQuizParser.extractQuizzesFromCoursePaperInfo(json)

        assertEquals(1, result.size)
        assertEquals("paper-1", result.single().id)
        assertEquals("第一章测验", result.single().title)
        assertTrue(result.single().isCommitted)
        assertEquals("2", result.single().status)
        assertEquals("08:00", result.single().publishTime)
        assertEquals("2026-04-01", result.single().publishDateTime)
        assertEquals("第1周", result.single().publishWeek)
        assertEquals(30, result.single().answerDurationMinutes)
    }

    @Test
    fun `merge quiz list fills missing primary fields from secondary`() {
        val primary = quiz(
            id = "paper-1",
            title = "测验",
            isCommitted = false,
        )
        val secondary = quiz(
            id = "paper-1",
            title = "测验",
            isCommitted = true,
            status = "已发布",
            publishTime = "09:00",
            publishDateTime = "2026-04-02",
            publishWeek = "第2周",
            answerDurationMinutes = 45,
        )

        val result = AiClassQuizParser.mergeQuizLists(
            primary = listOf(primary),
            secondary = listOf(secondary),
        )

        assertEquals(1, result.size)
        assertFalse(result.single().isCommitted)
        assertEquals("已发布", result.single().status)
        assertEquals("09:00", result.single().publishTime)
        assertEquals("2026-04-02", result.single().publishDateTime)
        assertEquals("第2周", result.single().publishWeek)
        assertEquals(45, result.single().answerDurationMinutes)
    }

    private fun quiz(
        id: String = "",
        title: String,
        isCommitted: Boolean = false,
        status: String = "",
        publishTime: String = "",
        publishDateTime: String = "",
        publishWeek: String = "",
        answerDurationMinutes: Int? = null,
    ): AiQuiz {
        return AiQuiz(
            id = id,
            title = title,
            isCommitted = isCommitted,
            status = status,
            publishTime = publishTime,
            publishDateTime = publishDateTime,
            publishWeek = publishWeek,
            answerDurationMinutes = answerDurationMinutes,
        )
    }
}
