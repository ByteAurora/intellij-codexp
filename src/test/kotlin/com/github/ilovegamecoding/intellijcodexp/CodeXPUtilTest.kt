package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.util.StringUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class CodeXPUtilTest : BasePlatformTestCase() {
    fun testStringUtil() {
        val number1: Long = 123456789
        val number2: Long = 0
        val number3: Long = 12345

        assertEquals("123,456,789", StringUtil.numberToStringWithCommas(number1))
        assertEquals("0", StringUtil.numberToStringWithCommas(number2))
        assertEquals("12,345", StringUtil.numberToStringWithCommas(number3))
    }
}