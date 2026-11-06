package com.rassvet.essential.data.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


data class ChatAttachment(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val base64: String,
    val sizeBytes: Long,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isPdf: Boolean get() = mimeType.equals("application/pdf", ignoreCase = true)
}


private const val MAX_ATTACHMENT_BYTES = 12L * 1024 * 1024

class AttachmentTooLargeException(sizeBytes: Long) :
    RuntimeException("Файл слишком большой: ${sizeBytes / 1024} КБ (макс 12 МБ).")


suspend fun encodeAttachment(
    context: Context,
    uri: Uri,
    compressImages: Boolean = true,
): ChatAttachment =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var name = "файл"
        var size = -1L
        resolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = c.getLong(sizeIdx)
            }
        }

        val isImage = mime.startsWith("image/")
        val (finalBytes: ByteArray, finalMime: String, finalName: String) = if (isImage && compressImages) {
            val compressed = compressImage(context, uri, maxSide = 1600, quality = 85)
            if (compressed != null) {
                val (bytes, mt) = compressed
                Triple(bytes, mt, ensureJpegName(name))
            } else {

                val raw = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("openInputStream вернул null для $uri")
                Triple(raw, mime, name)
            }
        } else {
            if (size in 1..Long.MAX_VALUE && size > MAX_ATTACHMENT_BYTES) {
                throw AttachmentTooLargeException(size)
            }
            val raw = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("openInputStream вернул null для $uri")
            Triple(raw, mime, name)
        }

        if (finalBytes.size.toLong() > MAX_ATTACHMENT_BYTES) {
            throw AttachmentTooLargeException(finalBytes.size.toLong())
        }
        ChatAttachment(
            uri = uri,
            displayName = finalName,
            mimeType = finalMime,
            base64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP),
            sizeBytes = finalBytes.size.toLong(),
        )
    }

private fun ensureJpegName(name: String): String {
    val base = name.substringBeforeLast('.').ifBlank { "image" }
    return "$base.jpg"
}


