package com.github.ilovegamecoding.intellijcodexp.services

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressResult
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEventListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.managers.CodeXPNotificationManager
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.MessageBus

/**
 * CodeXPService class
 *
 * This is the main class for the plugin. It manages the state of the plugin, including challenges, user progress, and more.
 * All data stored in the plugin is contained within this class.
 */
@Service(Service.Level.APP)
@State(
    name = "com.github.ilovegamecoding.intellijcodexp.services.CodeXP",
    storages = [Storage("CodeXP.xml")],
)
class CodeXPService :
    PersistentStateComponent<CodeXPState>,
    CodeXPEventListener,
    Disposable {
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
    private var connection = messageBus.connect(this)

    private val progressEngine = CodeXPProgressEngine()

    init {
        // Connect to the application message bus
        connection.subscribe(CodeXPEventListener.CODEXP_EVENT, this)
    }

    override fun getState(): CodeXPState = codeXPState

    override fun noStateLoaded() {
        super.noStateLoaded()
        initialize { }
    }

    override fun loadState(codeXPState: CodeXPState) {
        this.codeXPState = codeXPState
        initialize { }
    }

    override fun dispose() {
    }

    override fun eventOccurred(
        event: Event,
        dataContext: DataContext?,
    ) {
        handleProgressResult(progressEngine.recordEvent(codeXPState, event), dataContext)
    }

    fun updateNickname(nickname: String) {
        codeXPState.nickname = nickname
    }

    fun setCompletedChallengesVisible(isVisible: Boolean) {
        codeXPState.showCompletedChallenges = isVisible
    }

    fun updateConfiguration(configuration: CodeXPConfiguration) {
        codeXPState.codeXPConfiguration = configuration
    }

    /**
     * Initialize the plugin.
     *
     * @param initializeCallback The callback to execute when the plugin is initialized.
     */
    private fun initialize(initializeCallback: () -> Unit) {
        val shouldRunCallback = !codeXPState.hasExecuted

        if (shouldRunCallback) {
            initializeCallback()
        }

        progressEngine.initialize(codeXPState)
    }

    private fun handleProgressResult(
        result: CodeXPProgressResult,
        dataContext: DataContext?,
    ) {
        result.xpChanges.forEach { change ->
            if (change.isLevelUp && codeXPState.codeXPConfiguration.showLevelUpNotification) {
                when (codeXPState.codeXPConfiguration.notificationType) {
                    "IntelliJ Notification" -> {
                        CodeXPNotificationManager.notifyLevelUp(
                            codeXPState.nickname,
                            change.currentLevelInfo.level,
                            change.currentLevelInfo.totalXPForNextLevel,
                        )
                    }

                    "CodeXP Notification" -> {
                        messageBus.syncPublisher(CodeXPListener.CODEXP).levelUp(change.currentLevelInfo, dataContext)
                    }
                }
            }

            messageBus.syncPublisher(CodeXPListener.CODEXP).xpUpdated(change.currentLevelInfo)
        }

        result.completedChallenge?.let { challenge ->
            if (codeXPState.codeXPConfiguration.showCompleteChallengeNotification) {
                when (codeXPState.codeXPConfiguration.notificationType) {
                    "IntelliJ Notification" -> {
                        CodeXPNotificationManager.notifyChallengeComplete(
                            challenge,
                        )
                    }

                    "CodeXP Notification" -> {
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
    }
}
