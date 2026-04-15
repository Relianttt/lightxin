package com.lightxin.feature.aiclass.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.network.FifSessionManager
import com.lightxin.feature.aiclass.data.AiClassRepository
import com.lightxin.feature.aiclass.domain.AiCourse
import com.lightxin.feature.aiclass.domain.AiWorkingRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiClassUiState(
    val courses: List<AiCourse> = emptyList(),
    val workingRecord: AiWorkingRecord? = null,
    val isLoading: Boolean = true,
    val isSsoInProgress: Boolean = false,
    val isSigningIn: Boolean = false,
    val error: String? = null,
    val signResult: String? = null,
)

@HiltViewModel
class AiClassViewModel @Inject constructor(
    private val repository: AiClassRepository,
    private val fifSession: FifSessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiClassUiState())
    val uiState: StateFlow<AiClassUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 确保 SSO
            if (!fifSession.isSessionValid()) {
                _uiState.update { it.copy(isSsoInProgress = true) }
                val ssoResult = fifSession.performSso()
                _uiState.update { it.copy(isSsoInProgress = false) }
                if (ssoResult.isFailure) {
                    _uiState.update {
                        it.copy(isLoading = false, error = ssoResult.exceptionOrNull()?.message)
                    }
                    return@launch
                }
            }

            // 并行加载课程和课堂状态
            val coursesResult = repository.getCourses()
            val workingResult = repository.getWorkingRecord()

            _uiState.update {
                it.copy(
                    courses = coursesResult.getOrDefault(emptyList()),
                    workingRecord = workingResult.getOrNull(),
                    isLoading = false,
                    error = coursesResult.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun submitSignCode(signCode: String) {
        val working = _uiState.value.workingRecord ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningIn = true, signResult = null) }

            // 先查签到信息获取 signId
            val signInfoResult = repository.getSignInInfo(
                teachClassId = working.teachClassId,
                courseRecordId = working.courseRecordId,
            )
            val signInfo = signInfoResult.getOrNull()

            if (signInfo == null || !signInfo.hasActiveSign) {
                _uiState.update {
                    it.copy(
                        isSigningIn = false,
                        signResult = signInfoResult.exceptionOrNull()?.message ?: "当前没有进行中的签到",
                    )
                }
                return@launch
            }

            // 提交数字码
            val result = repository.submitSignCode(signInfo.signId, signCode)
            _uiState.update {
                it.copy(
                    isSigningIn = false,
                    signResult = result.getOrElse { e -> e.message ?: "签到失败" },
                )
            }
        }
    }

    /** 直接用 signId 签到（当已知 signId 时） */
    fun submitSignCodeDirect(signId: String, signCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningIn = true, signResult = null) }
            val result = repository.submitSignCode(signId, signCode)
            _uiState.update {
                it.copy(
                    isSigningIn = false,
                    signResult = result.getOrElse { e -> e.message ?: "签到失败" },
                )
            }
        }
    }

    fun consumeSignResult() {
        _uiState.update { it.copy(signResult = null) }
    }

    fun retry() {
        load()
    }
}
