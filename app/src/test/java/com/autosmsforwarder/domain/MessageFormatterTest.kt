package com.autosmsforwarder.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageFormatterTest {
    @Test
    fun preservesEmojiAndNonLatinBody() {
        assertEquals(
            "FWD from +919876543210: नमस्ते 👋",
            MessageFormatter.format("+919876543210", "नमस्ते 👋")
        )
    }

    @Test
    fun acceptsEmptyBody() {
        assertEquals("FWD from 12345: ", MessageFormatter.format("12345", ""))
    }
}
