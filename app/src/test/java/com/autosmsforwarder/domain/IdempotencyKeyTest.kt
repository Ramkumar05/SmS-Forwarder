package com.autosmsforwarder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdempotencyKeyTest {
    @Test
    fun identicalMessageHasStableKeyFromNormalizedSenderAndSmsTimestamp() {
        val first = IdempotencyKey.create("98765 43210", 123L, "नमस्ते 👋")
        val identicalInputs = IdempotencyKey.create("98765 43210", 123L, "नमस्ते 👋")
        val duplicate = IdempotencyKey.create("+91 (98765) 43210", 123L, "नमस्ते 👋")

        assertEquals(first, identicalInputs)
        assertEquals(first, duplicate)
        assertEquals("dbc63828d2e34b9129e1f09c50e9b27d675a9f8838b50f2cd3a31e6b7adc0819", first)
        assertEquals(64, first.length)
        assertFalse(first.contains("9876543210"))
    }

    @Test
    fun identicalBodiesWithDifferentSmsTimestampsHaveDifferentKeys() {
        assertNotEquals(
            IdempotencyKey.create("+919876543210", 456L, "hello"),
            IdempotencyKey.create("+919876543210", 457L, "hello")
        )
    }

    @Test
    fun changingSenderOrBodyChangesKey() {
        val original = IdempotencyKey.create("123", 456L, "hello")

        assertNotEquals(original, IdempotencyKey.create("124", 456L, "hello"))
        assertNotEquals(original, IdempotencyKey.create("123", 456L, "hello!"))
    }

    @Test
    fun emptyBodyIsStableAndFieldBoundariesRemainDistinct() {
        assertEquals(
            IdempotencyKey.create("123", 456L, ""),
            IdempotencyKey.create("123", 456L, "")
        )
        assertNotEquals(
            IdempotencyKey.create("12", 34L, "5"),
            IdempotencyKey.create("1", 234L, "5")
        )
    }
}
