package com.lightxin.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lightxin.core.designsystem.component.LxSessionBanner
import com.lightxin.core.designsystem.theme.LightXinTheme

@Composable
fun MainSessionExpiredBanner(onRelogin: () -> Unit, modifier: Modifier = Modifier) {
    LxSessionBanner(
        title = "登录状态已失效",
        body = "部分功能已失效，重新登录后恢复",
        onAction = onRelogin,
        modifier = modifier,
    )
}

@Preview(showBackground = true, widthDp = 360)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.6f)
@Preview(showBackground = true, widthDp = 360, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MainSessionExpiredBannerPreview() {
    LightXinTheme { MainSessionExpiredBanner({}, Modifier.padding(24.dp)) }
}
