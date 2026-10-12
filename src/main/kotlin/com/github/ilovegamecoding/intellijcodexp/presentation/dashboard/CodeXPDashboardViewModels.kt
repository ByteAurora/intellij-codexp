package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
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
    ): CodeXPLevelSummaryViewModel =
        CodeXPLevelSummaryViewModel(
            totalXP = StringUtil.numberToStringWithCommas(totalXP),
            currentLevel = StringUtil.numberToStringWithCommas(levelInfo.level.toLong()),
            nextLevel = StringUtil.numberToStringWithCommas((levelInfo.level + 1).toLong()),
            currentLevelXP = StringUtil.numberToStringWithCommas(levelInfo.xpIntoCurrentLevel),
            progressToNextLevel = levelInfo.progressPercentage,
            progressText = "${levelInfo.progressPercentage} %",
        )

    /**
     * Creates a completed challenge count label.
     */
    fun completedChallengeCount(count: Int): String = StringUtil.numberToStringWithCommas(count.toLong())

    /**
     * Creates challenge display values.
     */
    fun challenge(challenge: CodeXPChallenge): CodeXPChallengeViewModel {
        val progressPercentage = ((challenge.progress.toDouble() / challenge.goal) * 100).toInt()
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
)

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
