package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallengeFactory
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.utils.StringUtil

/**
 * Creates display-ready values for the dashboard UI.
 */
internal object CodeXPDashboardViewModels {
    /**
     * Creates level summary text and progress values.
     */
    fun levelSummary(
        totalXP: Long,
        levelInfo: CodeXPLevel,
    ): CodeXPLevelSummaryViewModel {
        val range = dashboardLevel(totalXP, levelInfo)
        return CodeXPLevelSummaryViewModel(
            totalXP = StringUtil.numberToStringWithCommas(totalXP),
            currentLevel = StringUtil.numberToStringWithCommas(levelInfo.level.toLong()),
            nextLevel = if (levelInfo.level >= CodeXPGrowthPolicy.MAX_LEVEL) "MAX" else StringUtil.numberToStringWithCommas((levelInfo.level + 1).toLong()),
            currentLevelXP = StringUtil.numberToStringWithCommas(levelInfo.xpIntoCurrentLevel),
            progressToNextLevel = levelInfo.progressPercentage,
            progressText = "${levelInfo.progressPercentage} %",
            requiredXP = StringUtil.numberToStringWithCommas(range.required),
            remainingXP = StringUtil.numberToStringWithCommas(range.remaining),
        )
    }

    /**
     * Creates a completed challenge count label.
     */
    fun completedChallengeCount(count: Int): String = StringUtil.numberToStringWithCommas(count.toLong())

    /**
     * Creates challenge display values.
     */
    fun challenge(challenge: CodeXPChallenge): CodeXPChallengeViewModel {
        val progressPercentage = ((challenge.progress.toDouble() / challenge.goal.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)
        return CodeXPChallengeViewModel(
            name = challenge.name,
            rewardXP = StringUtil.numberToStringWithCommas(challenge.rewardXP),
            description = challenge.description.replace("[goal]", StringUtil.numberToStringWithCommas(challenge.goal)),
            progressPercentage = progressPercentage,
            isCompleted = challenge.progress >= challenge.goal,
        )
    }
}

/**
 * Display-ready level summary values for the dashboard.
 */
internal data class CodeXPLevelSummaryViewModel(
    val totalXP: String,
    val currentLevel: String,
    val nextLevel: String,
    val currentLevelXP: String,
    val progressToNextLevel: Int,
    val progressText: String,
    val requiredXP: String,
    val remainingXP: String,
)

internal data class DashboardLevel(
    val level: Int,
    val into: Long,
    val required: Long,
    val remaining: Long,
) {
    val fraction: Double get() = (into.toDouble() / required).coerceIn(0.0, 1.0)
}

/** Use the engine's exact integer boundaries, including migrated fractional progress. */
internal fun dashboardLevel(
    totalXP: Long,
    info: CodeXPLevel = CodeXPLevel.createLevelInfo(totalXP),
): DashboardLevel {
    if (info.level == CodeXPGrowthPolicy.MAX_LEVEL) return DashboardLevel(info.level, 1, 1, 0)
    return DashboardLevel(info.level, info.xpIntoCurrentLevel, info.totalXPForNextLevel, info.totalXPForNextLevel - info.xpIntoCurrentLevel)
}

internal data class DashboardChallenge(
    val id: String,
    val event: Event,
    val name: String,
    val description: String,
    val progress: Long,
    val goal: Long,
    val reward: Long,
    val lastUpdatedOrder: Long = 0,
    val sequenceNumber: Long? = null,
) {
    val fraction: Double get() = (progress.toDouble() / goal.coerceAtLeast(1)).coerceIn(0.0, 1.0)

    companion object {
        fun from(challenge: CodeXPChallenge): DashboardChallenge =
            DashboardChallenge(
                challenge.id,
                challenge.event,
                challenge.name,
                challenge.description.replace("[goal]", StringUtil.numberToStringWithCommas(challenge.goal)),
                challenge.progress,
                challenge.goal,
                challenge.rewardXP,
                challenge.lastUpdatedOrder,
                CodeXPChallengeFactory.sequenceNumber(challenge),
            )
    }
}

internal data class DashboardSnapshot(
    val nickname: String,
    val xp: Long,
    val active: List<DashboardChallenge>,
    val completed: List<DashboardChallenge>,
    val counts: Map<Event, Long>,
    val reducedMotion: Boolean,
    val subtle: Boolean,
    val levelOffset: Long = 0,
) {
    companion object {
        fun from(
            state: CodeXPState,
            completed: List<DashboardChallenge> = state.completedChallenges.asReversed().map(DashboardChallenge::from),
        ): DashboardSnapshot =
            DashboardSnapshot(
                state.nickname,
                state.xp,
                state.challenges.values
                    .map(DashboardChallenge::from)
                    .sortedWith(compareByDescending<DashboardChallenge> { it.lastUpdatedOrder }.thenBy { it.event.ordinal }.thenBy { it.id }),
                completed,
                Event.entries.filter { it != Event.NONE }.associateWith(state::getEventCount),
                state.codeXPConfiguration.reducedMotion,
                EffectStyle.fromStoredValue(state.codeXPConfiguration.effectStyle) == EffectStyle.SUBTLE,
                state.levelOffset,
            )
    }
}

/**
 * Display-ready challenge values for the dashboard.
 */
internal data class CodeXPChallengeViewModel(
    val name: String,
    val rewardXP: String,
    val description: String,
    val progressPercentage: Int,
    val isCompleted: Boolean,
)
