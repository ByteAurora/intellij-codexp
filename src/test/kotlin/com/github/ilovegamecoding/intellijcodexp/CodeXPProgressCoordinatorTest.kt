package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEffectListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementEffect
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPProgressCoordinator
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CodeXPProgressCoordinatorTest : BasePlatformTestCase() {
    fun testWholeProgressResultPreservesDomainOrderAndProjectIdentity() {
        val state = CodeXPState(xp = 899)
        val engine = CodeXPProgressEngine()
        engine.initialize(state)
        val challenge = state.challenges.getValue(Event.CUT)
        challenge.progress = challenge.goal - 1
        challenge.rewardXP = 1000
        val result = engine.recordEvent(state, Event.CUT)
        val bus = ApplicationManager.getApplication().messageBus
        val connection = bus.connect(testRootDisposable)
        val order = mutableListOf<String>()
        val levels = mutableListOf<CodeXPLevel>()
        connection.subscribe(
            CodeXPListener.CODEXP,
            object : CodeXPListener {
                override fun xpUpdated(levelInfo: CodeXPLevel) {
                    levels += levelInfo
                    order += "xp"
                }

                override fun levelUp(
                    levelInfo: CodeXPLevel,
                    dataContext: DataContext?,
                ) {
                    order += "level"
                }

                override fun challengeUpdated(
                    event: Event,
                    challenge: CodeXPChallenge,
                    newChallenge: CodeXPChallenge?,
                ) {
                    order += "challengeUpdated"
                }

                override fun challengeCompleted(
                    event: Event,
                    challenge: CodeXPChallenge,
                    dataContext: DataContext?,
                ) {
                    order += "challengeCompleted"
                }
            },
        )
        var effects = 0
        connection.subscribe(
            CodeXPEffectListener.EFFECTS,
            CodeXPEffectListener { actual, context ->
                effects++
                order += "effects"
                assertSame(project, CommonDataKeys.PROJECT.getData(context!!))
                val presentation = AchievementEffect.from(actual, state.codeXPConfiguration)!!
                assertEquals(challenge.id, presentation.challenge!!.id)
                assertEquals(1000, presentation.challenge!!.reward)
                assertEquals(1, presentation.level!!.from)
                assertEquals(
                    result.xpChanges
                        .last()
                        .currentLevelInfo.level,
                    presentation.level!!.to,
                )
            },
        )
        CodeXPProgressCoordinator(bus).handleProgressResult(state, result, SimpleDataContext.getProjectContext(project))
        assertEquals(listOf("level", "xp", "level", "xp", "challengeCompleted", "challengeUpdated", "effects"), order)
        assertEquals(result.xpChanges.map { it.currentLevelInfo }, levels)
        assertEquals(1, effects)
        assertEquals(1900, state.xp)
        assertEquals(1, state.completedChallenges.size)
    }
}
