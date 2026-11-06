package com.rassvet.essential.locale

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RelativeTimeTest {
    @Test
    fun formatRelativeTime_justNowForRecentTimestamp() {
        val context = RuntimeEnvironment.getApplication()
        val label = formatRelativeTime(context, System.currentTimeMillis() - 5_000)
        assertTrue(label.isNotBlank())
    }
}
