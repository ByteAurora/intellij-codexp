package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressResult
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.IdeXpOrigin
import com.github.ilovegamecoding.intellijcodexp.listeners.XpDestination
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallengeFactory
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardController
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardPanel
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.CodeXPOverlayController
import com.github.ilovegamecoding.intellijcodexp.presentation.settings.CodeXPConfigurable
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.XmlSerializer
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLayeredPane

class CodeXPResetTest : BasePlatformTestCase() {
    fun testCancelPreservesAllDataAndConfirmUsesCanonicalDefaultsInEveryOpenSettingsForm() =
        withFixture { service ->
            val first = CodeXPConfigurable()
            val second = CodeXPConfigurable()
            try {
                val root = first.createComponent()
                val other = second.createComponent()
                descendants(other).filterIsInstance<JCheckBox>().first().doClick()
                assertTrue(second.isModified())
                val button = descendants(root).filterIsInstance<JButton>().single { it.text == CodeXPBundle.message("settings.history.reset") }
                val before = xml(service.state)
                val cancel =
                    TestDialogManager.setTestDialog(
                        TestDialog { message ->
                            assertTrue(message.contains("except your nickname"))
                            assertTrue(message.contains("cannot be undone"))
                            0
                        },
                    )
                try {
                    button.doClick()
                } finally {
                    TestDialogManager.setTestDialog(cancel)
                }
                assertEquals(before, xml(service.state))
                val oldIds =
                    service.state.challenges.values
                        .map { it.id }
                        .toSet()
                val confirm = TestDialogManager.setTestDialog(TestDialog { 1 })
                try {
                    button.doClick()
                } finally {
                    TestDialogManager.setTestDialog(confirm)
                }
                assertInitial(service)
                assertTrue(
                    service.state.challenges.values
                        .none { it.id in oldIds },
                )
                assertFalse(first.isModified())
                assertFalse(second.isModified())
                first.apply()
                second.apply()
                assertEquals(CodeXPConfiguration(), service.state.codeXPConfiguration)
                val saved = xml(service.state)
                service.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(service.state), CodeXPState::class.java))
                assertEquals(saved, xml(service.state))
                assertInitial(service)
                // Starting over deliberately makes initial-tier rewards available again, exactly once.
                service.state.codeXPConfiguration.showCompleteChallengeNotification = false
                service.state.codeXPConfiguration.showLevelUpNotification = false
                val initial = service.state.challenges.getValue(Event.SAVE)
                repeat(initial.goal.toInt()) { service.eventOccurred(Event.SAVE, null) }
                assertEquals(initial.goal * Event.SAVE.xpValue + initial.rewardXP, service.state.xp)
                assertEquals(1, service.state.completedChallenges.size)
                val xp = service.state.xp
                service.eventOccurred(Event.SAVE, null)
                assertEquals(xp + Event.SAVE.xpValue, service.state.xp)
                assertEquals(
                    1L,
                    service.state.challenges
                        .getValue(Event.SAVE)
                        .progress,
                )
            } finally {
                first.disposeUIResources()
                second.disposeUIResources()
            }
        }

    fun testResetClearsBothDashboardsAndDropsPendingPreResetEffects() =
        withFixture { service ->
            val first = CodeXPDashboardPanel { false }
            val second = CodeXPDashboardPanel { false }
            val firstController = CodeXPDashboardController(service, first, testRootDisposable).also { it.initialize() }
            val secondController = CodeXPDashboardController(service, second, testRootDisposable).also { it.initialize() }
            first.visibilityChanged(true)
            second.visibilityChanged(true)
            service.eventOccurred(Event.DEBUG, null)
            firstController.flushPending()
            secondController.flushPending()
            assertTrue(first.challenges.motion.isRunning)
            val pane =
                object : JLayeredPane() {
                    override fun isShowing(): Boolean = true
                }.apply { setSize(800, 500) }
            val context = SimpleDataContext.getSimpleContext(IdeXpOrigin.KEY, IdeXpOrigin(project, null, pane, XpDestination.WINDOW))
            val controller = CodeXPOverlayController(project)
            Disposer.register(testRootDisposable, controller)
            controller.progressRecorded(CodeXPProgressResult(Event.SAVE), context)
            assertEquals(1, pane.componentCount)
            Thread { controller.progressRecorded(CodeXPProgressResult(Event.SAVE), context) }.apply {
                start()
                join()
            }
            service.resetAllExceptNickname()
            UIUtil.dispatchAllInvocationEvents()
            assertEquals(0, pane.componentCount)
            for (panel in listOf(first, second)) {
                assertEquals("0", panel.totalXP.text)
                assertEquals(0, panel.historyModel.size)
                assertFalse(panel.challenges.motion.isRunning)
                assertEquals(0, panel.challenges.motion.movementStarts)
                assertTrue(panel.challengeRows.values.all { it.sequence.text == "Challenge #1" })
            }
            assertInitial(service)
            controller.progressRecorded(CodeXPProgressResult(Event.SAVE), context)
            assertEquals(1, pane.componentCount)
        }

    private fun assertInitial(service: CodeXPService) {
        val state = service.state
        assertEquals("Keep this nickname 한글", state.nickname)
        assertEquals(0L, state.xp)
        assertTrue(state.hasExecuted)
        assertTrue(state.completedChallenges.isEmpty())
        assertTrue(Event.entries.all { state.getEventCount(it) == 0L })
        assertEquals(CodeXPConfiguration(), state.codeXPConfiguration)
        val expected = CodeXPChallengeFactory.createEventDefaultChallenges().associateBy { it.event }
        assertEquals(expected.keys, state.challenges.keys)
        for ((event, actual) in state.challenges) {
            val initial = expected.getValue(event)
            assertEquals(initial.goal, actual.goal)
            assertEquals(initial.rewardXP, actual.rewardXP)
            assertEquals(initial.goalIncrement, actual.goalIncrement)
            assertEquals(initial.rewardXPIncrement, actual.rewardXPIncrement)
            assertEquals(0L, actual.progress)
            assertEquals(0L, actual.lastUpdatedOrder)
            assertEquals(1L, CodeXPChallengeFactory.sequenceNumber(actual))
        }
    }

    private fun withFixture(action: (CodeXPService) -> Unit) {
        val service = ApplicationManager.getApplication().getService(CodeXPService::class.java)
        val original = service.state
        try {
            service.loadState(CodeXPState(nickname = "Keep this nickname 한글"))
            service.state.codeXPConfiguration.apply {
                reducedMotion = true
                effectStyle = "subtle"
                showLevelUpNotification = false
                showCompleteChallengeNotification = false
            }
            repeat(12) { service.eventOccurred(Event.SAVE, null) }
            service.state.codeXPConfiguration.reducedMotion = false
            action(service)
        } finally {
            service.loadState(original)
        }
    }

    private fun xml(state: CodeXPState): String = JDOMUtil.writeElement(XmlSerializer.serialize(state))

    private fun descendants(component: Component): Sequence<Component> =
        sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }
}
