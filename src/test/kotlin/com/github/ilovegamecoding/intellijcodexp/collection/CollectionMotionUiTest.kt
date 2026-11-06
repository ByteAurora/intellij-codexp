package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CodeXPCollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionRecord
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CodeXPCollectionPanel
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionAnimationClock
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionCrestPainter
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionFilter
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionMotion
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionMotionFrame
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardController
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardPanel
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.number
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPCollectionPersistence
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.github.ilovegamecoding.intellijcodexp.services.MemoryCollectionPersistence
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.JViewport
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager

class CollectionMotionUiTest : BasePlatformTestCase() {
    override fun isIconRequired(): Boolean = name == "testProductionCollectionPanelsInBothThemes"

    fun testElapsedTimeIsIndependentOfTickCountAndReclickRestartsAccent() {
        var time = 10_000_000_000L
        val first = CollectionMotion({}, { false }, { time }, false)
        val second = CollectionMotion({}, { false }, { time }, false)
        try {
            first.visibilityChanged(true, true, true)
            second.visibilityChanged(true, true, true)
            assertTrue(first.emphasize("crest", true))
            repeat(60) {
                time += 16_666_667
                first.tick(time)
            }
            second.tick(time)
            assertEquals(second.frame("crest", true).seconds, first.frame("crest", true).seconds, .00001)
            assertTrue(first.frame("crest", true).emphasis < .3)
            assertTrue(first.emphasize("crest", true))
            assertEquals(1.0, first.frame("crest", true).emphasis, .00001)
            assertFalse(first.emphasize("locked", false))
            assertFalse(first.frame("locked", false).animated)
            first.visibilityChanged(false, true, true)
            val paused = first.frame("crest", true)
            time += 30_000_000_000
            first.tick(time)
            assertEquals(paused, first.frame("crest", true))
            first.visibilityChanged(true, true, true)
            time += 16_000_000
            first.tick(time)
            assertTrue(first.frame("crest", true).seconds < 1.1)
        } finally {
            first.dispose()
            second.dispose()
        }
    }

    fun testClockUnsubscribesForHiddenBackgroundOffscreenReducedAndDisposedHosts() {
        var time = 10_000_000_000L
        var systemReduced = false
        val baseline = CollectionAnimationClock.subscriberCount
        val motion = CollectionMotion({}, { systemReduced }, { time })
        try {
            motion.visibilityChanged(true, true, true)
            assertEquals(baseline + 1, CollectionAnimationClock.subscriberCount)
            motion.visibilityChanged(true, false, true)
            assertFalse(motion.isRunning)
            assertEquals(baseline, CollectionAnimationClock.subscriberCount)
            motion.visibilityChanged(true, true, false)
            assertFalse(motion.isRunning)
            motion.visibilityChanged(true, true, true)
            motion.configure(true)
            assertFalse(motion.isRunning)
            motion.configure(false)
            assertTrue(motion.isRunning)
            systemReduced = true
            time += 3_000_000_000
            motion.tick(time)
            assertFalse(motion.isRunning)
            assertFalse(motion.emphasize("owned", true))
            systemReduced = false
            motion.visibilityChanged(false, false, true)
            motion.visibilityChanged(true, true, true)
            assertTrue(motion.isRunning)
        } finally {
            motion.dispose()
        }
        assertEquals(baseline, CollectionAnimationClock.subscriberCount)
        motion.visibilityChanged(true, true, true)
        assertFalse(motion.isRunning)
    }

