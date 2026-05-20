package com.lightxin.feature.about.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.settings.DeveloperPrefs
import com.lightxin.feature.update.data.UpdateRepository
import com.lightxin.feature.update.domain.UpdateResult
import com.lightxin.feature.update.download.ApkDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AboutUiState(
    val versionName: String = "",
    val advancedEnabled: Boolean = false,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
)

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class HasUpdate(val versionName: String) : UpdateCheckState
    data object AlreadyLatest : UpdateCheckState
    data object Failed : UpdateCheckState
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val developerPrefs: DeveloperPrefs,
    private val updateRepository: UpdateRepository,
    private val apkDownloader: ApkDownloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AboutUiState(versionName = readVersionName()))
    val uiState: StateFlow<AboutUiState> = _uiState

    init {
        viewModelScope.launch {
            developerPrefs.isAdvancedEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(advancedEnabled = enabled) }
            }
        }
        viewModelScope.launch { apkDownloader.checkPendingDownload() }
    }

    fun setAdvancedEnabled(enabled: Boolean) {
        viewModelScope.launch {
            developerPrefs.setAdvancedEnabled(enabled)
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.update { it.copy(updateCheckState = UpdateCheckState.Checking) }
            val result = updateRepository.checkForUpdate(force = true)
            val state = when (result) {
                is UpdateResult.HasUpdate -> UpdateCheckState.HasUpdate(result.info.versionName)
                is UpdateResult.AlreadyLatest -> UpdateCheckState.AlreadyLatest
                is UpdateResult.Failed -> UpdateCheckState.Failed
                is UpdateResult.Throttled -> UpdateCheckState.AlreadyLatest
            }
            _uiState.update { it.copy(updateCheckState = state) }
        }
    }

    fun downloadUpdate() {
        val state = _uiState.value.updateCheckState
        if (state !is UpdateCheckState.HasUpdate) return
        viewModelScope.launch {
            val url = updateRepository.getDownloadUrl()
            if (url.isNotBlank()) {
                apkDownloader.startDownload(url, state.versionName)
            }
        }
    }

    private fun readVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
