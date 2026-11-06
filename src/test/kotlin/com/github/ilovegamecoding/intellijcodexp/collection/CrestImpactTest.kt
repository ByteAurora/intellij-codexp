package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CodeXPCollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestFamily
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionCrestPainter
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CrestImpactPainter
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.MiniCrestSurface
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XP_FLIGHT_MS
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XP_IMPACT_MS
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpAbsorptionLight
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Point
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class CrestImpactTest : BasePlatformTestCase() {
    private val designs =
        CodeXPCollectionCatalog.content.items
            .mapNotNull { it.design }
            .sortedWith(compareBy({ it.family.ordinal }, { it.form }))

    fun testImpactReusesActualOutlineAndCachedDeviceScaleSprites() {
        val shield = CrestDesign(CrestFamily.SHIELD, 1)
        val outline = CollectionCrestPainter.impactOutline(shield)
        assertEquals(103.0, outline.bounds2D.maxY)
        assertEquals(-95.0, outline.bounds2D.minY)
        assertTrue(outline.contains(75.0, -78.0)) // Shield corner, outside a circular silhouette.
        assertFalse(outline.contains(70.0, 80.0))
        val image = CrestImpactPainter.sprite(shield, 1.0)
        repeat(2_000) {
            assertSame(outline, CollectionCrestPainter.impactOutline(shield))
            assertSame(image, CrestImpactPainter.sprite(shield, 1.0))
        }
        assertEquals(image.width * 2, CrestImpactPainter.sprite(shield, 2.0).width)
        assertTrue(CollectionCrestPainter.impactOutline(CrestDesign(CrestFamily.WINGS, 3)).bounds2D.width > 210)
    }

    fun testEveryOutlineFitsAtAllSizesAndRetinaScalesWithoutBatchScaling() {
        for (design in designs) {
            for (side in listOf(96, 128, 164, 256)) {
                val artworkScale = side / 240.0
                val edge = BasicStroke(22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(CollectionCrestPainter.impactOutline(design))
                for (deviceScale in listOf(1, 2)) {
                    for (subtle in listOf(true, false)) {
                        val first = impact(design, side, deviceScale, subtle, 1)
                        val batched = impact(design, side, deviceScale, subtle, 4)
                        var lit = 0
                        var batchChanges = 0
                        for (y in 0 until first.height) {
                            for (x in 0 until first.width) {
                                val dx = (x + .5 - first.width / 2) / deviceScale / artworkScale
                                val dy = (y + .5 - first.height / 2) / deviceScale / artworkScale
                                val alpha = first.getRGB(x, y) ushr 24
                                if (x == 0 || y == 0 || x == first.width - 1 || y == first.height - 1) assertEquals("clipped $design $side/$deviceScale", 0, alpha)
                                if (dx * dx + dy * dy < 30 * 30) continue // Only the tiny arriving head varies with a batch.
                                if (alpha > 12) {
                                    lit++
                                    assertTrue("not on $design outline at $dx,$dy", edge.contains(dx, dy))
                                }
                                if (first.getRGB(x, y) != batched.getRGB(x, y)) batchChanges++
                            }
                        }
                        assertTrue("missing outline $design $side/$deviceScale", lit > 30)
                        assertTrue("batch scales the crest outline $design $side/$deviceScale", batchChanges < lit * .15 + 10)
                    }
                }
            }
        }
    }

    fun testOutlineFullyFadesAndRemainsVisibleInBothThemes() {
        val oldDark = !JBColor.isBright()
        try {
            for (dark in listOf(true, false)) {
                JBColor.setDark(dark)
                for (design in designs) {
                    val light = XpAbsorptionLight(Point(200, 200), true)
                    light.advance(Point(200, 200), XP_FLIGHT_MS + XP_IMPACT_MS, 80.0, design, 164 / 240.0)
                    val image = BufferedImage(light.width, light.height, BufferedImage.TYPE_INT_ARGB)
                    val g = image.createGraphics()
                    light.paint(g)
                    g.dispose()
                    assertTrue(image.getRGB(0, 0, image.width, image.height, null, 0, image.width).all { it ushr 24 == 0 })
                    val visible = impact(design, 128, 2, true, 1)
                    assertTrue(visible.getRGB(0, 0, visible.width, visible.height, null, 0, visible.width).count { it ushr 24 > 12 } > 100)
                }
            }
        } finally {
            JBColor.setDark(oldDark)
        }
    }

    fun testExportThirtyOutlineArrivalComparisons() {
        val directory = File("build/reports/mini-crest/impact").apply { mkdirs() }
        val oldDark = !JBColor.isBright()
        try {
            for (dark in listOf(true, false)) {
                JBColor.setDark(dark)
                for (elapsed in listOf(0L, 80L, 160L, 240L)) {
                    val sheet = BufferedImage(1320, 1100, BufferedImage.TYPE_INT_RGB)
                    val g = sheet.createGraphics()
                    g.color = if (dark) Color(30, 33, 39) else Color(248, 248, 248)
                    g.fillRect(0, 0, sheet.width, sheet.height)
                    g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
                    for ((index, design) in designs.withIndex()) {
                        val x = index % 6 * 220
                        val y = index / 6 * 220
                        val mini =
                            MiniCrestSurface().apply {
                                this.design = design
                                level = 43
                                setSize(164, 164)
                            }
                        val copy = g.create(x + 28, y + 24, 164, 164)
                        mini.paint(copy)
                        copy.dispose()
                        if (elapsed > 0) {
                            val light = XpAbsorptionLight(Point(x + 110, y + 106), true)
                            light.advance(Point(x + 110, y + 106), XP_FLIGHT_MS + elapsed, 80.0, design, 164 / 240.0)
                            val overlay = g.create(light.x, light.y, light.width, light.height)
                            light.paint(overlay)
                            overlay.dispose()
                        }
                        g.color = if (dark) Color.LIGHT_GRAY else Color.DARK_GRAY
                        g.drawString("${design.family} ${design.form}", x + 55, y + 210)
                    }
                    g.dispose()
                    ImageIO.write(sheet, "png", File(directory, "${if (dark) "dark" else "light"}-$elapsed.png"))
                }
            }
        } finally {
            JBColor.setDark(oldDark)
        }
    }

    private fun impact(
        design: CrestDesign,
        side: Int,
        deviceScale: Int,
        subtle: Boolean,
        batch: Int,
    ): BufferedImage {
        val extent = side + 48
        val center = extent / 2
        val light = XpAbsorptionLight(Point(center, center), subtle, batch)
        light.advance(Point(center, center), XP_FLIGHT_MS + 190, side * .48, design, side / 240.0)
        val image = BufferedImage(extent * deviceScale, extent * deviceScale, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.scale(deviceScale.toDouble(), deviceScale.toDouble())
        g.translate(light.x, light.y)
        g.clipRect(0, 0, light.width, light.height)
        light.paint(g)
        g.dispose()
        return image
    }
}
