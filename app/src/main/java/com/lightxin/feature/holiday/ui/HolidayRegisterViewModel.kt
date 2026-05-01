package com.lightxin.feature.holiday.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.holiday.data.HolidayRepository
import com.lightxin.feature.holiday.domain.HolidayFormData
import com.lightxin.feature.holiday.domain.StrokeOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HolidayRegisterUiState(
    val holidayName: String = "",
    val strokeOptions: List<StrokeOption> = emptyList(),
    val existingData: HolidayFormData? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val error: String? = null,
    // 表单字段
    val startDate: String = "",
    val endDate: String = "",
    val stroke: String = "0",
    val reason: String = "",
    val destination: String = "",
    val urgentPhone: String = "",
    // 字段显隐（由节假日配置决定）
    val destinationEnabled: Boolean = false,
    val urgentPhoneEnabled: Boolean = false,
)

@HiltViewModel
class HolidayRegisterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: HolidayRepository,
) : ViewModel() {

    private val holidayId: String = savedStateHandle.get<String>("holidayId") ?: ""

    private val _uiState = MutableStateFlow(HolidayRegisterUiState())
    val uiState: StateFlow<HolidayRegisterUiState> = _uiState

    init {
        loadFormData()
    }

    private fun loadFormData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 并行加载配置、已有记录、字典
            val (detailResult, existingResult, strokeResult) = coroutineScope {
                val detailDeferred = async { repository.getHolidayDetail(holidayId) }
                val existingDeferred = async { repository.getExistingRegister(holidayId) }
                val strokeDeferred = async { repository.getStrokeTypes() }
                Triple(detailDeferred.await(), existingDeferred.await(), strokeDeferred.await())
            }

            val detail = detailResult.getOrNull()
            val existing = existingResult.getOrNull()
            val strokeOptions = strokeResult.getOrNull().orEmpty()

            val firstError = listOf(
                detailResult.exceptionOrNull(),
                existingResult.exceptionOrNull(),
                strokeResult.exceptionOrNull(),
            ).firstOrNull()

            if (firstError != null && detail == null && existing == null && strokeOptions.isEmpty()) {
                _uiState.update {
                    it.copy(isLoading = false, error = firstError.message ?: "加载失败")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    holidayName = detail?.name.orEmpty(),
                    strokeOptions = strokeOptions,
                    existingData = existing,
                    isLoading = false,
                    destinationEnabled = detail?.leaveDestinationEnable == "1",
                    urgentPhoneEnabled = detail?.leaveEmergencyPhoneEnable == "1",
                    startDate = existing?.startDate.orEmpty(),
                    endDate = existing?.endDate.orEmpty(),
                    stroke = existing?.stroke ?: "0",
                    reason = existing?.reason.orEmpty(),
                    destination = existing?.destination.orEmpty(),
                    urgentPhone = existing?.urgentPhone.orEmpty(),
                )
            }
        }
    }

    fun updateStartDate(value: String) {
        _uiState.update { it.copy(startDate = value) }
    }

    fun updateEndDate(value: String) {
        _uiState.update { it.copy(endDate = value) }
    }

    fun updateStroke(value: String) {
        _uiState.update { it.copy(stroke = value) }
    }

    fun updateReason(value: String) {
        _uiState.update { it.copy(reason = value) }
    }

    fun updateDestination(value: String) {
        _uiState.update { it.copy(destination = value) }
    }

    fun updateUrgentPhone(value: String) {
        _uiState.update { it.copy(urgentPhone = value) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            val form = HolidayFormData(
                startDate = state.startDate,
                endDate = state.endDate,
                stroke = state.stroke,
                reason = state.reason,
                destination = state.destination,
                urgentPhone = state.urgentPhone,
                registerId = state.existingData?.registerId.orEmpty(),
            )

            repository.saveRegistration(holidayId, form).fold(
                onSuccess = {
                    _uiState.update { it.copy(isSubmitting = false, submitSuccess = true) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = e.message ?: "提交失败")
                    }
                },
            )
        }
    }

    fun retry() {
        loadFormData()
    }
}
