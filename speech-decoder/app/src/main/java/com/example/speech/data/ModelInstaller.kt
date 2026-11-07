package com.example.speech.data

import android.content.Context
import java.io.File

/** Копирует assets/models/* в filesDir/models (ORT грузит модели по пути; копия делается один раз). */
object ModelInstaller {
    fun ensureModels(ctx: Context): File {
        val outDir = File(ctx.filesDir, "models").apply { mkdirs() }
        val names = ctx.assets.list("models").orEmpty()
        for (name in names) {
            val dst = File(outDir, name)
            val size = ctx.assets.openFd("models/$name").use { it.length }
            if (dst.exists() && dst.length() == size) continue
            ctx.assets.open("models/$name").use { src -> dst.outputStream().use { src.copyTo(it, 1 shl 16) } }
        }
        return outDir
    }
}
