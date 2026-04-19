package com.lightxin.feature.about.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.R
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxDialog
import com.lightxin.core.designsystem.component.LxDialogConfirmTone
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.NewsreaderDisplay

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "关于轻小信", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 28.dp),
        ) {
            BrandCard(versionName = uiState.versionName)

            Spacer(modifier = Modifier.height(22.dp))

            DeveloperToggleCard(
                enabled = uiState.advancedEnabled,
                onCheckedChange = { checked ->
                    if (checked && !uiState.advancedEnabled) {
                        showConfirmDialog = true
                    } else if (!checked) {
                        viewModel.setAdvancedEnabled(false)
                    }
                },
            )
        }
    }

    if (showConfirmDialog) {
        LxDialog(
            title = "启用调试功能",
            message = "开启后将显示\"模拟提交\"与\"路线模拟\"入口。此类功能仅供开发调试，生成的数据为模拟结果，不代表真实跑步记录，请勿用于日常考核。",
            confirmText = "确定",
            dismissText = "取消",
            onDismissRequest = { showConfirmDialog = false },
            onDismiss = { showConfirmDialog = false },
            onConfirm = {
                showConfirmDialog = false
                viewModel.setAdvancedEnabled(true)
            },
            confirmTone = LxDialogConfirmTone.Destructive,
        )
    }
}

@Composable
private fun BrandCard(versionName: String) {
    LxCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(80.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.splash_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .scale(1.45f),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "轻小信",
                fontFamily = NewsreaderDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                color = LxTerra,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "让校园生活更轻盈",
                fontSize = 13.sp,
                color = LxInkMuted,
            )
            if (versionName.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "v$versionName",
                    fontSize = 12.sp,
                    color = LxInkMuted,
                )
            }
        }
    }
}

@Composable
private fun DeveloperToggleCard(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    LxCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "启用调试功能",
                fontSize = 15.sp,
                color = LxInk,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = LxTerra,
                    uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                    uncheckedTrackColor = LxRose.copy(alpha = 0.18f),
                ),
            )
        }
    }
}
