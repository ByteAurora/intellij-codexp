package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressResult
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEffectListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallengeFactory
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardController
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardPanel
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementEffect
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

/** Fixtures were serialized by the published 2.1.1 JAR, not by this version's models. */
class CodeXPLegacyUpgradeTest : BasePlatformTestCase() {
    fun testPublished211FixturesSurviveLoadFirstDisplaySaveAndRestartWithoutReplayingEvents() =
        withService { service ->
            val signals = Signals()
            signals.connect()
            for (variant in listOf("default", "intellij", "silent")) {
                val legacy = fixture(variant)
                val scope = Disposer.newDisposable()
                try {
                    service.loadState(XmlSerializer.deserialize(legacy, CodeXPState::class.java))
                    assertLegacy(legacy, service.state, variant)
                    val panel = CodeXPDashboardPanel { false }
                    CodeXPDashboardController(service, panel, scope).initialize()
                    panel.visibilityChanged(true)
                    for (tab in 0..2) panel.tabs.selectedIndex = tab
                    UIUtil.dispatchAllInvocationEvents()
                    assertEquals(8, panel.historyModel.size) // Legacy visibility=false no longer hides the History tab.
                    assertEquals(
                        "Challenge #1,234",
                        panel.challengeRows
                            .getValue(Event.SAVE)
                            .sequence.text,
                    )
                    assertFalse(panel.challenges.motion.isRunning)
                    assertFalse(panel.history.motion.isRunning)
                    assertEquals(0, signals.domainSignals)
                    assertTrue(signals.results.isEmpty())

                    val written = XmlSerializer.serialize(service.state)
                    service.loadState(XmlSerializer.deserialize(written, CodeXPState::class.java))
                    UIUtil.dispatchAllInvocationEvents()
                    assertLegacy(legacy, service.state, variant)
                    assertEquals(JDOMUtil.writeElement(written), JDOMUtil.writeElement(XmlSerializer.serialize(service.state)))
                    assertEquals(0, signals.domainSignals)
                    assertTrue(signals.results.isEmpty())
                } finally {
                    Disposer.dispose(scope)
                }
            }
        }

