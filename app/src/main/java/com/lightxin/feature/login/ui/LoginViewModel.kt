package com.lightxin.feature.login.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.feature.login.data.LoginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.loginPrefs by preferencesDataStore(name = "lightxin_login")
private val KEY_SAVED_USER_CODE = stringPreferencesKey("saved_user_code")

data class LoginUiState(
    val userCode: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loginRepository: LoginRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        loadSavedUserCode()
    }

    private fun loadSavedUserCode() {
        viewModelScope.launch {
            val saved = context.loginPrefs.data.first()[KEY_SAVED_USER_CODE] ?: ""
            _uiState.update { it.copy(userCode = saved) }
        }
    }

    fun onUserCodeChange(value: String) {
        _uiState.update { it.copy(userCode = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.userCode.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "请输入学号和密码") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = loginRepository.login(state.userCode, state.password)

            result.fold(
                onSuccess = {
                    // 保存学号
                    context.loginPrefs.edit { prefs ->
                        prefs[KEY_SAVED_USER_CODE] = state.userCode
                    }
                    _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "登录失败")
                    }
                },
            )
        }
    }
}
