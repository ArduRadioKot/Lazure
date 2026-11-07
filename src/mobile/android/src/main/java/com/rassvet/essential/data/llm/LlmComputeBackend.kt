package com.rassvet.essential.data.llm

import com.google.ai.edge.litertlm.Backend


object LlmComputeBackend {
    const val CPU = "cpu"
    const val GPU = "gpu"

    /** [cpuThreads]: decode is memory-bound, so more threads than big cores slow it down; null keeps the library default. */
    fun toBackend(backend: String, cpuThreads: Int? = null): Backend =
        if (isGpu(backend)) {
            Backend.GPU()
        } else {
            Backend.CPU(cpuThreads)
        }

    fun isGpu(backend: String): Boolean = backend == GPU
}


