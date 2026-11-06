package com.rassvet.essential.data.chat

import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDeviceClockTest {
    @Test
    fun systemDateTimeLine_containsDeviceHint() {
        val line = ChatDeviceClock.systemDateTimeLine()
        assertTrue(line.contains("Текущие дата и время"))
        assertTrue(line.contains("устройстве"))
    }
}
