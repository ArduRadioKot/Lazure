package com.rassvet.essential.data.llm

import android.content.Context
import com.rassvet.essential.data.local.VaultPreferencesRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Copies LLMs shipped in assets/bundled_llm into the model directory on first launch, so chat works offline. */
object BundledModelInstaller {
    private const val DIR = "bundled_llm"

    suspend fun install(context: Context, prefs: VaultPreferencesRepository) = withContext(Dispatchers.IO) {
        val names = context.assets.list(DIR).orEmpty().filter { it.endsWith(".litertlm") }
        if (names.isEmpty()) return@withContext
        val target = File(context.filesDir, "llm_models").apply { mkdirs() }
        for (name in names) {
            val out = File(target, name)
            if (out.isFile && out.length() > 0) continue
            val tmp = File(target, "$name.installing")
            runCatching {
                context.assets.open("$DIR/$name").use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
                check(tmp.renameTo(out)) { "rename failed" }
            }.onFailure { tmp.delete(); android.util.Log.e("BundledModel", "install $name failed", it) }
        }
        if (prefs.activeGgufName.first().isNullOrBlank()) {
            names.firstOrNull { File(target, it).isFile }?.let { prefs.setActiveGguf(it) }
        }
    }
}
