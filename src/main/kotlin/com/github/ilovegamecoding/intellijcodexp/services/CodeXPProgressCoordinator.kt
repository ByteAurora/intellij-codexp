package com.github.ilovegamecoding.intellijcodexp.services

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressResult
import com.github.ilovegamecoding.intellijcodexp.enums.CodeXPNotificationType
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEffectListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.listeners.IdeXpOrigin
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.notification.CodeXPNotificationNotifier
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.util.messages.MessageBus

/**
 * Coordinates UI-facing side effects after progress rules update the CodeXP state.
 */
internal class CodeXPProgressCoordinator(
    private val messageBus: MessageBus,
) {
    /**
     * Publishes progress updates and dispatches configured notifications.
     */
    fun handleProgressResult(
        state: CodeXPState,
        result: CodeXPProgressResult,
        dataContext: DataContext?,
    ) {
        val notificationType = CodeXPNotificationType.fromStoredValue(state.codeXPConfiguration.notificationType)

        result.xpChanges.forEach { change ->
            if (change.isLevelUp && state.codeXPConfiguration.showLevelUpNotification) {
                when (notificationType) {
                    CodeXPNotificationType.INTELLIJ -> {
                        CodeXPNotificationNotifier.notifyLevelUp(
                            state.nickname,
                            change.currentLevelInfo.level,
                            change.currentLevelInfo.totalXPForNextLevel,
                        )
                    }

                    CodeXPNotificationType.CODEXP -> {
                        messageBus.syncPublisher(CodeXPListener.CODEXP).levelUp(change.currentLevelInfo, dataContext)
                    }
                }
            }

            messageBus.syncPublisher(CodeXPListener.CODEXP).xpUpdated(change.currentLevelInfo)
        }

        result.completedChallenge?.let { challenge ->
            if (state.codeXPConfiguration.showCompleteChallengeNotification) {
                when (notificationType) {
                    CodeXPNotificationType.INTELLIJ -> {
                        CodeXPNotificationNotifier.notifyChallengeComplete(challenge)
                    }

                    CodeXPNotificationType.CODEXP -> {
                        messageBus.syncPublisher(CodeXPListener.CODEXP).challengeCompleted(result.event, challenge, dataContext)
                    }
                }
            }
        }

        result.updatedChallenge?.let { challenge ->
            messageBus
                .syncPublisher(CodeXPListener.CODEXP)
                .challengeUpdated(result.event, challenge, result.newChallenge)
        }

        if (notificationType == CodeXPNotificationType.INTELLIJ && state.codeXPConfiguration.showLevelUpNotification) {
            result.choiceAward?.let { award ->
                val project = dataContext?.let { IdeXpOrigin.KEY.getData(it)?.project ?: CommonDataKeys.PROJECT.getData(it) }
                CodeXPNotificationNotifier.notifyChoiceCredits(award.count, project)
            }
        }
        messageBus.syncPublisher(CodeXPEffectListener.EFFECTS).progressRecorded(result, dataContext)
    }
}
