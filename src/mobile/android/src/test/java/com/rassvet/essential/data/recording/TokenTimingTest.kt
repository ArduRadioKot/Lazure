package com.rassvet.essential.data.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenTimingTest {
    @Test
    fun tokenEndsWhereTheNextTokenStarts() {
        assertEquals(1_420L, TokenTiming.endMs(startMs = 1_000, nextStartMs = 1_420))
    }

    @Test
    fun lastTokenEndsAtEndOfSpeech() {
        // The last token of a VAD segment: "next" is where the VAD says speech stopped.
        assertEquals(2_350L, TokenTiming.endMs(startMs = 2_000, nextStartMs = 2_350))
    }

    @Test
    fun tokenIsNeverShorterThanTheMinimum() {
        assertEquals(1_000L + TokenTiming.MIN_TOKEN_MS, TokenTiming.endMs(startMs = 1_000, nextStartMs = 1_010))
        assertEquals(1_000L + TokenTiming.MIN_TOKEN_MS, TokenTiming.endMs(startMs = 1_000, nextStartMs = 900))
    }

    @Test
    fun aLongGapIsAPauseNotPartOfTheWord() {
        assertEquals(1_000L + TokenTiming.MAX_TOKEN_MS, TokenTiming.endMs(startMs = 1_000, nextStartMs = 9_000))
    }
}
