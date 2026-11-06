package com.rassvet.essential.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

class ChatHaptics(
    private val haptic: HapticFeedback,
    private val enabled: Boolean,
) {
    fun onMessageSent() {
        if (enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun onGenerationComplete() {
        if (enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        }
    }
}

@Composable
fun rememberChatHaptics(enabled: Boolean): ChatHaptics {
    val haptic = LocalHapticFeedback.current
    return remember(enabled, haptic) { ChatHaptics(haptic, enabled) }
}


