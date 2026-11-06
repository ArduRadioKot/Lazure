package com.rassvet.essential.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaRuntimeTuningTest {
    @Test
    fun cpuThreadBudget_scalesWithRam() {
        assertTrue(LlamaRuntimeTuning.cpuThreadBudget(deviceRamMb = 12_000) >= 4)
        assertEquals(2, LlamaRuntimeTuning.cpuThreadBudget(deviceRamMb = 2_000, modelId = "smollm"))
    }

    @Test
    fun tune_capsContextForLowRam() {
        val params =
            LlamaRuntimeParams(
                contextSize = 8192,
                maxTokens = 1024,
                nThreads = 8,
                temperature = 0.7f,
                topP = 0.95f,
                topK = 40,
            )
        val tuned = LlamaRuntimeTuning.tune(params, modelId = "qwen2.5-0.5", deviceRamMb = 3_000)
        assertTrue(tuned.contextSize <= 1024)
        assertTrue(tuned.maxTokens <= 256)
    }

    @Test
    fun vaultCharsForModel_smallerOnLowRam() {
        val low = LlamaRuntimeTuning.vaultCharsForModel("qwen-0.5", deviceRamMb = 3_000)
        val high = LlamaRuntimeTuning.vaultCharsForModel("qwen-0.5", deviceRamMb = 12_000)
        assertTrue(high > low)
    }

    @Test
    fun promptCharCap_positiveForAllProfiles() {
        assertTrue(LlamaRuntimeTuning.promptCharCap("gemma", 6_000) > 0)
        assertTrue(LlamaRuntimeTuning.promptCharCap("smollm", 4_000) > 0)
    }
}
