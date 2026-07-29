package com.github.ilovegamecoding.intellijcodexp.presentation.settings

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.enums.CodeXPNotificationType

/**
 * Defines notification type labels and descriptions used by the settings UI.
 */
internal object CodeXPNotificationUiOptions {
    /**
     * Notification type values shown in the settings combo box.
     */
    val values: List<String> = CodeXPNotificationType.entries.map { it.storedValue }

    /**
     * Returns the settings description for a notification type.
     */
    fun descriptionFor(notificationType: String): String {
        val type = CodeXPNotificationType.fromStoredValue(notificationType)
        return CodeXPBundle.message(type.descriptionKey)
    }
}
