package com.lightxin.feature.running.data

import com.google.gson.JsonParser
import com.lightxin.feature.running.exercise.domain.ClubTaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ClubDetailMapperTest {

    private fun json(s: String) = JsonParser.parseString(s).asJsonObject

    private val sample = """
      {"flag":true,"rows":[
        {"autoId":"A6","memberId":"M6","name":"第6次自主练习任务","requiredTime":"45","completeTime":"113",
         "checkState":"1","startDate":"2026/05/25 08:05","endDate":"2026/05/31 20:05",
         "checkList":[
           {"checkState":"1","startDate":"2026-05-29 13:46","endDate":"15:39","venueName":"篮球场A","time":"1:53:19"},
           {"checkState":"2","startDate":"2026-05-28 19:02","endDate":"20:36","venueName":"篮球场A","time":"无数据"}
         ]},
        {"autoId":"A5","memberId":"M5","name":"第5次自主练习任务","requiredTime":"45","completeTime":"166",
         "checkState":"2","startDate":"2026/05/11 08:05","endDate":"2026/05/17 20:05","checkList":[]}
      ]}
    """.trimIndent()

    @Test
    fun `parses task list with records`() {
        val now = LocalDateTime.of(2026, 5, 30, 12, 0)
        val tasks = ClubDetailMapper.parseTasks(json(sample), now)

        assertEquals(2, tasks.size)
        val t6 = tasks[0]
        assertEquals("A6", t6.autoId)
        assertEquals("M6", t6.memberId)
        assertEquals(113, t6.completedMinutes)
        assertEquals(2, t6.checkRecords.size)
        assertTrue(t6.checkRecords[0].isNormal)   // checkState=1 正常
        assertFalse(t6.checkRecords[1].isNormal)  // checkState=2 异常
    }

    @Test
    fun `active task within window can check and finished cannot`() {
        val now = LocalDateTime.of(2026, 5, 30, 12, 0)
        val tasks = ClubDetailMapper.parseTasks(json(sample), now)

        assertEquals(ClubTaskState.ACTIVE, tasks[0].state)
        assertTrue(tasks[0].canCheck)             // checkState=1 且 5/30 在 [5/25,5/31] 窗内
        assertEquals(ClubTaskState.FINISHED, tasks[1].state)
        assertFalse(tasks[1].canCheck)
    }

    @Test
    fun `active task outside window cannot check`() {
        val afterWindow = LocalDateTime.of(2026, 6, 1, 12, 0)
        val tasks = ClubDetailMapper.parseTasks(json(sample), afterWindow)

        assertEquals(ClubTaskState.ACTIVE, tasks[0].state) // 状态仍按 checkState
        assertFalse(tasks[0].canCheck)                     // 但超出时间窗不可打卡
    }

    @Test
    fun `null or empty rows yields empty list`() {
        assertTrue(ClubDetailMapper.parseTasks(null).isEmpty())
        assertTrue(ClubDetailMapper.parseTasks(json("""{"rows":null}""")).isEmpty())
    }

    @Test
    fun `tasks sorted by startDate descending and unparseable last in original order`() {
        val unordered = """
          {"rows":[
            {"autoId":"OLD","checkState":"2","startDate":"2026/03/16 08:03","endDate":"2026/03/22 20:03","checkList":[]},
            {"autoId":"BAD1","checkState":"2","startDate":"","endDate":"","checkList":[]},
            {"autoId":"NEW","checkState":"2","startDate":"2026/05/25 08:05","endDate":"2026/05/31 20:05","checkList":[]},
            {"autoId":"BAD2","checkState":"2","startDate":"not-a-date","endDate":"","checkList":[]}
          ]}
        """.trimIndent()
        val ids = ClubDetailMapper.parseTasks(json(unordered), LocalDateTime.of(2026, 6, 1, 0, 0))
            .map { it.autoId }

        assertEquals(listOf("NEW", "OLD", "BAD1", "BAD2"), ids)
    }
}
