package com.autosmsforwarder.domain

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object IdempotencyKey {
    fun create(sender: String, receivedAtMillis: Long, body: String): String {
        val senderBytes = sender.toByteArray(StandardCharsets.UTF_8)
        val bodyHash = sha256(body.toByteArray(StandardCharsets.UTF_8))
        val identity = ByteBuffer.allocate(Int.SIZE_BYTES + senderBytes.size + Long.SIZE_BYTES + bodyHash.size)
            .putInt(senderBytes.size)
            .put(senderBytes)
            .putLong(receivedAtMillis)
            .put(bodyHash)
            .array()
        return sha256(identity).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
