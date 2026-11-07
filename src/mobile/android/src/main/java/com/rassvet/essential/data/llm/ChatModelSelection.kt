package com.rassvet.essential.data.llm

import android.app.ActivityManager
import android.content.Context
import com.rassvet.essential.data.local.VaultPreferencesRepository

object ChatModelSelection {
    /** Every model is local; the enum is kept so the option model can grow without changing callers. */
    enum class Kind {
        LocalModel,
    }

    data class Option(
        val id: String,
        val pillLabel: String,
        val title: String,
        val subtitle: String?,
        val description: String? = null,
        val sizeLabel: String? = null,
        val kind: Kind,
        val localFileName: String? = null,
        val available: Boolean = true,
        val isDownloadable: Boolean = false,
        val recommended: Boolean = false,
    ) {

        val ggufFileName: String? get() = localFileName
    }

    fun deviceTotalRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 4096
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }

    fun recommendedPreset(ramMb: Long): LocalModelCatalog.Preset {
        val budget = (ramMb * 0.42).toLong().coerceAtLeast(200)
        return LocalModelCatalog.presets
            .sortedBy { it.ramMb }
            .lastOrNull { it.ramMb <= budget }
            ?: LocalModelCatalog.presets.minBy { it.ramMb }
    }

    fun buildOptions(
        context: Context,
        installedLocalModels: List<String>,
        activeLocalModel: String?,
    ): List<Option> {
        val ramMb = deviceTotalRamMb(context)
        val selectedId = currentSelectionId(activeLocalModel)
        val options = mutableListOf<Option>()

        installedLocalModels.forEach { fileName ->
            val preset = LocalModelCatalog.presetForFileName(fileName)
            options +=
                optionFromPreset(
                    context = context,
                    preset = preset,
                    fileName = fileName,
                    available = true,
                    isDownloadable = false,
                )
        }

        LocalModelCatalog.presets
            .filter { preset -> installedLocalModels.none { it == preset.fileName } }
            .forEach { preset ->
                options +=
                    optionFromPreset(
                        context = context,
                        preset = preset,
                        fileName = preset.fileName,
                        available = false,
                        isDownloadable = true,
                    )
            }

        return markRecommended(options, ramMb, selectedId)
    }

    private fun optionFromPreset(
        context: Context,
        preset: LocalModelCatalog.Preset?,
        fileName: String,
        available: Boolean,
        isDownloadable: Boolean,
    ): Option =
        Option(
            id = if (available) "local:$fileName" else "catalog:$fileName",
            pillLabel = shortLocalModelLabel(fileName),
            title = preset?.label ?: LocalModelFormats.displayStem(fileName),
            subtitle = preset?.info ?: context.getString(
                com.rassvet.essential.R.string.chat_model_local_subtitle,
                estimateRamLabel(fileName),
            ),
            description = preset?.let {
                if (context.resources.configuration.locales[0].language == "ru") it.description
                else context.getString(com.rassvet.essential.R.string.model_local_description, it.label)
            },
            sizeLabel = preset?.expectedBytes?.let { android.text.format.Formatter.formatShortFileSize(context, it) },
            kind = Kind.LocalModel,
            localFileName = fileName,
            available = available,
            isDownloadable = isDownloadable,
        )

    private fun markRecommended(
        options: List<Option>,
        ramMb: Long,
        selectedId: String,
    ): List<Option> {
        val recId =
            when {
                else -> {
                    val preset = recommendedPreset(ramMb)
                    options.firstOrNull { it.id == "local:${preset.fileName}" && it.available }?.id
                        ?: options.firstOrNull { it.id == "catalog:${preset.fileName}" }?.id
                        ?: options.firstOrNull { it.kind == Kind.LocalModel && it.available }?.id
                }
            }
        return options.map { option ->
            option.copy(recommended = option.id == recId && option.id != selectedId && option.available)
        }
    }

    fun currentSelectionId(activeLocalModel: String?): String =
        when {
            !activeLocalModel.isNullOrBlank() -> "local:$activeLocalModel"
            else -> "local:${LocalModelCatalog.presets.first().fileName}"
        }

    fun presetForOption(option: Option): LocalModelCatalog.Preset? =
        option.localFileName?.let { name -> LocalModelCatalog.presetForFileName(name) }

    suspend fun applySelection(prefs: VaultPreferencesRepository, option: Option) {
        when (option.kind) {
            Kind.LocalModel -> {
                val file = option.localFileName ?: return
                prefs.setActiveGguf(file)
                prefs.setHomeChatMode("local")
            }
        }
    }

    fun shortLocalModelLabel(fileName: String): String {
        val preset = LocalModelCatalog.presetForFileName(fileName)
        if (preset != null) return preset.label
        return LocalModelFormats.displayStem(fileName)
            .replace('-', ' ')
            .replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(" ")
            .take(20)
            .ifBlank { "Локальная" }
    }


    fun shortGgufLabel(fileName: String): String = shortLocalModelLabel(fileName)

    private fun estimateRamLabel(fileName: String): String {
        val preset = LocalModelCatalog.presetForFileName(fileName)
        return if (preset != null) preset.info else "локально"
    }
}


