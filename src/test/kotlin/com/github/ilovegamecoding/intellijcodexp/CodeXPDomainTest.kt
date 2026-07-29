package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine
import com.github.ilovegamecoding.intellijcodexp.enums.CodeXPNotificationType
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallengeFactory
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
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
        val xpEvents = Event.entries.filterNot { it == Event.NONE }.toSet()

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

    @Test
    fun `progress engine initializes event counts and active challenges`() {
        val state = CodeXPState()

        CodeXPProgressEngine().initialize(state)

        assertTrue(state.hasExecuted)
        Event.entries.forEach { event ->
            assertEquals(0, state.getEventCount(event))
        }
        assertEquals(Event.entries.filterNot { it == Event.NONE }.size, state.challenges.size)
    }

    @Test
    fun `notification type parses persisted display values`() {
        assertEquals(
            CodeXPNotificationType.INTELLIJ,
            CodeXPNotificationType.fromStoredValue("IntelliJ Notification"),
        )
        assertEquals(
            CodeXPNotificationType.CODEXP,
            CodeXPNotificationType.fromStoredValue("CodeXP Notification"),
        )
    }

    @Test
    fun `progress engine records event xp and challenge progress`() {
        val state = CodeXPState()
        val engine = CodeXPProgressEngine()
        engine.initialize(state)

        val result = engine.recordEvent(state, Event.TYPING)

        assertEquals(2, state.xp)
        assertEquals(1, state.getEventCount(Event.TYPING))
        assertEquals(1, state.challenges.getValue(Event.TYPING).progress)
        assertEquals(Event.TYPING, result.event)
        assertEquals(1, result.xpChanges.size)
        assertEquals(
            2,
            result.xpChanges
                .single()
                .currentLevelInfo.xpIntoCurrentLevel,
        )
    }

    @Test
    fun `progress engine completes challenge and creates next challenge`() {
        val state = CodeXPState()
        val engine = CodeXPProgressEngine()
        engine.initialize(state)
        val challenge = state.challenges.getValue(Event.CUT)
        challenge.progress = challenge.goal - 1

        val result = engine.recordEvent(state, Event.CUT)

        assertEquals(101, state.xp)
        assertEquals(1, state.completedChallenges.size)
        assertEquals(challenge.id, state.completedChallenges.single().id)
        assertEquals(0, state.challenges.getValue(Event.CUT).progress)
        assertEquals(challenge.goal + challenge.goalIncrement, state.challenges.getValue(Event.CUT).goal)
        assertEquals(challenge.rewardXP + challenge.rewardXPIncrement, state.challenges.getValue(Event.CUT).rewardXP)
        assertEquals(challenge.id, result.completedChallenge?.id)
        assertEquals(state.challenges.getValue(Event.CUT).id, result.newChallenge?.id)
        assertEquals(2, result.xpChanges.size)
    }
}
