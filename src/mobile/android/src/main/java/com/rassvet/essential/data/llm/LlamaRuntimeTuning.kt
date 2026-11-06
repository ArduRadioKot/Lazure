package com.rassvet.essential.data.llm


object LlamaRuntimeTuning {
    fun deviceRamMb(context: android.content.Context): Long =
        ChatModelSelection.deviceTotalRamMb(context)

    fun cpuThreadBudget(deviceRamMb: Long, modelId: String = ""): Int {
        val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val id = modelId.lowercase()
        if (id.contains("smollm") || id.contains("135m")) {
            return minOf(cores, 2)
        }
        return when {
            deviceRamMb >= 10_000 -> minOf(cores, 8)
            deviceRamMb >= 6_000 -> minOf(cores, 6)
            deviceRamMb >= 3_500 -> minOf(cores, 4)
            else -> 2
        }
    }

    fun tune(params: LlamaRuntimeParams, modelId: String, deviceRamMb: Long): LlamaRuntimeParams =
        params.forDevice(modelId, deviceRamMb)

    private fun LlamaRuntimeParams.forDevice(modelId: String, deviceRamMb: Long): LlamaRuntimeParams {
        val id = modelId.lowercase()
        val threads = cpuThreadBudget(deviceRamMb, modelId)

        val (ctxCap, maxTokCap) =
            when {
                id.contains("smollm") || id.contains("135m") ->
                    when {
                        deviceRamMb >= 8_000 -> 768 to 192
                        else -> 512 to 128
                    }
                id.contains("qwen2.5-0.5") || (id.contains("qwen") && id.contains("0.5")) ->
                    when {
                        deviceRamMb >= 10_000 -> 4096 to 512
                        deviceRamMb >= 6_000 -> 2048 to 384
                        else -> 1024 to 256
                    }
                id.contains("gemma") ->
                    when {
                        deviceRamMb >= 10_000 -> 2048 to 384
                        deviceRamMb >= 6_000 -> 1536 to 256
                        else -> 1024 to 192
                    }
                else ->
                    when {
                        deviceRamMb >= 10_000 -> 4096 to 512
                        deviceRamMb >= 6_000 -> 3072 to 384
                        else -> 2048 to 256
                    }
            }

        return copy(
            nThreads = threads,
            contextSize = maxOf(minOf(contextSize, ctxCap), 1024),
            maxTokens = maxOf(minOf(maxTokens, maxTokCap), 256),
        )
    }

    fun mergedContextCharLimit(modelId: String, deviceRamMb: Long): Int {
        val id = modelId.lowercase()
        return when {
            id.contains("smollm") || id.contains("135m") ->
                if (deviceRamMb >= 8_000) 2000 else 1400
            id.contains("qwen") && id.contains("0.5") ->
                if (deviceRamMb >= 10_000) 8000 else if (deviceRamMb >= 6_000) 4500 else 1800
            else -> if (deviceRamMb >= 8_000) 10_000 else 5500
        }
    }

    fun vaultCharsForModel(modelId: String, deviceRamMb: Long): Int {
        val id = modelId.lowercase()
        return when {
            id.contains("smollm") || id.contains("135m") ->
                if (deviceRamMb >= 8_000) 200 else 150
            id.contains("qwen") && id.contains("0.5") ->
                if (deviceRamMb >= 10_000) 2500 else if (deviceRamMb >= 6_000) 1500 else 700
            id.contains("gemma") ->
                if (deviceRamMb >= 10_000) 1200 else if (deviceRamMb >= 6_000) 800 else 400
            else -> if (deviceRamMb >= 8_000) 3500 else 3000
        }
    }

    fun webCharsForModel(modelId: String, deviceRamMb: Long): Int {
        val vault = vaultCharsForModel(modelId, deviceRamMb)
        return (vault * 0.7).toInt().coerceIn(400, 1800)
    }

    fun systemOverlayCharCap(modelId: String): Int {
        val id = modelId.lowercase()
        return when {
            id.contains("smollm") || id.contains("135m") -> 350
            id.contains("qwen") && id.contains("0.5") -> 1200
            id.contains("gemma") -> 900
            else -> 1800
        }
    }

    fun promptCharCap(modelId: String, deviceRamMb: Long): Int {
        val id = modelId.lowercase()
        return when {
            id.contains("smollm") || id.contains("135m") ->
                if (deviceRamMb >= 8_000) 400 else 320
            id.contains("qwen") && id.contains("0.5") ->
                if (deviceRamMb >= 10_000) 6000 else if (deviceRamMb >= 6_000) 3500 else 1500
            id.contains("gemma") ->
                if (deviceRamMb >= 10_000) 4500 else if (deviceRamMb >= 6_000) 2800 else 1200
            else -> if (deviceRamMb >= 8_000) 10_000 else 6000
        }
    }
}