    fun testThirtyFormsHaveDistinctMotionAndLockedFramesNeverRevealThem() {
        val originalDark = !JBColor.isBright()
        try {
            for (dark in listOf(true, false)) {
                JBColor.setDark(dark)
                val motionSignatures = mutableSetOf<Int>()
                val sheet = BufferedImage(1200, 1260, BufferedImage.TYPE_INT_ARGB)
                val output = sheet.createGraphics()
                CodeXPCollectionCatalog.content.items.mapNotNull { it.design }.forEachIndexed { index, design ->
                    fun render(
                        seconds: Double,
                        locked: Boolean = false,
                    ): BufferedImage {
                        val image = BufferedImage(240, 210, BufferedImage.TYPE_INT_ARGB)
                        val g = image.createGraphics()
                        g.color = DashboardPalette.background
                        g.fillRect(0, 0, 240, 210)
                        g.translate(120, 105)
                        CollectionCrestPainter.paintAnimated(g, design, 150, locked, CollectionMotionFrame(seconds, .5, true), false, null)
                        g.dispose()
                        return image
                    }
                    val one = render(.35)
                    val two = render(1.4)
                    val a = one.getRGB(0, 0, 240, 210, null, 0, 240)
                    val b = two.getRGB(0, 0, 240, 210, null, 0, 240)
                    val differences = IntArray(a.size) { a[it] xor b[it] }
                    assertTrue("$design must visibly animate", differences.count { it != 0 } > 80)
                    motionSignatures.add(differences.contentHashCode())
                    val lockedA = render(.35, true).getRGB(0, 0, 240, 210, null, 0, 240)
                    val lockedB = render(1.4, true).getRGB(0, 0, 240, 210, null, 0, 240)
                    assertTrue(lockedA.contentEquals(lockedB))
                    output.drawImage(two, index % 5 * 240, index / 5 * 210, null)
                }
                output.dispose()
                assertEquals(30, motionSignatures.size)
                val file = File("build/reports/collection-motion/motion-forms-${if (dark) "dark" else "light"}.png")
                file.parentFile.mkdirs()
                ImageIO.write(sheet, "png", file)
            }
        } finally {
            JBColor.setDark(originalDark)
        }
    }

    fun testViewingAcknowledgesOnlySelectedItemAndAcquireRemainsSeparateFromEquip() {
        val service = CodeXPService(MemoryCollectionPersistence())
        service.loadState(CollectionProductFixtures.profile(175, 310))
        val panel = CodeXPCollectionPanel(service) { false }
        try {
            panel.apply(service.collectionSnapshot())
            val unseen =
                service
                    .collectionSnapshot()
                    .entries
                    .filter { it.unseen }
                    .map { it.id }
                    .toSet()
            assertTrue(unseen.size > 1)
            layoutAt(panel, 280, 900)
            assertEquals(
                unseen,
                service
                    .collectionSnapshot()
                    .entries
                    .filter { it.unseen }
                    .map { it.id }
                    .toSet(),
            )
            panel.grid.selectedIndex = 0
            val viewed = panel.grid.selectedValue.id
            assertEquals(
                unseen - viewed,
                service
                    .collectionSnapshot()
                    .entries
                    .filter { it.unseen }
                    .map { it.id }
                    .toSet(),
            )
            assertNull(service.collectionSnapshot().equippedCrest)
            assertEquals(1L, service.collectionSnapshot().credits)
            assertEquals(CodeXPBundle.message("collection.equip"), panel.primary.text)
            panel.primary.doClick()
            assertNotNull(service.collectionSnapshot().equippedCrest)
            assertEquals(CodeXPBundle.message("collection.unequip"), panel.primary.text)
            panel.primary.doClick()
            assertNull(service.collectionSnapshot().equippedCrest)
            panel.filter.selectedItem = CollectionFilter.AVAILABLE
            assertTrue(panel.model.size > 0)
            panel.grid.selectedIndex = 0
            val choice = panel.grid.selectedValue
            assertEquals(CodeXPBundle.message("collection.acquire", 1), panel.primary.text)
            assertEquals(1L, service.collectionSnapshot().credits)
            panel.primary.doClick()
            assertEquals(0L, service.collectionSnapshot().credits)
            assertNull(service.collectionSnapshot().equippedCrest)
            assertEquals(CollectionFilter.OWNED, panel.filter.selectedItem)
            assertEquals(choice.id, panel.grid.selectedValue.id)
            assertEquals(CodeXPBundle.message("collection.equip"), panel.primary.text)
            panel.primary.doClick()
            assertEquals(choice.design, service.collectionSnapshot().equippedCrest)
            panel.setKind(CollectionKind.TITLE)
            panel.grid.selectedIndex = 0
            panel.primary.doClick()
            assertNotNull(service.collectionSnapshot().equippedTitle)
            service.resetAllExceptNickname()
            panel.apply(service.collectionSnapshot())
            assertNull(panel.grid.selectedValue)
            assertFalse(panel.primary.isVisible)
        } finally {
            panel.dispose()
            Disposer.dispose(service)
        }
    }

