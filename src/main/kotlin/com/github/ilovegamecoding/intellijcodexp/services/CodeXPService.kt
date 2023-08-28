package com.github.ilovegamecoding.intellijcodexp.services

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEventListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.managers.CodeXPNotificationManager
import com.github.ilovegamecoding.intellijcodexp.managers.CodeXPUIManager
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallengeFactory
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection

/**
 * CodeXPService class
 *
 * This is the main class for the plugin. It manages the state of the plugin, including challenges, user progress, and more.
 * All data stored in the plugin is contained within this class.
 */
@Service(Service.Level.APP)
@State(
    name = "com.github.ilovegamecoding.intellijcodexp.services.CodeXP",
    storages = [Storage("CodeXP.xml")]
)
class CodeXPService : PersistentStateComponent<CodeXPState>, CodeXPEventListener {
    /**
     * The state of the CodeXP plugin
     */
    private var codeXPState: CodeXPState = CodeXPState()

    /**
     * The message bus for the plugin
     */
    private var messageBus: MessageBus = ApplicationManager.getApplication().messageBus

    /**
     * The connection to the message bus
     */
    private var connection: MessageBusConnection = messageBus.connect()

    init {
        // Call manager to register the UI and notification managers
        CodeXPUIManager
        CodeXPNotificationManager

        // Connect to the application message bus
        connection.subscribe(CodeXPEventListener.CODEXP_EVENT, this)
    }

    override fun getState(): CodeXPState {
        return codeXPState
    }

    override fun noStateLoaded() {
        super.noStateLoaded()
        initialize { }
    }

    override fun loadState(codeXPState: CodeXPState) {
        this.codeXPState = codeXPState
        initialize { }
    }

    override fun eventOccurred(event: Event, dataContext: DataContext?) {
        increaseEventCount(event)
        increaseChallengeProgress(event)
    }

    /**
     * Initialize the plugin.
     *
     * @param initializeCallback The callback to execute when the plugin is initialized.
     */
    private fun initialize(initializeCallback: () -> Unit) {
        if (!codeXPState.hasExecuted) {
            initializeCallback()
            codeXPState.hasExecuted = true
        }

        Event.values().forEach { event ->
            if (!codeXPState.eventCounts.containsKey(event)) {
                codeXPState.eventCounts[event] = 0
            }
        }

        CodeXPChallengeFactory.createEventDefaultChallenges().forEach { challenge ->
            addChallenge(challenge)
        }
    }

    /**
     * Add a challenge to the list of challenges.
     *
     * @param challenge The challenge to add.
     */
    private fun addChallenge(challenge: CodeXPChallenge) {
        if (!codeXPState.challenges.containsKey(challenge.event)) {
            codeXPState.challenges[challenge.event] = challenge
        }
    }

    /**
     * Increase the event count for a specific event.
     *
     * @param event The event to increase the count for.
     * @param incrementValue The amount to increase the count by.
     */
    private fun increaseEventCount(event: Event, incrementValue: Long = 1) {
        codeXPState.eventCounts[event] = codeXPState.eventCounts.getOrDefault(event, 0) + incrementValue
        increaseXP(event.xpValue)
    }

    /**
     * Increase the user's XP by a specific amount.
     *
     * @param incrementAmount The amount to increase the user's XP by.
     */
    private fun increaseXP(incrementAmount: Long) {
        val beforeLevelInfo = CodeXPLevel.createLevelInfo(codeXPState.xp)
        codeXPState.xp += incrementAmount
        val currentLevelInfo = CodeXPLevel.createLevelInfo(codeXPState.xp)

        if (beforeLevelInfo.level != currentLevelInfo.level && beforeLevelInfo.level != 0 && codeXPState.codeXPConfiguration.showLevelUpNotification) {
            CodeXPNotificationManager.notifyLevelUp(
                codeXPState.nickname,
                currentLevelInfo.level,
                currentLevelInfo.totalXPForNextLevel
            )
        }

        messageBus.syncPublisher(CodeXPListener.CODEXP).xpUpdated(currentLevelInfo)
    }

    /**
     * Increase the progress of a challenge.
     *
     * @param event The type of the challenge.
     */
    private fun increaseChallengeProgress(event: Event) {
        codeXPState.challenges[event]?.let { challenge ->
            challenge.progress += 1

            if (challenge.progress >= challenge.goal) {
                increaseXP(challenge.rewardXP)
                replaceChallengeWithNew(challenge, event)
                CodeXPUIManager.showCodeXPDialog()

                if (codeXPState.codeXPConfiguration.showCompleteChallengeNotification)
                    CodeXPNotificationManager.notifyChallengeComplete(challenge)

                messageBus.syncPublisher(CodeXPListener.CODEXP)
                    .challengeUpdated(event, challenge, state.challenges[event])
            } else {
                messageBus.syncPublisher(CodeXPListener.CODEXP).challengeUpdated(event, challenge, null)
            }
        }
    }

    /**
     * Replace a completed challenge with a new challenge with an increased goal.
     *
     * @param completedChallenge The completed challenge.
     * @param event The type of the completed challenge.
     */
    private fun replaceChallengeWithNew(completedChallenge: CodeXPChallenge, event: Event) {
        codeXPState.completedChallenges.add(completedChallenge)
        codeXPState.challenges[event] = createNextChallenge(
            completedChallenge
        )
    }

    /**
     * Create a new challenge with an increased goal.
     *
     * @param completedChallenge The completed challenge.
     */
    private fun createNextChallenge(completedChallenge: CodeXPChallenge): CodeXPChallenge {
        return CodeXPChallenge(
            event = completedChallenge.event,
            name = completedChallenge.name,
            description = completedChallenge.description,
            rewardXP = completedChallenge.rewardXP + completedChallenge.rewardXPIncrement,
            rewardXPIncrement = completedChallenge.rewardXPIncrement,
            progress = 0,
            goal = completedChallenge.goal + completedChallenge.goalIncrement,
            goalIncrement = completedChallenge.goalIncrement
        )
    }
}