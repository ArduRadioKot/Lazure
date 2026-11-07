package com.rassvet.essential.data.llm

import android.app.ActivityManager
import android.content.Context
import com.rassvet.essential.BuildConfig
import com.rassvet.essential.data.local.VaultPreferencesRepository

object ChatModelSelection {
    const val CLOUD_SENTINEL = "essentialai"
    const val ESSENTIAL_AI_LABEL = "Lazure"

    enum class Kind {
        LocalModel,
        CloudTimeweb,
        CloudGemini,
        CloudOpenAi,
        CloudEssentialApi,
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

    fun isCloudMode(homeChatMode: String, activeLocalModel: String?): Boolean = false

    fun resolveCloudProvider(
        homeChatMode: String,
        activeLocalModel: String?,
        cloudProvider: String,
    ): String =
        when {
            cloudProvider == "essential" -> "essential"
            homeChatMode == "timeweb_cloud" ||
                (activeLocalModel == CLOUD_SENTINEL && homeChatMode != "cloud") ->
                if (BuildConfig.ALLOW_DIRECT_CLOUD) "timeweb" else "essential"
            isCloudMode(homeChatMode, activeLocalModel) -> cloudProvider
            else -> cloudProvider
        }

    suspend fun applyDefaultEssentialAi(prefs: VaultPreferencesRepository) {
        prefs.setActiveGguf(CLOUD_SENTINEL)
        prefs.setHomeChatMode("timeweb_cloud")
        prefs.setCloudLlmProvider("essential")
    }

    fun buildOptions(
        context: Context,
        installedLocalModels: List<String>,
        homeChatMode: String,
        activeLocalModel: String?,
        cloudProvider: String,
    ): List<Option> {
        val ramMb = deviceTotalRamMb(context)
        val selectedId = currentSelectionId(homeChatMode, activeLocalModel, cloudProvider)
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

    fun currentSelectionId(
        homeChatMode: String,
        activeLocalModel: String?,
        cloudProvider: String,
    ): String =
        when {
            isCloudMode(homeChatMode, activeLocalModel) -> "cloud_essential"
            !activeLocalModel.isNullOrBlank() -> "local:$activeLocalModel"
            else -> "local:${LocalModelCatalog.presets.first().fileName}"
        }

    fun presetForOption(option: Option): LocalModelCatalog.Preset? =
        option.localFileName?.let { name -> LocalModelCatalog.presetForFileName(name) }

    fun pillLabel(
        context: Context,
        homeChatMode: String,
        activeLocalModel: String?,
        cloudProvider: String,
        installedLocalModels: List<String>,
    ): String {
        val options =
            buildOptions(
                context = context,
                installedLocalModels = installedLocalModels,
                homeChatMode = homeChatMode,
                activeLocalModel = activeLocalModel,
                cloudProvider = cloudProvider,
            )
        val selected = currentSelectionId(homeChatMode, activeLocalModel, cloudProvider)
        return options.firstOrNull { it.id == selected && it.available }?.pillLabel
            ?: when {
                isCloudMode(homeChatMode, activeLocalModel) -> shortLocalModelLabel(recommendedPreset(deviceTotalRamMb(context)).fileName)
                activeLocalModel == CLOUD_SENTINEL -> shortLocalModelLabel(recommendedPreset(deviceTotalRamMb(context)).fileName)
                !activeLocalModel.isNullOrBlank() -> shortLocalModelLabel(activeLocalModel)
                installedLocalModels.isNotEmpty() -> shortLocalModelLabel(installedLocalModels.first())
                else -> shortLocalModelLabel(recommendedPreset(deviceTotalRamMb(context)).fileName)
            }
    }

    suspend fun applySelection(prefs: VaultPreferencesRepository, option: Option) {
        when (option.kind) {
            Kind.LocalModel -> {
                val file = option.localFileName ?: return
                prefs.setActiveGguf(file)
                prefs.setHomeChatMode("local")
            }
            Kind.CloudTimeweb,
            Kind.CloudEssentialApi -> {
                prefs.setActiveGguf(CLOUD_SENTINEL)
                prefs.setHomeChatMode("timeweb_cloud")
                prefs.setCloudLlmProvider("essential")
            }
            Kind.CloudGemini -> {
                prefs.setActiveGguf(CLOUD_SENTINEL)
                prefs.setHomeChatMode("cloud")
                prefs.setCloudLlmProvider("gemini")
            }
            Kind.CloudOpenAi -> {
                prefs.setActiveGguf(CLOUD_SENTINEL)
                prefs.setHomeChatMode("cloud")
                prefs.setCloudLlmProvider("openai_compat")
            }
        }
    }

    fun shortLocalModelLabel(fileName: String): String {
        if (fileName == CLOUD_SENTINEL) return ESSENTIAL_AI_LABEL
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


