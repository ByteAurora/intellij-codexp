package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPButtonUI
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPTab
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardController
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardPanel
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardLevel
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardSnapshot
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
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
    fun testXpHeaderAndPointerFollowTheActualBarWithoutClipping() =
        withService { service ->
            val oldScale = JBUIScale.scale(1f)
            val dark = !JBColor.isBright()
            try {
                for (factor in listOf(1f, 2f)) {
                    JBUIScale.setUserScaleFactor(factor)
                    for (isDark in listOf(true, false)) {
                        JBColor.setDark(isDark)
                        val panel = CodeXPDashboardPanel { true }
                        Disposer.register(testRootDisposable, panel)
                        for (width in listOf(230, 260, 420, 700)) {
                            panel.setSize(JBUIScale.scale(width), JBUIScale.scale(800))
                            for (xp in listOf(0L, 250L, 944L, 945L, 1641968L, 10000000L)) {
                                panel.apply(DashboardSnapshot.from(service.state).copy(xp = xp))
                                repeat(4) { layout(panel) }
                                assertTrue(panel.xpText.bounds.maxY <= panel.xpBar.y)
                                assertTrue(panel.remaining.bounds.maxY <= panel.xpBar.y)
                                assertFalse(panel.xpText.bounds.intersects(panel.remaining.bounds))
                                assertEquals(panel.xpBar.fraction, panel.xpMarker.fraction, 0.0)
                                assertEquals(panel.xpBar.x + panel.xpBar.width * panel.xpBar.fraction, panel.xpMarker.x + panel.xpMarker.pointerX, .01)
                                val text = panel.xpMarker.textBounds
                                assertTrue(text.x >= 0 && text.maxX <= panel.xpMarker.width)
                                assertTrue(text.y >= 0 && text.maxY <= panel.xpMarker.height)
                                assertTrue(panel.xpMarker.percentageText.matches(Regex("[0-9]+\\.[0-9]%")))
                            }
                        }
                    }
                }
            } finally {
                JBUIScale.setUserScaleFactor(oldScale)
                JBColor.setDark(dark)
            }
        }

    fun testMobileControlsFitNarrowHeadersAndKeepNativeActionsAndTabNavigation() =
        withService { service ->
            val panel = CodeXPDashboardPanel { true }
            Disposer.register(testRootDisposable, panel)
            CodeXPDashboardController(service, panel, testRootDisposable).initialize()
            var settings = 0
            var collections = 0
            var guides = 0
            panel.onSettings = { settings++ }
            panel.onCollectionRequested = { collections++ }
            panel.onGuide = { guides++ }
            assertSame(AllIcons.General.ContextHelp, panel.guideButton.icon)
            for (width in listOf(230, 260, 420)) {
                panel.setSize(width, 850)
                repeat(4) { layout(panel) }
                val buttons = listOf(panel.collectionButton, panel.guideButton, panel.settingsButton)
                buttons.forEach {
                    assertTrue(it.ui is CodeXPButtonUI)
                    assertFalse((it.ui as CodeXPButtonUI).isAnimating)
                    assertTrue(it.parent.bounds.width >= it.bounds.maxX)
                    assertTrue(it.height >= 36)
                }
                assertTrue(panel.collectionButton.bounds.maxX <= panel.guideButton.x)
                assertEquals(panel.collectionButton.width, panel.collectionButton.height)
                assertTrue(panel.collectionButton.text.isEmpty())
                assertNotNull(panel.collectionButton.icon)
                assertEquals(CodeXPBundle.message("collection.heading"), panel.collectionButton.toolTipText)
                assertEquals(panel.collectionButton.toolTipText, panel.collectionButton.accessibleContext.accessibleName)
                assertTrue(panel.guideButton.bounds.maxX <= panel.settingsButton.x)
                val tabs = descendants(panel.tabs).filterIsInstance<CodeXPTab>().toList()
                assertEquals(3, tabs.size)
                tabs.forEach {
                    assertTrue("${it.text} clipped at width=$width", it.getFontMetrics(it.font).stringWidth(it.text) <= it.width - it.insets.left - it.insets.right)
                }
                tabs[0].actionMap.get("RIGHT").actionPerformed(java.awt.event.ActionEvent(tabs[0], 0, "RIGHT"))
                assertEquals(1, panel.tabs.selectedIndex)
                tabs[1].doClick()
                assertEquals(1, panel.tabs.selectedIndex)
                tabs[1].actionMap.get("LEFT").actionPerformed(java.awt.event.ActionEvent(tabs[1], 0, "LEFT"))
                assertEquals(0, panel.tabs.selectedIndex)
            }
            panel.settingsButton.doClick()
            panel.collectionButton.doClick()
            panel.guideButton.doClick()
            assertEquals(listOf(1, 1, 1), listOf(settings, collections, guides))
        }

    fun testTabsReflowForWidePlatformFontsWithoutShrinkingTheirText() =
        withService { service ->
            val panel = CodeXPDashboardPanel { true }
            Disposer.register(testRootDisposable, panel)
            CodeXPDashboardController(service, panel, testRootDisposable).initialize()
            val tabs = descendants(panel.tabs).filterIsInstance<CodeXPTab>().toList()
            val wideFont = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 16)
            tabs.forEach { it.font = wideFont }
            for (width in listOf(230, 420, 230)) {
                panel.setSize(width, 900)
                repeat(4) { layout(panel) }
                tabs.forEach {
                    assertEquals(wideFont, it.font)
                    assertTrue(it.getFontMetrics(it.font).stringWidth(it.text) <= it.width - it.insets.left - it.insets.right)
                    assertTrue(it.bounds.maxX <= it.parent.width && it.bounds.maxY <= it.parent.height)
                }
                tabs.zipWithNext().forEach { (a, b) -> assertFalse(a.bounds.intersects(b.bounds)) }
                if (width == 230) assertTrue(tabs[0].y < tabs[1].y) else assertEquals(tabs[0].y, tabs[1].y)
            }
        }

    fun testAllHistoryAndTwoDashboardsStayInSyncWithLegacyHiddenHistory() =
        withService { service ->
            val legacy = XmlSerializer.serialize(service.state)
            legacy.addContent(Element("option").setAttribute("name", "showCompletedChallenges").setAttribute("value", "false"))
            service.loadState(XmlSerializer.deserialize(legacy, CodeXPState::class.java))
            val first = CodeXPDashboardPanel { false }
            val second = CodeXPDashboardPanel { false }
            val firstController = CodeXPDashboardController(service, first, testRootDisposable).also { it.initialize() }
            val secondController = CodeXPDashboardController(service, second, testRootDisposable).also { it.initialize() }
            first.visibilityChanged(true)
            second.visibilityChanged(true)
            assertEquals(Event.entries.size - 1, first.challengeRows.size)
            assertEquals(3, first.tabs.tabCount)
            assertTrue(descendants(first).none { it is JCheckBox })
            val row = first.challengeRows.getValue(Event.TYPING)
            service.eventOccurred(Event.TYPING, null)
            firstController.flushPending()
            secondController.flushPending()
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
            firstController.flushPending()
            secondController.flushPending()
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

    fun testBurstUpdatesOnlyOncePerFrameAndHiddenPanelsCatchUpOnOpen() =
        withService { service ->
            val panel = CodeXPDashboardPanel { true }
            var collectionUpdates = 0
            val controller = CodeXPDashboardController(service, panel, testRootDisposable) { collectionUpdates++ }
            controller.initialize()
            var numericUpdates = 0
            panel.totalXP.addPropertyChangeListener("text") { numericUpdates++ }
            val initialCollectionUpdates = collectionUpdates
            repeat(500) { service.eventOccurred(Event.TYPING, null) }
            controller.flushPending()
            assertEquals(500L, service.state.getEventCount(Event.TYPING))
            assertEquals(0, numericUpdates)
            assertEquals(initialCollectionUpdates, collectionUpdates)
            panel.visibilityChanged(true)
            assertEquals(service.state.xp, panel.targetXP)
            assertEquals(1, numericUpdates)
            repeat(100) { service.eventOccurred(Event.TYPING, null) }
            assertEquals(1, numericUpdates)
            controller.flushPending()
            assertEquals(2, numericUpdates)
            assertEquals(service.state.xp, panel.targetXP)
            assertEquals("600", panel.statistics.getValue(Event.TYPING).text)
            assertEquals(initialCollectionUpdates, collectionUpdates)
            panel.visibilityChanged(false)
            controller.isCollectionVisible = { true }
            controller.refreshVisible()
            assertTrue(collectionUpdates > initialCollectionUpdates)
            val afterOpen = collectionUpdates
            controller.refreshVisible()
            assertEquals(afterOpen, collectionUpdates)
            service.eventOccurred(Event.TYPING, null)
            service.resetAllExceptNickname()
            controller.flushPending()
            panel.visibilityChanged(true)
            assertEquals(0L, panel.targetXP)
            assertEquals("0", panel.statistics.getValue(Event.TYPING).text)
        }

    fun testAmbientRunsWhileVisibleAndStopsWhenHiddenReducedOrDisposed() =
        withService { service ->
            var osReduced = false
            val disposable = Disposer.newDisposable()
            Disposer.register(testRootDisposable, disposable)
            val panel = CodeXPDashboardPanel { osReduced }
            CodeXPDashboardController(service, panel, disposable).initialize()
            assertFalse(panel.isAnimationRunning)
            panel.visibilityChanged(true)
            assertTrue(panel.isAnimationRunning)
            panel.tick(System.nanoTime() / 1_000_000 + 7000)
            assertTrue(panel.crest.motionFrame.seconds > 0)
            assertEquals(0L, service.state.xp)
            val paused = panel.crest.motionFrame
            panel.visibilityChanged(false)
            assertFalse(panel.isAnimationRunning)
            panel.tick(System.nanoTime() / 1_000_000 + 9000)
            assertEquals(paused, panel.crest.motionFrame)
            panel.visibilityChanged(true)
            service.updateConfiguration(service.state.codeXPConfiguration.copy(reducedMotion = true))
            assertFalse(panel.isAnimationRunning)
            service.updateConfiguration(service.state.codeXPConfiguration.copy(reducedMotion = false))
            assertTrue(panel.isAnimationRunning)
            osReduced = true
            panel.visibilityChanged(false)
            panel.visibilityChanged(true)
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
            panel.apply(DashboardSnapshot.from(service.state).copy(xp = 898))
            panel.visibilityChanged(true)
            val rows = panel.challengeRows.toMap()
            val next = DashboardSnapshot.from(service.state).copy(xp = 925)
            panel.apply(next)
            assertEquals("925", panel.totalXP.text)
            assertEquals(2, panel.crest.level.level)
            assertEquals(25L, panel.crest.level.into)
            assertEquals(25.0 / 945, panel.xpBar.fraction, .000001)
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
            assertEquals("925", panel.totalXP.text)
            assertEquals(25.0 / 945, panel.xpBar.fraction, .000001)
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

    fun testProgressAndTotalsRemainReadableNearTheCapInANarrowPanel() =
        withService { service ->
            val panel = CodeXPDashboardPanel { false }
            Disposer.register(testRootDisposable, panel)
            for (width in listOf(230, 260, 420)) {
                for (xp in listOf(1641968L, 1651968L, 10000000L)) {
                    panel.apply(DashboardSnapshot.from(service.state).copy(xp = xp))
                    panel.setSize(width, 700)
                    repeat(4) { layout(panel) }
                    for (label in listOf(panel.xpText, panel.remaining, panel.totalXP, panel.completedCount)) {
                        assertTrue("$width/$xp: '${label.text}' clips at ${label.width}px", label.getFontMetrics(label.font).stringWidth(label.text) <= label.width)
                    }
                }
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
