package com.lightxin.feature.exam.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.lightxin.core.auth.AccountSession
import com.lightxin.core.auth.TokenManager
import com.lightxin.feature.exam.data.*
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
class ExamViewModelTest {
    private val main = newSingleThreadContext("exam-main")
    private val store = ViewModelStore()
    private val session = AccountSession()
    private val calls = mutableListOf<Pair<String, String>>()
    private var fetch: suspend (String, String) -> ExamScoreResponse = { _, _ -> response("initial") }
    private lateinit var repository: ExamRepository

    private fun response(name: String) = ExamScoreResponse(true, null,
        listOf(ExamScoreItem("code", name, null, "2", "90", null, "4", null, null)))

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(main)
        val tokens = TokenManager(RuntimeEnvironment.getApplication(), session)
        tokens.saveLoginData("t", "r", "A", "", "1", "")
        repository = ExamRepository(object : ExamApi {
            override suspend fun getCurrentTerm(dummy: String) = CurrentTermResponse(true, CurrentTermData(XlVo("2026", "1", "8")))
            override suspend fun getSchoolYears(dummy: String) = SchoolYearResponse(true, listOf(SchoolYearItem("2026-2027", "2026")))
            override suspend fun getExamScores(userCode: String, schoolYear: String, semester: String): ExamScoreResponse {
                calls += schoolYear to semester
                return fetch(schoolYear, semester)
            }
        }, tokens)
    }

    @After fun cleanup() = runBlocking {
        withContext(main) { store.clear() }
        session.invalidate()
        Dispatchers.resetMain()
        main.close()
    }

    @Test fun selectingTermMakesOneRequestAndRetryKeepsSelection() = runBlocking {
        val vm = withContext(main) { ExamViewModel(repository).also { store.put("vm", it) } }
        withTimeout(10_000) { vm.uiState.first { it.scores.isNotEmpty() } }
        withContext(main) {
            calls.clear()
            fetch = { _, _ -> throw IllegalStateException("offline") }
            vm.onTermSelected("2025", "2")
        }
        withTimeout(10_000) { vm.uiState.first { it.error != null } }
        withContext(main) {
            assertEquals(listOf("2025" to "2"), calls)
            fetch = { _, _ -> response("retried") }
            vm.retry()
        }
        val state = withTimeout(10_000) { vm.uiState.first { it.scores.firstOrNull()?.courseName == "retried" } }
        assertEquals("2025", state.selectedYear)
        assertEquals("2", state.selectedSemester)
        withContext(main) { assertEquals(listOf("2025" to "2", "2025" to "2"), calls) }
    }
}
