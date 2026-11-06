package com.rassvet.essential.data.llm

import com.rassvet.essential.litert.LiteRtNativeBootstrap


object LiteRtLmCapabilities {
    val isAvailable: Boolean =
        runCatching {
            Class.forName("com.google.ai.edge.litertlm.Engine")
            true
        }.getOrDefault(false)

    fun isGpuAvailable(): Boolean = isAvailable

    fun isGpuSamplingAvailable(): Boolean = LiteRtNativeBootstrap.openClSamplerPreloaded
    fun gpuDeviceDescription(): String =
        if (LiteRtNativeBootstrap.openClSamplerPreloaded) {
            "LiteRT-LM GPU (OpenCL + GPU sampling)"
        } else {
            "LiteRT-LM GPU (OpenCL, sampling CPU)"
        }
}


