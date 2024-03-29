package com.github.ilovegamecoding.intellijcodexp.managers

import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType

object CodeXPNotificationManager {
    private val notificationGroup: NotificationGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("CodeXP")

    private fun notify(title: String, content: String) {
        notificationGroup.createNotification(title, content, NotificationType.INFORMATION).notify(null)
    }

    fun notifyChallengeComplete(codeXPChallenge: CodeXPChallenge) {
        notify("${codeXPChallenge.name} completed!", "Reward: ${codeXPChallenge.rewardXP} XP")
    }

    fun notifyLevelUp(nickname: String, level: Int, xpToNextLevel: Long) {
        notify(
            "Level up!",
            "Congratulations $nickname! You are now level $level. You need $xpToNextLevel XP to reach the next level."
        )
    }
}