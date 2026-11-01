package com.lightxin.feature.water.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightxin.core.designsystem.component.LxBottomBar
import com.lightxin.core.designsystem.component.LxChoiceGroup
import com.lightxin.core.designsystem.component.LxField
import com.lightxin.core.designsystem.component.LxGhostButton
import com.lightxin.core.designsystem.component.LxInput
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPrimaryButton
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.theme.LxCanvas

@Composable
fun QzxyLoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QzxyLoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                WaterEvent.LoginSuccess -> onLoggedIn()
                WaterEvent.RequireLogin -> onBack()
                is WaterEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val submit = {
        if (uiState.canSubmit && !uiState.loading) viewModel.login()
    }

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(
                onBack = onBack,
                title = "登录趣智校园",
                collapsed = listState.lxHeaderCollapsed(),
            )
        },
        bottomBar = {
            LxBottomBar(Modifier.imePadding()) {
                LxPrimaryButton(
                    text = if (uiState.loading) "登录中…" else "登录",
                    onClick = submit,
                    enabled = uiState.canSubmit && !uiState.loading,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
        ) {
            item(key = "header") {
                LxPageHeader(title = "登录趣智校园")
                Spacer(Modifier.height(4.dp))
            }
            item(key = "mode") {
                Spacer(Modifier.height(16.dp))
                LxChoiceGroup(
                    options = listOf("密码" to "password", "验证码" to "sms"),
                    selectedValue = when (uiState.mode) {
                        LoginMode.Password -> "password"
                        LoginMode.Sms -> "sms"
                    },
                    onSelect = { value ->
                        viewModel.setMode(if (value == "sms") LoginMode.Sms else LoginMode.Password)
                    },
                )
                Spacer(Modifier.height(16.dp))
            }
            item(key = "phone") {
                LxField(label = "手机号", error = uiState.phoneError) {
                    LxInput(
                        value = uiState.phone,
                        onValueChange = viewModel::onPhoneChange,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.onFocusChanged { focus -> if (!focus.isFocused) viewModel.validatePhone() },
                    )
                }
            }
            item(key = "secret") {
                when (uiState.mode) {
                    LoginMode.Password -> LxField(label = "密码") {
                        LxInput(
                            value = uiState.password,
                            onValueChange = viewModel::onPasswordChange,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }

                    LoginMode.Sms -> LxField(label = "验证码") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LxInput(
                                value = uiState.smsCode,
                                onValueChange = viewModel::onSmsCodeChange,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { submit() }),
                            )
                            Spacer(Modifier.width(8.dp))
                            LxGhostButton(
                                text = if (uiState.countdown > 0) "${uiState.countdown}s" else "获取验证码",
                                onClick = viewModel::sendSmsCode,
                                modifier = Modifier.width(120.dp),
                                enabled = uiState.phoneValid && uiState.countdown == 0 && !uiState.sending,
                            )
                        }
                    }
                }
            }
        }
    }
}
