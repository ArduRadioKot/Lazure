package com.rassvet.essential.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.ui.markdown.CodeBlockCard
import com.rassvet.essential.ui.markdown.InlineMarkdownText
import com.rassvet.essential.ui.markdown.LatexBlockView
import com.rassvet.essential.ui.markdown.MdBlock
import com.rassvet.essential.ui.markdown.isCompactLatex
import com.rassvet.essential.ui.markdown.isNoteMarkdownFence
import com.rassvet.essential.ui.markdown.looksLikeMathLine
import com.rassvet.essential.ui.markdown.parseBlocks
import com.rassvet.essential.ui.settings.ReaderTypographySettings

@Composable
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.White,
    mutedColor: Color = Color(0xFF8E8E93),
    reader: ReaderTypographySettings = ReaderTypographySettings(),
    onTaskToggle: ((lineIndex: Int) -> Unit)? = null,
) {
    val blocks = remember(text) { parseBlocks(text) }
    LazyColumn(modifier = modifier) {
        items(blocks.size, key = { index ->
            when (val block = blocks[index]) {
                is MdBlock.TaskItem -> "task-${block.sourceLineIndex}"
                else -> block::class.simpleName + index
            }
        }) { index ->
            val block = blocks[index]
            MarkdownBlockItem(block, baseColor, mutedColor, reader, onTaskToggle)
        }
    }
}


@Composable
fun MarkdownContent(
    text: String,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.White,
    mutedColor: Color = Color(0xFF8E8E93),
    reader: ReaderTypographySettings = ReaderTypographySettings(),
    onTaskToggle: ((lineIndex: Int) -> Unit)? = null,
) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            MarkdownBlockItem(block, baseColor, mutedColor, reader, onTaskToggle)
        }
    }
}

@Composable
private fun MarkdownBlockItem(
    block: MdBlock,
    baseColor: Color,
    mutedColor: Color,
    reader: ReaderTypographySettings,
    onTaskToggle: ((lineIndex: Int) -> Unit)?,
) {
    when (block) {
        is MdBlock.Heading -> {
            val (size, weight) = when (block.level) {
                1 -> (reader.fontSizeSp * 1.35f).sp to FontWeight.Bold
                2 -> (reader.fontSizeSp * 1.2f).sp to FontWeight.SemiBold
                else -> (reader.fontSizeSp * 1.08f).sp to FontWeight.Medium
            }
            InlineMarkdownText(
                text = block.content,
                baseColor = baseColor,
                mutedColor = mutedColor,
                style = TextStyle(
                    fontFamily = reader.family,
                    fontWeight = weight,
                    fontSize = size,
                    lineHeight = size * 1.35f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (block.level == 1) 8.dp else 6.dp, bottom = 4.dp),
            )
        }
        is MdBlock.Paragraph -> {
            InlineMarkdownText(
                text = block.content,
                baseColor = baseColor,
                mutedColor = mutedColor,
                style = reader.bodyStyle(baseColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }
        is MdBlock.Code -> {
            if (isNoteMarkdownFence(block.lang)) {
                MarkdownContent(
                    text = block.code,
                    baseColor = baseColor,
                    mutedColor = mutedColor,
                    reader = reader,
                    onTaskToggle = onTaskToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                )
            } else {
                CodeBlockCard(
                    code = block.code,
                    language = block.lang,
                    baseColor = baseColor,
                )
            }
        }
        is MdBlock.Math -> {
            val compact = isCompactLatex(block.latex)
            val mathSizeSp =
                if (compact) {
                    reader.fontSizeSp * 1.04f
                } else {
                    (reader.fontSizeSp * 1.12f).coerceAtMost(19f)
                }
            LatexBlockView(
                latex = block.latex,
                textColor = baseColor,
                textSizeSp = mathSizeSp,
                compact = compact,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (compact) 4.dp else 8.dp),
            )
        }
        is MdBlock.TaskItem -> {
            val textColor = if (block.checked) mutedColor else baseColor
            val textStyle =
                reader.bodyStyle(textColor).copy(
                    textDecoration = if (block.checked) TextDecoration.LineThrough else null,
                )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TaskCheckbox(
                    checked = block.checked,
                    enabled = onTaskToggle != null,
                    accent = baseColor,
                    border = mutedColor,
                    onToggle = { onTaskToggle?.invoke(block.sourceLineIndex) },
                )
                Spacer(Modifier.width(10.dp))
                InlineMarkdownText(
                    text = block.content,
                    baseColor = textColor,
                    mutedColor = mutedColor,
                    style = textStyle,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        is MdBlock.BulletItem -> {
            if (looksLikeMathLine(block.content)) {
                InlineMarkdownText(
                    text = block.content,
                    baseColor = baseColor,
                    mutedColor = mutedColor,
                    style = reader.bodyStyle(baseColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                ) {
                    Text(
                        text = "•",
                        color = mutedColor,
                        style = reader.bodyStyle(mutedColor),
                    )
                    Spacer(Modifier.width(8.dp))
                    InlineMarkdownText(
                        text = block.content,
                        baseColor = baseColor,
                        mutedColor = mutedColor,
                        style = reader.bodyStyle(baseColor),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        MdBlock.Rule -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(1.dp)
                    .background(Color(0x33FFFFFF)),
            )
        }
    }
}

@Composable
private fun TaskCheckbox(
    checked: Boolean,
    enabled: Boolean,
    accent: Color,
    border: Color,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)
    val tap = remember(checked, enabled) { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .size(18.dp)
            .clip(shape)
            .border(1.5.dp, if (checked) accent else border.copy(alpha = 0.85f), shape)
            .background(if (checked) accent.copy(alpha = 0.92f) else Color.Transparent)
            .then(
                if (enabled) {
                    Modifier.clickable(interactionSource = tap, indication = null, onClick = onToggle)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.82f),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
