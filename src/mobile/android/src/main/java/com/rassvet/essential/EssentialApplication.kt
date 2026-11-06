package com.rassvet.essential

import android.app.Application
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.llm.LlmComputeBackend
import com.rassvet.essential.data.llm.LiteRtLmCapabilities
import com.rassvet.essential.litert.LiteRtNativeBootstrap
import com.rassvet.essential.locale.AppLocales
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class EssentialApplication : Application() {
    @Inject
    lateinit var prefs: VaultPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        LiteRtNativeBootstrap.ensureLoaded()
        runBlocking {
            val tag =
                if (prefs.hasAppLocaleTag()) {
                    prefs.resolveAppLocaleTag()
                } else {
                    val initial = prefs.resolveAppLocaleTag()
                    prefs.setAppLocaleTag(initial)
                    initial
                }
            AppLocales.apply(tag)
            if (!prefs.hasLlmComputeBackend()) {
                val backend =
                    if (LiteRtLmCapabilities.isGpuAvailable()) {
                        LlmComputeBackend.GPU
                    } else {
                        LlmComputeBackend.CPU
                    }
                prefs.setLlmComputeBackend(backend)
            }
        }
    }
}
