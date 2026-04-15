package com.lightxin.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.auth.SessionManager
import com.lightxin.core.auth.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userCode: String = "",
    val userName: String = "",
    val isLoggingOut: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager,
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
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            sessionManager.logout()
            onDone()
        }
    }
}
