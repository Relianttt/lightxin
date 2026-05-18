package com.lightxin.feature.aiclass.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    // teachClassId 需要从外部传入或从上一级获取
    // 这里通过 load 方法接收
    private var teachClassId: String = ""

    init {
        // 延迟加载，等待 teachClassId 设置
    }

    private var currentPage = 1
    private var hasMorePages = true

    fun load(classId: String) {
        if (classId.isBlank() || cwId.isBlank()) return
        teachClassId = classId
        currentPage = 1
        hasMorePages = true
        repository.clearJtzyCache()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isWorksLoadingMore = false,
                    hasMoreWorks = true,
                    error = null,
                )
            }

            // 先加载详情获取 cwDeadline
            val detailResult = repository.getHomeworkDetail(cwId, teachClassId)
            val detail = detailResult.getOrNull()
            if (detail == null) {
                _uiState.update {
                    it.copy(
                        detail = null,
                        studentWorks = emptyList(),
                        isLoading = false,
                        hasMoreWorks = false,
                        error = detailResult.exceptionOrNull()?.message,
                    )
                }
                return@launch
            }
            val cwDeadline = detail?.cwDeadlineFormat.orEmpty()

            // 再用 cwDeadline 加载提交列表
            val worksResult = repository.getStuWorkList(cwId, teachClassId, cwDeadline, page = 1)
            val works = worksResult.getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    detail = detail,
                    studentWorks = works,
                    isLoading = false,
                    hasMoreWorks = works.size >= WORKS_PAGE_SIZE,
                    error = worksResult.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun loadMoreWorks() {
        val state = _uiState.value
        if (!hasMorePages || state.isWorksLoadingMore || !state.hasMoreWorks) return
        val detail = _uiState.value.detail ?: return
        val nextPage = currentPage + 1
        viewModelScope.launch {
            _uiState.update { it.copy(isWorksLoadingMore = true) }
            val result = repository.getStuWorkList(cwId, teachClassId, detail.cwDeadlineFormat, page = nextPage)
            val newItems = result.getOrNull()
            if (newItems == null) {
                _uiState.update { it.copy(isWorksLoadingMore = false) }
                return@launch
            }

            currentPage = nextPage
            if (newItems.size < WORKS_PAGE_SIZE) {
                hasMorePages = false
            }
            _uiState.update {
                val existingIds = it.studentWorks.mapTo(mutableSetOf()) { work -> work.stuCwId }
                val merged = it.studentWorks + newItems.filterNot { work -> work.stuCwId in existingIds }
                it.copy(
                    studentWorks = merged,
                    isWorksLoadingMore = false,
                    hasMoreWorks = hasMorePages,
                )
            }
        }
    }

    fun showSubmitSheet() {
        _uiState.update { it.copy(showSubmitSheet = true) }
    }

    fun dismissSubmitSheet() {
        _uiState.update { it.copy(showSubmitSheet = false) }
    }

    fun submitHomework(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitResult = null) }
            val result = repository.submitHomework(cwId, teachClassId, text)
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    showSubmitSheet = false,
                    submitResult = result.getOrElse { e -> e.message ?: "提交失败" },
                )
            }
            // 提交成功后刷新列表
            if (result.isSuccess) {
                val cwDeadline = _uiState.value.detail?.cwDeadlineFormat.orEmpty()
                val worksResult = repository.getStuWorkList(cwId, teachClassId, cwDeadline)
                val works = worksResult.getOrDefault(_uiState.value.studentWorks)
                currentPage = 1
                hasMorePages = works.size >= WORKS_PAGE_SIZE
                _uiState.update {
                    it.copy(
                        studentWorks = works,
                        hasMoreWorks = hasMorePages,
                        isWorksLoadingMore = false,
                    )
                }
            }
        }
    }

    fun consumeSubmitResult() {
        _uiState.update { it.copy(submitResult = null) }
    }

    fun retry() {
        load(teachClassId)
    }
}
