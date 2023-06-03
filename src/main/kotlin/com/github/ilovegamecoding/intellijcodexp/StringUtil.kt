package com.github.ilovegamecoding.intellijcodexp

object StringUtil {
    fun numberToStringWithCommas(number: Long): String {
        return number.toString().reversed().chunked(3).joinToString(",").reversed()
    }
}