    fun testLockedNamesStayHiddenInSearchTooltipAndAccessibleCardAndButtonsAreUnambiguous() {
        val service = CodeXPService(MemoryCollectionPersistence())
        service.loadState(CollectionProductFixtures.profile())
        val panel = CodeXPCollectionPanel(service) { false }
        try {
            panel.apply(service.collectionSnapshot())
            panel.filter.selectedItem = CollectionFilter.LOCKED
            panel.grid.selectedIndex = 0
            val locked = panel.grid.selectedValue
            val realName =
                CodeXPCollectionCatalog.content
                    .find(locked.id)!!
                    .definition.displayName
            val cell = panel.grid.cellRenderer.getListCellRendererComponent(panel.grid, locked, 0, true, true)
            assertFalse(cell.accessibleContext.accessibleName.contains(realName))
            assertFalse(panel.detailName.text.contains(realName))
            assertEquals(CodeXPBundle.message("collection.locked.crest"), panel.detailName.text)
            panel.search.text = realName
            assertEquals(0, panel.model.size)
            val detailButtons = descendants(panel.detail).filterIsInstance<JButton>()
            assertEquals(setOf(panel.closeDetail, panel.primary), detailButtons.toSet())
            assertFalse(panel.primary.isEnabled && panel.primary.isVisible)
            assertEquals(CodeXPBundle.message("collection.back"), panel.back.accessibleContext.accessibleName)
        } finally {
            panel.dispose()
            Disposer.dispose(service)
        }
    }

