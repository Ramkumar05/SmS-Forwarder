package com.autosmsforwarder.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object IdempotencyKey {
    fun create(sender: String, smsTimestampMillis: Long, body: String): String {
        val identity = "${NumberNormalizer.normalize(sender)}|$smsTimestampMillis|$body"
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
