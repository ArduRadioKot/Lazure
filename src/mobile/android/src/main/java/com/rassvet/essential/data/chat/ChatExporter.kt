package com.rassvet.essential.data.chat

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.rassvet.essential.data.local.ChatMessageEntity
import com.rassvet.essential.data.local.UsageActivity
import com.rassvet.essential.data.vault.VaultDocuments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ISO_DATE = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US)

private val noteCreateOutroRe = Regex(
    "(?i)(база знаний|хранилищ[ео] обновлен|заметка (?:создан|добавлен|сохранен)|" +
        "нужно ли что|могу ли ещё|могу ли еще|что-нибудь ещё|что-нибудь еще)",
)

private val noteMetaLineRe = Regex("(?i)^\\(источник:\\s*.+\\)\\s*$")


fun extractTitleFromNoteMarkdown(markdown: String): String {
    for (line in markdown.lineSequence()) {
        val t = line.trim()
        if (t.isEmpty()) continue
        if (t.startsWith("#")) {
            return t.removePrefix("#").trim().trimEnd('#').trim().take(120)
        }
        if (t.startsWith("**") && t.endsWith("**") && t.length > 4) {
            return t.removeSurrounding("**").trim().take(120)
        }
    }
    return markdown.lineSequence()
        .firstOrNull { it.trim().isNotEmpty() && !isNoteIntroLine(it.trim()) }
        ?.trim()
        ?.replace(Regex("[#*_`>]"), "")
        ?.trim()
        ?.take(120)
        ?.ifBlank { null }
        ?: "Без названия"
}

private val NOTE_FENCE_LANGS = setOf("note", "markdown", "md")

private fun isFenceAtLineStart(text: String, fencePos: Int): Boolean {
    val lineStart = text.lastIndexOf('\n', fencePos - 1) + 1
    return fencePos == lineStart || text.substring(lineStart, fencePos).trim().isEmpty()
}

private fun fenceLanguageAt(text: String, fencePos: Int): String {
    val lineEnd = text.indexOf('\n', fencePos + 3).let { if (it < 0) text.length else it }
    return text.substring(fencePos + 3, lineEnd).trim().lowercase()
}


private fun findClosingFencePos(text: String, contentStart: Int): Int? {
    var i = contentStart
    while (i < text.length) {
        val fence = text.indexOf("```", i)
        if (fence < 0) return null
        if (!isFenceAtLineStart(text, fence)) {
            i = fence + 3
            continue
        }
        val lang = fenceLanguageAt(text, fence)
        val lineEnd = text.indexOf('\n', fence + 3).let { if (it < 0) text.length else it }
        if (lang.isEmpty()) {
            return fence
        }
        val nestedContentStart = lineEnd + 1
        val nestedClose = findClosingFencePos(text, nestedContentStart) ?: return null
        i = text.indexOf('\n', nestedClose + 3).let { if (it < 0) text.length else it } + 1
    }
    return null
}

private fun fenceContentStart(text: String, openPos: Int): Int? {
    val lineEnd = text.indexOf('\n', openPos + 3)
    if (lineEnd < 0) return null
    return lineEnd + 1
}

private fun findAllFenceOpenings(text: String): List<Int> {
    val openings = mutableListOf<Int>()
    var i = 0
    while (i < text.length) {
        val fence = text.indexOf("```", i)
        if (fence < 0) break
        if (isFenceAtLineStart(text, fence)) {
            openings.add(fence)
        }
        i = fence + 3
    }
    return openings
}


private fun extractFullFenceBlock(text: String, openPos: Int): String? {
    val contentStart = fenceContentStart(text, openPos) ?: return null
    val closePos = findClosingFencePos(text, contentStart) ?: return null
    val end = text.indexOf('\n', closePos + 3).let { if (it < 0) text.length else it }
    return text.substring(openPos, end).trim()
}

private fun extractFencedContent(text: String, openPos: Int): String? {
    val contentStart = fenceContentStart(text, openPos) ?: return null
    val closePos = findClosingFencePos(text, contentStart) ?: return null
    return text.substring(contentStart, closePos).trim()
}


