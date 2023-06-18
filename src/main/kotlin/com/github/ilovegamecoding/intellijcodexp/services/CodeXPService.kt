package com.github.ilovegamecoding.intellijcodexp.services

import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.manager.CodeXPNotificationManager
import com.github.ilovegamecoding.intellijcodexp.model.CodeXPChallenge
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
        SAVE(10),
        BUILD(5),
        RUN(10),
        DEBUG(20),
        ACTION(5);
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
        var showCompletedChallenges: Boolean = true
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
        var showCompleteChallengeNotification: Boolean = true
    )

    /**
     * The state of the CodeXP plugin
     */
    private var codeXPState: CodeXPState = CodeXPState()

    /**
     * The configuration of the CodeXP plugin
     */
    var codeXPConfiguration: CodeXPConfiguration = CodeXPConfiguration()

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

        initialize {
            addChallenge(
                CodeXPChallenge(
                    event = Event.TYPING,
                    name = "Typing Challenge",
                    description = "Typing [goal] times.",
                    progress = 0,
                    goal = 100,
                    goalIncrement = 100,
                    rewardXP = 100,
                    rewardXPIncrement = 200
                )
            )
            addChallenge(
                CodeXPChallenge(
                    event = Event.PASTE,
                    name = "Paste Challenge",
                    description = "Paste [goal] times.",
                    progress = 0,
                    goal = 10,
                    goalIncrement = 10,
                    rewardXP = 100,
                    rewardXPIncrement = 150
                )
            )
            addChallenge(
                CodeXPChallenge(
                    event = Event.BACKSPACE,
                    name = "Backspace Challenge",
                    description = "Press the backspace key [goal] times.",
                    progress = 0,
                    goal = 50,
                    goalIncrement = 50,
                    rewardXP = 100,
                    rewardXPIncrement = 150
                )
            )
            addChallenge(
                CodeXPChallenge(
                    event = Event.TAB,
                    name = "Tab Challenge",
                    description = "Press the tab key [goal] times.",
                    progress = 0,
                    goal = 50,
                    goalIncrement = 50,
                    rewardXP = 100,
                    rewardXPIncrement = 150
                )
            )
            addChallenge(
                CodeXPChallenge(
                    event = Event.SAVE,
                    name = "Save Challenge",
                    description = "Save [goal] times.",
                    progress = 0,
                    goal = 10,
                    goalIncrement = 10,
                    rewardXP = 300,
                    rewardXPIncrement = 400
                )
            )
            addChallenge(
                CodeXPChallenge(
                    event = Event.BUILD,
                    name = "Build Challenge",
                    description = "Build [goal] times.",
                    progress = 0,
                    goal = 10,
                    goalIncrement = 10,
                    rewardXP = 150,
                    rewardXPIncrement = 200
                )
            )
            addChallenge(
                CodeXPChallenge(
                    event = Event.RUN,
                    name = "Run Challenge",
                    description = "Run [goal] times.",
                    progress = 0,
                    goal = 10,
                    goalIncrement = 10,
                    rewardXP = 200,
                    rewardXPIncrement = 250
                )
            )
            addChallenge(
                CodeXPChallenge(
                    event = Event.DEBUG,
                    name = "Debug Challenge",
                    description = "Debug [goal] times.",
                    progress = 0,
                    goal = 10,
                    goalIncrement = 10,
                    rewardXP = 300,
                    rewardXPIncrement = 400
                )
            )
            addChallenge(
                CodeXPChallenge(
                    event = Event.ACTION,
                    name = "Action Challenge",
                    description = "Perform [goal] actions.",
                    progress = 0,
                    goal = 20,
                    goalIncrement = 20,
                    rewardXP = 100,
                    rewardXPIncrement = 120
                )
            )
        }
    }

    override fun getState(): CodeXPState {
        return codeXPState
    }

    override fun loadState(codeXPState: CodeXPState) {
        this.codeXPState = codeXPState
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
    }

    /**
     * Add a challenge to the list of challenges.
     *
     * @param challenge The challenge to add.
     */
    fun addChallenge(challenge: CodeXPChallenge) {
        codeXPState.challenges[challenge.event] = challenge
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

                if (codeXPConfiguration.showCompleteChallengeNotification)
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