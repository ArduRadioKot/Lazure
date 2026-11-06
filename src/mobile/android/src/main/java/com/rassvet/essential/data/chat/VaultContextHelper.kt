package com.rassvet.essential.data.chat

import android.content.Context
import com.rassvet.essential.data.index.IndexRepository
import com.rassvet.essential.data.index.SemanticIndex
import com.rassvet.essential.data.local.NoteFtsResult
import com.rassvet.essential.ui.PendingNoteContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CONTEXT_CHAR_BUDGET = 3_500

private val semanticIndex = SemanticIndex()


suspend fun refreshVaultSearchIndex(
    context: Context,
    vaultUri: String,
    index: IndexRepository,
) {
    withContext(Dispatchers.IO) {
        semanticIndex.clear()
        semanticIndex.rebuild(context, vaultUri, index.allNotes())
    }
}

suspend fun buildVaultContextForChat(
    context: Context,
    index: IndexRepository,
    vaultUri: String?,
    userQuery: String = "",
    semanticSearchEnabled: Boolean = true,
): VaultContextBundle =
    withContext(Dispatchers.IO) {
        if (vaultUri.isNullOrBlank()) return@withContext VaultContextBundle("", emptyList())
        if (index.allNotes().isEmpty()) {
            index.rebuild(context, vaultUri)
        }
        if (semanticSearchEnabled && !semanticIndex.isReady()) {
            semanticIndex.rebuild(context, vaultUri, index.allNotes())
        }
        val notes: List<NoteFtsResult> = when {
            userQuery.isBlank() -> index.topRecentNotes(6)
            semanticSearchEnabled -> {
                val semantic = semanticIndex.findSimilar(userQuery, 6)
                if (semantic.size >= 3) {
                    semantic
                } else {
                    val seenUris = semantic.mapTo(HashSet()) { it.uri }
                    val fts = index.searchByQuery(userQuery, 8)
                        .filter { it.uri !in seenUris }
                    (semantic + fts).take(8)
                }
            }
            else -> index.searchByQuery(userQuery, 8)
        }
        VaultContextBundle(
            text = packContext(notes),
            sources = notes.map { it.toChatSource() },
        )
    }

fun mergeChatSources(
    vaultSources: List<ChatSourceNote>,
    noteContext: PendingNoteContext?,
): List<ChatSourceNote> {
    if (noteContext == null) return vaultSources
    val uri = noteContext.uri.toString()
    val pinned =
        ChatSourceNote(
            uri = uri,
            title = noteContext.title.ifBlank { "Без названия" },
            snippet = noteContext.body.lineSequence().firstOrNull { it.isNotBlank() }?.take(72)?.trim().orEmpty(),
            kind = ChatSourceNote.Kind.PINNED,
        )
    val rest = vaultSources.filter { it.uri != uri }
    return listOf(pinned) + rest
}

private fun NoteFtsResult.toChatSource(): ChatSourceNote {
    val snippet =
        body.lineSequence()
            .firstOrNull { line ->
                val t = line.trim()
                t.isNotEmpty() && !t.startsWith("#")
            }
            ?.trim()
            ?.take(72)
            ?: body.trim().take(72)
    return ChatSourceNote(
        uri = uri,
        title = title.ifBlank { "Без названия" },
        snippet = snippet,
    )
}

private fun packContext(notes: List<NoteFtsResult>): String {
    val sb = StringBuilder()
    for (note in notes) {
        val block = buildString {
            append("# ${note.title}\n")
            append(note.body)
            append("\n(источник: [[${note.title}]])")
        }
        val sep = "\n\n---\n\n"
        val needed = block.length + sep.length
        if (sb.length + needed > CONTEXT_CHAR_BUDGET) {
            val remaining = CONTEXT_CHAR_BUDGET - sb.length - 8
            if (remaining > 80) {
                sb.append("# ${note.title}\n${note.body.take(remaining)}")
            }
            break
        }
        sb.append(block).append(sep)
    }
    return sb.toString().trimEnd()
}


