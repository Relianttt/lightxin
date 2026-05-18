package com.lightxin.feature.aiclass.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.aiclass.data.AiClassRepository
import com.lightxin.feature.aiclass.domain.AiHomeworkDetail
import com.lightxin.feature.aiclass.domain.AiStudentWork
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiHomeworkDetailUiState(
    val detail: AiHomeworkDetail? = null,
    val studentWorks: List<AiStudentWork> = emptyList(),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val showSubmitSheet: Boolean = false,
    val error: String? = null,
    val submitResult: String? = null,
)

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

    fun load(classId: String) {
        if (classId.isBlank() || cwId.isBlank()) return
        teachClassId = classId
        repository.clearJtzyCache()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val detailDeferred = async { repository.getHomeworkDetail(cwId, teachClassId) }
            val worksDeferred = async { repository.getStuWorkList(cwId, teachClassId) }

            val detailResult = detailDeferred.await()
            val worksResult = worksDeferred.await()

            _uiState.update {
                it.copy(
                    detail = detailResult.getOrNull(),
                    studentWorks = worksResult.getOrDefault(emptyList()),
                    isLoading = false,
                    error = detailResult.exceptionOrNull()?.message,
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
                val worksResult = repository.getStuWorkList(cwId, teachClassId)
                _uiState.update { it.copy(studentWorks = worksResult.getOrDefault(it.studentWorks)) }
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
