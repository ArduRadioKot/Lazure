package com.rassvet.essential.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.Bundle
import com.rassvet.essential.ui.components.LazureNavigationBar
import com.rassvet.essential.ui.home.MeetingHomeScreen
import com.rassvet.essential.ui.theme.EssentialTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiPolishDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun homeLight() = verify(false, 1f, "en", "home-light")
    @Test fun homeDark() = verify(true, 1f, "en", "home-dark")
    @Test fun homeLargeLight() = verify(false, 1.6f, "ru", "home-light-font160")
    @Test fun homeLargeDark() = verify(true, 1.6f, "ru", "home-dark-font160")

    private fun verify(dark: Boolean, scale: Float, language: String, name: String) {
        var clicks = 0
        val configuration = Configuration(rule.activity.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(language))) }
        val context = rule.activity.createConfigurationContext(configuration)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, scale),
            ) {
                Box(Modifier.requiredSize(320.dp, 640.dp).testTag("device_polish_surface")) {
                    EssentialTheme(darkTheme = dark, dynamicColor = false) {
                        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { LazureNavigationBar("home") {} }) { padding ->
                            Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                                MeetingHomeScreen(emptyList(), { clicks++ }, {}, {}, {}, {}, {})
                            }
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("nav_home").assertIsSelected().assertHasClickAction()
        saveImage(name)
        rule.onNodeWithTag("home_feed").performScrollToNode(hasTestTag("home_empty_record"))
        rule.onNodeWithTag("home_empty_record").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
        saveImage("$name-empty")
    }

    private fun saveImage(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: File(rule.activity.externalMediaDirs.first(), "additional_test_output")
        dir.mkdirs()
        val bitmap = rule.onNodeWithTag("device_polish_surface").captureToImage().asAndroidBitmap()
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        instrumentation.sendStatus(2, Bundle().apply { putString("additionalTestOutputFile_$name", file.absolutePath) })
    }
}
