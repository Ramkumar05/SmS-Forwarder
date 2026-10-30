package com.autosmsforwarder.domain

import java.security.MessageDigest

object WebIdempotencyKey {
    fun create(localKey: String, sourcePhone: String, sourceName: String): String {
        val phone = sourcePhone.trim()
        val name = sourceName.trim()
        if (phone.isEmpty() && name.isEmpty()) return localKey
        val identity = "app-source-v1|$localKey|${phone.length}:$phone|${name.length}:$name"
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
