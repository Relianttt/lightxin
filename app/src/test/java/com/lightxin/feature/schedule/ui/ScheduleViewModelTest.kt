package com.lightxin.feature.schedule.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.lightxin.core.auth.AccountSession
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.database.LightXinDatabase
import com.lightxin.feature.schedule.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScheduleViewModelTest {
    private val main = newSingleThreadContext("schedule-main")
    private val store = ViewModelStore()
    private lateinit var db: LightXinDatabase
    private lateinit var session: AccountSession
    private lateinit var repo: ScheduleRepository
    private var offline = false
    private var fetch: suspend (String) -> TimeTableResponse = { TimeTableResponse(emptyList(), true) }
    private val api = object : ScheduleApi {
        override suspend fun getWeekList(dummy: String): WeekListResponse {
            check(!offline) { "offline" }
            return WeekListResponse(WeekListData("7", "2025", "2"), listOf(WeekRow("7", ""), WeekRow("8", "")), true)
        }
        override suspend fun getTimeTable(userCode: String, schoolYear: String, schoolTerm: String, week: String): TimeTableResponse {
            check(!offline) { "offline" }
            return fetch(week)
        }
    }

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(main)
        val context = RuntimeEnvironment.getApplication()
        session = AccountSession()
        val tokens = TokenManager(context, session)
        tokens.saveLoginData("t", "r", "A", "", "1", "")
        db = Room.inMemoryDatabaseBuilder(context, LightXinDatabase::class.java).build()
        repo = ScheduleRepository(api, tokens, session, db)
    }

    @After fun cleanup() = runBlocking {
        withContext(main) { store.clear() }
        session.invalidate()
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    private suspend fun viewModel() = withContext(main) {
        ScheduleViewModel(repo, session, ScheduleSyncCoordinator(repo, session)).also { store.put("test", it) }
    }

    @Test fun cachedEmptyWeekRemainsVisibleWhenOfflineRefreshFails() = runBlocking {
        val account = repo.account()
        val info = repo.refreshSemester(account).getOrThrow().info
        repo.refreshWeek(account, info, 7).getOrThrow()
        offline = true
        val vm = viewModel()
        val state = withTimeout(10_000) { vm.uiState.first { it.hasLoadedWeek && it.refreshError != null } }
        assertTrue(state.courses.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNotNull(state.lastSyncedAt)
    }

    @Test fun missingCacheShowsErrorInsteadOfPretendingEmptyWeek() = runBlocking {
        offline = true
        val vm = viewModel()
        val state = withTimeout(10_000) { vm.uiState.first { !it.isLoading } }
        assertFalse(state.hasLoadedWeek)
        assertNotNull(state.error)
    }

    @Test fun lateWeekSevenCanBeCachedButCannotReplaceSelectedWeekEight() = runBlocking {
        val seven = CompletableDeferred<TimeTableResponse>()
        val started = CompletableDeferred<Unit>()
        fetch = { week ->
            if (week == "7") { started.complete(Unit); seven.await() }
            else TimeTableResponse(listOf(DaySchedule("1", listOf(CourseVo("eight", "1", "2", "", "", null)), "10/19")), true)
        }
        val vm = viewModel()
        withTimeout(10_000) { started.await() }
        withContext(main) { vm.onWeekSelected(8) }
        withTimeout(10_000) { vm.uiState.first { it.selectedWeek == 8 && it.hasLoadedWeek } }
        seven.complete(TimeTableResponse(emptyList(), true))
        val account = repo.account()
        val info = repo.cachedSemester(account)!!.info
        withTimeout(10_000) { while (repo.cachedWeek(account, info, 7) == null) delay(10) }
        assertEquals(8, vm.uiState.value.selectedWeek)
        assertEquals("eight", vm.uiState.value.courses.single().name)
    }
}
