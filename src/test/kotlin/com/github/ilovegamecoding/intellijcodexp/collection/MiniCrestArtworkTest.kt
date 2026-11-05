package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CodeXPCollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestFamily
import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionCrestPainter
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionMotionFrame
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.MiniCrestSurface
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardCrest
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.EffectOptions
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XP_EMISSION_DELAY_MS
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XP_FLIGHT_MS
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XP_IMPACT_MS
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpAbsorptionLight
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpAbsorptionTarget
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpGainEffectRenderer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.Point
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.swing.JComponent
import javax.swing.JLayeredPane

class MiniCrestArtworkTest : BasePlatformTestCase() {
    private val designs =
        CodeXPCollectionCatalog.content.items
            .mapNotNull { it.design }
            .sortedWith(compareBy({ it.family.ordinal }, { it.form }))

    fun testAllThirtyCrestsMoveAtBothCompactSizesWithoutRevealingLockedArt() {
        val oldDark = !JBColor.isBright()
        try {
            for (dark in listOf(true, false)) {
                JBColor.setDark(dark)
                for (subtle in listOf(true, false)) {
                    for (size in listOf(72, 96)) {
                        for (design in designs) {
                            val first = render(design, size, .35, subtle)
                            val later = render(design, size, 1.7, subtle)
                            assertTrue("$design $size subtle=$subtle dark=$dark motion too faint", changed(first, later) > 12)
                            assertEquals(0, changed(render(design, size, .35, subtle, locked = true), render(design, size, 1.7, subtle, locked = true)))
                            assertEquals(0, changed(render(design, size, .35, subtle, animated = false), render(design, size, 1.7, subtle, animated = false)))
                            // The corner stays transparent: the compact mode never introduces a card.
                            assertEquals(0, first.getRGB(0, 0) ushr 24)
                        }
                    }
                }
            }
        } finally {
            JBColor.setDark(oldDark)
        }
    }

    fun testSharedCompactArtStaysUnlabelledForAchievementOverlays() {
        val design = designs.first()
        val compact = render(design, 96, 0.0, false, animated = false, level = 1)
        val compact150 = render(design, 96, 0.0, false, animated = false, level = 150)
        assertEquals(0, changed(compact, compact150))
        val dashboard = render(design, 96, 0.0, false, animated = false, compact = false, level = 1)
        val dashboard150 = render(design, 96, 0.0, false, animated = false, compact = false, level = 150)
        assertTrue(changed(dashboard, dashboard150) > 12)
        assertTrue(changed(compact, dashboard150) > 100)
        assertEquals(0, changed(compact, render(design, 96, 0.0, false, animated = false)))
    }