private fun enrichNoteMarkdownWithCodeFromReply(noteBody: String, fullReply: String): String {
    if (noteBody.contains("```")) return noteBody

    val noteOpenPos =
        findAllFenceOpenings(fullReply).firstOrNull { open ->
            fenceLanguageAt(fullReply, open) in NOTE_FENCE_LANGS
        }
    val noteClosePos =
        noteOpenPos?.let { open ->
            fenceContentStart(fullReply, open)?.let { start ->
                findClosingFencePos(fullReply, start)
            }
        }

    val orphans = mutableListOf<String>()
    for (open in findAllFenceOpenings(fullReply)) {
        if (noteOpenPos != null && noteClosePos != null && open in noteOpenPos..noteClosePos) continue
        val lang = fenceLanguageAt(fullReply, open)
        if (lang in NOTE_FENCE_LANGS) continue
        val block = extractFullFenceBlock(fullReply, open) ?: continue
        if (block.length >= 6) orphans.add(block)
    }
    if (orphans.isEmpty()) return noteBody

    return buildString {
        append(noteBody.trimEnd())
        orphans.distinct().forEach { block ->
            append("\n\n")
            append(block)
        }
    }.trim()
}


fun extractNoteMarkdownFromReply(reply: String): String? {
    var genericFallback: String? = null

    for (open in findAllFenceOpenings(reply)) {
        val lang = fenceLanguageAt(reply, open)
        val content = extractFencedContent(reply, open) ?: continue
        if (lang in NOTE_FENCE_LANGS && content.length >= 8) {
            return enrichNoteMarkdownWithCodeFromReply(content, reply)
        }
        if (genericFallback == null && content.length >= 24) {
            genericFallback = content
        }
    }

    return genericFallback?.let { enrichNoteMarkdownWithCodeFromReply(it, reply) }
}

private fun isNoteIntroLine(t: String): Boolean {
    val l = t.lowercase()
    return l.startsWith("конечно") ||
        l.startsWith("хорошо") ||
        l.startsWith("да,") ||
        l.startsWith("да.") ||
        l.startsWith("ок") ||
        l.startsWith("сейчас") ||
        l.startsWith("добавляю") ||
        l.startsWith("сохраняю") ||
        l.startsWith("создаю") ||
        l.startsWith("вот ") ||
        l.contains("как новую заметку") ||
        l.contains("в ваше «хранилище»") ||
        l.contains("в хранилище")
}


fun extractNoteContentForCreate(reply: String): String? {
    extractNoteMarkdownFromReply(reply)?.let { return it }

    val trimmed = reply.trim()
    if (trimmed.length < 16) return null

    val outroIdx = noteCreateOutroRe.find(trimmed)?.range?.first ?: trimmed.length
    val beforeOutro = trimmed.substring(0, outroIdx).trim()
    val lines = beforeOutro.lines()

    var startIdx = lines.indexOfFirst { line ->
        val t = line.trim()
        t.startsWith("#") ||
            (t.startsWith("**") && t.endsWith("**") && t.length > 4) ||
            t.startsWith("- ") ||
            t.startsWith("* ")
    }
    if (startIdx < 0) {
        startIdx = lines.indices.firstOrNull { i ->
            val t = lines[i].trim()
            if (t.isEmpty() || isNoteIntroLine(t)) return@firstOrNull false
            i + 1 < lines.size && (
                lines[i + 1].trim().startsWith("- ") ||
                    lines[i + 1].trim().startsWith("* ")
                )
        } ?: -1
    }
    if (startIdx < 0) {

        val codeOnly = buildString {
            for (open in findAllFenceOpenings(beforeOutro)) {
                val block = extractFullFenceBlock(beforeOutro, open) ?: continue
                if (block.isNotBlank()) {
                    append(block)
                    append("\n\n")
                }
            }
        }.trim()
        return codeOnly.takeIf { it.length >= 12 }
    }

    val content = lines.drop(startIdx).joinToString("\n").trim()
    return content.takeIf { it.length >= 12 }
        ?.let { enrichNoteMarkdownWithCodeFromReply(it, reply) }
}


