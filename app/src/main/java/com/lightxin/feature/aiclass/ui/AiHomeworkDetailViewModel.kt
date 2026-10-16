package com.lightxin.feature.aiclass.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.network.FifMappingRecovery
import com.lightxin.core.network.FifOperation
import com.lightxin.core.network.fifMappingFailure
import com.lightxin.feature.aiclass.data.AiClassRepository
import com.lightxin.feature.aiclass.domain.AiHomeworkDetail
import com.lightxin.feature.aiclass.domain.AiStudentWork
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiHomeworkDetailUiState(
    val detail: AiHomeworkDetail? = null,
    val studentWorks: List<AiStudentWork> = emptyList(),
    val isLoading: Boolean = true,
    val isWorksLoadingMore: Boolean = false,
    val hasMoreWorks: Boolean = true,
    val isSubmitting: Boolean = false,
    val showSubmitSheet: Boolean = false,
    val error: String? = null,
    val submitResult: String? = null,
)

private const val WORKS_PAGE_SIZE = 10

@HiltViewModel
class AiHomeworkDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AiClassRepository,
) : ViewModel() {
    private val cwId: String = savedStateHandle["cwId"] ?: ""
    private val _uiState = MutableStateFlow(AiHomeworkDetailUiState())
    val uiState: StateFlow<AiHomeworkDetailUiState> = _uiState
    private val recovery = FifMappingRecovery(repository::resetFifSession)
    val mappingRecovery = recovery.state
    private var teachClassId = ""
    private var currentPage = 1
    private var loadVersion = 0

    fun retryMapping() { viewModelScope.launch { recovery.retry() } }

    fun load(classId: String) {
        if (classId.isBlank() || cwId.isBlank()) return
        teachClassId = classId
        val version = ++loadVersion
        currentPage = 1
        repository.clearJtzyCache()
        _uiState.update {
            it.copy(isLoading = true, isWorksLoadingMore = false, hasMoreWorks = true, error = null)
        }
        viewModelScope.launch {
            recovery.execute(FifOperation.HOMEWORK_DETAIL,
                request = { repository.getHomeworkDetail(cwId, classId) },
                isCurrent = { loadVersion == version },
            ) { result ->
                val detail = result.getOrNull()
                _uiState.update {
                    it.copy(detail = detail, studentWorks = emptyList(), isLoading = detail != null,
                        hasMoreWorks = detail != null, error = result.exceptionOrNull()?.safeMessage())
                }
                if (detail != null) loadWorks(detail, classId, page = 1, version = version)
            }
        }
    }

    private fun loadWorks(detail: AiHomeworkDetail, classId: String, page: Int, version: Int) {
        viewModelScope.launch {
            recovery.execute(FifOperation.WORKS_MORE,
                request = { repository.getStuWorkList(cwId, classId, detail.cwDeadlineFormat, page = page) },
                isCurrent = { loadVersion == version },
            ) { result ->
                val works = result.getOrNull()
                if (works != null) currentPage = page
                _uiState.update { state ->
                    val existing = if (page == 1) emptyList() else state.studentWorks
                    val ids = existing.mapTo(mutableSetOf()) { it.stuCwId }
                    state.copy(
                        studentWorks = if (works == null) state.studentWorks else existing + works.filter { ids.add(it.stuCwId) },
                        isLoading = false,
                        isWorksLoadingMore = false,
                        hasMoreWorks = works?.let { it.size >= WORKS_PAGE_SIZE } ?: state.hasMoreWorks,
                        error = result.exceptionOrNull()?.safeMessage(),
                    )
                }
            }
        }
    }

    fun loadMoreWorks() {
        val state = _uiState.value
        val detail = state.detail ?: return
        if (state.isWorksLoadingMore || !state.hasMoreWorks || mappingRecovery.value.failure != null) return
        _uiState.update { it.copy(isWorksLoadingMore = true) }
        loadWorks(detail, teachClassId, currentPage + 1, loadVersion)
    }

    fun showSubmitSheet() { _uiState.update { it.copy(showSubmitSheet = true) } }
    fun dismissSubmitSheet() { _uiState.update { it.copy(showSubmitSheet = false) } }

    fun submitHomework(text: String) {
        if (text.isBlank() || _uiState.value.isSubmitting || mappingRecovery.value.failure != null) return
        val classId = teachClassId
        val version = loadVersion
        _uiState.update { it.copy(isSubmitting = true, submitResult = null) }
        viewModelScope.launch {
            recovery.execute(FifOperation.SUBMIT_HOMEWORK,
                request = { repository.submitHomework(cwId, classId, text) },
                isCurrent = { loadVersion == version },
            ) { result ->
                _uiState.update {
                    it.copy(isSubmitting = false, showSubmitSheet = false,
                        submitResult = if (result.exceptionOrNull()?.fifMappingFailure(FifOperation.SUBMIT_HOMEWORK) != null) null
                            else result.getOrElse { error -> error.safeMessage() })
                }
                // Refresh reads separately: a failed refresh must never replay a successful submission.
                if (result.isSuccess) _uiState.value.detail?.let { detail ->
                    val refreshVersion = ++loadVersion
                    _uiState.update { it.copy(isWorksLoadingMore = true) }
                    loadWorks(detail, classId, 1, refreshVersion)
                }
            }
        }
    }

    fun consumeSubmitResult() { _uiState.update { it.copy(submitResult = null) } }
    fun retry() { load(teachClassId) }
}
