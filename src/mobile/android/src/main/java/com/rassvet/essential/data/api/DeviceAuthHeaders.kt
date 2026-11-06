package com.rassvet.essential.data.api

import android.content.Context
import android.os.Build
import com.rassvet.essential.BuildConfig
import okhttp3.Request

object DeviceAuthHeaders {
    @JvmStatic
    fun apply(builder: Request.Builder) {
        builder.header("X-Lazure-Device-Name", deviceLabel())
        builder.header("X-Lazure-Device-Platform", devicePlatform())
        builder.header("X-Lazure-App-Version", BuildConfig.VERSION_NAME)
    }

    fun deviceLabel(): String = Build.MODEL?.trim()?.takeIf { it.isNotEmpty() } ?: "Android"

    fun devicePlatform(): String = "Android ${Build.VERSION.RELEASE.orEmpty()}".trim()
}


