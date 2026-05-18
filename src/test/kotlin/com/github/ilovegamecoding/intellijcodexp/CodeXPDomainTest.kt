package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallengeFactory
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeXPDomainTest {
    @Test
    fun `level info starts at level one`() {
        val levelInfo = CodeXPLevel.createLevelInfo(0)

        assertEquals(1, levelInfo.level)
        assertEquals(0, levelInfo.xpIntoCurrentLevel)
        assertEquals(0, levelInfo.progressPercentage)
        assertEquals(300, levelInfo.totalXPForNextLevel)
    }

    @Test
    fun `level advances after reaching required xp`() {
        val levelInfo = CodeXPLevel.createLevelInfo(300)

        assertEquals(2, levelInfo.level)
        assertEquals(0, levelInfo.xpIntoCurrentLevel)
        assertEquals(0, levelInfo.progressPercentage)
        assertEquals(315, levelInfo.totalXPForNextLevel)
    }

    @Test
    fun `default challenges cover all xp events`() {
        val challenges = CodeXPChallengeFactory.createEventDefaultChallenges()
        val challengeEvents = challenges.map { it.event }.toSet()
        val xpEvents = Event.values().filterNot { it == Event.NONE }.toSet()

        assertEquals(xpEvents, challengeEvents)
        assertEquals(xpEvents.size, challenges.size)
    }

    @Test
    fun `default challenges have unique identifiers and playable goals`() {
        val challenges = CodeXPChallengeFactory.createEventDefaultChallenges()
        val challengeIds = challenges.map { it.id }

        assertEquals(challengeIds.size, challengeIds.toSet().size)
        challenges.forEach { challenge ->
            assertNotEquals(Event.NONE, challenge.event)
            assertTrue(challenge.id.isNotBlank())
            assertTrue(challenge.goal > 0)
            assertTrue(challenge.goalIncrement > 0)
            assertTrue(challenge.rewardXP > 0)
        }
    }
}
