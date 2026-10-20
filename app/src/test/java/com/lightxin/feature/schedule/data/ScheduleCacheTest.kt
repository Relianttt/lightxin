package com.lightxin.feature.schedule.data

import android.app.Application
import androidx.room.Room
import com.lightxin.core.auth.AccountSession
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.database.LightXinDatabase
import com.lightxin.feature.schedule.data.local.*
import com.lightxin.feature.home.domain.HomeBootstrap
import com.lightxin.feature.home.domain.TodayCourseLoader
import com.lightxin.feature.checkin.data.*
import okhttp3.MultipartBody
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScheduleCacheTest {
    private lateinit var db: LightXinDatabase
    private lateinit var session: AccountSession
    private lateinit var tokens: TokenManager
    private val context get() = RuntimeEnvironment.getApplication()
    private var response = TimeTableResponse(emptyList(), true)
    private val fetchedWeeks = mutableListOf<Int>()
    private var failWeek: Int? = null
    private val api = object : ScheduleApi {
        override suspend fun getWeekList(dummy: String) = WeekListResponse(
            WeekListData("1", "2025", "2"), (0..3).map { WeekRow(it.toString(), "") }, true,
        )
        override suspend fun getTimeTable(userCode: String, schoolYear: String, schoolTerm: String, week: String): TimeTableResponse {
            fetchedWeeks += week.toInt()
            check(week.toInt() != failWeek) { "offline" }
            return response
        }
    }

    @Before fun setup() = runBlocking {
        session = AccountSession()
        tokens = TokenManager(context, session)
        tokens.saveLoginData("token", "refresh", "A", "", "1", "")
        db = Room.inMemoryDatabaseBuilder(context, LightXinDatabase::class.java).build()
    }

    @After fun cleanup() { db.close(); session.invalidate() }

    @Test fun emptyWeeksAreStoredAndRejectedResponsesCannotOverwriteThem() = runBlocking {
        val repo = ScheduleRepository(api, tokens, session, db)
        val account = repo.account()
        val info = repo.refreshSemester(account).getOrThrow().info
        val saved = repo.refreshWeek(account, info, 1).getOrThrow()
        assertTrue(repo.cachedWeek(account, info, 1)!!.data.courses.isEmpty())
        for (invalid in listOf(TimeTableResponse(null, true), TimeTableResponse(emptyList(), false), TimeTableResponse(emptyList(), null))) {
            response = invalid
            assertTrue(repo.refreshWeek(account, info, 1).isFailure)
            assertEquals(saved, repo.cachedWeek(account, info, 1))
        }
        assertNull(repo.cachedWeek(account, info, 0))
        assertEquals(setOf(1), repo.cachedWeekNumbers(account, info))
    }

    @Test fun usersAndSemestersAreIsolatedAndLatestSwitchIsAtomic() = runBlocking {
        val dao = db.scheduleCacheDao()
        fun semester(user: String, year: String) = SemesterCacheEntity(user, year, "2", "[0,1]", 1, "2026-10-19", 1)
        dao.saveSemester(semester("A", "2025"))
        dao.saveSemester(semester("B", "2024"))
        dao.saveSemester(semester("A", "2026"))
        assertEquals("2026", dao.semester("A")!!.schoolYear)
        assertEquals("2024", dao.semester("B")!!.schoolYear)
        assertNull(dao.semester("C"))
        dao.saveWeek(ScheduleWeekCacheEntity("A", "2026", "2", 1, "{}", 1))
        assertNull(dao.week("B", "2026", "2", 1))
        assertNull(dao.week("A", "2025", "2", 1))
    }

    @Test fun databaseCloseAndReopenKeepsSemesterAndValidEmptyWeek() = runBlocking {
        val name = "schedule-reopen-test.db"
        context.deleteDatabase(name)
        var disk = Room.databaseBuilder(context, LightXinDatabase::class.java, name).build()
        val week = ScheduleWeekCacheEntity("A", "2025", "2", 1, """{"courses":[],"weekDates":{}}""", 123)
        disk.scheduleCacheDao().saveWeek(week)
        disk.close()
        disk = Room.databaseBuilder(context, LightXinDatabase::class.java, name).build()
        try {
            assertEquals(week, disk.scheduleCacheDao().week("A", "2025", "2", 1))
            assertTrue(ScheduleCacheMapper.week(week)!!.data.courses.isEmpty())
            assertNull(ScheduleCacheMapper.week(week.copy(payloadVersion = 2)))
            assertNull(ScheduleCacheMapper.week(week.copy(payloadJson = "{}")))
        } finally { disk.close(); context.deleteDatabase(name) }
    }

    @Test fun tokenRenewalKeepsGenerationButLogoutRejectsLateRenewal() = runBlocking {
        val lease = session.capture()
        assertTrue(tokens.updateTokens(lease, "renewed", "refresh"))
        assertSame(lease, session.capture())
        tokens.clear()
        assertFalse(tokens.updateTokens(lease, "late", "refresh"))
        assertNull(tokens.getAccessToken())
    }

    @Test fun syncPrioritizesCurrentAndNextThenResumesOnlyMissingWeeks() = runBlocking {
        val repo = ScheduleRepository(api, tokens, session, db)
        val sync = ScheduleSyncCoordinator(repo, session)
        val account = repo.account()
        val info = repo.refreshSemester(account).getOrThrow().info
        failWeek = 0
        assertTrue(sync.sync(account, info).isFailure)
        assertEquals(listOf(1, 2, 0), fetchedWeeks)
        assertEquals(setOf(1, 2), repo.cachedWeekNumbers(account, info))
        failWeek = null
        fetchedWeeks.clear()
        assertTrue(sync.sync(account, info).isSuccess)
        assertEquals(listOf(0, 3), fetchedWeeks)
        assertEquals(setOf(0, 1, 2, 3), repo.cachedWeekNumbers(account, info))
    }

    @Test fun bootstrapPublishesCoursesBeforeSlowCheckinWithoutChangingItsRequest() = runBlocking {
        val checkin = CompletableDeferred<SignInPageResponse>()
        val checkinApi = object : CheckinApi {
            override suspend fun pageStudentSignIn(body: Map<String, Any>): SignInPageResponse {
                assertEquals(mapOf("pageNum" to 1, "pageSize" to 5, "type" to "1", "taskMajorType" to "3"), body)
                return checkin.await()
            }
            override suspend fun getTaskInfoByDateId(body: Map<String, String>): TaskInfoResponse = error("unused")
            override suspend fun signIn(body: Map<String, String>): SignInSubmitResponse = error("unused")
        }
        val upload = object : FileUploadApi {
            override suspend fun uploadFile(file: MultipartBody.Part): FileUploadResponse = error("unused")
        }
        val repo = ScheduleRepository(api, tokens, session, db)
        val bootstrap = HomeBootstrap(tokens, TodayCourseLoader(repo, ScheduleSyncCoordinator(repo, session), session),
            CheckinRepository(checkinApi, upload), session)
        bootstrap.load()
        val before = withTimeout(10_000) { bootstrap.snapshot.first { it?.scheduleLoaded == true } }!!
        assertTrue(before.todayKnown)
        assertFalse(before.checkinLoaded)
        assertTrue(bootstrap.ready.value)
        checkin.complete(SignInPageResponse("0", null, true, null, emptyList(), 0))
        val after = withTimeout(10_000) { bootstrap.snapshot.first { it?.checkinLoaded == true } }!!
        assertEquals(before.todayCourses, after.todayCourses)
        assertTrue(after.todayKnown)
    }
}
