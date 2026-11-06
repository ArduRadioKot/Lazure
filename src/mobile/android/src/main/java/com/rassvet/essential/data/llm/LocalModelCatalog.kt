package com.rassvet.essential.data.llm

object LocalModelCatalog {
    data class Preset(
        val label: String,
        val url: String,
        val fileName: String,
        val pageUrl: String,
        val ramMb: Int,

        val info: String,

        val description: String,
        val expectedBytes: Long? = null,
        val sha256Hex: String? = null,
    ) {
        val sizeLabel: String
            get() = expectedBytes?.let { formatDownloadSize(it) } ?: info.substringBefore(" ·").trim()
    }

    val presets =
        listOf(
            Preset(
                label = "Gemma 3 270M",
                pageUrl = "https://huggingface.co/litert-community/gemma-3-270m-it",
                url =
                    "https://huggingface.co/litert-community/gemma-3-270m-it/" +
                        "resolve/main/gemma3-270m-it-q8.litertlm",
                fileName = "gemma3-270m-it-q8.litertlm",
                ramMb = 900,
                expectedBytes = 304_005_120L,
                info = "290 МБ · от ~900 МБ RAM",
                description =
                    "Ультра-лёгкая модель Google. Мгновенные ответы на простые вопросы — " +
                        "идеально для старых телефонов и быстрых заметок offline.",
            ),
            Preset(
                label = "SmolLM2 360M",
                pageUrl = "https://huggingface.co/litert-community/SmolLM2-360M-Instruct",
                url =
                    "https://huggingface.co/litert-community/SmolLM2-360M-Instruct/" +
                        "resolve/main/SmolLM2_360M_instruct.litertlm",
                fileName = "SmolLM2_360M_instruct.litertlm",
                ramMb = 1100,
                expectedBytes = 373_719_040L,
                info = "360 МБ · от ~1,1 ГБ RAM",
                description =
                    "Компактная instruct-модель HuggingFace. Хороша для черновиков, кратких " +
                        "сводок и простых диалогов на устройствах с 3–4 ГБ RAM.",
            ),
            Preset(
                label = "Qwen3 0.6B",
                pageUrl = "https://huggingface.co/litert-community/Qwen3-0.6B",
                url =
                    "https://huggingface.co/litert-community/Qwen3-0.6B/" +
                        "resolve/main/Qwen3-0.6B.litertlm",
                fileName = "Qwen3-0.6B.litertlm",
                ramMb = 1800,
                expectedBytes = 614_236_160L,
                info = "585 МБ · от ~1,8 ГБ RAM",
                description =
                    "Новое поколение Qwen — быстрее Qwen 2.5 при схожем размере. Подходит для " +
                        "повседневных вопросов и работы с заметками без интернета.",
            ),
            Preset(
                label = "Qwen 2.5 1.5B",
                pageUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
                url =
                    "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/" +
                        "resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                ramMb = 2200,
                expectedBytes = 1_597_931_520L,
                info = "1,5 ГБ · от ~2,2 ГБ RAM",
                description =
                    "Проверенная классика LiteRT-LM. Баланс скорости и качества для телефонов " +
                        "с 4–6 ГБ RAM — рекомендуемый старт для большинства пользователей.",
            ),
            Preset(
                label = "DeepSeek R1 1.5B",
                pageUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
                url =
                    "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/" +
                        "resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
                fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
                ramMb = 3000,
                expectedBytes = 1_833_451_520L,
                info = "1,7 ГБ · от ~3 ГБ RAM",
                description =
                    "Reasoning-модель DeepSeek R1 (дистилляция Qwen 1.5B). Лучше справляется " +
                        "с логикой, планированием и пошаговыми ответами offline.",
            ),
            Preset(
                label = "Gemma 4 E2B",
                pageUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
                url =
                    "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/" +
                        "resolve/main/gemma-4-E2B-it.litertlm",
                fileName = "gemma-4-E2B-it.litertlm",
                ramMb = 5200,
                expectedBytes = 2_588_147_712L,
                info = "2,6 ГБ · от ~5 ГБ RAM",
                description =
                    "Флагман Google Gemma 4 среднего размера. Отличный баланс качества и скорости, " +
                        "длинный контекст — оптимальна для телефонов с 6+ ГБ RAM.",
            ),
            Preset(
                label = "Phi-4 mini",
                pageUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct",
                url =
                    "https://huggingface.co/litert-community/Phi-4-mini-instruct/" +
                        "resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                fileName = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                ramMb = 7500,
                expectedBytes = 3_910_090_752L,
                info = "3,6 ГБ · от ~7,5 ГБ RAM",
                description =
                    "Microsoft Phi-4 mini — сильная малая модель для кода, структурированных " +
                        "ответов и точных формулировок. Нужен телефон с 8+ ГБ RAM.",
            ),
            Preset(
                label = "Gemma 4 E4B",
                pageUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
                url =
                    "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/" +
                        "resolve/main/gemma-4-E4B-it.litertlm",
                fileName = "gemma-4-E4B-it.litertlm",
                ramMb = 6800,
                expectedBytes = 3_659_530_240L,
                info = "3,7 ГБ · от ~7 ГБ RAM",
                description =
                    "Самая мощная Gemma в каталоге. Лучше справляется со сложными вопросами и " +
                        "длинными диалогами offline — нужен флагман с 8+ ГБ RAM.",
            ),
            Preset(
                label = "Qwen3 4B",
                pageUrl = "https://huggingface.co/litert-community/Qwen3-4B",
                url =
                    "https://huggingface.co/litert-community/Qwen3-4B/" +
                        "resolve/main/qwen3_4b_channelwise_int8_float32kv.litertlm",
                fileName = "qwen3_4b_channelwise_int8_float32kv.litertlm",
                ramMb = 9000,
                expectedBytes = 5_672_370_176L,
                info = "5,3 ГБ · от ~9 ГБ RAM",
                description =
                    "Топовая Qwen3 для offline на Android. Максимальное качество ответов и " +
                        "работа с длинным контекстом — только для мощных устройств (10+ ГБ RAM).",
            ),
        )

    fun presetForUrl(url: String): Preset? {
        val u = url.trim()
        return presets.firstOrNull { p -> p.url == u || u.startsWith(p.url) || p.fileName == guessFileName(u) }
    }

    fun presetForFileName(fileName: String): Preset? =
        presets.find { it.fileName == fileName }

    private fun guessFileName(url: String): String =
        url
            .trim()
            .substringAfterLast('/')
            .substringBefore('?')
            .substringBefore('#')

    fun formatDownloadSize(bytes: Long): String {
        if (bytes <= 0L) return "—"
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format("%.1f ГБ", gb)
        } else {
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            String.format("%.0f МБ", mb)
        }
    }
}


typealias GgufDownloadCatalog = LocalModelCatalog


