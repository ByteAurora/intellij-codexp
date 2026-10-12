package com.github.ilovegamecoding.intellijcodexp.enums

/**
 * Notification delivery modes supported by CodeXP.
 */
enum class CodeXPNotificationType(
    val storedValue: String,
    val descriptionKey: String,
) {
    INTELLIJ(
        storedValue = "IntelliJ Notification",
        descriptionKey = "settings.notification.type.intellij.description",
    ),
    CODEXP(
        storedValue = "CodeXP Notification",
        descriptionKey = "settings.notification.type.codexp.description",
    ),
    ;

    companion object {
        /**
         * Parses persisted notification values while keeping compatibility with existing settings.
         */
        fun fromStoredValue(value: String): CodeXPNotificationType =
            entries.firstOrNull { type ->
                type.storedValue == value || type.name == value
            } ?: CODEXP
    }
}
