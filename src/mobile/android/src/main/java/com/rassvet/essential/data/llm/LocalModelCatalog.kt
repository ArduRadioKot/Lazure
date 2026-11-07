package com.rassvet.essential.data.llm

object LocalModelCatalog {
    // Every URL is pinned to a Hugging Face commit from before 2026-10-11. That day litert-community re-exported the
    // models with a chat template for LiteRT-LM 0.18 ("content parts"); our runtime is 0.12 (newer ones need Kotlin 2.4)
    // and silently drops the user turn from such files, so the model answers "I cannot assist" / "I'm not sure what
    // you're asking". The pre-October files have the same sizes and work with 0.12.
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
                label = "SmolLM2 360M",
                pageUrl = "https://huggingface.co/litert-community/SmolLM2-360M-Instruct",
                url =
                    "https://huggingface.co/litert-community/SmolLM2-360M-Instruct/" +
                        "resolve/507c99cfe6541ba2bcd84818786f7b025935e5e1/SmolLM2_360M_instruct.litertlm",
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
                pageUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4",
                url =
                    "https://huggingface.co/litert-community/Qwen3-0.6B-int4/" +
                        "resolve/6aa2daf8ab/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
                fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
                ramMb = 1200,
                expectedBytes = 347_251_840L,
                info = "331 МБ · от ~1,2 ГБ RAM",
                description =
                    "Qwen3 в 4-битной сборке без «рассуждений»: отвечает сразу и примерно вдвое быстрее " +
                        "INT8-версии на CPU, занимает вдвое меньше места. Контекст 1280 токенов.",
            ),
            Preset(
                label = "Qwen3 0.6B INT8",
                pageUrl = "https://huggingface.co/litert-community/Qwen3-0.6B",
                url =
                    "https://huggingface.co/litert-community/Qwen3-0.6B/" +
                        "resolve/49837332af6863b008a73a5394ed60789504069d/Qwen3-0.6B.litertlm",
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
                        "resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
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
                        "resolve/2f8b8ee90d8f93b15305b699e8772b277d074a9a/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
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
                        "resolve/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm",
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
                        "resolve/8cd368be75fdb94d5a6f6f5b40f1ab22a6c2543e/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
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
                        "resolve/2eee7ac325f20eb8c9ac1d0e972f7c84663062da/gemma-4-E4B-it.litertlm",
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
                        "resolve/84cc5a35c9c65cd18fcd65bb1f3a7d77a4acfe6e/qwen3_4b_channelwise_int8_float32kv.litertlm",
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


