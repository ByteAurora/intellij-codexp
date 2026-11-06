package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CodeXPCollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionCardRenderer
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionEntry
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionMotionFrame
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardChallenge
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardHistoryList
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.dashboardFont
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPCollectionCoordinator
import com.github.ilovegamecoding.intellijcodexp.services.MemoryCollectionPersistence
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.Font
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.CellRendererPane
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.RepaintManager
import javax.swing.SwingUtilities

class CollectionPaintRegressionTest : BasePlatformTestCase() {
    fun testSelectionChangesOnlyTheBorderAndAccessibleNameNotCardLabels() {
        val coordinator = CodeXPCollectionCoordinator(MemoryCollectionPersistence())
        coordinator.load(CollectionProductFixtures.profile())
        val snapshot = coordinator.snapshot()
        val list = JList(snapshot.entries.toTypedArray())
        val renderer = CollectionCardRenderer { CollectionMotionFrame() }
        renderer.snapshot = snapshot
        try {
            for (row in listOf(true, false)) {
                renderer.rowLayout = row
                for (item in snapshot.entries) {
                    fun render(selected: Boolean): BufferedImage {
                        renderer.getListCellRendererComponent(list, item, 0, selected, selected)
                        renderer.setSize(JBUI.scale(if (row) 230 else 158), renderer.cellHeight(item.kind))
                        return paint(renderer)
                    }
                    val normal = render(false)
                    val selected = render(true)
                    val inset = JBUI.scale(14)
                    val width = renderer.width - inset * 2
                    val height = renderer.height - inset * 2
                    assertTrue(
                        "Selection must not add labels or shift content: ${item.id}, row=$row",
                        normal
                            .getRGB(inset, inset, width, height, null, 0, width)
                            .contentEquals(selected.getRGB(inset, inset, width, height, null, 0, width)),
                    )
                    assertFalse(
                        "The selection border must remain visible: ${item.id}",
                        normal
                            .getRGB(0, 0, normal.width, normal.height, null, 0, normal.width)
                            .contentEquals(selected.getRGB(0, 0, selected.width, selected.height, null, 0, selected.width)),
                    )
                    assertEquals(CodeXPBundle.message("collection.selected.accessible", item.accessibleName), renderer.accessibleContext.accessibleName)
                }
            }
        } finally {
            renderer.dispose()
        }
    }

    fun testCompactCardsPaintTheUnlockHintButLeaveFullProgressToTheInspector() {
        val coordinator = CodeXPCollectionCoordinator(MemoryCollectionPersistence())
        coordinator.load(CollectionProductFixtures.profile())
        val snapshot = coordinator.snapshot()
        val item = snapshot.entries.first { it.choice && !it.owned }
        val list = JList(snapshot.entries.toTypedArray())
        val renderer = CollectionCardRenderer { CollectionMotionFrame() }
        renderer.snapshot = snapshot
        try {
            for (row in listOf(true, false)) {
                renderer.rowLayout = row
                val width = if (row) 198 else 158
                val height = renderer.cellHeight(item.kind)

                fun pixels(
                    entry: CollectionEntry,
                    selected: Boolean = false,
                ): IntArray {
                    val cell = renderer.getListCellRendererComponent(list, entry, 0, selected, selected)
                    cell.setSize(width, height)
                    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    val graphics = image.createGraphics()
                    try {
                        cell.paint(graphics)
                    } finally {
                        graphics.dispose()
                    }
                    return image.getRGB(0, 0, width, height, null, 0, width)
                }
                val original = pixels(item)
                assertTrue(original.contentEquals(pixels(item.copy(condition = "전체 조건", progress = "새 진행도", fraction = .81))))
                assertFalse("The credit cost must still be legible at the end of the line", original.contentEquals(pixels(item.copy(conditionHint = CodeXPBundle.message("collection.condition.choice", 150, 2)))))
                assertFalse(original.contentEquals(pixels(item, selected = true)))
                assertEquals(CodeXPBundle.message("collection.selected.accessible", item.accessibleName), renderer.accessibleContext.accessibleName)
            }
        } finally {
            renderer.dispose()
        }
    }

