package com.rassvet.essential.data.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max


fun compressImage(
    context: Context,
    uri: Uri,
    maxSide: Int = 1600,
    quality: Int = 85,
): Pair<ByteArray, String>? {
    val resolver = context.contentResolver


    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: return null
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return null


    var sample = 1
    while (max(srcW, srcH) / sample > maxSide * 2) sample *= 2

    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val sampled: Bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOpts)
    } ?: return null


    val rotation = runCatching {
        resolver.openInputStream(uri)?.use { readExifRotation(it) } ?: 0
    }.getOrDefault(0)


    val longSide = max(sampled.width, sampled.height)
    val scale = if (longSide > maxSide) maxSide.toFloat() / longSide else 1f
    val targetW = (sampled.width * scale).toInt().coerceAtLeast(1)
    val targetH = (sampled.height * scale).toInt().coerceAtLeast(1)
    val resized = if (scale != 1f || rotation != 0) {
        val m = Matrix()
        if (scale != 1f) m.postScale(scale, scale)
        if (rotation != 0) m.postRotate(rotation.toFloat())
        Bitmap.createBitmap(sampled, 0, 0, sampled.width, sampled.height, m, true)
    } else {
        sampled
    }
    if (resized !== sampled) sampled.recycle()

    val out = ByteArrayOutputStream()
    val ok = resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
    resized.recycle()
    if (!ok) return null
    return out.toByteArray() to "image/jpeg"
}

private fun readExifRotation(input: InputStream): Int {
    val exif = ExifInterface(input)
    return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}


