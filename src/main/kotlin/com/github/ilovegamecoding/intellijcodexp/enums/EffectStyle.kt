package com.github.ilovegamecoding.intellijcodexp.enums

enum class EffectStyle(
    val storedValue: String,
) {
    SUBTLE("subtle"),
    CELEBRATORY("celebratory"),
    ;

    companion object {
        fun fromStoredValue(value: String?): EffectStyle = entries.firstOrNull { it.storedValue == value } ?: CELEBRATORY
    }
}
