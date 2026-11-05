package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CodeXPCollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestFamily
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CodeXPCollectionPanel
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionArtwork
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionCrestPainter
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionMotionFrame
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.crestNumberLayout
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPCollectionCoordinator
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.github.ilovegamecoding.intellijcodexp.services.MemoryCollectionPersistence
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import java.awt.Container
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class CollectionArtworkTest : BasePlatformTestCase() {
    fun testScaledCrestAndMotionDoNotInheritPixelSnappingOrNearestNeighbour() {
        fun pixels(
            design: CrestDesign,
            scale: Double,
            sharp: Boolean,
            animated: Boolean,
        ): IntArray {
            val image = BufferedImage((248 * scale).toInt(), (232 * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            try {
                g.translate(image.width / 2.0 + .25, image.height / 2.0 + .375)
                g.scale(scale, scale)
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, if (sharp) RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR else RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, if (sharp) RenderingHints.VALUE_STROKE_NORMALIZE else RenderingHints.VALUE_STROKE_PURE)
                val original = g.renderingHints
                CollectionCrestPainter.paintAnimated(g, design, 24, false, CollectionMotionFrame(.375, 0.0, animated), false, null, compact = scale < 1.0)
                assertEquals(original, g.renderingHints)
            } finally {
                g.dispose()
            }
            return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        }
        for (family in CrestFamily.entries) {
            val design = CrestDesign(family, 2)
            for (scale in listOf(.30, .60, 1.25)) {
                val still = pixels(design, scale, false, false)
                assertTrue(still.any { it ushr 24 != 0 })
                assertTrue("Static $design at $scale", still.contentEquals(pixels(design, scale, true, false)))
                val moving = pixels(design, scale, false, true)
                assertFalse(still.contentEquals(moving))
                assertTrue("Motion $design at $scale", moving.contentEquals(pixels(design, scale, true, true)))
            }
        }
    }

    fun testAllThirtyCrestSilhouettesAreDistinctInBothThemesAndLargeNumbersStayCentered() {
        val originalDark = !JBColor.isBright()
        try {
            for (dark in listOf(true, false)) {
                JBColor.setDark(dark)
                val sheet = BufferedImage(1200, 1260, BufferedImage.TYPE_INT_ARGB)
                val output = sheet.createGraphics()
                val hashes = mutableSetOf<Int>()
                CodeXPCollectionCatalog.content.items.mapNotNull { it.design }.forEachIndexed { index, design ->
                    val image = BufferedImage(240, 210, BufferedImage.TYPE_INT_ARGB)
                    val g = image.createGraphics()
                    try {
                        g.color = DashboardPalette.background
                        g.fillRect(0, 0, 240, 210)
                        g.translate(120, 105)
                        CollectionCrestPainter.paint(g, design, 150, true)
                        hashes.add(image.getRGB(0, 0, 240, 210, null, 0, 240).contentHashCode())
                        for (level in listOf("1", "150", "175", Int.MAX_VALUE.toString())) {
                            val bounds = crestNumberLayout(level, g.fontRenderContext).ink
                            assertEquals(0.0, bounds.centerX, .01)
                            assertEquals(19.0, bounds.centerY, .01)
                            assertTrue(bounds.width <= 124)
                        }
                    } finally {
                        g.dispose()
                    }
                    output.drawImage(image, index % 5 * 240, index / 5 * 210, null)
                }
                output.dispose()
                assertEquals(30, hashes.size)
                val file = File("build/reports/collection-motion/silhouettes-${if (dark) "dark" else "light"}.png")
                file.parentFile.mkdirs()
                ImageIO.write(sheet, "png", file)
            }
        } finally {
            JBColor.setDark(originalDark)
        }
    }

    fun testQuestionMarkIsCenteredInLockedCrestAndTitleArtwork() {
        val coordinator = CodeXPCollectionCoordinator(MemoryCollectionPersistence())
        coordinator.load(CollectionProductFixtures.profile())
        val snapshot = coordinator.snapshot()
        val art = CollectionArtwork()
        try {
            for (kind in CollectionKind.entries) {
                art.show(snapshot.entries.first { it.kind == kind && !it.owned }, snapshot)
                for (width in listOf(88, 160, 240)) {
                    val height = if (kind == CollectionKind.CREST) 108 else 54
                    art.setSize(width, height)
                    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    val g = image.createGraphics()
                    try {
                        art.paint(g)
                    } finally {
                        g.dispose()
                    }
                    // The silhouette is at most 65% opaque; include the filtered ink of the small dot.
                    val inkRows = (0 until height).filter { y -> (0 until width).any { x -> image.getRGB(x, y) ushr 24 >= 192 } }
                    assertTrue("Missing question mark: $kind at $width", inkRows.isNotEmpty())
                    assertEquals("$kind at $width", height / 2.0, (inkRows.first() + inkRows.last() + 1) / 2.0, 1.5)
                }
            }
        } finally {
            art.dispose()
        }
    }

    fun testLockedAndReducedArtworkRemainStaticEvenWhenAnAnimatedFrameIsSupplied() {
        val coordinator = CodeXPCollectionCoordinator(MemoryCollectionPersistence())
        coordinator.load(CollectionProductFixtures.profile(reduced = true))
        val snapshot = coordinator.snapshot()
        val art = CollectionArtwork()

        fun pixels(): IntArray {
            val image = BufferedImage(240, 210, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            art.paint(g)
            g.dispose()
            return image.getRGB(0, 0, 240, 210, null, 0, 240)
        }
        try {
            art.setSize(240, 210)
            for (entry in listOf(snapshot.entries.first { !it.owned }, snapshot.entries.first { it.owned })) {
                art.show(entry, snapshot)
                val before = pixels()
                art.frame = CollectionMotionFrame(1.5, 1.0, true)
                assertTrue(before.contentEquals(pixels()))
                assertEquals(entry.accessibleName, art.accessibleContext.accessibleName)
                if (!entry.owned) {
                    assertFalse(
                        art.toolTipText.contains(
                            CodeXPCollectionCatalog.content
                                .find(entry.id)!!
                                .definition.displayName,
                        ),
                    )
                }
            }
        } finally {
            art.dispose()
        }
    }

    fun testGridRetainsItsModelAndDoesNotRebuildForXpOnlyUpdates() {
        val service = CodeXPService()
        val coordinator = CodeXPCollectionCoordinator(MemoryCollectionPersistence())
        val profile = CollectionProductFixtures.profile()
        coordinator.load(profile)
        val panel = CodeXPCollectionPanel(service)
        try {
            panel.apply(coordinator.snapshot())
            panel.setSize(860, 820)
            repeat(4) { layout(panel) }
            val children = panel.componentCount
            var structuralUpdates = 0
            panel.model.addListDataListener(
                object : ListDataListener {
                    override fun intervalAdded(e: ListDataEvent) {
                        structuralUpdates++
                    }

                    override fun intervalRemoved(e: ListDataEvent) {
                        structuralUpdates++
                    }

                    override fun contentsChanged(e: ListDataEvent) {
                        structuralUpdates++
                    }
                },
            )
            repeat(20) {
                profile.xp += 2
                coordinator.reconcile()
                panel.apply(coordinator.snapshot())
            }
            assertEquals(0, structuralUpdates)
            assertEquals(children, panel.componentCount)
            assertEquals(30, panel.model.size)
            for (width in listOf(280, 860)) {
                panel.setSize(width, 820)
                repeat(4) { layout(panel) }
                assertTrue(panel.scroll.isVisible)
                assertFalse(panel.detailScroll.isVisible)
                assertTrue(panel.scroll.width > 0 && panel.scroll.height > 0)
                assertEquals(width, panel.scroll.width)
                val image = BufferedImage(width, 820, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                try {
                    panel.paint(g)
                } finally {
                    g.dispose()
                }
                val cardColors = image.getRGB(30, 200, 150, 180, null, 0, 150).toSet()
                assertTrue("The card must contain painted artwork and labels, not only an empty border.", cardColors.size > 30)
                val file = File("build/reports/collection-motion/collection-$width.png")
                file.parentFile.mkdirs()
                ImageIO.write(image, "png", file)
            }
        } finally {
            panel.dispose()
            Disposer.dispose(service)
        }
    }

    private fun layout(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layout)
    }

    private fun descendants(container: Container): List<java.awt.Component> = container.components.flatMap { listOf(it) + if (it is Container) descendants(it) else emptyList() }
}
