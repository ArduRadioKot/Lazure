package com.rassvet.essential.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.ui.components.EssentialLogoMark
import com.rassvet.essential.ui.settings.ReaderTypographySettings
import com.rassvet.essential.ui.theme.LocalEssentialChrome

private val PromptChipHeight = 32.dp

@Composable
fun EmptyChatPlaceholder(
    prompt: String,
    logoStroke: Color,
    promptColor: Color,
    reader: ReaderTypographySettings,
    quickPrompts: List<String>,
    onQuickPrompt: (String) -> Unit,
    usageDailyTotals: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val prompts = quickPrompts.take(3)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EssentialLogoMark(
            size = 112.dp,
            strokeColor = logoStroke,
        )
        Text(
            text = prompt,
            modifier = Modifier.offset(y = (-10).dp),
            style = reader.bodyStyle(promptColor).copy(
                fontSize = (reader.fontSizeSp * 1.28f).sp,
                lineHeight = (reader.fontSizeSp * 1.65f).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Top,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            textAlign = TextAlign.Center,
        )

        if (prompts.size == 3) {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EmptyChatPromptChip(
                    text = prompts[0],
                    onClick = { onQuickPrompt(prompts[0]) },
                    modifier = Modifier.weight(1f),
                )
                EmptyChatPromptChip(
                    text = prompts[1],
                    onClick = { onQuickPrompt(prompts[1]) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            EmptyChatPromptChip(
                text = prompts[2],
                onClick = { onQuickPrompt(prompts[2]) },
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }

        if (usageDailyTotals.isNotEmpty()) {
            Spacer(Modifier.height(if (prompts.size == 3) 24.dp else 22.dp))
            UsageActivityHeatmap(dailyTotals = usageDailyTotals)
        }
    }
}

@Composable
private fun EmptyChatPromptChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalEssentialChrome.current
    val shape = RoundedCornerShape(999.dp)
    val tap = remember(text) { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = chrome.sheetSelectedBg.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, chrome.sheetBorder.copy(alpha = 0.5f)),
        modifier = modifier
            .height(PromptChipHeight)
            .clip(shape)
            .clickable(interactionSource = tap, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = chrome.primaryText.copy(alpha = 0.88f),
                style = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
