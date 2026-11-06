package com.rassvet.essential.litert

import android.util.Log


object LiteRtNativeBootstrap {
    private const val TAG = "LiteRtBootstrap"

    @Volatile
    private var loaded = false

    @Volatile
    var openClSamplerPreloaded: Boolean = false
        private set

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                System.loadLibrary("litert_bootstrap")
                openClSamplerPreloaded = nativeIsOpenClSamplerReady()
                loaded = true
                Log.i(
                    TAG,
                    if (openClSamplerPreloaded) {
                        "LiteRT bootstrap OK (GPU sampling ready)"
                    } else {
                        "LiteRT bootstrap OK (GPU sampling unavailable)"
                    },
                )
            }.onFailure { e ->
                Log.e(TAG, "LiteRT bootstrap failed", e)
            }
        }
    }

    private external fun nativeIsOpenClSamplerReady(): Boolean
}


