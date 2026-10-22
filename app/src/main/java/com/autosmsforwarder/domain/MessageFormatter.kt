package com.autosmsforwarder.domain

object MessageFormatter {
    fun format(sender: String, body: String): String = "FWD from $sender: $body"
}
