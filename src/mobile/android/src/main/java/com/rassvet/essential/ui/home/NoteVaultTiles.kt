package com.rassvet.essential.ui.home

import android.content.Context
import android.net.Uri
import com.rassvet.essential.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.rassvet.essential.data.vault.VaultDocuments


data class NoteVaultTile(
    val uri: Uri,
    val file: DocumentFile?,
    val title: String,

    val summary: String?,
)

fun displayTitleForFile(context: Context, file: DocumentFile): String {
    val untitled = context.getString(R.string.note_untitled)
    val raw = VaultDocuments.displayName(file)
        .removeSuffix(".md").removeSuffix(".MD")
    return if (raw.startsWith("draft-")) untitled else raw
}

fun displayTitleFromName(context: Context, name: String): String {
    val untitled = context.getString(R.string.note_untitled)
    val raw = name.removeSuffix(".md").removeSuffix(".MD")
    return if (raw.startsWith("draft-")) untitled else raw
}

private val wikiLinkRe = Regex("""\[\[([^]|]+)(?:\|[^]]+)?]]""")
private val listLineRe = Regex("""^[-*+]\s+|\d+\.\s+""")

private fun String.cleanMarkdownInline(): String =
    wikiLinkRe.replace(this) { it.groupValues[1] }
        .replace(Regex("""[*_`]"""), "")
        .replace(Regex("""^>\s*"""), "")
        .replace(listLineRe, "")
        .replace(Regex("""\s+"""), " ")
        .trim()


fun truncateNoteSummary(text: String, maxLen: Int = 88): String {
    val t = text.cleanMarkdownInline()
    if (t.isEmpty()) return ""
    if (t.length <= maxLen) return t
    val wordCut = t.take(maxLen).substringBeforeLast(' ').trim()
    val cut = if (wordCut.length >= maxLen / 2) wordCut else t.take(maxLen).trim()
    return cut.trimEnd(',', '.', ';', ':', '—', '-', ' ') + "…"
}


fun extractNoteSummary(markdown: String, title: String = ""): String? {
    if (markdown.isBlank()) return null

    var sectionHint: String? = null
    val bodyLines = ArrayList<String>(8)
    var listLines = 0
    var bodyLinesCount = 0

    for (raw in markdown.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        when {
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                val heading = line.drop(level).cleanMarkdownInline()
                if (level in 2..4 && heading.isNotBlank()) {
                    sectionHint = heading
                }
            }
            else -> {
                bodyLinesCount++
                if (listLineRe.containsMatchIn(line) || line.startsWith("- ") || line.startsWith("* ")) {
                    listLines++
                }
                val clean = line.cleanMarkdownInline()
                if (clean.length >= 2) bodyLines.add(clean)
            }
        }
    }

    if (bodyLines.isEmpty()) {
        return sectionHint?.let { truncateNoteSummary(it) }
    }

    val summary = when {
        listLines >= 2 && bodyLines.size >= 2 -> {
            val topic = sectionHint ?: title.ifBlank { bodyLines.first() }
            val sample = bodyLines.take(2).joinToString(", ") { truncateNoteSummary(it, 36) }
            truncateNoteSummary("$topic · $sample", 96)
        }
        sectionHint != null && !title.contains(sectionHint, ignoreCase = true) -> {
            val lead = firstSentence(bodyLines.first())
            if (lead.length <= 24) sectionHint else truncateNoteSummary("$sectionHint — $lead")
        }
        else -> truncateNoteSummary(firstSentence(bodyLines.first()))
    }

    val normalized = summary.trim()
    if (normalized.isEmpty()) return null
    if (normalized.equals(title.trim(), ignoreCase = true)) {
        val alt = bodyLines.drop(1).firstOrNull()?.let { truncateNoteSummary(firstSentence(it)) }
        return alt?.takeIf { !it.equals(title.trim(), ignoreCase = true) }
    }
    return normalized
}

private fun firstSentence(text: String): String {
    val t = text.cleanMarkdownInline()
    if (t.isEmpty()) return ""
    val end = t.indexOfAny(charArrayOf('.', '!', '?', '。'))
    return if (end in 8 until t.length) {
        t.substring(0, end + 1).trim()
    } else {
        t
    }
}

fun summaryFromIndexPreview(snippet: String): String? {
    val t = snippet.trim().replace('\n', ' ').cleanMarkdownInline()
    if (t.length < 4) return null
    return truncateNoteSummary(firstSentence(t))
}


fun isVaultNoteEmpty(markdown: String): Boolean {
    val lines = markdown.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
    if (!lines.any()) return true
    return lines.all { it.startsWith("#") }
}


private val VaultTileHeight = 118.dp
private val VaultTileTitleHeight = 40.dp
private val VaultTileSummaryHeight = 34.dp

@Composable
fun NoteVaultTileGrid(
    tiles: List<NoteVaultTile>,
    modifier: Modifier = Modifier,
    onOpen: (Uri) -> Unit,
    onLongPress: ((DocumentFile) -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 8.dp,
        ),
    ) {
        items(tiles, key = { it.uri.toString() }) { tile ->
            NoteVaultTileCard(
                tile = tile,
                onClick = { onOpen(tile.uri) },
                onLongClick = tile.file?.let { f ->
                    if (onLongPress != null) ({ onLongPress(f) }) else null
                },
            )
        }
    }
}

@Composable
private fun NoteVaultTileCard(
    tile: NoteVaultTile,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val interaction = remember(tile.uri) { MutableInteractionSource() }
    val tileBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val titleColor = MaterialTheme.colorScheme.onSurface
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = tileBg,
        modifier = Modifier
            .fillMaxWidth()
            .height(VaultTileHeight)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VaultTileTitleHeight),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = tile.title,
                    color = titleColor,
                    style = TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = metaColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                color = dividerColor,
                thickness = 0.5.dp,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VaultTileSummaryHeight),
                contentAlignment = Alignment.TopStart,
            ) {
                if (!tile.summary.isNullOrBlank()) {
                    Text(
                        text = tile.summary,
                        color = metaColor,
                        style = TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}


