package com.lightxin.feature.checkin.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.location.CoordinateConverter
import com.lightxin.core.location.LocationProvider
import com.lightxin.feature.checkin.data.CheckinRepository
import com.lightxin.feature.checkin.domain.TaskDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class CheckinDetailUiState(
    val detail: TaskDetail? = null,
    val isLoading: Boolean = true,
    val error: String? = null,

    // 定位
    val bdLng: Double? = null,
    val bdLat: Double? = null,
    val locationStatus: LocationStatus = LocationStatus.IDLE,

    // 拍照
    val photoUri: Uri? = null,

    // 签到
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitError: String? = null,
)

enum class LocationStatus {
    IDLE, LOCATING, SUCCESS, FAILED
}

@HiltViewModel
class CheckinDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CheckinRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val taskDateId: String = savedStateHandle["taskDateId"] ?: ""

    private val _uiState = MutableStateFlow(CheckinDetailUiState())
    val uiState: StateFlow<CheckinDetailUiState> = _uiState

    init {
        loadDetail()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.getTaskDetail(taskDateId).fold(
                onSuccess = { detail ->
                    _uiState.update { it.copy(detail = detail, isLoading = false) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "加载失败")
                    }
                },
            )
        }
    }

    fun onPhotoTaken(uri: Uri?) {
        _uiState.update { it.copy(photoUri = uri) }
    }

    fun requestLocation() {
        _uiState.update { it.copy(locationStatus = LocationStatus.LOCATING) }

        // 先尝试 getLastKnownLocation 快速获取
        val lastLocation = locationProvider.getLastKnownLocation()
        if (lastLocation != null) {
            applyLocation(lastLocation.latitude, lastLocation.longitude)
            return
        }

        // 订阅持续定位，取首次结果
        viewModelScope.launch {
            try {
                locationProvider.locationUpdates(intervalMs = 2000L, minDistanceM = 0f)
                    .collect { location ->
                        applyLocation(location.latitude, location.longitude)
                        return@collect  // 取到立即停止
                    }
            } catch (_: SecurityException) {
                _uiState.update { it.copy(locationStatus = LocationStatus.FAILED) }
            }
        }
    }

    private fun applyLocation(wgsLat: Double, wgsLng: Double) {
        val bd = CoordinateConverter.wgs84ToBd09(wgsLat, wgsLng)
        _uiState.update {
            it.copy(
                bdLng = bd.longitude,
                bdLat = bd.latitude,
                locationStatus = LocationStatus.SUCCESS,
            )
        }
    }

    fun submitSignIn(photoFile: File?) {
        val state = _uiState.value
        val detail = state.detail ?: return
        if (state.bdLng == null || state.bdLat == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null) }

            // 1. 如需拍照则先上传
            var photoUrl = ""
            if (detail.needPhoto && photoFile != null) {
                val uploadResult = repository.uploadPhoto(photoFile)
                uploadResult.fold(
                    onSuccess = { url -> photoUrl = url },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isSubmitting = false, submitError = e.message ?: "照片上传失败")
                        }
                        return@launch
                    },
                )
            }

            // 2. 提交签到 latLng格式: "经度,纬度"
            val lngLatStr = "${state.bdLng},${state.bdLat}"
            repository.submitSignIn(
                taskDateId = detail.taskDateId,
                photoUrl = photoUrl,
                place = detail.address,
                lngLatString = lngLatStr,
            ).fold(
                onSuccess = {
                    _uiState.update { it.copy(isSubmitting = false, submitSuccess = true) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSubmitting = false, submitError = e.message ?: "签到失败")
                    }
                },
            )
        }
    }

    fun retry() {
        loadDetail()
    }

    fun clearSubmitError() {
        _uiState.update { it.copy(submitError = null) }
    }
}
