package com.lightxin.feature.login.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.auth.RememberedLoginStore
import com.lightxin.feature.login.data.LoginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val userCode: String = "",
    val password: String = "",
    val rememberPassword: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginRepository: LoginRepository,
    private val rememberedLoginStore: RememberedLoginStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        restoreRemembered()
    }

    /** 预填上次的学号；勾过「记住密码」且能解密时连密码一起填上。 */
    private fun restoreRemembered() {
        viewModelScope.launch {
            val userCode = rememberedLoginStore.loadUserCode()
            val password = rememberedLoginStore.loadPassword()
            _uiState.update {
                it.copy(
                    userCode = if (it.userCode.isBlank()) userCode else it.userCode,
                    password = password.orEmpty(),
                    rememberPassword = password != null,
                )
            }
        }
    }

    fun onUserCodeChange(value: String) {
        _uiState.update { it.copy(userCode = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun onRememberPasswordChange(value: Boolean) {
        _uiState.update { it.copy(rememberPassword = value, error = null) }
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
                    // 学号总是记住；密码只在勾选时经 Keystore 加密落盘
                    rememberedLoginStore.save(
                        userCode = state.userCode,
                        password = state.password,
                        remember = state.rememberPassword,
                    )
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