fun normalizeNoteMarkdown(markdown: String): String {
    var lines = markdown.lines()
        .map { it.trimEnd() }
        .dropWhile { it.trim().isEmpty() || isNoteIntroLine(it.trim()) }

    while (lines.isNotEmpty()) {
        val last = lines.last().trim()
        if (last.isEmpty() || noteCreateOutroRe.containsMatchIn(last)) {
            lines = lines.dropLast(1)
        } else {
            break
        }
    }
    if (lines.isEmpty()) return markdown.trim()

    val mutable = lines.toMutableList()
    val first = mutable.first().trim()
    when {
        first.startsWith("**") && first.endsWith("**") ->
            mutable[0] = "# ${first.removeSurrounding("**").trim()}"
        !first.startsWith("#") &&
            first.length in 3..120 &&
            !first.startsWith("-") &&
            !first.startsWith("*") &&
            !first.contains("?") ->
            mutable[0] = "# $first"
    }

    val joined =
        mutable
            .filter { line ->
                val t = line.trim()
                t.isNotEmpty() && !noteMetaLineRe.matches(t) && !isNoteIntroLine(t)
            }
            .joinToString("\n")
            .trim()


    return preserveFencedRegions(markdown, joined)
}


private fun preserveFencedRegions(original: String, normalized: String): String {
    if (original.contains("```") && !normalized.contains("```")) {
        val blocks = mutableListOf<String>()
        for (open in findAllFenceOpenings(original)) {
            extractFullFenceBlock(original, open)?.let { blocks.add(it) }
        }
        if (blocks.isNotEmpty()) {
            return buildString {
                append(normalized.trimEnd())
                blocks.distinct().forEach { block ->
                    append("\n\n")
                    append(block)
                }
            }.trim()
        }
    }
    return normalized
}


fun prepareNoteFromAiReply(aiReply: String): Pair<String, String>? {
    val extracted = extractNoteContentForCreate(aiReply) ?: return null
    val body = normalizeNoteMarkdown(extracted)
    if (body.length < 8) return null
    return extractTitleFromNoteMarkdown(body) to body
}

private suspend fun createNoteFile(
    context: Context,
    vaultStored: String,
    title: String,
    body: String,
): Uri? {
    val root = VaultDocuments.resolveRoot(context, vaultStored) ?: return null
    if (!root.canWrite()) return null

    val fileName = VaultDocuments.uniqueMarkdownFilenameInParent(context, root, title, null)
    val created = createMarkdownFileInRoot(root, fileName)
        ?: VaultDocuments.createNewMarkdownFile(root)
        ?: return null

    var uri = VaultDocuments.writableUriForVaultDocument(context, vaultStored, created.uri)
    if (!VaultDocuments.writeDocumentText(context, vaultStored, uri, body)) {
        runCatching { created.delete() }
        return null
    }

    val readBack = runCatching {
        VaultDocuments.readDocumentText(context, vaultStored, uri).trim()
    }.getOrNull()
    if (readBack.isNullOrBlank()) {
        runCatching { created.delete() }
        return null
    }


    val currentName = VaultDocuments.resolveDocumentDisplayName(context, vaultStored, uri)
    if (currentName.startsWith("draft-") || currentName.equals("Untitled.md", ignoreCase = true)) {
        val rename = VaultDocuments.renameMarkdownFile(context, vaultStored, uri, title)
        if (rename.ok && rename.uri != null) {
            uri = VaultDocuments.writableUriForVaultDocument(context, vaultStored, rename.uri)
        }
    }

    UsageActivity.recordNoteCreated(context)

    return uri
}

private fun createMarkdownFileInRoot(root: DocumentFile, displayName: String): DocumentFile? {
    for (mime in arrayOf(
            VaultDocuments.markdownMimeType(),
            "text/plain",
            "application/octet-stream",
        )) {
        val created = root.createFile(mime, displayName)
        if (created != null) return created
    }
    return null
}


