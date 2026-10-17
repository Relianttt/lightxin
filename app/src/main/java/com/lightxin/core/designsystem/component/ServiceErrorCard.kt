package com.lightxin.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lightxin.core.designsystem.theme.LightXinTheme
import com.lightxin.core.designsystem.theme.LxCard
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkSoft
import com.lightxin.core.designsystem.theme.LxSand
import com.lightxin.core.designsystem.theme.LxSandDeep

/**
 * 「服务不可用」卡片模板：一句标题 + 一段人话解释 + 三个动作（重试 / 重新登录 / 查看详情）。
 *
 * 起因是 AI课堂的映射失败卡片（[FifMappingErrorCard]）好用，各模块都想套同一版式，
 * 只是文案不同。**版式只在这一处**，各模块只提供措辞，别复制第二份：
 * 复制出去之后任何一次视觉调整都会漏掉一半入口。
 *
 * 文案要求：说清「发生了什么 / 是不是他的错 / 现在能做什么」，不要把服务端原文
 * （例如「非法访问」「-100」）直接当正文 —— 用户看不懂，也不知道要不要重登。
 */
@Composable
fun ServiceErrorCard(
    title: String,
    message: String,
    technicalDetail: String,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onRelogin: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "重新尝试连接",
    retryingLabel: String = "正在重新连接…",
    reloginLabel: String = "重新登录",
    detailDialogTitle: String = "错误详情",
    footer: String? = "你仍可以返回首页，尝试使用其他校园功能。",
    icon: ImageVector = Icons.Outlined.CloudOff,
) {
    var showDetails by rememberSaveable(technicalDetail) { mutableStateOf(false) }
    Column (
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(LxSand).padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon, contentDescription = null, tint = LxInkSoft,
            modifier = Modifier.size(76.dp).background(LxSandDeep.copy(alpha = 0.35f), CircleShape).padding(23.dp),
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = LxInk, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = LxInkSoft, textAlign = TextAlign.Center)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LxCard)) {
            RecoveryAction(
                icon = Icons.Outlined.Refresh,
                label = if (isRetrying) retryingLabel else retryLabel,
                enabled = !isRetrying,
                onClick = onRetry,
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LxSand)
            RecoveryAction(
                icon = Icons.AutoMirrored.Outlined.Login,
                label = reloginLabel,
                enabled = !isRetrying,
                onClick = onRelogin,
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LxSand)
            RecoveryAction(
                icon = Icons.Outlined.Info,
                label = "查看错误详情",
                chevron = true,
                onClick = { showDetails = true },
            )
        }
        if (footer != null) {
            Text(footer, style = MaterialTheme.typography.bodySmall,
                color = LxInkSoft, textAlign = TextAlign.Center)
        }
    }
    if (showDetails) {
        LxDialog(title = detailDialogTitle, confirmText = "关闭", onConfirm = { showDetails = false },
            onDismissRequest = { showDetails = false },
            content = {
                Text(technicalDetail, color = LxInkSoft, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState()))
            })
    }
}

@Composable
private fun RecoveryAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!enabled && icon == Icons.Outlined.Refresh) LxProgressIndicator(modifier = Modifier.size(22.dp))
        else Icon(icon, contentDescription = null, tint = LxInkSoft, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = LxInk, modifier = Modifier.weight(1f))
        if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = LxInkSoft)
    }
}

@Preview(showBackground = true, widthDp = 360)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.6f)
@Preview(showBackground = true, widthDp = 360, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ServiceErrorCardPreview() {
    LightXinTheme {
        ServiceErrorCard(
            title = "暂时无法加载查寝信息",
            message = "服务端这次没有认可你的身份信息，常见的两个原因是登录已过期、或只是这一下没对上。",
            technicalDetail = "CHECKIN_REJECTED\n操作：加载查寝列表",
            isRetrying = false,
            onRetry = {},
            onRelogin = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
