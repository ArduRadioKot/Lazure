package com.rassvet.essential.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLocales {
    const val SYSTEM = "system"
    const val RU = "ru"
    const val EN = "en"

    fun apply(tag: String) {
        val locales =
            when (tag) {
                RU -> LocaleListCompat.forLanguageTags("ru")
                EN -> LocaleListCompat.forLanguageTags("en")
                else -> LocaleListCompat.getEmptyLocaleList()
            }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    val supportedTags: List<String> = listOf(SYSTEM, RU, EN)

    fun nextTag(current: String): String {
        val idx = supportedTags.indexOf(current).let { if (it < 0) 0 else it }
        return supportedTags[(idx + 1) % supportedTags.size]
    }


    fun tagForDeviceLanguage(languageCode: String): String =
        when (languageCode.lowercase()) {
            "ru", "uk", "be" -> RU
            else -> EN
        }
}


