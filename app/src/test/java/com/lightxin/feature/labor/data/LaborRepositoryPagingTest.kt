package com.lightxin.feature.labor.data

import android.app.Application
import com.lightxin.core.auth.AccountSession
import com.lightxin.core.auth.TokenManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LaborRepositoryPagingTest {
    @Test fun `repository keeps total and uses captured protocol page size`() = runBlocking {
        val tokens = TokenManager(RuntimeEnvironment.getApplication(), AccountSession())
        tokens.saveLoginData("test-token", "refresh", "test-user", "", "1", "")
        var total: Int? = 28
        val api = object : LaborApi {
            override suspend fun queryHoursTotal(userCode: String, xh: String, accessToken: String): HoursTotalResponse =
                error("Unused")

            override suspend fun queryActivities(
                userCode: String, xh: String, accessToken: String, pageNo: String, pageSize: String,
            ): ActivityPageResponse {
                assertEquals("test-user", userCode)
                assertEquals(userCode, xh)
                assertEquals("test-token", accessToken)
                assertEquals("2", pageNo)
                assertEquals("100", pageSize)
                return ActivityPageResponse(
                    listOf(ActivityRow("same-id", "Category", "Activity", "1.5", "2026-03-01 09:00:00", "6")),
                    total, true, null,
                )
            }

            override suspend fun queryActivityDetail(
                userCode: String, xh: String, accessToken: String, id: String, type: String,
            ): ActivityDetailResponse = error("Unused")
        }
        val repository = LaborRepository(api, tokens)
        val page = repository.getActivities(2).getOrThrow()
        assertEquals(28, page.total)
        assertEquals("6:same-id", page.records.single().key)
        assertEquals(1.5, page.records.single().serviceTimes, 0.0)
        total = null
        assertNull(repository.getActivities(2).getOrThrow().total)
    }
}
