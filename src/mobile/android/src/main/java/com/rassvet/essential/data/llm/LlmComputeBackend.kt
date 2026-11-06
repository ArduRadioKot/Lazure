package com.rassvet.essential.data.llm

import com.google.ai.edge.litertlm.Backend


object LlmComputeBackend {
    const val CPU = "cpu"
    const val GPU = "gpu"

    fun toBackend(backend: String): Backend =
        if (isGpu(backend)) {
            Backend.GPU()
        } else {
            Backend.CPU()
        }

    fun isGpu(backend: String): Boolean = backend == GPU
}


