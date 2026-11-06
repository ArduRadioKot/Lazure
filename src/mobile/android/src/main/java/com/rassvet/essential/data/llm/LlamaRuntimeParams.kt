package com.rassvet.essential.data.llm


data class LlamaRuntimeParams(
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val maxTokens: Int,
    val contextSize: Int,
    val nThreads: Int,
) {
    companion object {
        val Default =
            LlamaRuntimeParams(
                temperature = 0.6f,
                topP = 0.9f,
                topK = 40,
                maxTokens = 1024,
                contextSize = 4096,
                nThreads = 4,
            )
    }
}


