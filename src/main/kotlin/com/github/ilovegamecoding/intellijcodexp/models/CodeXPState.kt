package com.github.ilovegamecoding.intellijcodexp.models

import com.github.ilovegamecoding.intellijcodexp.enums.Event

/**
 * CodeXPState class
 *
 * This class is used to store the state of the plugin.
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
     * Get the number of times an event has occurred.
     */
    fun getEventCount(event: Event): Long {
        return eventCounts[event] ?: 0
    }
}