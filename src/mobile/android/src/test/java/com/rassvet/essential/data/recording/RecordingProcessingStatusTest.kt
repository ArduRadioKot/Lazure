package com.rassvet.essential.data.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingProcessingStatusTest {
    @Test
    fun validLifecycleAllowsQueueProcessingAndCompletion() {
        assertTrue(RecordingProcessingStatus.AwaitingAsr.canTransitionTo(RecordingProcessingStatus.Queued))
        assertTrue(RecordingProcessingStatus.Queued.canTransitionTo(RecordingProcessingStatus.Processing))
        assertTrue(RecordingProcessingStatus.Processing.canTransitionTo(RecordingProcessingStatus.Completed))
    }

    @Test
    fun lifecycleRejectsSkippingProcessingAndRestartingCompletedWork() {
        assertFalse(RecordingProcessingStatus.AwaitingAsr.canTransitionTo(RecordingProcessingStatus.Completed))
        assertFalse(RecordingProcessingStatus.Processing.canTransitionTo(RecordingProcessingStatus.Queued))
        assertTrue(RecordingProcessingStatus.Completed.canTransitionTo(RecordingProcessingStatus.Queued))
    }

    @Test
    fun persistedStatusValuesRoundTrip() {
        RecordingProcessingStatus.entries.forEach { status ->
            assertEquals(status, RecordingProcessingStatus.fromStoredValue(status.storedValue))
        }
        assertEquals(null, RecordingProcessingStatus.fromStoredValue("fake"))
    }
}
