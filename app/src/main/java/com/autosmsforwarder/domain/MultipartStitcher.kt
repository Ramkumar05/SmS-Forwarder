package com.autosmsforwarder.domain

data class SmsPart(val sender: String, val body: String, val smsTimestampMillis: Long)

object MultipartStitcher {
    fun stitch(parts: List<SmsPart>): List<IncomingMessage> =
        parts.groupBy { it.sender to it.smsTimestampMillis }
            .map { (identity, groupedParts) ->
                IncomingMessage(identity.first, groupedParts.joinToString("") { it.body }, identity.second)
            }
}