    fun testChoiceTitlesKeepTheirMaskAndOfferDistinctVisibleAndAccessibleClues() {
        val coordinator = CodeXPCollectionCoordinator(MemoryCollectionPersistence())
        coordinator.load(CodeXPState(xp = CodeXPGrowthPolicy.capXP))
        val snapshot = coordinator.snapshot()
        val titles = snapshot.entries.filter { it.kind == CollectionKind.TITLE && it.canAcquire }
        assertEquals(8, titles.size)
        val list = JList(titles.toTypedArray())
        val renderer = CollectionCardRenderer { CollectionMotionFrame() }
        renderer.snapshot = snapshot
        try {
            val artwork = mutableSetOf<Int>()
            val captions = mutableSetOf<Int>()
            val names = mutableSetOf<String>()
            for (title in titles) {
                val cell = renderer.getListCellRendererComponent(list, title, 0, false, false)
                cell.setSize(JBUI.scale(158), renderer.cellHeight(CollectionKind.TITLE))
                val clueWidth = cell.getFontMetrics(dashboardFont(14f, Font.BOLD)).stringWidth(title.name)
                assertTrue("Clue must fit the narrowest tile: ${title.name}", clueWidth <= cell.width - JBUI.scale(29))
                val image = BufferedImage(cell.width, cell.height, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                try {
                    cell.paint(g)
                } finally {
                    g.dispose()
                }
                val artworkHeight = JBUI.scale(76)
                artwork.add(image.getRGB(0, 0, cell.width, artworkHeight, null, 0, cell.width).contentHashCode())
                captions.add(image.getRGB(0, artworkHeight, cell.width, cell.height - artworkHeight, null, 0, cell.width).contentHashCode())
                val accessible = cell.accessibleContext.accessibleName
                assertTrue(accessible.contains(title.name))
                assertTrue(title.matches(title.name))
                val revealedName =
                    CodeXPCollectionCatalog.content
                        .find(title.id)!!
                        .definition.displayName
                assertFalse(accessible.contains(revealedName, ignoreCase = true))
                assertFalse(title.matches(revealedName))
                names.add(accessible)
            }
            assertEquals(1, artwork.size)
            assertEquals(titles.size, captions.size)
            assertEquals(titles.size, names.size)
        } finally {
            renderer.dispose()
        }
    }

    fun testCollectionRendererDoesNotEnterAnotherBufferPassDuringManagedPainting() {
        val coordinator = CodeXPCollectionCoordinator(MemoryCollectionPersistence())
        coordinator.load(CollectionProductFixtures.profile())
        val snapshot = coordinator.snapshot()
        val renderer = CollectionCardRenderer { CollectionMotionFrame(1.25, .8, true) }
        renderer.snapshot = snapshot
        val list = JList(snapshot.entries.toTypedArray())
        val pane = CellRendererPane()
        list.add(pane)
        val requests = mutableListOf<String>()
        val manager =
            object : RepaintManager() {
                override fun getVolatileOffscreenBuffer(
                    c: Component,
                    width: Int,
                    height: Int,
                ): Image? {
                    requests.add(c.javaClass.name)
                    return null
                }

                override fun getOffscreenBuffer(
                    c: Component,
                    width: Int,
                    height: Int,
                ): Image {
                    requests.add(c.javaClass.name)
                    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                }
            }
        val previous = RepaintManager.currentManager(list)
        val begin = RepaintManager::class.java.getDeclaredMethod("beginPaint").apply { isAccessible = true }
        val end = RepaintManager::class.java.getDeclaredMethod("endPaint").apply { isAccessible = true }
        val painting = RepaintManager::class.java.getDeclaredField("painting").apply { isAccessible = true }
        try {
            RepaintManager.setCurrentManager(manager)
            manager.isDoubleBufferingEnabled = true
            val image = BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            // A real dirty-region pass is already active when Swing invokes a list renderer.
            // Ordinary offscreen paint tests miss the nested-buffer branch in JComponent.paint.
            painting.setBoolean(manager, true)
            begin.invoke(manager)
            try {
                val cell = renderer.getListCellRendererComponent(list, snapshot.entries.first { it.owned }, 0, true, false)
                pane.paintComponent(graphics, cell, list, 0, 0, 300, 300, true)
            } finally {
                end.invoke(manager)
                painting.setBoolean(manager, false)
                graphics.dispose()
            }
            assertTrue("A renderer must use the current paint pass, not request another buffer for $requests", requests.isEmpty())
            assertTrue(image.getRGB(0, 0, 300, 300, null, 0, 300).toSet().size > 30)
        } finally {
            RepaintManager.setCurrentManager(previous)
            renderer.dispose()
        }
    }

    fun testHistoryCardGapsAndEmptyAreaUseTheCanvasInBothThemesAfterUiUpdates() {
        val originalDark = !JBColor.isBright()
        val history = DashboardHistoryList(DefaultListModel())
        try {
            for (dark in listOf(true, false)) {
                JBColor.setDark(dark)
                SwingUtilities.updateComponentTreeUI(history)
                history.setSize(340, 350)
                val items = (1..2).map { DashboardChallenge("history-$it", Event.SAVE, "Save Challenge", "Save 10 times.", 10, 10, 300, sequenceNumber = it.toLong()) }
                history.update(items, emptySet())
                val image = paint(history)
                val output = File("build/reports/rendering-regression/history-${if (dark) "dark" else "light"}.png")
                output.parentFile.mkdirs()
                ImageIO.write(image, "png", output)
                for (y in listOf(0, 2, 114, 115, 116, 118, 230, 231, 345)) {
                    assertEquals("History gap y=$y dark=$dark", DashboardPalette.background.rgb, image.getRGB(20, y))
                }
                assertEquals("Cards keep their distinct surface", DashboardPalette.surface.rgb, image.getRGB(3, 10))
                history.update(emptyList(), emptySet())
                val empty = paint(history)
                assertEquals(setOf(DashboardPalette.background.rgb), empty.getRGB(0, 0, 340, 350, null, 0, 340).toSet())
            }
        } finally {
            history.dispose()
            JBColor.setDark(originalDark)
        }
    }

    private fun paint(component: Container): BufferedImage {
        fun layout(container: Container) {
            container.doLayout()
            container.components.filterIsInstance<Container>().forEach(::layout)
        }
        layout(component)
        return BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB).also { image ->
            val graphics = image.createGraphics()
            try {
                component.paint(graphics)
            } finally {
                graphics.dispose()
            }
        }
    }
}
