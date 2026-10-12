package com.github.ilovegamecoding.intellijcodexp.utils

/**
 * String util class.
 */
object StringUtil {
    /**
     * Convert a number to a string with commas.
     */
    fun numberToStringWithCommas(number: Long): String =
        number
            .toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
}
