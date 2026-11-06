package com.rassvet.essential.data.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

private const val THUMB_JPEG_QUALITY = 82


fun decodeAttachmentThumbnail(base64: String?, maxSidePx: Int = 384): Bitmap? {
    if (base64.isNullOrBlank()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = inSampleSizeFor(bounds.outWidth, bounds.outHeight, maxSidePx)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()
}


fun thumbnailBase64ForStorage(fullBase64: String, maxSidePx: Int = 512): String? {
    val bmp = decodeAttachmentThumbnail(fullBase64, maxSidePx) ?: return null
    return runCatching {
        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, THUMB_JPEG_QUALITY, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }.getOrNull().also {
        if (!bmp.isRecycled) bmp.recycle()
    }
}

private fun inSampleSizeFor(width: Int, height: Int, maxSidePx: Int): Int {
    var sample = 1
    while (max(width, height) / sample > maxSidePx) {
        sample *= 2
    }
    return sample.coerceAtLeast(1)
}


fun ChatAttachment.toPersistedForStorage(): PersistedAttachment {
    val storedB64 = if (isImage && base64.isNotBlank()) {
        thumbnailBase64ForStorage(base64) ?: base64
    } else {
        null
    }
    return PersistedAttachment(
        displayName = displayName,
        mimeType = mimeType,
        base64 = storedB64,
        sizeBytes = sizeBytes,
    )
}


