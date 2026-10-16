package com.lightxin.feature.aiclass.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.network.FifMappingRecovery
import com.lightxin.core.network.FifOperation
import com.lightxin.core.network.fifMappingFailure
import com.lightxin.feature.aiclass.data.AiClassRepository
import com.lightxin.feature.aiclass.domain.AiClassQrPayload
import com.lightxin.feature.aiclass.domain.AiCourse
import com.lightxin.feature.aiclass.domain.AiQuiz
import com.lightxin.feature.aiclass.domain.AiWorkingRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

private const val AICLASS_INITIAL_LOAD_TIMEOUT_MS = 18_000L
private const val AICLASS_OPTIONAL_LOAD_TIMEOUT_MS = 8_000L
private const val AICLASS_DETAIL_LOAD_TIMEOUT_MS = 12_000L

data class AiClassUiState(
    val courses: List<AiCourse> = emptyList(),
    val workingRecord: AiWorkingRecord? = null,
    val selectedCourse: AiCourse? = null,
    val quizList: List<AiQuiz> = emptyList(),
    val homeworkList: List<com.lightxin.feature.aiclass.domain.AiHomework> = emptyList(),
    val isLoading: Boolean = true,
    val isSsoInProgress: Boolean = false,
    val isSigningIn: Boolean = false,
    val isQuizLoading: Boolean = false,
    val isHomeworkLoading: Boolean = false,
    val error: String? = null,
    val quizError: String? = null,
    val homeworkError: String? = null,
    val signResult: String? = null,
)