suspend fun createNoteFromMarkdown(
    context: Context,
    vaultStored: String?,
    markdown: String,
): Uri? = withContext(Dispatchers.IO) {
    if (vaultStored.isNullOrBlank()) return@withContext null
    val body = normalizeNoteMarkdown(markdown.trim())
    if (body.length < 8) return@withContext null
    val title = extractTitleFromNoteMarkdown(body)
    createNoteFile(context, vaultStored, title, body)
}


suspend fun saveAiReplyAsNote(
    context: Context,
    vaultStored: String?,
    @Suppress("UNUSED_PARAMETER") userQuestion: String,
    aiReply: String,
    @Suppress("UNUSED_PARAMETER") chatSessionTitle: String?,
): Uri? = withContext(Dispatchers.IO) {
    if (vaultStored.isNullOrBlank()) return@withContext null

    val prepared = prepareNoteFromAiReply(aiReply)
        ?: run {
            val body = normalizeNoteMarkdown(aiReply.trim())
            if (body.length < 8) return@withContext null
            extractTitleFromNoteMarkdown(body) to body
        }
    val (title, body) = prepared
    createNoteFile(context, vaultStored, title, body)
}


suspend fun saveFullChatAsNote(
    context: Context,
    vaultStored: String?,
    sessionTitle: String,
    messages: List<ChatMessageEntity>,
): Uri? = withContext(Dispatchers.IO) {
    if (vaultStored.isNullOrBlank()) return@withContext null
    if (messages.isEmpty()) return@withContext null
    val now = Date()
    val sanitized = VaultDocuments.sanitizeFileBase("Чат · $sessionTitle · ${ISO_DATE.format(now)}")
    val fileName = "$sanitized.md"
    val uri = VaultDocuments.createMarkdownDocumentRaw(context, vaultStored, fileName)
        ?: return@withContext null
    val body = buildString {
        appendLine("# $sessionTitle")
        appendLine()
        appendLine("> Экспорт чата · ${ISO_DATE.format(now)}.")
        appendLine()
        for (m in messages) {
            if (m.isUser) {
                appendLine("## 🧑 Я")
            } else {
                appendLine("## 🤖 Lazure")
            }
            appendLine()
            appendLine(m.text.trim())
            appendLine()
            appendLine("---")
            appendLine()
        }
    }
    runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(body.toByteArray(Charsets.UTF_8))
        }
    }
    uri
}


suspend fun importMarkdownFolder(
    context: Context,
    vaultStored: String?,
    sourceTreeUri: Uri,
): Int = withContext(Dispatchers.IO) {
    if (vaultStored.isNullOrBlank()) return@withContext 0
    val resolver = context.contentResolver
    runCatching {
        resolver.takePersistableUriPermission(
            sourceTreeUri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
    val sourceRoot = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, sourceTreeUri)
        ?: return@withContext 0
    val mdFiles = VaultDocuments.listMarkdown(sourceRoot)
    var copied = 0
    for (file in mdFiles) {
        val name = file.name ?: continue
        val baseName = name.removeSuffix(".md").removeSuffix(".MD")
        val sanitized = VaultDocuments.sanitizeFileBase(baseName)
        val targetName = "$sanitized.md"
        val targetUri = VaultDocuments.createMarkdownDocumentRaw(context, vaultStored, targetName)
            ?: continue
        val text = runCatching { VaultDocuments.readText(context, file) }.getOrNull() ?: continue
        runCatching {
            resolver.openOutputStream(targetUri, "wt")?.use { out ->
                out.write(convertNotionToObsidian(text).toByteArray(Charsets.UTF_8))
            }
            copied++
        }
    }
    copied
}


private fun convertNotionToObsidian(text: String): String {
    val notionLinkRe = Regex("""\[([^\]]+)]\(([^)]+\.md)\)""")
    val converted = notionLinkRe.replace(text) { m ->
        val title = m.groupValues[1].trim()
        "[[${title}]]"
    }
    return converted.trimStart()
}


