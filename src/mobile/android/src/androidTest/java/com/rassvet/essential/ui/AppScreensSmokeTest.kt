package com.rassvet.essential.ui

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rassvet.essential.MainActivity
import com.rassvet.essential.R
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the actual Hilt-backed app and exercises current navigation without importing or recording audio. */
@RunWith(AndroidJUnit4::class)
class AppScreensSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun existingScreensAndPinSetupRemainReachable() {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("nav_home").fetchSemanticsNodes().isNotEmpty() }
        save("app-home")
        rule.onNodeWithTag("nav_vault").performClick()
        rule.onNodeWithText(text(R.string.recordings_title)).assertIsDisplayed()
        save("app-archive")
        rule.onNodeWithTag("nav_ai").performClick()
        rule.onNodeWithText(text(R.string.chat_welcome_title)).assertIsDisplayed()
        save("app-chat")
        rule.onNodeWithTag("nav_settings").performClick()
        rule.onNode(hasText(text(R.string.settings_title)) and !hasTestTag("nav_settings")).assertIsDisplayed()
        save("app-settings")
        rule.onNodeWithTag("pin_toggle").performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.pin_setup_title)).assertIsDisplayed()
        save("app-pin-setup", "pin_form")
        back()
        rule.onNodeWithTag("nav_home").performClick()
        rule.onNodeWithText(text(R.string.home_action_models)).performClick()
        rule.onNodeWithText(text(R.string.chat_pick_model)).assertIsDisplayed()
        save("app-models")
        back()
        rule.onNodeWithText(text(R.string.home_action_live)).performClick()
        rule.onNodeWithText(text(R.string.live_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.live_start), substring = true).assertIsDisplayed()
        save("app-live")
    }

    private fun text(id: Int) = rule.activity.getString(id)
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }
    private fun save(name: String, tag: String? = null) {
        rule.waitForIdle()
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: File(rule.activity.externalMediaDirs.first(), "additional_test_output")
        dir.mkdirs()
        val bitmap = (if (tag == null) rule.onRoot() else rule.onNodeWithTag(tag)).captureToImage().asAndroidBitmap()
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("additionalTestOutputFile_$name", file.absolutePath) })
    }
}
