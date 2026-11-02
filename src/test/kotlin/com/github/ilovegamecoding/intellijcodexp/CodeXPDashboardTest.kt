package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardController
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardPanel
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardLevel
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardSnapshot
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JCheckBox
import javax.swing.SwingUtilities

class CodeXPDashboardTest : BasePlatformTestCase() {
    fun testAllHistoryAndTwoDashboardsStayInSyncWithLegacyHiddenHistory() =
        withService { service ->
            val legacy = XmlSerializer.serialize(service.state)
            legacy.addContent(Element("option").setAttribute("name", "showCompletedChallenges").setAttribute("value", "false"))
            service.loadState(XmlSerializer.deserialize(legacy, CodeXPState::class.java))
            val first = CodeXPDashboardPanel { false }
            val second = CodeXPDashboardPanel { false }
            CodeXPDashboardController(service, first, testRootDisposable).initialize()
            CodeXPDashboardController(service, second, testRootDisposable).initialize()
            assertEquals(Event.entries.size - 1, first.challengeRows.size)
            assertEquals(3, first.tabs.tabCount)
            assertTrue(descendants(first).none { it is JCheckBox })
            val row = first.challengeRows.getValue(Event.TYPING)
            service.eventOccurred(Event.TYPING, null)
            assertEquals("2", first.totalXP.text)
            assertEquals(first.totalXP.text, second.totalXP.text)
            assertEquals("1", first.statistics.getValue(Event.TYPING).text)
            assertSame(row, first.challengeRows[Event.TYPING])
            assertEquals(1L, service.state.getEventCount(Event.TYPING))
            repeat(18) {
                service.state.challenges
                    .getValue(Event.CUT)
                    .apply { progress = goal - 1 }
                service.eventOccurred(Event.CUT, null)
            }
            first.tabs.selectedIndex = 1
            assertEquals("18", first.completedCount.text)
            assertEquals(18, first.historyModel.size)
            assertEquals(
                service.state.completedChallenges
                    .last()
                    .id,
                first.historyModel[0].id,
            )
            assertEquals(18, second.historyModel.size)
            assertEquals(18L, first.historyModel[0].sequenceNumber)
            assertEquals(
                "Challenge #19",
                first.challengeRows
                    .getValue(Event.CUT)
                    .sequence.text,
            )
            assertEquals(
                "Challenge #19",
                second.challengeRows
                    .getValue(Event.CUT)
                    .sequence.text,
            )
            assertEquals(
                "Challenge #1",
                first.challengeRows
                    .getValue(Event.TYPING)
                    .sequence.text,
            )
            first.nickname.text = "Real nickname"
            assertEquals("Real nickname", service.state.nickname)
            assertEquals("Real nickname", second.nickname.text)
        }

    fun testAmbientRunsWithoutEventsButStopsHiddenInactiveReducedAndDisposed() =
        withService { service ->
            var osReduced = false
            val disposable = Disposer.newDisposable()
            Disposer.register(testRootDisposable, disposable)
            val panel = CodeXPDashboardPanel { osReduced }
            CodeXPDashboardController(service, panel, disposable).initialize()
            assertFalse(panel.isAnimationRunning)
            panel.visibilityChanged(true, false)
            assertFalse(panel.isAnimationRunning)
            panel.visibilityChanged(true, true)
            assertTrue(panel.isAnimationRunning)
            panel.tick(7000)
            assertTrue(panel.crest.ambientPhase > 0)
            assertEquals(0L, service.state.xp)
            panel.visibilityChanged(false)
            assertFalse(panel.isAnimationRunning)
            panel.tick(9000)
            assertEquals(0.0, panel.crest.ambientPhase, .0)
            panel.visibilityChanged(true)
            service.updateConfiguration(service.state.codeXPConfiguration.copy(reducedMotion = true))
            assertFalse(panel.isAnimationRunning)
            service.updateConfiguration(service.state.codeXPConfiguration.copy(reducedMotion = false))
            assertTrue(panel.isAnimationRunning)
            osReduced = true
            panel.tick(12000)
            assertFalse(panel.isAnimationRunning)
            panel.visibilityChanged(false)
            osReduced = false
            panel.visibilityChanged(true)
            assertTrue(panel.isAnimationRunning)
            Disposer.dispose(disposable)
            assertFalse(panel.isAnimationRunning)
            val previous = panel.targetXP
            service.eventOccurred(Event.TYPING, null)
            assertEquals(previous, panel.targetXP)
            panel.visibilityChanged(true)
            assertFalse(panel.isAnimationRunning)
        }

