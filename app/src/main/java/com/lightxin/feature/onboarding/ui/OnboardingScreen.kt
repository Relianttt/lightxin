package com.lightxin.feature.onboarding.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxSecondaryButton
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.NewsreaderLarge
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LxCream)
            .statusBarsPadding(),
    ) {
        OnboardingIllustration(modifier = Modifier.fillMaxWidth())

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 32.dp),
        ) {
            StaggerItem(index = 0) {
                Text(
                    text = "更轻量，更舒适的校园体验",
                    fontFamily = NewsreaderLarge,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                    lineHeight = 29.sp,
                    letterSpacing = (-0.2).sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            StaggerItem(index = 1) {
                Text(
                    text = "轻小信基于安小信重新设计，去掉繁琐，保留核心。整合课程、运动与宿舍提醒，让校园日常更自然、更从容地流动。",
                    fontSize = 13.5.sp,
                    lineHeight = 24.sp,
                    color = LxInkMuted,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            StaggerItem(index = 2) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LxButton(text = "我已知晓", onClick = onAcknowledge)
                    LxSecondaryButton(text = "暂不进入", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun StaggerItem(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80L + index * 90L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 4 },
    ) {
        content()
    }
}
