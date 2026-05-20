package com.lightxin.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.auth.SessionManager
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.settings.DeveloperPrefs
import com.lightxin.core.settings.UpdatePrefs
import com.lightxin.feature.update.domain.AppVersion
import com.lightxin.feature.update.domain.AppVersionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userCode: String = "",
    val userName: String = "",
    val isLoggingOut: Boolean = false,
    val advancedEnabled: Boolean = false,
    val updateHint: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager,
    private val developerPrefs: DeveloperPrefs,
    private val updatePrefs: UpdatePrefs,
    private val appVersionProvider: AppVersionProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    userCode = tokenManager.getUserCode().orEmpty(),
                    userName = tokenManager.getUserName().orEmpty(),
                )
            }
        }
        viewModelScope.launch {
            developerPrefs.isAdvancedEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(advancedEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                updatePrefs.latestVersion,
                updatePrefs.skippedVersion,
            ) { latest, skipped -> latest to skipped }
                .collectLatest { (latest, skipped) ->
                    val hint = computeUpdateHint(latest, skipped)
                    _uiState.update { it.copy(updateHint = hint) }
                }
        }
    }

    private fun computeUpdateHint(latest: String, skipped: String): String? {
        if (latest.isBlank()) return null
        val remoteVersion = AppVersion.parse(latest) ?: return null
        val localVersion = appVersionProvider.appVersion ?: return null
        if (remoteVersion <= localVersion) return null
        if (latest == skipped) return null
        return "发现新版本 v$latest"
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            sessionManager.logout()
            onDone()
        }
    }
}
