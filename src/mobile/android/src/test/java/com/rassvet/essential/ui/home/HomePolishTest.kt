package com.rassvet.essential.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.rassvet.essential.ui.components.LazureNavigationBar
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import com.rassvet.essential.R
import com.rassvet.essential.ui.theme.EssentialTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en-rUS-w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomePolishTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun smallLight() = checkHome(false, 1f, "light-320")
    @Test fun smallDark() = checkHome(true, 1f, "dark-320")
    @Test @Config(qualifiers = "ru-rRU-w320dp-h640dp")
    fun largeFontLightRussian() = checkHome(false, 1.6f, "light-320-ru-font160")
    @Test @Config(qualifiers = "ru-rRU-w320dp-h640dp")
    fun largeFontDarkRussian() = checkHome(true, 1.6f, "dark-320-ru-font160")

    @Test @Config(qualifiers = "ru-rRU-w320dp-h640dp")
    fun chatHeaderLargeLight() = checkChatHeader(false, "chat-header-light-font160")
    @Test @Config(qualifiers = "ru-rRU-w320dp-h640dp")
    fun chatHeaderLargeDark() = checkChatHeader(true, "chat-header-dark-font160")

    private fun checkChatHeader(dark: Boolean, name: String) {
        var opened = 0
        val modelName = "Qwen 2.5 — long local model name"
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                EssentialTheme(darkTheme = dark, dynamicColor = false) {
                    Scaffold(topBar = { com.rassvet.essential.ui.chat.ChatHeader(modelName, {}, { opened++ }) }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding))
                    }
                }
            }
        }
        rule.onNodeWithText(rule.activity.getString(R.string.chat_title)).assertIsDisplayed()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.chat_models_chip)).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText(modelName).performClick()
        rule.runOnIdle { assertEquals(2, opened) }
        screenshot(name)
    }

    @Test @Config(qualifiers = "ru-rRU-w320dp-h640dp")
    fun settingsLargeDark() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                EssentialTheme(darkTheme = true, dynamicColor = false) {
                    com.rassvet.essential.ui.settings.SettingsScreen(com.rassvet.essential.data.local.VaultPreferencesRepository(rule.activity), {})
                }
            }
        }
        screenshot("settings-dark-font160")
        rule.onNodeWithText(rule.activity.getString(R.string.pin_setting)).performScrollTo().assertIsDisplayed()
        screenshot("settings-dark-font160-pin")
    }

    private fun checkHome(dark: Boolean, scale: Float, name: String) {
        var recordClicks = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                EssentialTheme(darkTheme = dark, dynamicColor = false) {
                    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { LazureNavigationBar("home") {} }) { padding ->
                        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                            MeetingHomeScreen(emptyList(), { recordClicks++ }, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        screenshot(name + "-initial")
        val ids = listOf(R.string.home_action_live, R.string.home_action_import, R.string.home_action_models, R.string.home_action_vault)
        val bounds = ids.map { id ->
            val title = rule.activity.getString(id)
            val card = rule.onNodeWithTag("home_action_$title")
            card.assertHasClickAction().assertHeightIsAtLeast(48.dp)
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNode(hasText(title) and hasAnyAncestor(hasTestTag("home_action_$title")), useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Title clipped: $title", layouts.all { layout ->
                !layout.didOverflowHeight && (0 until layout.lineCount).all { line ->
                    !layout.isLineEllipsized(line) && layout.getLineRight(line) <= layout.size.width + 1f
                }
            })
            card.getUnclippedBoundsInRoot()
        }
        bounds.forEach { assertEquals((bounds.first().right - bounds.first().left).value, (it.right - it.left).value, 1f) }
        assertEquals((bounds[0].bottom - bounds[0].top).value, (bounds[1].bottom - bounds[1].top).value, 1f)
        assertEquals((bounds[2].bottom - bounds[2].top).value, (bounds[3].bottom - bounds[3].top).value, 1f)
        rule.onNodeWithTag("nav_home").assertIsSelected().assertHasClickAction()
        rule.onNodeWithTag("nav_vault").assertHasClickAction()
        screenshot(name)
        rule.onNodeWithTag("home_feed").performScrollToNode(hasTestTag("home_empty_record"))
        rule.onNodeWithTag("home_empty_record").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, recordClicks) }
        screenshot("$name-empty")
    }

    private fun screenshot(name: String) {
        val target = File("build/reports/ui-polish/$name.png")
        target.parentFile?.mkdirs()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
