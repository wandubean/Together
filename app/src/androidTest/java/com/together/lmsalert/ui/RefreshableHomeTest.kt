package com.together.lmsalert.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RefreshableHomeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pullReleaseRefreshesOnceAndReturnsAfterCompletion() {
        val refreshing = mutableStateOf(false)
        var refreshes = 0
        compose.setContent {
            MaterialTheme {
                RefreshableHome(refreshing.value, { refreshes++; refreshing.value = true }) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(30) { Text("항목 $it", Modifier.height(64.dp)) }
                    }
                }
            }
        }
        val initialTop = compose.onNodeWithTag("home_content").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("home_refresh").performTouchInput {
            down(Offset(centerX, height * 0.2f))
            moveTo(Offset(centerX, height * 0.8f), delayMillis = 500)
        }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("home_content").fetchSemanticsNode().boundsInRoot.top > initialTop)
        compose.runOnIdle { assertEquals(0, refreshes) }
        compose.onNodeWithTag("home_refresh").performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, refreshes) }
        assertEquals(initialTop, compose.onNodeWithTag("home_content").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("home_refresh").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, refreshes); refreshing.value = false }
        compose.waitForIdle()
        assertEquals(initialTop, compose.onNodeWithTag("home_content").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test
    fun smallPullReturnsWithoutRefreshEvenWhenListIsEmpty() {
        var refreshes = 0
        compose.setContent {
            MaterialTheme {
                RefreshableHome(false, { refreshes++ }) {
                    LazyColumn(Modifier.fillMaxSize()) { item { Text("등록된 과제가 없어요") } }
                }
            }
        }
        val initialTop = compose.onNodeWithTag("home_content").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("home_refresh").performTouchInput {
            swipe(Offset(centerX, 100f), Offset(centerX, 140f), 500)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, refreshes) }
        assertEquals(initialTop, compose.onNodeWithTag("home_content").fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