@HiltViewModel
class AiClassViewModel @Inject constructor(
    private val repository: AiClassRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiClassUiState())
    val uiState: StateFlow<AiClassUiState> = _uiState
    private val recovery = FifMappingRecovery(repository::resetFifSession)
    val mappingRecovery = recovery.state
    private var loadingHome = false

    init { load() }

    fun retryMapping() { viewModelScope.launch { recovery.retry() } }

    fun load() {
        if (_uiState.value.isLoading && loadingHome) return
        loadingHome = true
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                recovery.execute(FifOperation.HOME, request = {
                    coroutineScope {
                        val courses = async {
                            withResultTimeout("获取课程列表", AICLASS_INITIAL_LOAD_TIMEOUT_MS) { repository.getCourses() }
                        }
                        val working = async {
                            withResultTimeout("获取课堂状态", AICLASS_OPTIONAL_LOAD_TIMEOUT_MS) { repository.getWorkingRecord() }
                        }
                        val courseResult = courses.await()
                        val workingResult = working.await()
                        val error = listOfNotNull(courseResult.exceptionOrNull(), workingResult.exceptionOrNull())
                            .firstOrNull { it.fifMappingFailure(FifOperation.HOME) != null }
                            ?: courseResult.exceptionOrNull()
                        if (error != null) Result.failure(error)
                        else Result.success(courseResult.getOrDefault(emptyList()) to workingResult.getOrNull())
                    }
                }) { result ->
                    _uiState.update {
                        it.copy(
                            courses = result.getOrNull()?.first.orEmpty(),
                            workingRecord = result.getOrNull()?.second,
                            isLoading = false,
                            error = result.exceptionOrNull()?.safeMessage(),
                        )
                    }
                }
            } finally { loadingHome = false }
        }
    }

    fun submitSignCode(signCode: String) {
        val working = _uiState.value.workingRecord ?: return
        sign(FifOperation.SIGN_CODE) {
            val infoResult = repository.getSignInInfo(working.teachClassId, working.courseRecordId)
            val info = infoResult.getOrNull()
            when {
                infoResult.isFailure -> Result.failure(infoResult.exceptionOrNull()!!)
                info == null || !info.hasActiveSign -> Result.success("当前没有进行中的签到")
                else -> repository.submitSignCode(info.signId, signCode)
            }
        }
    }

    fun submitSignCodeDirect(signId: String, signCode: String) {
        sign(FifOperation.SIGN_CODE) { repository.submitSignCode(signId, signCode) }
    }

    fun submitQrCode(payload: AiClassQrPayload) {
        sign(FifOperation.QR_SIGN) { repository.submitQrCode(payload) }
    }

    private fun sign(operation: FifOperation, request: suspend () -> Result<String>) {
        if (_uiState.value.isSigningIn || mappingRecovery.value.failure != null) return
        _uiState.update { it.copy(isSigningIn = true, signResult = null) }
        viewModelScope.launch {
            recovery.execute(operation, request) { result ->
                _uiState.update {
                    it.copy(
                        isSigningIn = false,
                        signResult = if (result.exceptionOrNull()?.fifMappingFailure(operation) != null) null
                            else result.getOrElse { error -> error.safeMessage() },
                    )
                }
            }
        }
    }

    fun consumeSignResult() { _uiState.update { it.copy(signResult = null) } }
    fun retry() { load() }

    fun openCourseDetail(classId: String) {
        if (classId == "_working") return
        val course = _uiState.value.courses.find { it.stableId == classId }
        if (course == null) {
            _uiState.update {
                it.copy(selectedCourse = null, isQuizLoading = false, isHomeworkLoading = false,
                    quizError = "课程信息不存在，请返回重试")
            }
            return
        }
        _uiState.update {
            it.copy(selectedCourse = course, quizList = emptyList(), homeworkList = emptyList(),
                isQuizLoading = true, isHomeworkLoading = true, quizError = null, homeworkError = null)
        }
        loadQuiz(course, course.courseId)
        viewModelScope.launch {
            recovery.execute(FifOperation.HOMEWORK, request = {
                withResultTimeout("获取作业列表", AICLASS_DETAIL_LOAD_TIMEOUT_MS) {
                    repository.getHomeworkList(course.courseId, course.teachClassId)
                }
            }, isCurrent = { _uiState.value.selectedCourse?.stableId == classId }) { result ->
                _uiState.update {
                    it.copy(homeworkList = result.getOrDefault(emptyList()), isHomeworkLoading = false,
                        homeworkError = result.exceptionOrNull()?.safeMessage())
                }
            }
        }
    }

    private fun loadQuiz(course: AiCourse, courseId: String) {
        viewModelScope.launch {
            recovery.execute(FifOperation.QUIZ, request = {
                withResultTimeout("获取测验列表", AICLASS_DETAIL_LOAD_TIMEOUT_MS) { repository.getQuizList(courseId) }
            }, isCurrent = { _uiState.value.selectedCourse?.stableId == course.stableId }) { result ->
                _uiState.update {
                    it.copy(quizList = result.getOrDefault(emptyList()), isQuizLoading = false,
                        quizError = result.exceptionOrNull()?.safeMessage())
                }
            }
        }
    }

    fun retryQuiz() {
        val course = _uiState.value.selectedCourse ?: return
        _uiState.update { it.copy(isQuizLoading = true, quizError = null) }
        loadQuiz(course, if (course.stableId == "_working") course.teachClassId else course.courseId)
    }

    fun openWorkingRecordDetail() {
        val record = _uiState.value.workingRecord ?: return
        val base = _uiState.value.courses.find { it.teachClassId == record.teachClassId } ?: AiCourse(
            stableId = "", id = "", code = "", classId = record.teachClassId,
            courseId = record.teachClassId, courseRecordId = record.courseRecordId,
            courseName = record.courseItemName.ifBlank { record.courseName }, teacherName = "",
            studentNum = 0, teachClassId = record.teachClassId, cover = "", termYear = "", term = "", typeName = "",
        )
        val course = base.copy(stableId = "_working")
        _uiState.update {
            it.copy(selectedCourse = course, quizList = emptyList(), isQuizLoading = true, quizError = null,
                homeworkList = emptyList(), isHomeworkLoading = false, homeworkError = null)
        }
        loadQuiz(course, record.teachClassId)
    }
}

private suspend fun <T> withResultTimeout(action: String, timeoutMs: Long, block: suspend () -> Result<T>): Result<T> =
    try { withTimeout(timeoutMs) { block() } }
    catch (e: TimeoutCancellationException) { Result.failure(Exception("${action}超时，请稍后重试", e)) }

internal fun Throwable.safeMessage(): String =
    fifMappingFailure(FifOperation.HOME)?.message ?: message ?: "操作失败，请稍后重试"
