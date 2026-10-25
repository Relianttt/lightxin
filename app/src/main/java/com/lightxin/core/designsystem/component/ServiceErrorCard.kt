package com.lightxin.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextFaint
import com.lightxin.core.designsystem.theme.LxTextMuted

enum class RecoveryPrimary { Retry, Relogin }

/** 恢复操作共用版式，服务端原文只放进可展开的错误详情。 */
@Composable
fun ServiceErrorCard(
    title: String,
    message: String,
    technicalDetail: String,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onRelogin: () -> Unit,
    modifier: Modifier = Modifier,
    primary: RecoveryPrimary = RecoveryPrimary.Retry,
) {
    var showDetails by rememberSaveable(technicalDetail) { mutableStateOf(false) }
    val retryText = if (isRetrying) "正在重试…" else "重试"
    val reloginFirst = primary == RecoveryPrimary.Relogin
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LxText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 13.5.sp, lineHeight = 22.sp, color = LxTextMuted, textAlign = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.height(24.dp))
        LxPrimaryButton(
            text = if (reloginFirst) "重新登录" else retryText,
            onClick = if (reloginFirst) onRelogin else onRetry,
            enabled = !isRetrying,
            modifier = Modifier.fillMaxWidth(0.72f),
        )
        Spacer(Modifier.height(8.dp))
        LxTextAction(
            text = if (reloginFirst) retryText else "重新登录",
            onClick = if (reloginFirst) onRetry else onRelogin,
            enabled = !isRetrying,
        )
        Spacer(Modifier.height(4.dp))
        LxTextAction("查看错误详情", onClick = { showDetails = true }, color = LxTextFaint)
    }
    if (showDetails) {
        LxDialog(
            title = "错误详情",
            confirmText = "关闭",
            onConfirm = { showDetails = false },
            onDismissRequest = { showDetails = false },
        ) {
            Text(
                technicalDetail,
                color = LxTextMuted,
                fontSize = 13.5.sp,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }
    }
}
