package com.github.ilovegamecoding.intellijcodexp.domain

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallengeFactory
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState

/**
 * Handles CodeXP progress rules without depending on IntelliJ platform APIs or Swing.
 */
class CodeXPProgressEngine(
    private val defaultChallenges: () -> List<CodeXPChallenge> = CodeXPChallengeFactory::createEventDefaultChallenges,
) {
    fun initialize(state: CodeXPState) {
        if (!state.hasExecuted) {
            state.hasExecuted = true
        }

        Event.entries.forEach { event ->
            if (!state.eventCounts.containsKey(event)) {
                state.eventCounts[event] = 0
            }
        }

        defaultChallenges().forEach { challenge ->
            if (!state.challenges.containsKey(challenge.event)) {
                state.challenges[challenge.event] = challenge
            }
        }
    }

    fun recordEvent(
        state: CodeXPState,
        event: Event,
    ): CodeXPProgressResult {
        state.eventCounts[event] = state.eventCounts.getOrDefault(event, 0).let { if (it == Long.MAX_VALUE) it else it + 1 }

        val xpChanges = mutableListOf<CodeXPChange>()
        xpChanges += increaseXP(state, event.xpValue)

        val challenge = state.challenges[event] ?: return CodeXPProgressResult(event, xpChanges = xpChanges)
        challenge.lastUpdatedOrder = nextChallengeOrder(state)
        challenge.progress += 1

        if (challenge.progress < challenge.goal) {
            return CodeXPProgressResult(
                event = event,
                updatedChallenge = challenge,
                xpChanges = xpChanges,
            )
        }

        xpChanges += increaseXP(state, challenge.rewardXP)
        val newChallenge = createNextChallenge(challenge)
        newChallenge.lastUpdatedOrder = challenge.lastUpdatedOrder
        state.completedChallenges.add(challenge)
        state.challenges[event] = newChallenge

        return CodeXPProgressResult(
            event = event,
            updatedChallenge = challenge,
            completedChallenge = challenge,
            newChallenge = newChallenge,
            xpChanges = xpChanges,
        )
    }

    private fun nextChallengeOrder(state: CodeXPState): Long {
        var latest = state.challenges.values.maxOfOrNull { it.lastUpdatedOrder } ?: 0
        if (latest == Long.MAX_VALUE) {
            // Preserve relative order if an imported/corrupted counter reaches its limit.
            state.challenges.values
                .sortedWith(compareBy<CodeXPChallenge> { it.lastUpdatedOrder }.thenByDescending { it.event.ordinal })
                .forEachIndexed { index, item -> item.lastUpdatedOrder = index + 1L }
            latest = state.challenges.size.toLong()
        }
        return latest.coerceAtLeast(0) + 1
    }

    private fun increaseXP(
        state: CodeXPState,
        incrementAmount: Long,
    ): CodeXPChange {
        val beforeLevelInfo = state.levelInfo()
        state.xp = if (incrementAmount > 0 && state.xp > Long.MAX_VALUE - incrementAmount) Long.MAX_VALUE else state.xp + incrementAmount
        val currentLevelInfo = state.levelInfo()

        return CodeXPChange(
            beforeLevelInfo = beforeLevelInfo,
            currentLevelInfo = currentLevelInfo,
        )
    }

    private fun createNextChallenge(completedChallenge: CodeXPChallenge): CodeXPChallenge =
        CodeXPChallenge(
            event = completedChallenge.event,
            name = completedChallenge.name,
            description = completedChallenge.description,
            rewardXP = completedChallenge.rewardXP + completedChallenge.rewardXPIncrement,
            rewardXPIncrement = completedChallenge.rewardXPIncrement,
            progress = 0,
            goal = completedChallenge.goal + completedChallenge.goalIncrement,
            goalIncrement = completedChallenge.goalIncrement,
        )
}

data class CodeXPProgressResult(
    val event: Event,
    val updatedChallenge: CodeXPChallenge? = null,
    val completedChallenge: CodeXPChallenge? = null,
    val newChallenge: CodeXPChallenge? = null,
    val xpChanges: List<CodeXPChange> = emptyList(),
    val choiceAward: com.github.ilovegamecoding.intellijcodexp.domain.collection.ChoiceAward? = null,
)

data class CodeXPChange(
    val beforeLevelInfo: CodeXPLevel,
    val currentLevelInfo: CodeXPLevel,
) {
    val isLevelUp: Boolean = beforeLevelInfo.level != currentLevelInfo.level && beforeLevelInfo.level != 0
}
