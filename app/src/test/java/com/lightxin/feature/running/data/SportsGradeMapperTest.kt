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
    fun `parseExtraId reads junior extra id`() {
        val extraId = SportsGradeMapper.parseExtraId(
            json("""{"data":{"extraInfo":{"extraId":"7af5218ea81643638b69532a245a4f6c"}}}"""),
        )

        assertEquals("7af5218ea81643638b69532a245a4f6c", extraId)
    }

    @Test
    fun `parseExtraDetail reads junior running metrics`() {
        val detail = SportsGradeMapper.parseExtraDetail(
            json(
                """{"data":{"extraDetail":{"memberId":"94f8c6ff9c13411abd6ecfef5a5f5728",""" +
                    """"mixOnceMile":"1","todayMile":"3.20","completeMile":"11.86",""" +
                    """"surplusMile":"48.14","maxMile":"5.41","maxMileDate":"2026-05-30"}}}""",
            ),
        )

        assertEquals("94f8c6ff9c13411abd6ecfef5a5f5728", detail.memberId)
        assertEquals(1.0, detail.mixOnceMileKm, 0.001)
        assertEquals(3.20, detail.todayMileKm, 0.001)
        assertEquals(11.86, detail.completedMileKm, 0.001)
        assertEquals(48.14, detail.surplusMileKm, 0.001)
        assertEquals(5.41, detail.maxMileKm, 0.001)
        assertEquals("2026-05-30", detail.maxMileDate)
    }

    @Test
    fun `parseExtraDetail returns defaults when null`() {
        val detail = SportsGradeMapper.parseExtraDetail(json("""{"data":{"extraDetail":null}}"""))

        assertEquals("", detail.memberId)
        assertEquals(1.0, detail.mixOnceMileKm, 0.001)
        assertEquals(0.0, detail.todayMileKm, 0.001)
        assertEquals(0.0, detail.completedMileKm, 0.001)
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
