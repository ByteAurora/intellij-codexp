package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardViewModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeXPDashboardViewModelsTest {
    @Test
    fun `level boundary displays new level with empty progress`() {
        val summary = CodeXPDashboardViewModels.levelSummary(300, CodeXPLevel.createLevelInfo(300))

        assertEquals("300", summary.totalXP)
        assertEquals("2", summary.currentLevel)
        assertEquals("3", summary.nextLevel)
        assertEquals("0", summary.currentLevelXP)
        assertEquals(0, summary.progressToNextLevel)
        assertEquals("0 %", summary.progressText)
    }

    @Test
    fun `challenge formats goal and reward and tracks completion`() {
        val challenge =
            CodeXPChallenge().apply {
                name = "Typing Challenge"
                description = "Typing [goal] times."
                goal = 1000
                rewardXP = 2500
                progress = 500
            }
        val active = CodeXPDashboardViewModels.challenge(challenge)

        assertEquals("Typing Challenge", active.name)
        assertEquals("Typing 1,000 times.", active.description)
        assertEquals("2,500", active.rewardXP)
        assertEquals(50, active.progressPercentage)
        assertFalse(active.isCompleted)

        challenge.progress = challenge.goal
        val completed = CodeXPDashboardViewModels.challenge(challenge)
        assertEquals(100, completed.progressPercentage)
        assertTrue(completed.isCompleted)
    }
}
