package com.autosmsforwarder.domain

object NumberNormalizer {
    fun normalize(raw: String): String {
        val digits = raw.filter { it in '0'..'9' }
        if (digits.isEmpty()) return ""

        val firstDigit = raw.indexOfFirst { it in '0'..'9' }
        val hasLeadingPlus = raw.substring(0, firstDigit).contains('+')
        return when {
            hasLeadingPlus -> "+$digits"
            digits.length == 10 -> "+91$digits"
            else -> digits
        }
    }

    fun sameForLoop(sender: String, target: String): Boolean {
        val senderDigits = normalize(sender).filter { it in '0'..'9' }
        val targetDigits = normalize(target).filter { it in '0'..'9' }
        return senderDigits.length >= 10 &&
            targetDigits.length >= 10 &&
            senderDigits.takeLast(10) == targetDigits.takeLast(10)
    }
}
