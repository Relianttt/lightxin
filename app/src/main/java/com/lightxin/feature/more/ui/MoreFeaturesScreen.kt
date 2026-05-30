package com.lightxin.feature.more.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Grading
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.WorkHistory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkGhost
import com.lightxin.core.designsystem.theme.LxInkSoft
import com.lightxin.core.designsystem.theme.LxSand

@Composable
fun MoreFeaturesScreen(
    onBack: () -> Unit,
    onNavigateLabor: () -> Unit,
    onNavigateExam: () -> Unit,
    onNavigateCredit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(MORE_PREFS_NAME, Context.MODE_PRIVATE) }
    var liveNotificationEnabled by remember {
        mutableStateOf(prefs.getBoolean(KEY_LIVE_NOTIFICATION_ENABLED, true))
    }
    var permissionRefreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exactAlarmGranted = permissionRefreshTick.let { context.canScheduleExactAlarmsCompat() }
    val batteryOptimizationIgnored = permissionRefreshTick.let { context.isIgnoringBatteryOptimizationsCompat() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "更多功能", onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 16.dp)) {
            LxCard {
                Column {
                    MoreMenuRow(
                        icon = Icons.Outlined.WorkHistory,
                        title = "劳动教育",
                        onClick = onNavigateLabor,
                    )
                    MoreMenuDivider()
                    MoreMenuRow(
                        icon = Icons.Outlined.Grading,
                        title = "考试成绩",
                        onClick = onNavigateExam,
                    )
                    MoreMenuDivider()
                    MoreMenuRow(
                        icon = Icons.Outlined.EmojiEvents,
                        title = "素质学分",
                        onClick = onNavigateCredit,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            LxCard {
                Column {
                    LiveNotificationRow(
                        enabled = liveNotificationEnabled,
                        onCheckedChange = { enabled ->
                            liveNotificationEnabled = enabled
                            prefs.edit().putBoolean(KEY_LIVE_NOTIFICATION_ENABLED, enabled).apply()
                        },
                    )
                    if (liveNotificationEnabled) {
                        MoreMenuDivider()
                        MoreMenuRow(
                            icon = Icons.Outlined.Alarm,
                            title = "精确闹钟权限",
                            trailing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (exactAlarmGranted) "已授权" else "未授权"
                            } else {
                                "无需授权"
                            },
                            trailingColor = if (exactAlarmGranted) LxInkGhost else MaterialTheme.colorScheme.error,
                            onClick = { context.openExactAlarmSettings() },
                        )
                        MoreMenuDivider()
                        MoreMenuRow(
                            icon = Icons.Outlined.BatterySaver,
                            title = "电池优化白名单",
                            trailing = if (batteryOptimizationIgnored) "已加入" else "未加入",
                            trailingColor = if (batteryOptimizationIgnored) LxInkGhost else MaterialTheme.colorScheme.error,
                            onClick = { context.openBatteryOptimizationSettings(batteryOptimizationIgnored) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveNotificationRow(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.NotificationsActive,
            contentDescription = null,
            tint = LxInkSoft,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "实况通知",
                fontSize = 15.sp,
                color = LxInk,
            )
            Text(
                text = "为保障实况通知正常发送，请授予精确闹钟权限和电池优化白名单，该功能不会增加耗电量",
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = LxInkSoft,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun MoreMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    trailing: String? = null,
    trailingColor: Color = LxInkGhost,
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
        if (trailing != null) {
            Text(
                text = trailing,
                fontSize = 13.sp,
                color = trailingColor,
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
private fun MoreMenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 54.dp, end = 18.dp)
            .height(1.dp)
            .background(LxSand),
    )
}

private const val MORE_PREFS_NAME = "app_prefs"
private const val KEY_LIVE_NOTIFICATION_ENABLED = "live_notification_enabled"

private fun Context.canScheduleExactAlarmsCompat(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
}

private fun Context.isIgnoringBatteryOptimizationsCompat(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        },
    )
}

private fun Context.openBatteryOptimizationSettings(alreadyIgnored: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val intent = if (alreadyIgnored) {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    } else {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
    }
    startActivity(intent.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
}
