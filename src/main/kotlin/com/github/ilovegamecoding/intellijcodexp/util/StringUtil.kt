package com.github.ilovegamecoding.intellijcodexp.util

object StringUtil {
    fun numberToStringWithCommas(number: Long): String {
        return number.toString().reversed().chunked(3).joinToString(",").reversed()
    }
}