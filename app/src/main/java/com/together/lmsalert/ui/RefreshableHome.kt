package com.together.lmsalert.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefreshableHome(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    var returningToRest by remember { mutableStateOf(false) }
    LaunchedEffect(state.distanceFraction, isRefreshing) {
        if (!isRefreshing && state.distanceFraction == 0f) returningToRest = false
    }
    val distance by animateFloatAsState(
        targetValue = if (isRefreshing || returningToRest) 0f else state.distanceFraction.coerceAtMost(1.5f),
        animationSpec = if (isRefreshing || returningToRest) tween(220) else snap(),
        label = "homePullReturn",
    )
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing && !returningToRest) {
                returningToRest = true
                onRefresh()
            }
        },
        state = state,
        modifier = modifier.fillMaxSize().clipToBounds().testTag("home_refresh"),
    ) {
        Box(Modifier.fillMaxSize().offset {
            IntOffset(0, (distance * 80.dp.toPx()).roundToInt())
        }.testTag("home_content")) {
            content()
        }
    }
}
