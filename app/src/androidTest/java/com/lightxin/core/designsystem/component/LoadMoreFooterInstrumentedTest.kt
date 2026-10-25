package com.lightxin.core.designsystem.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightxin.core.designsystem.theme.LightXinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoadMoreFooterInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedPageOffersExplicitRetryWithoutShowingServerText() {
        var retries = 0
        compose.setContent {
            LightXinTheme {
                LxLoadMoreFooter(false, true, "raw server error", { retries++ }, true)
            }
        }
        compose.onNodeWithText("加载失败，点此重试").assertIsDisplayed().performClick()
        compose.onNodeWithText("raw server error").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun loadingTransitionsToEndText() {
        val loading = mutableStateOf(true)
        compose.setContent {
            LightXinTheme { LxLoadMoreFooter(loading.value, false, null, {}, true) }
        }
        compose.onNodeWithText("正在加载…").assertIsDisplayed()
        compose.onNodeWithText("没有更多了").assertDoesNotExist()
        compose.runOnIdle { loading.value = false }
        compose.onNodeWithText("正在加载…").assertDoesNotExist()
        compose.onNodeWithText("没有更多了").assertIsDisplayed()
    }

    @Test fun shortListsDoNotShowEndText() {
        compose.setContent {
            LightXinTheme { LxLoadMoreFooter(false, false, null, {}, false) }
        }
        compose.onNodeWithText("没有更多了").assertDoesNotExist()
        compose.onNodeWithText("正在加载…").assertDoesNotExist()
    }
}
