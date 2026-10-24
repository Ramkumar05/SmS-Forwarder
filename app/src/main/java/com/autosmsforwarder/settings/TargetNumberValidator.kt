package com.autosmsforwarder.settings

object TargetNumberValidator {
    private val pattern = Regex("\\+?[0-9]{10,15}")

    fun isValid(number: String): Boolean = pattern.matches(number)

    fun canonical(number: String): String {
        require(isValid(number))
        return if (number.length == 10) "+91$number" else number
    }
}