    fun testFirstNewEventAndCompletionAwardExactlyOnceAndRespectPreservedPreferences() =
        withService { service ->
            for (variant in listOf("default", "intellij", "silent")) {
                service.loadState(XmlSerializer.deserialize(fixture(variant), CodeXPState::class.java))
                val signals = Signals()
                val connection = signals.connect()
                try {
                    val state = service.state
                    val beforeXP = state.xp
                    val saveCount = state.getEventCount(Event.SAVE)
                    val typingCount = state.getEventCount(Event.TYPING)
                    val save = state.challenges.getValue(Event.SAVE)
                    val completedIds = state.completedChallenges.map { it.id }
                    service.eventOccurred(Event.TYPING, null)
                    assertEquals(beforeXP + 2, state.xp)
                    assertEquals(typingCount + 1, state.getEventCount(Event.TYPING))
                    assertEquals(4L, state.challenges.getValue(Event.TYPING).progress)
                    assertEquals(1L, state.challenges.getValue(Event.TYPING).lastUpdatedOrder)
                    assertTrue(
                        state.challenges
                            .filterKeys { it != Event.TYPING }
                            .values
                            .all { it.lastUpdatedOrder == 0L },
                    )
                    assertNull(AchievementEffect.from(signals.results.single(), state.codeXPConfiguration))

                    service.eventOccurred(Event.SAVE, null)
                    assertEquals(beforeXP + 2 + 10 + 493500, state.xp)
                    assertEquals(saveCount + 1, state.getEventCount(Event.SAVE))
                    assertEquals(completedIds + save.id, state.completedChallenges.map { it.id })
                    assertEquals(1234L, CodeXPChallengeFactory.sequenceNumber(state.completedChallenges.last()))
                    assertEquals(1235L, CodeXPChallengeFactory.sequenceNumber(state.challenges.getValue(Event.SAVE)))
                    assertEquals(0L, state.challenges.getValue(Event.SAVE).progress)
                    assertEquals(2L, state.challenges.getValue(Event.SAVE).lastUpdatedOrder)
                    val effect = AchievementEffect.from(signals.results.last(), state.codeXPConfiguration)
                    if (variant == "default") {
                        assertEquals(save.id, effect!!.challenge!!.id)
                        assertEquals(493500L, effect.challenge!!.reward)
                    } else {
                        assertNull(effect)
                        assertFalse(state.codeXPConfiguration.showGainedXP)
                    }

                    val afterCompletion = JDOMUtil.writeElement(XmlSerializer.serialize(state))
                    service.loadState(XmlSerializer.deserialize(JDOMUtil.load(afterCompletion), CodeXPState::class.java))
                    UIUtil.dispatchAllInvocationEvents()
                    assertEquals(2, signals.results.size)
                    assertEquals(afterCompletion, JDOMUtil.writeElement(XmlSerializer.serialize(service.state)))
                    service.eventOccurred(Event.SAVE, null)
                    assertEquals(beforeXP + 2 + 10 + 493500 + 10, service.state.xp)
                    assertEquals(9, service.state.completedChallenges.size)
                    assertEquals(
                        1L,
                        service.state.challenges
                            .getValue(Event.SAVE)
                            .progress,
                    )
                    assertEquals(
                        3L,
                        service.state.challenges
                            .getValue(Event.SAVE)
                            .lastUpdatedOrder,
                    )
                    assertEquals(3, signals.results.size)
                    assertEquals(1, signals.results.count { it.completedChallenge != null })
                    assertNull(AchievementEffect.from(signals.results.last(), service.state.codeXPConfiguration))
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun assertLegacy(
        legacy: Element,
        state: CodeXPState,
        variant: String,
    ) {
        assertTrue(state.hasExecuted)
        assertEquals(legacy.option("nickname")!!.getAttributeValue("value"), state.nickname)
        assertEquals(legacy.option("xp")!!.getAttributeValue("value").toLong(), state.xp)
        val counts =
            legacy
                .option("eventCounts")!!
                .getChild("map")
                .getChildren("entry")
                .associate { Event.valueOf(it.getAttributeValue("key")) to it.getAttributeValue("value").toLong() }
        assertEquals(counts, state.eventCounts)
        val active = legacy.option("challenges")!!.getChild("map").getChildren("entry")
        assertEquals(active.size, state.challenges.size)
        active.forEach { assertChallenge(it.getChild("value").getChild("CodeXPChallenge"), state.challenges.getValue(Event.valueOf(it.getAttributeValue("key")))) }
        val completed = legacy.option("completedChallenges")!!.getChild("list").getChildren("CodeXPChallenge")
        assertEquals(completed.size, state.completedChallenges.size)
        completed.zip(state.completedChallenges).forEach { (saved, actual) -> assertChallenge(saved, actual) }
        assertEquals(
            when (variant) {
                "intellij" -> CodeXPConfiguration(notificationType = "IntelliJ Notification", showGainedXP = false, positionToDisplayGainedXP = PositionToDisplayGainedXP.BOTTOM_LEFT)
                "silent" -> CodeXPConfiguration(showLevelUpNotification = false, showCompleteChallengeNotification = false, showGainedXP = false, positionToDisplayGainedXP = PositionToDisplayGainedXP.LEFT)
                else -> CodeXPConfiguration()
            },
            state.codeXPConfiguration,
        )
        assertEquals("celebratory", state.codeXPConfiguration.effectStyle)
        assertFalse(state.codeXPConfiguration.reducedMotion)
    }

    private fun assertChallenge(
        saved: Element,
        actual: CodeXPChallenge,
    ) {
        val expected = saved.getChildren("option").associate { it.getAttributeValue("name") to it.getAttributeValue("value") }
        assertEquals(
            expected,
            mapOf(
                "id" to actual.id,
                "event" to actual.event.name,
                "name" to actual.name,
                "description" to actual.description,
                "goal" to actual.goal.toString(),
                "goalIncrement" to actual.goalIncrement.toString(),
                "progress" to actual.progress.toString(),
                "rewardXP" to actual.rewardXP.toString(),
                "rewardXPIncrement" to actual.rewardXPIncrement.toString(),
            ),
        )
        assertEquals(0L, actual.lastUpdatedOrder)
    }

    private fun fixture(variant: String): Element {
        val text = javaClass.getResource("/upgrade/v2.1.1/codexp-2.1.1-$variant.xml")!!.readText()
        assertFalse(text.contains("lastUpdatedOrder"))
        assertFalse(text.contains("effectStyle"))
        assertFalse(text.contains("reducedMotion"))
        val component = JDOMUtil.load(text).getChild("component")
        assertEquals("com.github.ilovegamecoding.intellijcodexp.services.CodeXP", component.getAttributeValue("name"))
        assertEquals("false", component.option("showCompletedChallenges")!!.getAttributeValue("value"))
        return component
    }

    private fun Element.option(name: String): Element? = getChildren("option").singleOrNull { it.getAttributeValue("name") == name }

    private inner class Signals : CodeXPListener {
        val results = mutableListOf<CodeXPProgressResult>()
        var domainSignals = 0

        fun connect() =
            ApplicationManager.getApplication().messageBus.connect(testRootDisposable).also {
                it.subscribe(CodeXPListener.CODEXP, this)
                it.subscribe(CodeXPEffectListener.EFFECTS, CodeXPEffectListener { result, _ -> results += result })
            }

        override fun xpUpdated(levelInfo: CodeXPLevel) {
            domainSignals++
        }

        override fun levelUp(
            levelInfo: CodeXPLevel,
            dataContext: DataContext?,
        ) {
            domainSignals++
        }

        override fun challengeUpdated(
            event: Event,
            challenge: CodeXPChallenge,
            newChallenge: CodeXPChallenge?,
        ) {
            domainSignals++
        }

        override fun challengeCompleted(
            event: Event,
            challenge: CodeXPChallenge,
            dataContext: DataContext?,
        ) {
            domainSignals++
        }
    }

    private fun withService(action: (CodeXPService) -> Unit) {
        val service = ApplicationManager.getApplication().getService(CodeXPService::class.java)
        val original = service.state
        try {
            action(service)
        } finally {
            service.loadState(original)
        }
    }
}
