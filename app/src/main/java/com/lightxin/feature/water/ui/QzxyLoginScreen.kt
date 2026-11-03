package com.lightxin.feature.water.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightxin.core.designsystem.component.LxBottomBar
import com.lightxin.core.designsystem.component.LxFilterTabs
import com.lightxin.core.designsystem.component.LxIconButton
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPrimaryButton
import com.lightxin.core.designsystem.component.LxTabItem
import com.lightxin.core.designsystem.component.LxTextField
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.theme.LxAccent
import com.lightxin.core.designsystem.theme.LxAccentText
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxDanger
import com.lightxin.core.designsystem.theme.LxRadiusSm
import com.lightxin.core.designsystem.theme.LxSurface
import com.lightxin.core.designsystem.theme.LxTextFaint
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTint
import com.lightxin.core.designsystem.theme.lxPressable

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

    var passwordVisible by rememberSaveable { mutableStateOf(false) }

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
                Spacer(Modifier.height(20.dp))
            }
            item(key = "mode") {
                LxFilterTabs(
                    items = listOf(LxTabItem("密码"), LxTabItem("验证码")),
                    selectedIndex = if (uiState.mode == LoginMode.Sms) 1 else 0,
                    onSelect = { index -> viewModel.setMode(if (index == 1) LoginMode.Sms else LoginMode.Password) },
                    modifier = Modifier.fillMaxWidth(),
                    equalWidth = true,
                    itemHeight = 44.dp,
                )
            }
            item(key = "fields") {
                Column(
                    modifier = Modifier.padding(top = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Column {
                        LxTextField(
                            value = uiState.phone,
                            onValueChange = viewModel::onPhoneChange,
                            label = "手机号",
                            containerColor = LxSurface,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.onFocusChanged { focus -> if (!focus.hasFocus) viewModel.validatePhone() },
                        )
                        uiState.phoneError?.let { error ->
                            Text(
                                text = error,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = LxDanger,
                                modifier = Modifier.padding(start = 12.dp, top = 6.dp),
                            )
                        }
                    }
                    when (uiState.mode) {
                        LoginMode.Password -> LxTextField(
                            value = uiState.password,
                            onValueChange = viewModel::onPasswordChange,
                            label = "密码",
                            containerColor = LxSurface,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                LxIconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Outlined.VisibilityOff
                                        } else {
                                            Icons.Outlined.Visibility
                                        },
                                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                        tint = LxTextMuted,
                                    )
                                }
                            },
                        )

                        // 验证码不用 NumberPassword：部分系统（Flyme）聚焦密码类输入框时会禁止截屏
                        LoginMode.Sms -> LxTextField(
                            value = uiState.smsCode,
                            onValueChange = viewModel::onSmsCodeChange,
                            label = "验证码",
                            containerColor = LxSurface,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            trailingIcon = {
                                SmsCodeChip(
                                    text = if (uiState.countdown > 0) "${uiState.countdown}s" else "获取验证码",
                                    onClick = viewModel::sendSmsCode,
                                    enabled = uiState.phoneValid && uiState.countdown == 0 && !uiState.sending,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 输入框内的「获取验证码」：浅底色块，和输入内容分开。 */
@Composable
private fun SmsCodeChip(text: String, onClick: () -> Unit, enabled: Boolean) {
    val shape = RoundedCornerShape(LxRadiusSm)
    Box(
        modifier = Modifier
            .padding(end = 2.dp)
            .height(40.dp)
            .clip(shape)
            .background(if (enabled) LxAccent.copy(alpha = 0.12f) else LxTint)
            .lxPressable(enabled = enabled, onClick = onClick, shape = shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) LxAccentText else LxTextFaint,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}
