package com.github.ilovegamecoding.intellijcodexp.models

/**
 * CodeXPLevel class
 *
 * This class is used to calculate the level and progress based on XP.
 */
data class CodeXPLevel(
    /**
     * Current level.
     */
    val level: Int,

    /**
     * XP into the current level.
     */
    val xpIntoCurrentLevel: Long,

    /**
     * Progress to the next level.
     */
    val progressPercentage: Int,

    /**
     * XP needed to reach the next level.
     */
    val totalXPForNextLevel: Long
) {
    companion object {
        /**
         * Return an information object based on XP, including level and experience points.
         *
         * @param totalXP XP to calculate the level and progress to the next level.
         * @return Level info data.
         */
        fun createLevelInfo(totalXP: Long): CodeXPLevel {
            var level = 1
            var xpRequiredForNextLevel = 300.0
            var accumulatedXP = 300.0

            while (totalXP >= accumulatedXP) {
                level++
                xpRequiredForNextLevel *= 1.05
                accumulatedXP += xpRequiredForNextLevel
            }

            val xpGainedInCurrentLevel = totalXP - (accumulatedXP - xpRequiredForNextLevel).toLong()
            val progressPercentage = (xpGainedInCurrentLevel / xpRequiredForNextLevel * 100).toInt()

            return CodeXPLevel(level, xpGainedInCurrentLevel, progressPercentage, xpRequiredForNextLevel.toLong())
        }
    }
}