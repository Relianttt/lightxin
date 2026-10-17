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
    fun `parseExtraDetail reads server-side valid speed window`() {
        // 取自 2026-10-13 真机 extraDetailInfo.do 响应
        val detail = SportsGradeMapper.parseExtraDetail(
            json(
                """{"data":{"extraDetail":{"memberId":"f6b7604dbb5043a083dfb5189e762c90",""" +
                    """"mixOnceMile":"1","maxDayMile":"3","todayMile":"3.06",""" +
                    """"minSpeed":"2","maxSpeed":"15"}}}""",
            ),
        )

        assertEquals(2.0, detail.minSpeedKmh!!, 0.001)
        assertEquals(15.0, detail.maxSpeedKmh!!, 0.001)
    }

    @Test
    fun `parseExtraDetail leaves speed window null when server omits it`() {
        val detail = SportsGradeMapper.parseExtraDetail(
            json("""{"data":{"extraDetail":{"memberId":"m","mixOnceMile":"1"}}}"""),
        )

        assertNull(detail.minSpeedKmh)
        assertNull(detail.maxSpeedKmh)
    }

    @Test
    fun `parseCheckRecord reads the verdict from a real getExtraCheck response`() {
        // 2026-10-14 实测：runningRoute 由服务端加密存储，回读拿到的是密文
        val verdict = requireNotNull(
            SportsGradeMapper.parseCheckRecord(
                json(
                    """{"flag":true,"result":"success","rows":null,"total":null,"data":{"applyInfo":null,""" +
                        """"extraInfo":{"checkId":"f5fb74df0422448ea7639c5432339125","mile":"1.02",""" +
                        """"time":"00:06:26","speed":"9.51","state":"0","remark":"运动轨迹异常，无效",""" +
                        """"runningRoute":"CnlTehTkkCjHiKnQHTXpm3+pNRyGJDtohT6DZHJVjv6r4f8="}}}""",
                ),
            ),
        )

        assertEquals("f5fb74df0422448ea7639c5432339125", verdict.checkId)
        assertFalse(verdict.isValid)
        assertEquals("无效：运动轨迹异常，无效", verdict.describe())
        assertTrue(verdict.hasStoredRoute)
    }

    @Test
    fun `parseCheckRecord treats a route array as stored without throwing`() {
        val verdict = requireNotNull(
            SportsGradeMapper.parseCheckRecord(
                json("""{"data":{"extraInfo":{"state":"1","runningRoute":[{"latitude":31.1,"longitude":118.6}]}}}"""),
            ),
        )

        assertTrue(verdict.isValid)
        assertTrue(verdict.hasStoredRoute)
    }

    @Test
    fun `parseCheckRecord reports no stored route when the field is blank, empty or absent`() {
        listOf(
            """{"data":{"extraInfo":{"state":"0","runningRoute":""}}}""",
            """{"data":{"extraInfo":{"state":"0","runningRoute":[]}}}""",
            """{"data":{"extraInfo":{"state":"0","runningRoute":null}}}""",
            """{"data":{"extraInfo":{"state":"0"}}}""",
        ).forEach { raw ->
            val verdict = requireNotNull(SportsGradeMapper.parseCheckRecord(json(raw)))
            assertFalse(verdict.hasStoredRoute)
        }
    }

    @Test
    fun `parseCheckRecord returns null without extraInfo`() {
        assertNull(SportsGradeMapper.parseCheckRecord(json("""{"data":{}}""")))
        assertNull(SportsGradeMapper.parseCheckRecord(null))
    }

    @Test
    fun `parseCheckRecord also reads extraInfo at the response top level`() {
        val verdict = requireNotNull(
            SportsGradeMapper.parseCheckRecord(
                json("""{"extraInfo":{"state":"1","runningRoute":"CnlTehTkkCjHiKnQ=="}}"""),
            ),
        )

        assertTrue(verdict.isValid)
        assertTrue(verdict.hasStoredRoute)
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
