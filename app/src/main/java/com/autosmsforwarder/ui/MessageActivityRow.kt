package com.autosmsforwarder.ui

import com.autosmsforwarder.data.ForwardLogEntity
import com.autosmsforwarder.data.WebLogEntity
import com.autosmsforwarder.data.WebStatus
import com.autosmsforwarder.domain.ForwardStatus

data class MessageActivityRow(
    val dedupeKey: String,
    val receivedAt: Long,
    val sender: String,
    val snippet: String,
    val smsStatus: ForwardStatus?,
    val webStatus: WebStatus?
)

fun mergeMessageActivity(forward: List<ForwardLogEntity>, web: List<WebLogEntity>): List<MessageActivityRow> {
    val webByKey = web.associateBy { it.dedupeKey }
    val forwardKeys = forward.mapTo(mutableSetOf()) { it.dedupeKey }
    return (forward.map { row ->
        MessageActivityRow(row.dedupeKey, row.receivedAt, row.sender, row.snippet,
            row.status, webByKey[row.dedupeKey]?.status)
    } + web.filterNot { it.dedupeKey in forwardKeys }.map { row ->
        MessageActivityRow(row.dedupeKey, row.receivedAt, row.sender, row.snippet,
            null, row.status)
    }).sortedWith(compareByDescending<MessageActivityRow> { it.receivedAt }.thenByDescending { it.dedupeKey })
}
