package com.lightxin.feature.checkin.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 查寝把鉴权失败塞进 200：`{"code":"0","flag":false,"msg":"非法访问"}`。
 *
 * 现场实测 code 是 `"0"`（文档里记的是 `-100`），所以**不能靠 code 判断**：
 * 之前 `code == "0"` 被当成成功，rows/data 都是 null，界面渲染成“暂无签到任务”——
 * 用户以为没有查寝安排，实际是请求被拒。
 */
class CheckinRejectionTest {

    @Test
    fun `illegal access with flag false is a rejection`() {
        assertTrue(isCheckinRejection(false, "非法访问"))
        // 线上实际形态：code 是 "0"，不是文档里的 "-100"
        assertTrue(isCheckinRejection(false, "非法访问"))
    }

    @Test
    fun `flag false alone or message alone is not enough`() {
        assertFalse(isCheckinRejection(false, "获取任务详情失败"))
        assertFalse(isCheckinRejection(null, "非法访问"))
        assertFalse(isCheckinRejection(true, "非法访问"))
        assertFalse(isCheckinRejection(true, null))
    }

    @Test
    fun `technical detail keeps the server fragments`() {
        val detail = CheckinRejectedException(listOf("0", "非法访问")).technicalDetail
        assertTrue(detail.startsWith("CHECKIN_REJECTED"))
        assertTrue(detail.contains("非法访问"))
    }
}
