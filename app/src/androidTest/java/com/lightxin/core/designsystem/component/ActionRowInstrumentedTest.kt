package com.lightxin.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.test.assertHasClickAction
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
class ActionRowInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actionRowExposesLabelAndInvokesActionOnce() {
        var clicks = 0
        compose.setContent {
            LightXinTheme {
                LxGroupCard {
                    LxActionRow(Icons.Outlined.Edit, "重命名", onClick = { clicks++ })
                }
            }
        }
        compose.onNodeWithText("重命名").assertIsDisplayed().assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
