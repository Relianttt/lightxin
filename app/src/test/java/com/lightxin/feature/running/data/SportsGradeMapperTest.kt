package com.lightxin.feature.running.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsGradeMapperTest {

    private fun json(s: String) = JsonParser.parseString(s).asJsonObject

    @Test
    fun `grade detection only by checkDsStudent result`() {
        assertTrue(SportsGradeMapper.isJuniorGrade("大一大二学生"))
        assertFalse(SportsGradeMapper.isJuniorGrade("大三大四学生"))
        assertFalse(SportsGradeMapper.isJuniorGrade(""))
    }

    @Test
    fun `parseExtraProgress reads server plan and complete mile`() {
        val (complete, plan) = SportsGradeMapper.parseExtraProgress(
            json("""{"data":{"extraInfo":{"planMile":"60.0","completeMile":"11.86"}}}"""),
        )
        assertEquals(11.86, complete, 0.001)
        assertEquals(60.0, plan, 0.001)
    }

    @Test
    fun `parseExtraProgress returns zero when null`() {
        assertEquals(0.0 to 0.0, SportsGradeMapper.parseExtraProgress(null))
        assertEquals(0.0 to 0.0, SportsGradeMapper.parseExtraProgress(json("""{"data":{"extraInfo":null}}""")))
    }

    @Test
    fun `parseClubSummary maps junior club fields`() {
        val summary = SportsGradeMapper.parseClubSummary(
            json(
                """{"data":{"clubInfo":{"schoolYear":"2025","semester":"2","clubName":"体育（4）",""" +
                    """"memberLevelName":"初级会员","teacherList":[{"name":"曹春顺"}]}}}""",
            ),
        )!!
        assertEquals("体育（4）", summary.courseName)
        assertEquals("2025-2", summary.term)
        assertEquals("曹春顺", summary.teacherName)
        assertEquals("初级会员", summary.memberLevel)
    }

    @Test
    fun `parseClubSummary returns null for senior (clubInfo null)`() {
        assertNull(SportsGradeMapper.parseClubSummary(json("""{"data":{"clubInfo":null}}""")))
        assertNull(SportsGradeMapper.parseClubSummary(null))
    }
}
