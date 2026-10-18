package com.lightxin.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightxin.core.designsystem.theme.LightXinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class TabPagerInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var pager: PagerState
    private lateinit var firstList: LazyListState
    private lateinit var secondList: LazyListState

    private fun showPager() {
        compose.setContent {
            LightXinTheme {
                pager = rememberLxTabPagerState()
                firstList = rememberLazyListState()
                secondList = rememberLazyListState()
                val select = rememberLxTabPagerSelection(pager)
                Column(Modifier.fillMaxSize()) {
                    // 与三个列表页一致：tab 行在 pager 外，切页时不跟内容一起移动。
                    LxFilterTabs(
                        items = listOf(LxTabItem("First"), LxTabItem("Second")),
                        selectedIndex = pager.currentPage,
                        indicatorFraction = pager.currentPage + pager.currentPageOffsetFraction,
                        onSelect = select,
                    )
                    LxTabPager(pager, Modifier.fillMaxSize().testTag("pager")) { page ->
                        LazyColumn(
                            state = if (page == 0) firstList else secondList,
                            modifier = Modifier.fillMaxSize().testTag("list-$page"),
                        ) {
                            items(100, key = { it }) { Text("Row $it", Modifier.padding(24.dp)) }
                        }
                    }
                }
            }
        }
    }

    @Test fun contentFollowsFingerBeforeReleaseAndShortDragReturnsWithoutBounce() {
        showPager()
        compose.onNodeWithTag("pager").performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.3f, 0f), delayMillis = 300)
        }
        compose.runOnIdle {
            assertEquals(0, pager.currentPage)
            assertTrue(abs(pager.currentPageOffsetFraction) > 0.1f)
        }
        compose.onNodeWithTag("pager").performTouchInput {
            advanceEventTime(400)
            up()
        }
        compose.runOnIdle { assertEquals(0, pager.settledPage) }
    }

    @Test fun clicksAndSwipesNavigateBothDirectionsAndRespectEdges() {
        showPager()
        compose.onNodeWithText("Second").performClick()
        compose.runOnIdle { assertEquals(1, pager.settledPage) }
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, pager.settledPage) }
        compose.onNodeWithTag("pager").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(0, pager.settledPage) }
        compose.onNodeWithTag("pager").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(0, pager.settledPage) }
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, pager.settledPage) }
    }

    @Test fun verticalScrollDoesNotSwitchTabsAndEachPageRetainsPosition() {
        showPager()
        compose.onNodeWithTag("list-0").performTouchInput { swipeUp() }
        var index = 0
        var offset = 0
        compose.runOnIdle {
            index = firstList.firstVisibleItemIndex
            offset = firstList.firstVisibleItemScrollOffset
            assertTrue(index > 0)
            assertEquals(0, pager.settledPage)
        }
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("list-1").performTouchInput { swipeUp() }
        compose.onNodeWithTag("pager").performTouchInput { swipeRight() }
        compose.runOnIdle {
            assertEquals(index, firstList.firstVisibleItemIndex)
            assertEquals(offset, firstList.firstVisibleItemScrollOffset)
            assertTrue(secondList.firstVisibleItemIndex > 0)
        }
    }
}
