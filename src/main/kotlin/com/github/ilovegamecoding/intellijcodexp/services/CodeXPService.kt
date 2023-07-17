package com.github.ilovegamecoding.intellijcodexp.services

import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.manager.CodeXPNotificationManager
import com.github.ilovegamecoding.intellijcodexp.model.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.model.CodeXPChallengeFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
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
class CodeXPService : PersistentStateComponent<CodeXPService.CodeXPState>, CodeXPListener {
    /**
     * Event enum for the plugin events.
     */
    enum class Event(val xpValue: Long) {
        NONE(0),
        TYPING(2),
        PASTE(1),
        BACKSPACE(1),
        TAB(1),
        ENTER(1),
        SAVE(10),
        BUILD(5),
        RUN(10),
        DEBUG(20),
        ACTION(5);
    }


    /**
     * Position enum for the gained XP label.
     */
    enum class PositionToDisplayGainedXP(val x: Int, val y: Int) {
        TOP(0, -1),
        TOP_LEFT(-1, -1),
        LEFT(-1, 0),
        BOTTOM_LEFT(-1, 1),
        BOTTOM(0, 1),
        BOTTOM_RIGHT(1, 1),
        RIGHT(1, 0),
        TOP_RIGHT(1, -1)
    }

    /**
     * The state of the CodeXP plugin.
     */
    data class CodeXPState(
        /**
         * Whether the plugin has been executed before.
         */
        var hasExecuted: Boolean = false,

        /**
         * User's nickname.
         */
        var nickname: String = "",

        /**
         * User's total XP.
         */
        var xp: Long = 0,

        /**
         * Save the number of times an event has occurred based on the [Event] enum.
         */
        var eventCounts: MutableMap<Event, Long> = mutableMapOf(),

        /**
         * Challenges that not yet been completed.
         */
        var challenges: MutableMap<Event, CodeXPChallenge> = mutableMapOf(),

        /**
         * Challenges that have been completed.
         */
        var completedChallenges: MutableList<CodeXPChallenge> = mutableListOf(),

        /**
         * Show completed challenges in the dashboard.
         */
        var showCompletedChallenges: Boolean = true,

        /**
         * The configuration of the CodeXP plugin
         */
        var codeXPConfiguration: CodeXPConfiguration = CodeXPConfiguration()
    ) {
        /**
         * Increment the event count for a specific event.
         */
        fun incrementEventCount(event: Event, incrementValue: Long = 1) {
            eventCounts[event] = eventCounts.getOrDefault(event, 0) + incrementValue
            xp += event.xpValue
        }

        /**
         * Get the number of times an event has occurred.
         */
        fun getEventCount(event: Event): Long {
            return eventCounts[event] ?: 0
        }
    }

    /**
     * The configuration of the CodeXP plugin
     */
    data class CodeXPConfiguration(
        /**
         * Show a notification when the user levels up.
         */
        var showLevelUpNotification: Boolean = true,

        /**
         * Show a notification when the user completes a challenge.
         */
        var showCompleteChallengeNotification: Boolean = true,

        /**
         * Show a notification when the user gains XP.
         */
        var showGainedXP: Boolean = true,

        /**
         * Gained XP display position.
         */
        var positionToDisplayGainedXP: PositionToDisplayGainedXP = PositionToDisplayGainedXP.TOP_RIGHT
    )

    /**
     * The state of the CodeXP plugin
     */
    private var codeXPState: CodeXPState = CodeXPState()

    /**
     * The message bus for the plugin
     */
    private var messageBus: MessageBus? = null

    /**
     * The connection to the message bus
     */
    private var connection: MessageBusConnection? = null

    init {
        // Connect to the application message bus
        messageBus = ApplicationManager.getApplication().messageBus
        connection = messageBus?.connect()
        connection?.subscribe(CodeXPListener.CODEXP_EVENT, this)
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

    override fun eventOccurred(event: Event) {
        codeXPState.incrementEventCount(event)
        checkChallenge(event)
    }

    /**
     * Initialize the plugin.
     *
     * @param initializeCallback The callback to execute when the plugin is initialized.
     */
    fun initialize(initializeCallback: () -> Unit) {
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
    fun addChallenge(challenge: CodeXPChallenge) {
        if (!codeXPState.challenges.containsKey(challenge.event)) {
            codeXPState.challenges[challenge.event] = challenge
        }
    }

    /**
     * Checks if the user has completed any challenges for the given event
     *
     * @param event The event to check for challenges
     */
    fun checkChallenge(event: Event) {
        codeXPState.challenges[event]?.let { challenge ->
            challenge.progress += 1

            if (challenge.progress >= challenge.goal) {
                codeXPState.xp += challenge.rewardXP
                replaceChallengeWithNew(challenge, event)

                if (codeXPState.codeXPConfiguration.showCompleteChallengeNotification)
                    CodeXPNotificationManager.notifyChallengeComplete(challenge)
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