    fun testCollectionTabsReflowForWideFontsWithoutHidingCounts() {
        val service = CodeXPService(MemoryCollectionPersistence())
        service.loadState(CollectionProductFixtures.profile(175, 310))
        val panel = CodeXPCollectionPanel(service) { false }
        try {
            panel.apply(service.collectionSnapshot())
            val tabs = listOf(panel.crestTab, panel.titleTab)
            val wideFont = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 18)
            tabs.forEach { it.font = wideFont }
            for (width in listOf(230, 420, 230)) {
                layoutAt(panel, width, 900)
                tabs.forEach {
                    assertEquals(wideFont, it.font)
                    assertTrue(it.text.contains('/'))
                    assertTrue(it.getFontMetrics(it.font).stringWidth(it.text) <= it.width - it.insets.left - it.insets.right)
                    assertTrue(it.bounds.maxX <= it.parent.width && it.bounds.maxY <= it.parent.height)
                }
                assertFalse(panel.crestTab.bounds.intersects(panel.titleTab.bounds))
                assertTrue(panel.titleTab.bounds.maxY <= panel.search.y)
                if (width == 230) assertTrue(panel.crestTab.y < panel.titleTab.y) else assertEquals(panel.crestTab.y, panel.titleTab.y)
            }
        } finally {
            panel.dispose()
            Disposer.dispose(service)
        }
    }

    fun testResponsiveLayoutAtAllScalesKeepsListInlineDetailsAndLargeBalancesInsideBounds() {
        val originalScale = JBUIScale.scale(1f)
        val service = CodeXPService(MemoryCollectionPersistence())
        service.loadState(CollectionProductFixtures.profile(175, 310))
        try {
            for (scale in listOf(1f, 1.5f, 2f)) {
                JBUIScale.setUserScaleFactor(scale)
                for (logicalWidth in listOf(230, 250, 380, 900)) {
                    val panel = CodeXPCollectionPanel(service) { false }
                    try {
                        val state = service.collectionSnapshot()
                        val first = state.entries.first()
                        val longName = "긴 이름의 각인 · 별빛을 기억하는 오래된 잎 장식"
                        panel.apply(state.copy(credits = Long.MAX_VALUE, entries = state.entries.map { if (it.id == first.id) it.copy(name = longName, unseen = false) else it }))
                        panel.grid.selectedIndex = 0
                        val w = (logicalWidth * scale).toInt()
                        val h = (900 * scale).toInt()
                        layoutAt(panel, w, h)
                        assertTrue(panel.scroll.isVisible && panel.detailScroll.isVisible)
                        assertTrue(panel.scroll.width > 0 && panel.scroll.height >= 100 * scale)
                        for (component in listOf(panel.search, panel.filter, panel.balance, panel.crestTab, panel.titleTab, panel.primary, panel.detailName)) {
                            assertTrue("$logicalWidth/$scale: ${component.javaClass.simpleName} outside parent ${component.bounds}", component.x >= 0 && component.y >= 0 && component.x + component.width <= component.parent.width && component.y + component.height <= component.parent.height)
                        }
                        assertEquals(longName, panel.detailName.text)
                        for (tab in listOf(panel.crestTab, panel.titleTab)) {
                            assertTrue("${tab.text} clips at $logicalWidth/$scale", tab.getFontMetrics(tab.font).stringWidth(tab.text) <= tab.width - tab.insets.left - tab.insets.right)
                        }
                        assertTrue(panel.primary.height >= JBUI.scale(40))
                        panel.crestTab.actionMap
                            .get("otherKind")
                            .actionPerformed(null)
                        assertTrue(panel.titleTab.isSelected)
                        panel.titleTab.actionMap
                            .get("otherKind")
                            .actionPerformed(null)
                        assertTrue(panel.crestTab.isSelected)
                        panel.grid.selectedIndex = 0
                        layoutAt(panel, w, h)
                        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                        val g = image.createGraphics()
                        panel.paint(g)
                        g.dispose()
                        val file = File("build/reports/collection-motion/layout-$logicalWidth-$scale.png")
                        file.parentFile.mkdirs()
                        ImageIO.write(image, "png", file)
                    } finally {
                        panel.dispose()
                    }
                }
            }
            val dashboard = CodeXPDashboardPanel { false }
            try {
                dashboard.applyCollection(service.collectionSnapshot().copy(credits = Long.MAX_VALUE))
                layoutAt(dashboard, 280, 900)
                assertEquals(CodeXPBundle.message("collection.heading"), dashboard.collectionButton.accessibleContext.accessibleName)
                assertEquals(
                    CodeXPBundle.message("collection.credits.balance", number(Long.MAX_VALUE)),
                    dashboard.choiceBalance.accessibleContext.accessibleName,
                )
                assertFalse(dashboard.collectionButton.bounds.intersects(dashboard.choiceBalance.bounds))
            } finally {
                dashboard.dispose()
            }
        } finally {
            JBUIScale.setUserScaleFactor(originalScale)
            Disposer.dispose(service)
        }
    }

    fun testCompactDashboardKeepsAFullChallengeRowAndExplainsWhenCreditsAreEarned() {
        val service = CodeXPService(MemoryCollectionPersistence())
        val scope = Disposer.newDisposable()
        try {
            service.loadState(CodeXPState())
            val dashboard = CodeXPDashboardPanel { false }
            val collection = CodeXPCollectionPanel(service) { false }
            Disposer.register(scope, collection)
            val controller = CodeXPDashboardController(service, dashboard, scope, collection::apply)
            controller.isCollectionVisible = { true }
            controller.initialize()
            dashboard.visibilityChanged(true)
            layoutAt(dashboard, JBUI.scale(250), JBUI.scale(340))
            val viewport = dashboard.challenges.parent as JViewport
            assertTrue(viewport.viewRect.contains(dashboard.challenges.getComponent(0).bounds))
            val profileScroll = SwingUtilities.getAncestorOfClass(JBScrollPane::class.java, dashboard.crest) as JBScrollPane
            profileScroll.verticalScrollBar.value = profileScroll.verticalScrollBar.maximum
            val totals = SwingUtilities.convertRectangle(dashboard.totalXP.parent, dashboard.totalXP.bounds, profileScroll.viewport.view)
            assertTrue(profileScroll.viewport.viewRect.contains(totals))
            val expectedBeforeCap = CodeXPBundle.message("collection.credits.rule", CodeXPGrowthPolicy.MAX_LEVEL, number(CodeXPGrowthPolicy.CHOICE_INTERVAL_XP), number(CodeXPGrowthPolicy.CHOICE_INTERVAL_INCREMENT_XP), number(CodeXPGrowthPolicy.CHOICE_MAX_INTERVAL_XP))
            assertEquals(expectedBeforeCap, dashboard.choiceBalance.toolTipText)
            assertEquals(expectedBeforeCap, collection.balance.toolTipText)

            service.loadState(CodeXPState(xp = CodeXPGrowthPolicy.capXP))
            service.eventOccurred(Event.TYPING, null)
            controller.flushPending()
            val state = service.collectionSnapshot()
            val expectedAtCap = CodeXPBundle.message("collection.credits.next", number(state.choiceIntervalXP - state.choiceProgressXP))
            assertEquals(expectedAtCap, dashboard.choiceBalance.toolTipText)
            assertEquals(expectedAtCap, dashboard.xpText.toolTipText)
            assertEquals(expectedAtCap, collection.balance.toolTipText)
        } finally {
            Disposer.dispose(scope)
            Disposer.dispose(service)
        }
    }

    fun testChoiceBalanceIsOneAlignedRowAndLargeBalancesDoNotClip() {
        val service = CodeXPService(MemoryCollectionPersistence())
        val dashboard = CodeXPDashboardPanel { false }
        val scope = Disposer.newDisposable()
        try {
            service.loadState(CodeXPState())
            CodeXPDashboardController(service, dashboard, scope).initialize()
            for (width in listOf(230, 280, 420)) {
                for (credits in listOf(0L, 1L, 1234L, Long.MAX_VALUE)) {
                    dashboard.applyCollection(service.collectionSnapshot().copy(credits = credits))
                    layoutAt(dashboard, JBUI.scale(width), JBUI.scale(700))
                    val balance = dashboard.choiceBalance
                    assertEquals(number(credits), balance.amount.text)
                    assertTrue(balance.amount.getFontMetrics(balance.amount.font).stringWidth(balance.amount.text) <= balance.amount.width)
                    assertTrue(balance.symbol.isVisible)
                    assertNotNull(balance.symbol.icon)
                    assertFalse(balance.symbol.bounds.intersects(balance.amount.bounds))
                    assertEquals(balance.symbol.y, balance.amount.y)
                    assertEquals(balance.symbol.height, balance.amount.height)
                    assertFalse(dashboard.collectionButton.bounds.intersects(balance.bounds))
                    assertFalse(balance.bounds.intersects(dashboard.nickname.bounds))
                    assertEquals(service.collectionSnapshot().choiceCreditHelp, balance.amount.toolTipText)
                }
            }
            dashboard.applyCollection(service.collectionSnapshot().copy(credits = 3L))
            layoutAt(dashboard, JBUI.scale(280), JBUI.scale(700))
            val image = BufferedImage(dashboard.width, dashboard.height, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            dashboard.paint(graphics)
            graphics.dispose()
            val file = File("build/reports/choice-balance/dashboard.png")
            file.parentFile.mkdirs()
            ImageIO.write(image, "png", file)
        } finally {
            Disposer.dispose(scope)
            dashboard.dispose()
            Disposer.dispose(service)
        }
    }

    fun testSelectionOwnsTheInspectorSpaceAndEscapeRestoresTheGrid() {
        val service = CodeXPService(MemoryCollectionPersistence())
        service.loadState(CollectionProductFixtures.profile())
        val panel = CodeXPCollectionPanel(service) { false }
        try {
            val snapshot = service.collectionSnapshot()
            panel.apply(snapshot)
            for ((kind, tab) in listOf(CollectionKind.CREST to panel.crestTab, CollectionKind.TITLE to panel.titleTab)) {
                val items = snapshot.entries.filter { it.kind == kind }
                assertTrue(tab.text.contains("${items.count { it.owned }}/${items.size}"))
            }
            assertFalse(panel.balance.isFocusable)
            assertTrue(
                panel.balance.accessibleContext.accessibleDescription
                    .contains("XP"),
            )
            panel.filter.selectedItem = CollectionFilter.LOCKED
            for (width in listOf(230, 380, 860)) {
                layoutAt(panel, width, 600)
                assertFalse(panel.detailScroll.isVisible)
                val full = panel.scroll.size
                val index = (0 until panel.model.size).first { panel.model[it].id.endsWith(".challenge") }
                val entry = panel.model[index]
                assertTrue(entry.conditionHint.contains("#"))
                assertTrue(entry.conditionHint.length < entry.condition.length)
                panel.grid.selectedIndex = index
                panel.grid.actionMap
                    .get(panel.grid.inputMap.get(KeyStroke.getKeyStroke("SPACE")))
                    .actionPerformed(null)
                layoutAt(panel, width, 600)
                assertTrue(panel.detailScroll.isVisible)
                assertTrue(panel.grid.visibleRect.intersects(panel.grid.getCellBounds(index, index)))
                assertTrue(descendants(panel.detail).filterIsInstance<JTextArea>().any { it.text == entry.condition && it.height > 0 })
                assertTrue(if (width < 720) panel.scroll.height < full.height else panel.scroll.width < full.width)
                val key = panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("ESCAPE"))
                panel.actionMap.get(key).actionPerformed(null)
                layoutAt(panel, width, 600)
                assertNull(panel.grid.selectedValue)
                assertFalse(panel.detailScroll.isVisible)
                assertEquals(full, panel.scroll.size)
            }
        } finally {
            panel.dispose()
            Disposer.dispose(service)
        }
    }

    fun testMigrationAndFailedActionsAreReadableWithNoSelection() {
        var failWrites = false
        val memory = MemoryCollectionPersistence()
        val service =
            CodeXPService(
                object : CodeXPCollectionPersistence by memory {
                    override fun save(record: CodeXPCollectionRecord) {
                        if (failWrites) throw IOException("Simulated save failure")
                        memory.save(record)
                    }
                },
            )
        service.loadState(CollectionProductFixtures.profile(175, 310))
        val panel = CodeXPCollectionPanel(service) { false }
        try {
            panel.apply(service.collectionSnapshot())
            val acknowledge = descendants(panel).filterIsInstance<JButton>().single { it.text == CodeXPBundle.message("collection.migration.acknowledge") }
            assertTrue(acknowledge.isVisible)
            failWrites = true
            acknowledge.doClick()
            layoutAt(panel, 250, 640)
            assertFalse(panel.detailScroll.isVisible)
            assertTrue(descendants(panel).filterIsInstance<JTextArea>().any { CodeXPBundle.message("collection.error.save.retry") in it.text && it.height > 0 && it.isVisible })
            failWrites = false
            acknowledge.doClick()
            assertNull(service.collectionSnapshot().migrationNotice)
            assertFalse(acknowledge.isVisible)
            panel.filter.selectedItem = CollectionFilter.AVAILABLE
            panel.grid.selectedIndex = 0
            val id = panel.grid.selectedValue.id
            val balance = service.collectionSnapshot().credits
            failWrites = true
            panel.primary.doClick()
            assertEquals(balance, service.collectionSnapshot().credits)
            assertFalse(
                service
                    .collectionSnapshot()
                    .entries
                    .single { it.id == id }
                    .owned,
            )
            panel.closeDetail.doClick()
            layoutAt(panel, 250, 640)
            assertFalse(panel.detailScroll.isVisible)
            assertTrue(descendants(panel).filterIsInstance<JTextArea>().any { CodeXPBundle.message("collection.error.save.retry") in it.text && it.height > 0 && it.isVisible })
        } finally {
            panel.dispose()
            Disposer.dispose(service)
        }
    }

    fun testProductionCollectionPanelsInBothThemes() {
        val manager = LafManager.getInstance()
        val previous = manager.currentUIThemeLookAndFeel
        val previousDark = !JBColor.isBright()
        val export = System.getenv("CODEXP_COLLECTION_PREVIEWS") == "1"
        try {
            if (export) IconLoader.activate()
            for (dark in listOf(false, true)) {
                val theme = if (dark) manager.defaultDarkLaf else manager.defaultLightLaf
                if (export) requireNotNull(theme) { "Render collection previews in a UI-enabled test worker to load the IDE theme." }
                if (theme != null) manager.setCurrentLookAndFeel(theme, false)
                JBColor.setDark(dark)
                val service = CodeXPService(MemoryCollectionPersistence())
                service.loadState(CollectionProductFixtures.profile())
                val panel = CodeXPCollectionPanel(service) { false }
                try {
                    if (export) println("Collection preview: ${manager.javaClass.simpleName}, theme=${theme?.name}, dark=$dark, back=${panel.back.icon.javaClass.simpleName}, field=${UIManager.getColor("TextField.background")}")
                    panel.apply(service.collectionSnapshot())

                    fun capture(
                        name: String,
                        width: Int,
                        height: Int,
                    ) {
                        layoutAt(panel, width, height)
                        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                        val g = img.createGraphics()
                        try {
                            panel.paint(g)
                        } finally {
                            g.dispose()
                        }
                        assertEquals(DashboardPalette.background.rgb, img.getRGB(0, 0))
                        if (export) {
                            val f = File("build/reports/collection-layout/$name-${if (dark) "dark" else "light"}.png")
                            f.parentFile.mkdirs()
                            ImageIO.write(img, "png", f)
                        }
                    }
                    capture("migration", 280, 650)
                    descendants(panel).filterIsInstance<JButton>().single { it.text == CodeXPBundle.message("collection.migration.acknowledge") }.doClick()
                    panel.grid.selectedIndex = 0
                    panel.primary.doClick()
                    assertTrue(panel.grid.selectedValue.equipped)
                    panel.closeDetail.doClick()
                    capture("narrow", 250, 650)
                    capture("gallery", 380, 690)
                    panel.filter.selectedItem = CollectionFilter.LOCKED
                    capture("locked-gallery", 380, 690)
                    panel.grid.selectedIndex = (0 until panel.model.size).first { !panel.model[it].owned && !panel.model[it].choice }
                    capture("locked-detail", 380, 690)
                    panel.filter.selectedItem = CollectionFilter.ALL
                    panel.grid.selectedIndex = 0
                    capture("wide-detail", 880, 600)
                    panel.titleTab.doClick()
                    assertFalse(panel.detailScroll.isVisible)
                    capture("titles", 380, 690)
                } finally {
                    panel.dispose()
                    Disposer.dispose(service)
                }
            }
        } finally {
            if (previous != null) manager.setCurrentLookAndFeel(previous, false)
            if (export) IconLoader.deactivate()
            JBColor.setDark(previousDark)
        }
    }

    private fun layoutAt(
        container: Container,
        width: Int,
        height: Int,
    ) {
        container.setSize(width, height)
        repeat(5) { layout(container) }
    }

    private fun layout(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layout)
    }

    private fun descendants(container: Container): List<Component> = container.components.flatMap { listOf(it) + if (it is Container) descendants(it) else emptyList() }
}
