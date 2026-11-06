@file:OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)

package com.rassvet.essential.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun ChatSharedTransitionLayout(
    modifier: Modifier = Modifier,
    content: @Composable SharedTransitionScope.() -> Unit,
) {
    SharedTransitionLayout(modifier = modifier, content = content)
}

@Composable
fun SharedTransitionScope.ChatUserMessageEnter(
    messageId: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember(messageId) { mutableStateOf(false) }
    LaunchedEffect(messageId) { visible = true }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                slideInVertically(
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                ) { fullHeight -> fullHeight / 3 } +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                ),
    ) {
        content()
    }
}

@Composable
fun ChatAssistantMessageEnter(
    messageId: Long,
    animate: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!animate) {
        Box(modifier = modifier) { content() }
        return
    }
    var visible by remember(messageId) { mutableStateOf(false) }
    LaunchedEffect(messageId) { visible = true }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            fadeIn(tween(durationMillis = 320, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                ) { fullHeight -> fullHeight / 10 },
    ) {
        content()
    }
}


