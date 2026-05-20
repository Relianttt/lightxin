package com.lightxin.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.settings.UpdatePrefs
import com.lightxin.feature.update.domain.AppVersion
import com.lightxin.feature.update.domain.AppVersionProvider
import com.lightxin.feature.update.download.ApkDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdatePromptState(
    val showDialog: Boolean = false,
    val versionName: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
)

@HiltViewModel
class UpdatePromptViewModel @Inject constructor(
    private val updatePrefs: UpdatePrefs,
    private val appVersionProvider: AppVersionProvider,
    private val apkDownloader: ApkDownloader,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdatePromptState())
    val state: StateFlow<UpdatePromptState> = _state

    private var dismissedThisSession = false

    init {
        viewModelScope.launch {
            combine(
                updatePrefs.latestVersion,
                updatePrefs.skippedVersion,
                updatePrefs.releaseNotes,
                updatePrefs.downloadUrl,
            ) { latest, skipped, notes, url ->
                UpdatePromptState(
                    showDialog = shouldShow(latest, skipped),
                    versionName = latest,
                    releaseNotes = notes,
                    downloadUrl = url,
                )
            }.collect { newState ->
                _state.update { newState.copy(showDialog = newState.showDialog && !dismissedThisSession) }
            }
        }
    }

    fun dismiss() {
        dismissedThisSession = true
        _state.update { it.copy(showDialog = false) }
    }

    fun skipVersion() {
        dismissedThisSession = true
        _state.update { it.copy(showDialog = false) }
        viewModelScope.launch { updatePrefs.setSkippedVersion(_state.value.versionName) }
    }

    fun downloadUpdate() {
        val url = _state.value.downloadUrl
        val version = _state.value.versionName
        if (url.isBlank()) return
        viewModelScope.launch {
            val started = apkDownloader.startDownload(url, version)
            if (started) {
                dismissedThisSession = true
                _state.update { it.copy(showDialog = false) }
            }
            // 未授权时不 dismiss，用户授权回来后弹窗仍在
        }
    }

    private fun shouldShow(latest: String, skipped: String): Boolean {
        if (latest.isBlank()) return false
        if (latest == skipped) return false
        val remote = AppVersion.parse(latest) ?: return false
        val local = appVersionProvider.appVersion ?: return false
        return remote > local
    }
}