    fun testValuesAndNewChallengeUpdateImmediatelyWithoutTweenOrCompletionHold() =
        withService { service ->
            val panel = CodeXPDashboardPanel { false }
            Disposer.register(testRootDisposable, panel)
            panel.apply(DashboardSnapshot.from(service.state).copy(xp = 298))
            panel.visibilityChanged(true)
            val rows = panel.challengeRows.toMap()
            val next = DashboardSnapshot.from(service.state).copy(xp = 325)
            panel.apply(next)
            assertEquals("325", panel.totalXP.text)
            assertEquals(2, panel.crest.level.level)
            assertEquals(25L, panel.crest.level.into)
            assertEquals(25.0 / 315, panel.xpBar.fraction, .000001)
            val old = next.active.first()
            val replacement = old.copy(id = "replacement", progress = 0, goal = 200)
            panel.apply(next.copy(active = next.active.map { if (it.id == old.id) replacement else it }))
            assertEquals(
                "0 / 200",
                panel.challengeRows
                    .getValue(replacement.event)
                    .count.text,
            )
            assertEquals(
                0.0,
                panel.challengeRows
                    .getValue(replacement.event)
                    .bar.fraction,
                .0,
            )
            panel.tick(7300)
            assertEquals("325", panel.totalXP.text)
            assertEquals(25.0 / 315, panel.xpBar.fraction, .000001)
            assertEquals(rows, panel.challengeRows)
        }

    fun testCrestBoundsDoNotMoveWhenNumericWidthsChange() =
        withService { service ->
            val panel = CodeXPDashboardPanel { false }
            Disposer.register(testRootDisposable, panel)
            for (width in listOf(230, 260, 420)) {
                panel.apply(DashboardSnapshot.from(service.state))
                panel.setSize(width, 850)
                repeat(3) { layout(panel) }
                val bounds = panel.crest.bounds
                for (xp in listOf(299L, 300L, 9999L, 10000L, Long.MAX_VALUE)) {
                    panel.apply(DashboardSnapshot.from(service.state).copy(xp = xp))
                    repeat(3) { layout(panel) }
                    assertEquals(bounds, panel.crest.bounds)
                    assertEquals(width / 2.0, bounds.centerX, .5)
                }
                for (level in listOf(99, 100, 999, 1000, 9999, Int.MAX_VALUE)) {
                    panel.crest.level = DashboardLevel(level, 1, 10, 9)
                    repeat(3) { layout(panel) }
                    assertEquals(bounds, panel.crest.bounds)
                }
            }
        }

    fun testActivityColumnsAndAllTabViewportsUseAvailableHeight() =
        withService { service ->
            val panel = CodeXPDashboardPanel { false }
            Disposer.register(testRootDisposable, panel)
            panel.apply(DashboardSnapshot.from(service.state))
            panel.activityRows.values
                .first()
                .nameLabel.text = "Very long localized activity / 긴 활동 이름"
            panel.activityRows.values
                .last()
                .count.text = Long.MAX_VALUE.toString()
            for (width in listOf(230, 260, 450)) {
                for (height in listOf(360, 850, 1200)) {
                    panel.setSize(width, height)
                    for (tab in 0..2) {
                        panel.tabs.selectedIndex = tab
                        repeat(4) { layout(panel) }
                        val scroll = descendants(panel.tabs).filterIsInstance<JBScrollPane>().single { visible(it, panel.tabs) }
                        val bottom = SwingUtilities.convertRectangle(scroll.viewport.parent, scroll.viewport.bounds, panel).maxY
                        assertEquals(height - 6.0, bottom, 1.0)
                        assertTrue(scroll.viewport.height > 0)
                        assertEquals(6, panel.settingsButton.parent.width - panel.settingsButton.x - panel.settingsButton.width)
                    }
                    assertEquals(
                        1,
                        panel.activityRows.values
                            .map { it.xpLabel.bounds.centerX }
                            .toSet()
                            .size,
                    )
                    panel.activityRows.values.forEach {
                        assertTrue(it.nameLabel.bounds.maxX <= it.xpLabel.x)
                        assertTrue(it.xpLabel.bounds.maxX <= it.count.x)
                    }
                }
            }
        }

