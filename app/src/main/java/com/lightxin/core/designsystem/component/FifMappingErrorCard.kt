package com.lightxin.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lightxin.core.designsystem.theme.LightXinTheme
import com.lightxin.core.network.FifMappingFailure
import com.lightxin.core.network.FifMappingReason
import com.lightxin.core.network.FifOperation

/**
 * AI课堂的映射失败卡片。版式来自 [ServiceErrorCard]，这里只提供文案。
 *
 * 文案与动作保持不变：`RecoveryUiInstrumentedTest` 依赖这些字符串与 3 个动作的语义。
 */
@Composable
fun FifMappingErrorCard(
    message: String,
    technicalDetail: String,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onRelogin: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "暂时无法连接 AI 课堂",
) {
    ServiceErrorCard(
        title = title,
        message = message,
        technicalDetail = technicalDetail,
        isRetrying = isRetrying,
        onRetry = onRetry,
        onRelogin = onRelogin,
        modifier = modifier,
        retryLabel = "重新尝试连接",
        retryingLabel = "正在重新连接…",
        reloginLabel = "重新登录",
        detailDialogTitle = "错误详情",
        footer = "你仍可以返回首页，尝试使用其他校园功能。",
    )
}

@Composable
fun FifMappingErrorPage(
    failure: FifMappingFailure,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onRelogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        FifMappingErrorCard(failure.message, failure.technicalDetail, isRetrying, onRetry, onRelogin)
        Spacer(Modifier.height(16.dp))
    }
}

@Preview(showBackground = true, widthDp = 360)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.6f)
@Preview(showBackground = true, widthDp = 360, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FifMappingErrorPreview() {
    LightXinTheme {
        val failure = FifMappingFailure(FifMappingReason.ACCOUNT_INFO, FifOperation.HOME)
        FifMappingErrorCard(failure.message, failure.technicalDetail, false, {}, {}, Modifier.padding(16.dp))
    }
}
