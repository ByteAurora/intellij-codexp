package com.github.ilovegamecoding.intellijcodexp.presentation.notification

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType

object CodeXPNotificationNotifier {
    private val notificationGroup: NotificationGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("CodeXP")

    private fun notify(
        title: String,
        content: String,
    ) {
        notificationGroup.createNotification(title, content, NotificationType.INFORMATION).notify(null)
    }

    fun notifyChallengeComplete(codeXPChallenge: CodeXPChallenge) {
        notify(
            CodeXPBundle.message("notification.challenge.completed.title", codeXPChallenge.name),
            CodeXPBundle.message("notification.challenge.completed.content", codeXPChallenge.rewardXP),
        )
    }

    fun notifyLevelUp(
        nickname: String,
        level: Int,
        xpToNextLevel: Long,
    ) {
        notify(
            CodeXPBundle.message("notification.level.up.title"),
            CodeXPBundle.message("notification.level.up.content", nickname, level, xpToNextLevel),
        )
    }
}
