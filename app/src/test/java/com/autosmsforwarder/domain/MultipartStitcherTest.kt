package com.autosmsforwarder.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MultipartStitcherTest {
    @Test
    fun joinsPartsInBroadcastOrder() {
        val parts = listOf(
            SmsPart("+919876543210", "Hello ", 123L),
            SmsPart("+919876543210", "🌍", 123L)
        )

        assertEquals(
            listOf(IncomingMessage("+919876543210", "Hello 🌍", 123L)),
            MultipartStitcher.stitch(parts)
        )
    }

    @Test
    fun differentSendersOrSmsTimestampsStaySeparate() {
        val parts = listOf(
            SmsPart("111", "one", 123L),
            SmsPart("222", "two", 123L),
            SmsPart("111", "later", 124L)
        )

        assertEquals(
            listOf(
                IncomingMessage("111", "one", 123L),
                IncomingMessage("222", "two", 123L),
                IncomingMessage("111", "later", 124L)
            ),
            MultipartStitcher.stitch(parts)
        )
    }

    @Test
    fun emptyPartsAndNoPartsAreHandled() {
        assertEquals(emptyList<IncomingMessage>(), MultipartStitcher.stitch(emptyList()))
        assertEquals(
            listOf(IncomingMessage("111", "", 123L)),
            MultipartStitcher.stitch(listOf(SmsPart("111", "", 123L)))
        )
    }
}
