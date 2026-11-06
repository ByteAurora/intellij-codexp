package com.github.ilovegamecoding.intellijcodexp.models

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.intellij.util.xmlb.annotations.Transient

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
     * The configuration of the CodeXP plugin
     */
    var codeXPConfiguration: CodeXPConfiguration = CodeXPConfiguration(),
    /** Reset fence only. The collection ledger lives outside the legacy CodeXP.xml file. */
    var collectionGeneration: String = "",
) {
    /** Rehydrated from the collection ledger. Never added to earned XP or stored in legacy XML. */
    @get:Transient
    var levelOffset: Long = 0

    fun levelInfo(): CodeXPLevel = CodeXPGrowthPolicy.levelInfo(xp, levelOffset)

    /**
     * Get the number of times an event has occurred.
     */
    fun getEventCount(event: Event): Long = eventCounts[event] ?: 0

    internal fun detachedCopy(): CodeXPState =
        copy(
            eventCounts = eventCounts.toMutableMap(),
            challenges = challenges.mapValues { it.value.detachedCopy() }.toMutableMap(),
            completedChallenges = completedChallenges.map { it.detachedCopy() }.toMutableList(),
            codeXPConfiguration = codeXPConfiguration.copy(),
        )
}
