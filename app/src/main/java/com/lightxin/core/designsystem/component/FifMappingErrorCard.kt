package com.lightxin.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.lightxin.core.network.FifMappingFailure
import com.lightxin.core.network.FifMappingReason
import com.lightxin.core.network.FifOperation

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
    var showDetails by rememberSaveable(technicalDetail) { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(LxSand).padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Outlined.CloudOff, contentDescription = null, tint = LxInkSoft,
            modifier = Modifier.size(76.dp).background(LxSandDeep.copy(alpha = 0.35f), CircleShape).padding(23.dp),
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = LxInk, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = LxInkSoft, textAlign = TextAlign.Center)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LxCard)) {
            MappingAction(Icons.Outlined.Refresh, if (isRetrying) "正在重新连接…" else "重新尝试连接",
                enabled = !isRetrying, onClick = onRetry)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LxSand)
            MappingAction(Icons.AutoMirrored.Outlined.Login, "重新登录", enabled = !isRetrying, onClick = onRelogin)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LxSand)
            MappingAction(Icons.Outlined.Info, "查看错误详情", chevron = true, onClick = { showDetails = true })
        }
        Text("你仍可以返回首页，尝试使用其他校园功能。", style = MaterialTheme.typography.bodySmall,
            color = LxInkSoft, textAlign = TextAlign.Center)
    }
    if (showDetails) {
        LxDialog(title = "错误详情", confirmText = "关闭", onConfirm = { showDetails = false },
            onDismissRequest = { showDetails = false },
            content = {
                Text(technicalDetail, color = LxInkSoft, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState()))
            })
    }
}

@Composable
private fun MappingAction(
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