    fun testMiniMatchesDashboardPixelsForEveryEquippedCrestAndSelectableSize() {
        val oldDark = !JBColor.isBright()
        val surface = MiniCrestSurface()
        val dashboard = DashboardCrest()
        val frames = listOf(CollectionMotionFrame(), CollectionMotionFrame(.35, 0.0, true), CollectionMotionFrame(1.7, 0.0, true))
        try {
            for (dark in listOf(false, true)) {
                JBColor.setDark(dark)
                for (size in listOf(96, 128, 164, 256)) {
                    for ((i, design) in designs.withIndex()) {
                        val level = listOf(1, 25, 150)[i % 3]
                        surface.design = design
                        surface.level = level
                        surface.options = surface.options.copy(style = if (i % 2 == 0) EffectStyle.SUBTLE else EffectStyle.CELEBRATORY)
                        dashboard.collectionDesign = design
                        dashboard.level = dashboard.level.copy(level = level)
                        dashboard.subtle = surface.options.subtle
                        for (frame in frames) {
                            surface.frame = frame
                            dashboard.motionFrame = frame
                            val mini = renderSurface(surface, size)
                            val expected = renderSurface(dashboard, size)
                            assertEquals("$design level=$level size=$size dark=$dark frame=$frame", 0, changed(expected, mini))
                        }
                    }
                }
            }
            val empty = BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB)
            surface.design = null
            assertEquals(0, changed(empty, renderSurface(surface, 96)))
        } finally {
            JBColor.setDark(oldDark)
        }
    }

    fun testLevelRemainsVisibleAtTheSmallestSizeWithoutFillingTheEditorBackground() {
        val mini = MiniCrestSurface().apply { design = CrestDesign(CrestFamily.SEAL, 1) }
        val size = 96
        var previous = renderSurface(mini, size)
        for (level in listOf(25, 150)) {
            mini.level = level
            val current = renderSurface(mini, size)
            val allChanges = changed(previous, current)
            assertTrue(allChanges > 24)
            assertEquals(allChanges, changed(previous.getSubimage(22, 40, 52, 32), current.getSubimage(22, 40, 52, 32)))
            assertEquals(0, current.getRGB(0, 0) ushr 24)
            previous = current
        }
    }

    private fun renderSurface(
        surface: JComponent,
        size: Int,
    ): BufferedImage {
        surface.setSize(size, size)
        surface.doLayout()
        val image = BufferedImage(size, surface.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        surface.paint(g)
        g.dispose()
        return image
    }

    fun testExportThirtyCrestContactSheetsAndTimeSequences() {
        val directory = File("build/reports/mini-crest/artwork").apply { mkdirs() }
        val oldDark = !JBColor.isBright()
        try {
            for (dark in listOf(true, false)) {
                JBColor.setDark(dark)
                for (subtle in listOf(true, false)) {
                    val label = "${if (dark) "dark" else "light"}-${if (subtle) "subtle" else "celebratory"}"
                    for (seconds in listOf(.0, 1.2, 2.4)) ImageIO.write(sheet(seconds, subtle, dark), "png", File(directory, "$label-$seconds.png"))
                    if (dark) {
                        writeGif(File(directory, "$label-96-128.gif"), 24, 15) { index -> sheet(index * .15, subtle, dark) }
                    }
                }
            }
        } finally {
            JBColor.setDark(oldDark)
        }
        assertEquals(30, designs.size)
    }

    fun testNativeXpAbsorptionPreview() {
        val oldDark = !JBColor.isBright()
        val pane =
            object : JLayeredPane() {
                override fun isShowing() = true
            }.apply {
                setSize(550, 230)
                isOpaque = true
                background = Color(31, 32, 36)
            }
        val crest =
            MiniCrestSurface().apply {
                setBounds(400, 18, 128, 128)
                design = CrestDesign(CrestFamily.ORBIT, 3)
                level = 25
                doLayout()
            }
        pane.add(crest, JLayeredPane.PALETTE_LAYER, -1)
        var now = 0L
        val renderer = XpGainEffectRenderer(clock = { now }, crestTarget = { XpAbsorptionTarget(Point(crest.x + crest.width / 2, crest.y + crest.height / 2), crest.width * .48, crest.design, crest.width / 240.0) })
        val config = CodeXPConfiguration()
        try {
            JBColor.setDark(true)
            renderer.renderWindow(24, pane, config)
            val dir = File("build/reports/achievement-effects").apply { mkdirs() }
            val launch = XP_EMISSION_DELAY_MS
            writeGif(File(dir, "xp-to-mini-crest.gif"), ((launch + XP_FLIGHT_MS + XP_IMPACT_MS + 360) / 20).toInt(), 2) { index ->
                now = index * 20L
                renderer.tick()
                crest.frame = CollectionMotionFrame(now / 1000.0, 0.0, true)
                val flight = pane.components.filterIsInstance<XpAbsorptionLight>().singleOrNull()
                assertEquals(now in launch until (launch + XP_FLIGHT_MS + XP_IMPACT_MS), flight != null)
                val image = BufferedImage(pane.width, pane.height, BufferedImage.TYPE_INT_RGB)
                val g = image.createGraphics()
                pane.paint(g)
                if (now == launch + XP_FLIGHT_MS / 2 + 10) {
                    val light = requireNotNull(flight)
                    assertTrue(image.getRGB(light.bounds.centerX.toInt(), light.bounds.centerY.toInt()) != pane.background.rgb)
                }
                g.dispose()
                image
            }
        } finally {
            renderer.dispose()
            JBColor.setDark(oldDark)
        }
    }

    private fun writeGif(
        destination: File,
        frames: Int,
        delayHundredths: Int,
        imageAt: (Int) -> BufferedImage,
    ) {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        ImageIO.createImageOutputStream(destination).use { output ->
            writer.output = output
            writer.prepareWriteSequence(null)
            repeat(frames) { index ->
                val image = imageAt(index)
                val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), writer.defaultWriteParam)
                val root = metadata.getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
                val control = root.getElementsByTagName("GraphicControlExtension").item(0) as IIOMetadataNode
                control.setAttribute("delayTime", delayHundredths.toString())
                control.setAttribute("disposalMethod", "none")
                if (index == 0) {
                    val extensions = IIOMetadataNode("ApplicationExtensions")
                    extensions.appendChild(
                        IIOMetadataNode("ApplicationExtension").apply {
                            setAttribute("applicationID", "NETSCAPE")
                            setAttribute("authenticationCode", "2.0")
                            userObject = byteArrayOf(1, 0, 0)
                        },
                    )
                    root.appendChild(extensions)
                }
                metadata.setFromTree("javax_imageio_gif_image_1.0", root)
                writer.writeToSequence(IIOImage(image, null, metadata), writer.defaultWriteParam)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
    }

    private fun sheet(
        seconds: Double,
        subtle: Boolean,
        dark: Boolean,
    ): BufferedImage {
        val sheet = BufferedImage(1440, 912, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = if (dark) Color(30, 33, 39) else Color(249, 246, 239)
        g.fillRect(0, 0, sheet.width, sheet.height)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 10)
        val mini = MiniCrestSurface()
        mini.options = mini.options.copy(style = if (subtle) EffectStyle.SUBTLE else EffectStyle.CELEBRATORY)
        mini.frame = CollectionMotionFrame(seconds, 0.0, true)
        for ((index, design) in designs.withIndex()) {
            mini.design = design
            mini.level = listOf(1, 25, 150)[index % 3]
            for ((row, size) in listOf(96, 128).withIndex()) {
                val x = index / 3 * 144
                val y = (row * 3 + index % 3) * 152
                g.drawImage(renderSurface(mini, size), x + (144 - size) / 2, y + 4, null)
                g.color = if (dark) Color(220, 220, 225) else Color(60, 60, 65)
                g.drawString("${design.family}/${design.form} · $size", x + 4, y + 147)
            }
        }
        g.dispose()
        return sheet
    }

    private fun render(
        design: CrestDesign,
        size: Int,
        seconds: Double,
        subtle: Boolean,
        animated: Boolean = true,
        locked: Boolean = false,
        compact: Boolean = true,
        level: Int = 1,
    ): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.translate(size / 2.0, size / 2.0)
        g.scale(size / 240.0, size / 240.0)
        CollectionCrestPainter.paintAnimated(g, design, level, locked, CollectionMotionFrame(seconds, 0.0, animated), subtle, null, compact)
        g.dispose()
        return image
    }

    private fun changed(
        first: BufferedImage,
        second: BufferedImage,
    ): Int {
        var count = 0
        for (y in 0 until first.height) for (x in 0 until first.width) if (first.getRGB(x, y) != second.getRGB(x, y)) count++
        return count
    }
}
