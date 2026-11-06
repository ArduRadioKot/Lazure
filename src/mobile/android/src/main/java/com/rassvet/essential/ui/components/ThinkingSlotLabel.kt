package com.rassvet.essential.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rassvet.essential.R
import kotlinx.coroutines.delay
import kotlin.random.Random


@Composable
fun ThinkingSlotLabel(
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    slotHeight: Dp? = null,
    minDisplayMs: Long = 2000L,
    maxJitterMs: Long = 700L,
) {
    val phrases = stringArrayResource(R.array.thinking_phrases).toList()
    val order =
        remember(phrases) {
            if (phrases.size <= 1) {
                phrases.indices.toList()
            } else {
                phrases.indices.shuffled(Random(System.nanoTime()))
            }
        }

    var step by remember { mutableIntStateOf(0) }

    LaunchedEffect(order) {
        while (true) {
            val wait = minDisplayMs + Random.nextLong(maxJitterMs.coerceAtLeast(0L))
            delay(wait)
            step++
        }
    }

    val lineHeight = style.lineHeight
    val minHeight =
        slotHeight
            ?: if (lineHeight.value > 0f) {
                (lineHeight.value * 1.15f).dp
            } else {
                22.dp
            }

    Box(
        modifier = modifier
            .height(minHeight)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart,
    ) {
        AnimatedContent(
            targetState = step,
            modifier = Modifier.align(Alignment.CenterStart),
            contentAlignment = Alignment.CenterStart,
            transitionSpec = {
                val enter =
                    slideInVertically(
                        animationSpec = tween(340, easing = FastOutSlowInEasing),
                        initialOffsetY = { full -> full / 2 },
                    ) + fadeIn(tween(280, easing = FastOutSlowInEasing))
                val exit =
                    slideOutVertically(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        targetOffsetY = { full -> -full / 2 },
                    ) + fadeOut(tween(220, easing = FastOutSlowInEasing))
                enter togetherWith exit using SizeTransform(clip = true)
            },
            label = "thinking_slot",
        ) { s ->
            val idx = order[s % order.size]
            Text(
                text = phrases.getOrElse(idx) { phrases.first() },
                color = color,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@Composable
fun ThinkingStatusRow(
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    logoSize: Dp = 34.dp,
    spacing: Dp = 3.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        EssentialLogoThinkingMark(modifier = Modifier.size(logoSize))
        ThinkingSlotLabel(
            color = color,
            style = style,
            slotHeight = logoSize,
        )
    }
}


