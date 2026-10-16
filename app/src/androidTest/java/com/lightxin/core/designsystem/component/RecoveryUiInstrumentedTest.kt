package com.lightxin.core.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightxin.core.designsystem.theme.LightXinTheme
import com.lightxin.core.network.FifMappingFailure
import com.lightxin.core.network.FifMappingReason
import com.lightxin.core.network.FifOperation
import com.lightxin.feature.home.ui.MainSessionExpiredBanner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mappingCardActionsAndDetailsAreExplicit() {
        var retries = 0
        var logins = 0
        val failure = FifMappingFailure(FifMappingReason.ACCOUNT_INFO, FifOperation.HOME)
        compose.setContent {
            LightXinTheme { FifMappingErrorPage(failure, false, { retries++ }, { logins++ }) }
        }
        compose.onNodeWithText("暂时无法连接 AI 课堂").assertIsDisplayed()
        compose.onNodeWithText(failure.technicalDetail).assertDoesNotExist()
        compose.onNodeWithText("重新尝试连接").performScrollTo().performClick()
        compose.onNodeWithText("重新登录").performScrollTo().performClick()
        compose.onNodeWithText("查看错误详情").performScrollTo().performClick()
        compose.onNodeWithText(failure.technicalDetail).assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(1, logins) }
    }

    @Test fun retryDisablesMutatingActionsAndSupportsLargeTypeInDarkMode() {
        val failure = FifMappingFailure(FifMappingReason.TIMEOUT, FifOperation.QR_SIGN)
        compose.setContent {
            val density = LocalDensity.current.density
            val configuration = android.content.res.Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            CompositionLocalProvider(
                LocalDensity provides Density(density, 1.6f),
                LocalConfiguration provides configuration,
            ) {
                LightXinTheme(darkTheme = true) { FifMappingErrorPage(failure, true, {}, {}) }
            }
        }
        compose.onNodeWithText("正在重新连接…").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("重新登录").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("查看错误详情").performScrollTo().performClick()
        compose.onNodeWithText(failure.technicalDetail).assertIsDisplayed()
    }

    @Test fun bannerRendersAndDelegatesLoginWithoutAnAuthRequest() {
        var clicks = 0
        compose.setContent { LightXinTheme { MainSessionExpiredBanner({ clicks++ }) } }
        compose.onNodeWithText("登录状态已失效").assertIsDisplayed()
        compose.onNodeWithText("重新登录").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
