package com.lightxin.feature.aiclass.data

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.lightxin.core.auth.AccountSession
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.network.*
import com.lightxin.feature.aiclass.ui.AiClassViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AiClassCacheTest {
    private val main = newSingleThreadContext("aiclass-main")
    private val viewModels = ViewModelStore()
    private lateinit var session: AccountSession
    private lateinit var tokens: TokenManager
    private lateinit var memory: AiClassMemoryStore
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(main)
        session = AccountSession()
        tokens = TokenManager(context, session)
        tokens.saveLoginData("test", "refresh", "A", "", "1", "")
        memory = AiClassMemoryStore(session, tokens)
    }

    @After fun cleanup() = runBlocking {
        withContext(main) { viewModels.clear() }
        memory.clear()
        session.invalidate()
        Dispatchers.resetMain()
        main.close()
    }

    private fun emptyCourses() = AiCourseSnapshot("2025", "2", emptyList())

    @Test fun requestsShareWorkAndCancellingOneWaiterDoesNotCancelOthers() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val fetch: suspend () -> Result<AiCourseSnapshot> = {
            calls.incrementAndGet(); entered.complete(Unit); release.await(); Result.success(emptyCourses())
        }
        val first = async { memory.courses(fetch = fetch) }
        entered.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { memory.courses(fetch = fetch) }
        // Let the second caller finish its asynchronous credential read before releasing the owner.
        delay(100)
        first.cancelAndJoin()
        release.complete(Unit)
        assertTrue(withTimeout(5000) { second.await() }.isSuccess)
        assertEquals(1, calls.get())
        assertNotNull(memory.snapshot().coursesAt)
    }

    @Test fun workingStateAndCoursesPublishIndependentlyIncludingValidEmptyResults() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val working = async { memory.working { release.await(); Result.success(null) } }
        memory.courses { Result.success(emptyCourses()) }
        assertNotNull(memory.snapshot().courses)
        assertNull(memory.snapshot().workingAt)
        release.complete(Unit)
        working.await()
        assertNotNull(memory.snapshot().workingAt)
        assertNull(memory.snapshot().workingRecord)
    }

    @Test fun definiteFailureSurvivesAnotherResourcesSuccessAndNetworkFailure() = runBlocking {
        memory.courses { Result.success(emptyCourses()) }
        memory.working { Result.failure(FifUserMappingException(FifMappingReason.ACCOUNT_INFO)) }
        memory.courses { Result.success(emptyCourses()) }
        memory.working { Result.failure(IOException("offline")) }
        assertNotNull(memory.snapshot().courses)
        assertTrue(FifOperation.WORKING_RECORD in memory.snapshot().failures)
        memory.working { Result.success(null) }
        assertTrue(memory.snapshot().failures.isEmpty())
    }

    @Test fun ordinaryNetworkErrorsKeepCacheWithoutInventingAccountFailure() = runBlocking {
        memory.courses { Result.success(emptyCourses()) }
        memory.courses { Result.failure(IOException("offline")) }
        assertNotNull(memory.snapshot().courses)
        assertTrue(memory.snapshot().failures.isEmpty())
    }

    @Test fun blockedHealthCheckOverridesFreshCacheAndEachResourceMustRecover() = runBlocking {
        memory.courses { Result.success(emptyCourses()) }
        memory.working { Result.success(null) }
        memory.markBlocked(session.capture())
        var fetched = false
        memory.courses(maxAgeMs = 300_000) { fetched = true; Result.success(emptyCourses()) }
        assertTrue(fetched)
        assertTrue(FifOperation.WORKING_RECORD in memory.snapshot().failures)
        memory.working(maxAgeMs = 20_000) { Result.success(null) }
        assertTrue(memory.snapshot().failures.isEmpty())
    }

    @Test fun resetRejectsLateResultEvenIfOldNetworkIgnoresCancellation() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val old = async {
            try { memory.courses {
                withContext(NonCancellable) { entered.complete(Unit); release.await() }
                Result.success(emptyCourses().copy(termYear = "old"))
            } } catch (_: CancellationException) { null }
        }
        entered.await()
        memory.clear()
        memory.courses { Result.success(emptyCourses().copy(termYear = "new")) }
        release.complete(Unit)
        old.await()
        assertEquals("new", memory.snapshot().courses!!.termYear)
    }

    @Test fun accountChangeHidesOldSnapshotAndRejectsLateFailure() = runBlocking {
        memory.courses { Result.success(emptyCourses()) }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val old = async {
            try { memory.working {
                withContext(NonCancellable) { entered.complete(Unit); release.await() }
                Result.failure(FifUserMappingException(FifMappingReason.ACCOUNT_INFO))
            } } catch (_: CancellationException) { null }
        }
        entered.await()
        tokens.saveLoginData("new", "r", "B", "", "1", "")
        assertNull(memory.snapshot().courses)
        memory.courses { Result.success(emptyCourses().copy(termYear = "B")) }
        release.complete(Unit)
        old.await()
        assertEquals("B", memory.snapshot().courses!!.termYear)
        assertTrue(memory.snapshot().failures.isEmpty())
    }

    private class Responses : Interceptor {
        val calls = ConcurrentHashMap<String, AtomicInteger>()
        var workingGate: CountDownLatch? = null
        var supplementStatus = 200
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val name = request.url.pathSegments.last()
            calls.getOrPut(name) { AtomicInteger() }.incrementAndGet()
            var status = 200
            val body = when (name) {
                "login" -> ""
                "getAiktUserIdByMemberId" -> """{"data":{"id":"student","schoolId":"school"}}"""
                "termList" -> """{"data":{"dataList":[{"year":"2025","num":"2","selected":"1"}]}}"""
                "myClassroom" -> """{"data":{"dataList":[]}}"""
                "getWorkingCourseRecordByStudentId" -> {
                    check(workingGate?.await(10, TimeUnit.SECONDS) != false)
                    """{"data":[]}"""
                }
                "timetableInfo" -> { status = supplementStatus; """{"data":[]}""" }
                else -> "{}"
            }
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (name == "login") 302 else status)
                .message("test").apply {
                    if (name == "login") header("Location", "https://sttp.fifedu.com/studycenter-nh5/?token=h.eyJtZW1iZXJJZCI6Im1lbWJlciJ9.s")
                }.body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private suspend fun repository(responses: Responses): AiClassRepository {
        val cookies = FifCookieJar()
        val client = OkHttpClient.Builder().cookieJar(cookies).addInterceptor(responses).build()
        val fif = FifSessionManager(context, tokens, client, cookies, session)
        fif.clear()
        fif.performSso().getOrThrow()
        cookies.saveFromResponse(ApiConstants.BASE_FIF.toHttpUrl(), listOf(
            Cookie.Builder().name("SESSION").value("test").domain("fifedu.com").path("/").build(),
        ))
        val retrofit = Retrofit.Builder().baseUrl(ApiConstants.BASE_FIF + "/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
        return AiClassRepository(retrofit.create(AiClassApi::class.java), retrofit.create(IzuoyeApi::class.java), fif, cookies, client, memory)
    }

    @Test fun onlyHealthyPrewarmsAndFreshSnapshotAvoidsRepeatedWarmup() = runBlocking {
        val responses = Responses()
        val repo = repository(responses)
        val baseline = responses.calls.values.sumOf { it.get() }
        for (check in AiClassSessionCheck.entries.filter { it != AiClassSessionCheck.Healthy }) repo.warmUp(check)
        assertEquals(baseline, responses.calls.values.sumOf { it.get() })
        repo.warmUp(AiClassSessionCheck.Healthy)
        repo.warmUp(AiClassSessionCheck.Healthy)
        assertEquals(1, responses.calls["myClassroom"]!!.get())
        assertEquals(1, responses.calls["getWorkingCourseRecordByStudentId"]!!.get())
        assertNotNull(memory.snapshot().workingAt)
    }

    @Test fun pageSharesWarmupAndShowsCoursesBeforeWorkingResponse() = runBlocking {
        val responses = Responses().apply { workingGate = CountDownLatch(1) }
        try {
            val repo = repository(responses)
            val warm = async { repo.warmUp(AiClassSessionCheck.Healthy) }
            withTimeout(5000) { memory.snapshots.first { it.courses != null } }
            val vm = withContext(main) { AiClassViewModel(repo, memory).also { viewModels.put("page", it) } }
            val state = withTimeout(5000) { vm.uiState.first { !it.isLoading && it.isWorkingLoading } }
            assertTrue(state.courses.isEmpty())
            assertFalse(state.workingKnown)
            responses.workingGate!!.countDown()
            warm.await()
            withTimeout(5000) { vm.uiState.first { !it.isWorkingLoading && it.workingKnown } }
            assertEquals(1, responses.calls["getWorkingCourseRecordByStudentId"]!!.get())
        } finally { responses.workingGate!!.countDown() }
    }

    @Test fun supplementalUnauthorizedIsNotSwallowedAndPageStartsWithExistingError() = runBlocking {
        val responses = Responses().apply { supplementStatus = 403 }
        val repo = repository(responses)
        memory.courses { Result.success(emptyCourses()) }
        assertTrue(repo.getCourses(forceRefresh = true).isFailure)
        assertTrue(memory.snapshot().failures.isNotEmpty())
        val vm = withContext(main) { AiClassViewModel(repo, memory).also { viewModels.put("page", it) } }
        assertNotNull(vm.mappingRecovery.value.failure)
        assertNotNull(memory.snapshot().courses)
    }
}
