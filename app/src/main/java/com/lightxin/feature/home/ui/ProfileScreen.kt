package com.lightxin.feature.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WorkHistory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkGhost
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxInkSoft
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxSand
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.NewsreaderDisplay
import com.lightxin.core.designsystem.theme.NewsreaderLarge

@Composable
fun ProfileScreen(
    onNavigateCheckin: () -> Unit,
    onNavigateLabor: () -> Unit,
    onNavigateAiClass: () -> Unit,
    onNavigateRouteSimulation: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 28.dp), // 状态栏 ↔ 页标题 24dp
    ) {
        // ── 顶部标题（陶土衬线；本屏陶土色用法 1/2，头像字色用法 2/2）──
        Text(
            text = "我的",
            fontFamily = NewsreaderDisplay,
            fontWeight = FontWeight.Medium,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.3).sp,
            color = LxTerra,
            modifier = Modifier.padding(start = 4.dp),
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ── 用户信息卡 ──
        LxCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarRing(userName = uiState.userName)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = uiState.userName.ifBlank { "未知用户" },
                        fontFamily = NewsreaderLarge,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        color = LxInk,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = uiState.userCode.ifBlank { "---" },
                        fontSize = 13.sp,
                        color = LxInkMuted,
                    )
                }
            }
        }

        // 两个功能组之间 6dp 额外 spacer（合计 22dp，自然分组，无需 section label）
        Spacer(modifier = Modifier.height(22.dp))

        // ── 功能列表（单卡多行） ──
        LxCard {
            Column {
                ProfileMenuRow(
                    icon = Icons.Outlined.Bed,
                    title = "查寝签到",
                    onClick = onNavigateCheckin,
                )
                MenuDivider()
                ProfileMenuRow(
                    icon = Icons.Outlined.WorkHistory,
                    title = "劳动教育",
                    onClick = onNavigateLabor,
                )
                MenuDivider()
                ProfileMenuRow(
                    icon = Icons.Outlined.School,
                    title = "AI课堂",
                    onClick = onNavigateAiClass,
                )
                MenuDivider()
                ProfileMenuRow(
                    icon = Icons.Outlined.Route,
                    title = "路线模拟",
                    onClick = onNavigateRouteSimulation,
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ── 其他组 ──
        LxCard {
            ProfileMenuRow(
                icon = Icons.Outlined.Info,
                title = "关于轻小信",
                hint = "v1.0.0",
                onClick = { /* 关于页暂未实现，按住占位 */ },
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── 退出登录（陶土轮廓按钮，与登录的"实心陶土"做视觉区分）──
        LogoutOutlinedButton(
            isLoggingOut = uiState.isLoggingOut,
            onClick = { showLogoutDialog = true },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("退出登录") },
            text = { Text("确定要退出登录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout(onLogout)
                }) {
                    Text("确定", color = LxRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

// ═══════════════ 子组件 ═══════════════

@Composable
private fun AvatarRing(userName: String) {
    val char = userName.firstOrNull()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(LxSand),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = char,
            fontFamily = NewsreaderLarge,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            color = LxTerra,
        )
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bg = if (isPressed) LxCream else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 22×22 墨灰线性图标 — 取消分类底色，仅卡头"分类"语义由课表块色承担
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LxInkSoft,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = title,
            fontSize = 15.sp,
            color = LxInk,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 13.sp,
                color = LxInkMuted,
            )
        }
        Text(
            text = "›",
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
            color = LxInkGhost,
        )
    }
}

@Composable
private fun MenuDivider() {
    // 左 54dp（与图标+间距对齐）起至右 18dp 止
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 54.dp, end = 18.dp)
            .height(1.dp)
            .background(LxSand),
    )
}

@Composable
private fun LogoutOutlinedButton(isLoggingOut: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // 按压浅填充陶土 soft，轻微反馈
    val bg = if (isPressed) LxTerra.copy(alpha = 0.06f) else Color.Transparent
    val shape = RoundedCornerShape(16.dp)
    val borderStroke = BorderStroke(1.dp, LxTerra.copy(alpha = 0.55f))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(bg)
            .border(borderStroke, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isLoggingOut,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isLoggingOut) "退出中..." else "退出登录",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = LxTerra,
        )
    }
}
