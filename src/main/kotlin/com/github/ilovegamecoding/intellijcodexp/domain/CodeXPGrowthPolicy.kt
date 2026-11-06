package com.github.ilovegamecoding.intellijcodexp.domain

import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import kotlin.math.ceil

/** Level progression and post-cap rewards share one set of cumulative XP boundaries. */
object CodeXPGrowthPolicy {
    const val PREVIOUS_ID = "growth-2.3-rc-v1"
    const val PREVIOUS_CHOICE_ID = "growth-2.3-v2"
    const val PREVIOUS_FIXED_CHOICE_ID = "growth-2.3-v3"
    const val ID = "growth-2.3-v4"
    const val MIGRATION_VERSION = 4
    const val MAX_LEVEL = 150
    private const val STEP_MULTIPLIER = 3L
    const val PREVIOUS_CHOICE_INTERVAL_XP = 25_000L
    const val CHOICE_INTERVAL_XP = 50_000L
    const val CHOICE_INTERVAL_INCREMENT_XP = 5_000L
    const val CHOICE_MAX_INTERVAL_XP = 100_000L
    const val CHOICE_COST = 1L
    const val MIGRATION_GIFT = 1L
    val crestLevels = listOf(1, 10, 25, 40, 60, 80, 100, 120, 140, 150)
    val crestChallenges = listOf(10, 30, 75, 150, 300, 10, 30, 75, 150, 300)
    val titleLevels = listOf(5, 15, 35, 50, 75, 90, 120, 140)
    val titleChallenges = listOf(1, 5, 10, 20, 35, 50, 75, 100)

    // Scale the integer XP boundaries players actually crossed under the original capped curve.
    private val thresholds =
        LongArray(MAX_LEVEL + 1).apply {
            var step = 300.0
            var cumulative = 0.0
            for (level in 2..MAX_LEVEL) {
                cumulative += step
                this[level] = ceil(cumulative).toLong() * STEP_MULTIPLIER
                step = (step * 1.05).coerceAtMost(5_000.0)
            }
        }

    val capXP: Long = xpAtLevel(MAX_LEVEL)
    val previousCapXP: Long = capXP / STEP_MULTIPLIER

    fun grandfatheredLevelOffset(xp: Long): Long = xp.coerceIn(0L, previousCapXP) * (STEP_MULTIPLIER - 1)

    fun choiceIntervalXP(earnedCredits: Long): Long = CHOICE_INTERVAL_XP + earnedCredits.coerceIn(0L, (CHOICE_MAX_INTERVAL_XP - CHOICE_INTERVAL_XP) / CHOICE_INTERVAL_INCREMENT_XP) * CHOICE_INTERVAL_INCREMENT_XP

    /** At most ten steps, then constant-time division, even for huge imported XP balances. */
    fun choiceProgress(
        eligibleXP: Long,
        previouslyEarned: Long = 0,
    ): ChoiceProgress {
        var remaining = eligibleXP.coerceAtLeast(0)
        var added = 0L
        var tier = previouslyEarned.coerceIn(0L, (CHOICE_MAX_INTERVAL_XP - CHOICE_INTERVAL_XP) / CHOICE_INTERVAL_INCREMENT_XP)
        var required = choiceIntervalXP(tier)
        while (required < CHOICE_MAX_INTERVAL_XP && remaining >= required) {
            remaining -= required
            added++
            required = choiceIntervalXP(++tier)
        }
        return ChoiceProgress(added + remaining / required, remaining % required, required)
    }

    fun xpAtLevel(level: Int): Long {
        require(level in 1..MAX_LEVEL)
        return thresholds[level]
    }

    fun levelInfo(
        totalXP: Long,
        levelOffset: Long = 0,
    ): CodeXPLevel {
        val xp = totalXP.coerceIn(0L, capXP) + levelOffset.coerceIn(0L, capXP)
        if (xp >= capXP) return CodeXPLevel(MAX_LEVEL, 0, 100, 0)
        val at = thresholds.binarySearch(xp, fromIndex = 1)
        val level = if (at >= 0) at else -at - 2
        val into = xp - thresholds[level]
        val required = thresholds[level + 1] - thresholds[level]
        return CodeXPLevel(level, into, (into * 100 / required).toInt(), required)
    }

    fun eligibleChoiceXP(
        totalXP: Long,
        migrationBaselineXP: Long,
        levelOffset: Long = 0,
    ): Long = (totalXP.coerceAtLeast(0) - maxOf(migrationBaselineXP, capXP - levelOffset.coerceIn(0L, capXP))).coerceAtLeast(0)
}

data class ChoiceProgress(
    val additionalCredits: Long,
    val progressXP: Long,
    val requiredXP: Long,
)
