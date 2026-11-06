package com.github.ilovegamecoding.intellijcodexp.presentation.notification

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object CodeXPNotificationNotifier {
    private val notificationGroup: NotificationGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("CodeXP")

    private fun notify(
        title: String,
        content: String,
        project: Project? = null,
    ) {
        notificationGroup.createNotification(title, content, NotificationType.INFORMATION).notify(project)
    }

    fun notifyChoiceCredits(
        count: Long,
        project: Project?,
    ) {
        notify(CodeXPBundle.message("notification.choice.title"), CodeXPBundle.message("effects.choice.earned", count), project)
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
            if (level >= CodeXPGrowthPolicy.MAX_LEVEL) {
                CodeXPBundle.message("notification.level.max.content", nickname, level)
            } else {
                CodeXPBundle.message("notification.level.up.content", nickname, level, xpToNextLevel)
            },
        )
    }
}
