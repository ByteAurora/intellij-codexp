package com.github.ilovegamecoding.intellijcodexp.services

import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.model.CodeXPChallenge
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
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
        COMMIT(8),
        PUSH(20),
        MERGE(10),
        PULL(5),
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
        var challenges: MutableMap<Event, MutableList<CodeXPChallenge>> = mutableMapOf(),

        /**
         * Challenges that have been completed.
         */
        var completedChallenges: MutableList<CodeXPChallenge> = mutableListOf()
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

        initialize {
            addChallenge(
                CodeXPChallenge(
                    event = Event.TYPING,
                    name = "Typing Challenge",
                    description = "Just type. We will give you XP for it.",
                    progress = 0,
                    goal = 10,
                    rewardXP = 100,
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
        if (codeXPState.challenges[challenge.event] == null) {
            codeXPState.challenges[challenge.event] = mutableListOf()
        }
        codeXPState.challenges[challenge.event]?.add(challenge)
    }

    /**
     * Checks if the user has completed any challenges for the given event
     *
     * @param event The event to check for challenges
     */
    fun checkChallenge(event: Event) {
        codeXPState.challenges[event]?.let { challenges -> // If there are challenges for the event
            // Create a list of completed challenges
            val completedChallenges = mutableListOf<CodeXPChallenge>()

            for (challenge in challenges) {
                // Increment the challenge progress
                challenge.progress += 1

                if (challenge.progress >= challenge.goal) { // If the challenge is completed
                    thisLogger().warn("Challenge completed: ${challenge.name}")

                    // Add the challenge to the completedChallenges list
                    completedChallenges.add(challenge)

                    // Add the reward XP to the user's XP
                    codeXPState.xp += challenge.rewardXP
                }
            }

            // Remove completed challenges
            challenges.removeAll(completedChallenges)

            // Add the completed challenges to the completedChallenges list
            codeXPState.completedChallenges.addAll(completedChallenges)
        }
    }
}