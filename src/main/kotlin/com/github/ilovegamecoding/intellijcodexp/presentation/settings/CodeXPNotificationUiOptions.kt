package com.github.ilovegamecoding.intellijcodexp.presentation.settings

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle

/**
 * Defines notification type labels and descriptions used by the settings UI.
 */
internal object CodeXPNotificationUiOptions {
    const val INTELLIJ_NOTIFICATION: String = "IntelliJ Notification"
    const val CODEXP_NOTIFICATION: String = "CodeXP Notification"

    /**
     * Notification type values shown in the settings combo box.
     */
    val values: List<String> = listOf(INTELLIJ_NOTIFICATION, CODEXP_NOTIFICATION)

    /**
     * Returns the settings description for a notification type.
     */
    fun descriptionFor(notificationType: String): String =
        when (notificationType) {
            INTELLIJ_NOTIFICATION -> CodeXPBundle.message("settings.notification.type.intellij.description")
            else -> CodeXPBundle.message("settings.notification.type.codexp.description")
        }
}