    fun testRenderNarrowWideLightDarkAndHiDpi() =
        withService { service ->
            service.state.nickname = "CodeXP tester"
            service.state.xp = 9720
            val originalDark = !JBColor.isBright()
            try {
                for (dark in listOf(false, true)) {
                    JBColor.setDark(dark)
                    for (width in listOf(260, 420)) {
                        val panel = CodeXPDashboardPanel { false }
                        Disposer.register(testRootDisposable, panel)
                        panel.apply(DashboardSnapshot.from(service.state))
                        panel.setSize(0, 0)
                        repeat(3) { layout(panel) }
                        panel.setSize(width, 850)
                        repeat(3) { layout(panel) }
                        val image = BufferedImage(width * 2, 1700, BufferedImage.TYPE_INT_ARGB)
                        val g = image.createGraphics()
                        g.scale(2.0, 2.0)
                        panel.paint(g)
                        g.dispose()
                        val file = File("build/reports/achievement-effects/activity-sequence/dashboard-${if (dark) "dark" else "light"}-$width.png")
                        file.parentFile.mkdirs()
                        ImageIO.write(image, "png", file)
                    }
                }
            } finally {
                JBColor.setDark(originalDark)
            }
        }

    fun testActivityBackgroundMatchesInsetsRowsAndEmptySpaceAcrossThemeSwitches() =
        withService { service ->
            val originalDark = !JBColor.isBright()
            val panel = CodeXPDashboardPanel { false }
            Disposer.register(testRootDisposable, panel)
            panel.apply(DashboardSnapshot.from(service.state))
            panel.tabs.selectedIndex = 2
            try {
                for (dark in listOf(false, true, false)) {
                    JBColor.setDark(dark)
                    SwingUtilities.updateComponentTreeUI(panel)
                    for (height in listOf(360, 1200)) {
                        panel.setSize(260, height)
                        repeat(4) { layout(panel) }
                        val scroll = descendants(panel.tabs).filterIsInstance<JBScrollPane>().single { visible(it, panel.tabs) }
                        assertEquals(height == 360, scroll.verticalScrollBar.isVisible)
                        val image = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_ARGB)
                        image.createGraphics().let { graphics ->
                            panel.paint(graphics)
                            graphics.dispose()
                        }

                        fun assertCanvas(
                            component: Component,
                            x: Int,
                            y: Int,
                        ) {
                            val point = SwingUtilities.convertPoint(component, Point(x, y), panel)
                            assertEquals("Canvas mismatch: dark=$dark height=$height point=$point", DashboardPalette.background.rgb, image.getRGB(point.x, point.y))
                        }
                        assertCanvas(panel, 2, 2)
                        assertCanvas(scroll, 2, 25)
                        assertCanvas(scroll, scroll.width - 2, 25)
                        assertCanvas(scroll, 20, scroll.height - 2)
                        assertCanvas(scroll.viewport, 2, 2)
                        if (height == 1200) {
                            val last = panel.activityRows.values.last()
                            assertCanvas(last.parent, 2, last.y + last.height + 10)
                            assertCanvas(scroll.viewport, 2, scroll.viewport.height - 2)
                        }
                        val file = File("build/reports/achievement-effects/activity-sequence/activity-${if (dark) "dark" else "light"}-$height.png")
                        file.parentFile.mkdirs()
                        ImageIO.write(image, "png", file)
                    }
                }
            } finally {
                JBColor.setDark(originalDark)
            }
        }

    private fun visible(
        component: Component,
        root: Component,
    ): Boolean = component.isVisible && (component === root || visible(component.parent, root))

    private fun descendants(component: Component): Sequence<Component> =
        sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }

    private fun layout(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layout)
    }

    private fun withService(action: (CodeXPService) -> Unit) {
        val service = ApplicationManager.getApplication().getService(CodeXPService::class.java)
        val original = service.state
        try {
            service.loadState(CodeXPState())
            service.state.codeXPConfiguration.showLevelUpNotification = false
            service.state.codeXPConfiguration.showCompleteChallengeNotification = false
            action(service)
        } finally {
            service.loadState(original)
        }
    }
}
