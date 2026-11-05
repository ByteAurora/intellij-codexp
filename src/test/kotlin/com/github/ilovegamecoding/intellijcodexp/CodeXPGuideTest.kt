package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPButton
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPButtonStyle
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPButtonUI
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardChoiceBalance
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.guide.CodeXPGuideAction
import com.github.ilovegamecoding.intellijcodexp.presentation.guide.CodeXPGuideCatalog
import com.github.ilovegamecoding.intellijcodexp.presentation.guide.CodeXPGuideDialog
import com.github.ilovegamecoding.intellijcodexp.presentation.guide.CodeXPGuidePanel
import com.github.ilovegamecoding.intellijcodexp.presentation.guide.GuideDestination
import com.github.ilovegamecoding.intellijcodexp.presentation.guide.GuideRelease
import com.github.ilovegamecoding.intellijcodexp.presentation.guide.GuideStep
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPGuideService
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPGuideState
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.github.ilovegamecoding.intellijcodexp.services.MemoryCollectionPersistence
import com.intellij.DynamicBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.xmlb.XmlSerializer
import java.awt.Container
import java.awt.event.ActionEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.accessibility.AccessibleRole
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

class CodeXPGuideTest : BasePlatformTestCase() {
    fun testCodeXPControlsKeepNativeActionsAndThemeAwareStatesWithoutIdleAnimation() {
        val dark = !JBColor.isBright()
        try {
            for (isDark in listOf(false, true)) {
                JBColor.setDark(isDark)
                val button = CodeXPButton(CodeXPButtonStyle.PRIMARY, { true }, "Next")
                button.setSize(160, 44)
                val before = paintButton(button)
                assertEquals(DashboardPalette.teal.rgb, before.getRGB(12, 22))
                button.model.isRollover = true
                assertFalse((button.ui as CodeXPButtonUI).isAnimating)
                val hover = paintButton(button)
                assertFalse(before.getRGB(12, 22) == hover.getRGB(12, 22))
                var clicked = 0
                button.addActionListener { clicked++ }
                button.doClick(0)
                assertEquals(1, clicked)
                button.isEnabled = false
                button.doClick(0)
                assertEquals(1, clicked)
                assertEquals(DashboardPalette.track.rgb, paintButton(button).getRGB(12, 22))
                button.updateUI()
                assertTrue(button.ui is CodeXPButtonUI)
                assertFalse((button.ui as CodeXPButtonUI).isAnimating)
                assertEquals(AccessibleRole.PUSH_BUTTON, button.accessibleContext.accessibleRole)
                assertEquals("Next", button.accessibleContext.accessibleName)
                assertNotNull(button.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("pressed SPACE")))
            }
        } finally {
            JBColor.setDark(dark)
        }
    }

    fun testGuideSegmentsAndProgressFollowKeyboardNavigation() {
        val service = CodeXPService(MemoryCollectionPersistence())
        service.loadState(CodeXPState(xp = 1000))
        val guide = CodeXPGuideService().apply { initialize(false) }
        try {
            val panel = CodeXPGuidePanel(guide, service, {}, {})
            panel.showGuide()
            assertEquals(5, panel.steps.maximum)
            assertEquals(1, panel.steps.value)
            assertFalse(panel.previous.isVisible)
            assertFalse(panel.previous.isEnabled)
            panel.next.doClick(0)
            assertEquals(2, panel.steps.value)
            assertTrue(panel.previous.isVisible)
            assertTrue(panel.previous.isEnabled)
            panel.introTab.actionMap
                .get("RIGHT")
                .actionPerformed(ActionEvent(panel.introTab, 0, "RIGHT"))
            assertTrue(panel.updatesTab.isSelected)
            assertFalse(panel.introTab.isSelected)
            assertEquals(4, panel.steps.maximum)
            assertFalse(panel.previous.isVisible)
            panel.updatesTab.actionMap
                .get("LEFT")
                .actionPerformed(ActionEvent(panel.updatesTab, 0, "LEFT"))
            assertTrue(panel.introTab.isSelected)
            assertEquals(2, panel.steps.value)
            assertTrue(panel.previous.isVisible)
            panel.previous.doClick(0)
            assertEquals(1, panel.steps.value)
            assertFalse(panel.previous.isVisible)
            assertFalse(panel.previous.isEnabled)
            assertEquals(panel.page.text, panel.steps.accessibleContext.accessibleName)
            assertEquals(AccessibleRole.PROGRESS_BAR, panel.steps.accessibleContext.accessibleRole)
        } finally {
            Disposer.dispose(service)
        }
    }

    private fun paintButton(button: CodeXPButton): BufferedImage {
        val image = BufferedImage(button.width, button.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        button.paint(g)
        g.dispose()
        return image
    }

    fun testModelessDialogClosePreservesUnreadPageAndDestinationClosesWindow() {
        val progress = CodeXPService(MemoryCollectionPersistence())
        progress.loadState(CodeXPState(xp = 1000))
        val guide = CodeXPGuideService().apply { initialize(false) }
        var destination: GuideDestination? = null
        val dialog = CodeXPGuideDialog(project, guide, progress) { destination = it }
        try {
            assertFalse(dialog.isModal)
            assertFalse(dialog.guidePanel.close.isVisible)
            assertEquals(JBUI.scale(600), dialog.guidePanel.preferredSize.width)
            assertEquals(JBUI.scale(620), dialog.guidePanel.preferredSize.height)
            dialog.guidePanel.next.doClick()
            dialog.guidePanel.next.doClick()
            dialog.doCancelAction()
            assertTrue(dialog.isDisposed)
            assertNotNull(guide.pending())
            val resumed = CodeXPGuideDialog(project, guide, progress) { destination = it }
            try {
                assertEquals(CodeXPBundle.message("guide.collection.title"), resumed.guidePanel.heading.text)
                resumed.guidePanel.destination.doClick()
                assertEquals(GuideDestination.COLLECTION, destination)
                assertTrue(resumed.isDisposed)
                assertNotNull(guide.pending())
            } finally {
                resumed.disposeIfNeeded()
            }
        } finally {
            dialog.disposeIfNeeded()
            Disposer.dispose(progress)
        }
    }

    fun testCompletingUpdateDialogPersistsReadState() {
        val progress = CodeXPService(MemoryCollectionPersistence())
        progress.loadState(CodeXPState(xp = 1000))
        val guide = CodeXPGuideService().apply { initialize(true) }
        val dialog = CodeXPGuideDialog(project, guide, progress) {}
        try {
            repeat(
                CodeXPGuideCatalog.releases
                    .last()
                    .steps.size,
            ) { dialog.guidePanel.next.doClick() }
            assertTrue(dialog.isDisposed)
            assertNull(guide.pending())
            val reopened = CodeXPGuideService()
            reopened.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(guide.state), CodeXPGuideState::class.java))
            assertNull(reopened.pending())
        } finally {
            dialog.disposeIfNeeded()
            Disposer.dispose(progress)
        }
    }

    fun testNewInstallationResumesWithoutTouchingRewardsAndCompletionIsPersistent() {
        val guide = CodeXPGuideService()
        guide.initialize(false)
        val run = guide.claimNotice()!!
        assertTrue(run.intro)
        assertNull(guide.claimNotice())
        guide.remember(run, 2)
        val reopened = CodeXPGuideService()
        reopened.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(guide.state), CodeXPGuideState::class.java))
        reopened.initialize(true)
        assertTrue(reopened.pending()!!.intro)
        assertEquals(2, reopened.page(reopened.open()))
        reopened.complete(reopened.open())
        assertNull(reopened.pending())
        assertNull(reopened.claimNotice())
        assertTrue(reopened.open(true).intro)
        assertFalse(reopened.open(false).intro)
    }

    fun testExistingUsersSeeOnlyUnreadUpdatesAndSkippedVersionsAreCombined() {
        val guide = CodeXPGuideService()
        guide.initialize(true)
        val run = guide.pending()!!
        assertFalse(run.intro)
        assertEquals(listOf("collections-2.3"), run.releases.map { it.id })
        guide.complete(guide.open(true))
        assertNotNull("Replaying the basic tutorial must not consume unread update pages", guide.pending())
        guide.complete(run)
        assertNull(guide.pending())
        val next = GuideRelease("new-2.4", "2.4.0", listOf(GuideStep("next", GuideDestination.COLLECTION)))
        val later = GuideRelease("new-2.5", "2.5.0", listOf(GuideStep("later", GuideDestination.DASHBOARD)))
        val pending = guide.pending(CodeXPGuideCatalog.releases + next + later)!!
        assertEquals(listOf("next", "later"), pending.steps.map { it.id })
        guide.complete(pending)
        assertNull(guide.pending(CodeXPGuideCatalog.releases + next + later))
        assertNull(guide.pending(CodeXPGuideCatalog.releases))
        assertTrue(guide.state.seenReleases.contains("new-2.5"))
        guide.reset()
        assertTrue(guide.pending()!!.intro)
        assertTrue(guide.state.seenReleases.isEmpty())
    }

    fun testGuideNavigationAndDestinationsDoNotEarnOrSpendRewards() {
        val service = CodeXPService(MemoryCollectionPersistence())
        service.loadState(CodeXPState(xp = 1000))
        val before = XmlSerializer.serialize(service.state)
        val credits = service.collectionSnapshot().credits
        val guide = CodeXPGuideService().apply { initialize(false) }
        var closed = 0
        var target: GuideDestination? = null
        val panel = CodeXPGuidePanel(guide, service, { closed++ }, { target = it })
        try {
            panel.showGuide()
            assertFalse(panel.previous.isEnabled)
            panel.next.doClick()
            panel.next.doClick()
            panel.destination.doClick()
            assertEquals(GuideDestination.COLLECTION, target)
            panel.close.doClick()
            assertEquals(1, closed)
            assertNotNull(guide.pending())
            panel.showGuide()
            assertEquals(CodeXPBundle.message("guide.collection.title"), panel.heading.text)
            panel.previous.doClick()
            assertEquals(CodeXPBundle.message("guide.challenges.title"), panel.heading.text)
            panel.skip.doClick()
            assertNull(guide.pending())
            panel.updatesTab.doClick()
            repeat(
                CodeXPGuideCatalog.releases
                    .last()
                    .steps.size,
            ) { panel.next.doClick() }
            assertEquals(3, closed)
            assertEquals(credits, service.collectionSnapshot().credits)
            assertEquals(
                com.intellij.openapi.util.JDOMUtil
                    .writeElement(before),
                com.intellij.openapi.util.JDOMUtil
                    .writeElement(XmlSerializer.serialize(service.state)),
            )
        } finally {
            Disposer.dispose(service)
        }
    }

    fun testGuidePagesFitNarrowWideDarkLightAndHiDpi() {
        val service = CodeXPService(MemoryCollectionPersistence())
        service.loadState(CodeXPState(xp = 1000))
        val scale = JBUIScale.scale(1f)
        val dark = !JBColor.isBright()
        try {
            for (factor in listOf(1f, 2f)) {
                JBUIScale.setUserScaleFactor(factor)
                for (isDark in listOf(true, false)) {
                    JBColor.setDark(isDark)
                    for (width in listOf(230, 420, 600)) {
                        val guide = CodeXPGuideService().apply { initialize(false) }
                        val panel = CodeXPGuidePanel(guide, service, {}, {})
                        panel.showGuide()
                        panel.setSize(JBUI.scale(width), JBUI.scale(620))
                        layoutTree(panel)
                        val nextBounds = panel.next.bounds
                        for (step in CodeXPGuideCatalog.intro.indices) {
                            layoutTree(panel)
                            assertEquals(step > 0, panel.previous.isVisible)
                            assertEquals(nextBounds, panel.next.bounds)
                            for (text in listOf(panel.heading, panel.description)) {
                                assertTrue(text.width > 0)
                                assertTrue(text.height >= text.preferredSize.height)
                                assertTrue(panel.body.bounds.width >= text.x + text.width)
                            }
                            assertFalse(panel.heading.bounds.intersects(panel.description.bounds))
                            assertFalse(panel.description.bounds.intersects(panel.destination.bounds))
                            assertTrue(panel.next.isShowing || panel.next.width > 0)
                            assertEquals(JBUI.scale(44), panel.next.height)
                            assertFalse(panel.next.bounds.intersects(panel.previous.bounds))
                            assertFalse(panel.next.bounds.intersects(panel.skip.bounds))
                            assertFalse(panel.steps.bounds.intersects(panel.page.bounds))
                            assertTrue(panel.next.getFontMetrics(panel.next.font).stringWidth(panel.next.text) < panel.next.width - panel.next.insets.left - panel.next.insets.right)
                            assertTrue(panel.scroll.viewport.width >= panel.body.width)
                            val image = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_ARGB)
                            val graphics = image.createGraphics()
                            panel.paint(graphics)
                            graphics.dispose()
                            assertTrue(image.getRGB(0, 0, image.width, image.height, null, 0, image.width).toSet().size > 50)
                            val file = File("build/reports/guide/guide-$width-$factor-$isDark-$step.png")
                            file.parentFile.mkdirs()
                            ImageIO.write(image, "png", file)
                            panel.next.doClick()
                        }
                    }
                }
            }
        } finally {
            JBUIScale.setUserScaleFactor(scale)
            JBColor.setDark(dark)
            Disposer.dispose(service)
        }
    }

    fun testCreditSymbolRemainsVisibleAndAmountsFit() {
        val badge = DashboardChoiceBalance()
        for (count in listOf(0L, 1L, 1234L, Long.MAX_VALUE)) {
            badge.update(count, "XP rule")
            badge.setSize(198, 28)
            badge.doLayout()
            assertNotNull(badge.symbol.icon)
            assertTrue(badge.symbol.width > 0)
            assertFalse(badge.symbol.bounds.intersects(badge.amount.bounds))
            assertTrue(badge.amount.getFontMetrics(badge.amount.font).stringWidth(badge.amount.text) <= badge.amount.width)
            assertTrue(badge.symbol.toolTipText.contains("XP rule"))
            assertEquals(CodeXPBundle.message("collection.credits.balance", badge.amount.text), badge.accessibleContext.accessibleName)
        }
    }

    fun testGuidesAreLocalizedAndRegistered() {
        val action = ActionManager.getInstance().getAction("CodeXP.Guide")
        assertTrue(action is CodeXPGuideAction)
        assertEquals(CodeXPBundle.message("action.CodeXP.Guide.text"), action.templatePresentation.text)
        for (locale in listOf(Locale.ENGLISH, Locale.KOREAN)) {
            val bundle = DynamicBundle.getResourceBundle(CodeXPBundle::class.java.classLoader, "messages.CodeXPBundle", locale)
            for (step in CodeXPGuideCatalog.intro + CodeXPGuideCatalog.releases.flatMap { it.steps }) {
                assertTrue(bundle.getString("guide.${step.id}.title").isNotBlank())
                assertTrue(bundle.getString("guide.${step.id}.body").isNotBlank())
            }
        }
    }

    private fun layoutTree(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layoutTree)
    }
}
