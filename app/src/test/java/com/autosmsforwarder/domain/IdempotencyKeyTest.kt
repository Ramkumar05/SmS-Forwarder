package com.autosmsforwarder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdempotencyKeyTest {
    @Test
    fun identicalMessageHasStableOpaqueKey() {
        val first = IdempotencyKey.create("+919876543210", 123L, "नमस्ते 👋")
        val duplicate = IdempotencyKey.create("+919876543210", 123L, "नमस्ते 👋")

        assertEquals(first, duplicate)
        assertEquals(64, first.length)
        assertFalse(first.contains("9876543210"))
    }

    @Test
    fun changingAnyIdentityFieldChangesKey() {
        val original = IdempotencyKey.create("123", 456L, "hello")

        assertNotEquals(original, IdempotencyKey.create("124", 456L, "hello"))
        assertNotEquals(original, IdempotencyKey.create("123", 457L, "hello"))
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
