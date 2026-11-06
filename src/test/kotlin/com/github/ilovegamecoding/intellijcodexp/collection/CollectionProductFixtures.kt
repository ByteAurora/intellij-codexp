package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallengeFactory
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import kotlin.math.ceil

/** Test sources only. No profile controls or synthetic grants are packaged with the plugin. */
internal object CollectionProductFixtures {
    fun legacyXP(level: Int): Long {
        var step = 300.0
        var xp = 0.0
        repeat(level - 1) {
            xp += step
            step *= 1.05
        }
        return ceil(xp).toLong()
    }

    fun profile(
        level: Int = 24,
        completed: Int = 38,
        reduced: Boolean = false,
    ): CodeXPState =
        CodeXPState(nickname = "CodeXP RC", xp = legacyXP(level)).apply {
            CodeXPProgressEngine().initialize(this)
            codeXPConfiguration.reducedMotion = reduced
            completedChallenges = (1..completed).map { challenge(it.toLong()) }.toMutableList()
        }

    fun challenge(
        ordinal: Long,
        event: Event = Event.SAVE,
    ): CodeXPChallenge =
        CodeXPChallengeFactory.createEventDefaultChallenges().first { it.event == event }.apply {
            id = "fixture-${event.name}-$ordinal"
            goal += goalIncrement * (ordinal - 1)
            rewardXP += rewardXPIncrement * (ordinal - 1)
            progress = goal
        }
}
