package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.ChoiceAward
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CodeXPCollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestFamily
import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementFrame
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementPainter
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.ChallengeEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.EffectOptions
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.LevelEffect
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class ChallengeCrestRenderingTest : BasePlatformTestCase() {
    private val designs =
        CodeXPCollectionCatalog.content.items
            .mapNotNull { it.design }
            .sortedWith(compareBy({ it.family.ordinal }, { it.form }))
    private val challenge = ChallengeEffect("check-preview", "Save Challenge", "Save 12340 times.", 493500)

    fun testEquippedWaveUsesTheLargerChallengeFootprint() {
        val wave = designs.single { it.family == CrestFamily.WAVE && it.form == 2 }
        withThemes { dark ->
            for (style in EffectStyle.entries) {
                val subtle = style == EffectStyle.SUBTLE
                val width = if (subtle) 340 else 520
                val frame = render(effect(wave, style, false), 1200, dark, width, 390, transparent = true)
                val artwork = pixels(frame).take(width * if (subtle) 183 else 237)
                val bounds = pixelBounds(artwork.indices.filter { artwork[it] ushr 24 != 0 }, width)
                assertTrue("Unexpected crest width: $bounds", bounds.width in if (subtle) 150..162 else 196..208)
                assertTrue("Unexpected crest height: $bounds", bounds.height in if (subtle) 124..136 else 173..185)
            }
        }
        System.getenv("CODEXP_CHALLENGE_PREVIEW_PHASE")?.let(::exportPainterSnapshots)
    }

    private fun exportPainterSnapshots(phase: String) {
        require(phase in setOf("before", "final"))
        val directory = File("build/reports/challenge-crest-check/$phase").apply { mkdirs() }
        withThemes { dark ->
            for (style in EffectStyle.entries) {
                for (reduced in listOf(false, true)) {
                    val theme = if (dark) "dark" else "light"
                    val motion = if (reduced) "reduced" else "animated"
                    val label = style.storedValue + "-" + theme + "-" + motion
                    val sheet = BufferedImage(1560, 1085, BufferedImage.TYPE_INT_RGB)
                    val g = sheet.createGraphics()
                    g.color = background(dark)
                    g.fillRect(0, 0, sheet.width, sheet.height)
                    g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
                    for ((index, design) in designs.withIndex()) {
                        val effect = effect(design, style, reduced)
                        val width = if (style == EffectStyle.SUBTLE) 340 else 520
                        val image = render(effect, 1200, dark, width, 390)
                        val x = index % 6 * 260
                        val y = index / 6 * 217
                        g.drawImage(image, x + (520 - width) / 4, y, width / 2, 195, null)
                        g.color = if (dark) Color.WHITE else Color.BLACK
                        g.drawString(design.family.toString() + "/" + design.form, x + 8, y + 210)
                        if (design.family == CrestFamily.WAVE && design.form == 2) {
                            ImageIO.write(image, "png", File(directory, "wave-$label.png"))
                            ImageIO.write(render(effect, 1200, dark, 336, 158), "png", File(directory, "small-$label.png"))
                            for (age in listOf(240L, 380L, 500L, 700L)) {
                                ImageIO.write(render(effect, age, dark, width, 390), "png", File(directory, "draw-$label-$age.png"))
                            }
                        }
                    }
                    g.dispose()
                    ImageIO.write(sheet, "png", File(directory, "all30-$label.png"))
                    val base = effect(designs.first(), style, reduced)
                    val guards =
                        mapOf(
                            "unequipped" to base.copy(crest = null),
                            "level" to base.copy(challenge = null, level = LevelEffect(149, 150)),
                            "choice" to base.copy(challenge = null, choice = ChoiceAward("preview", 1, 19), choiceCount = 19),
                            "equip" to base.copy(challenge = null, equippedEventId = "preview"),
                        )
                    val guardDirectory = File(directory, "unchanged").apply { mkdirs() }
                    for ((name, value) in guards) {
                        ImageIO.write(render(value, 800, dark, 520, 390), "png", File(guardDirectory, "$name-$label.png"))
                    }
                }
            }
        }
        assertEquals(30, designs.size)
    }

    fun testAllThirtyCrestsHaveACenteredStableHighContrastCheck() {
        assertEquals(30, designs.size)
        withThemes { dark ->
            for (style in EffectStyle.entries) {
                for (reduced in listOf(false, true)) {
                    val width = if (style == EffectStyle.SUBTLE) 340 else 520
                    val centerY = if (style == EffectStyle.SUBTLE) 106.0 else 120.0
                    for (design in designs) {
                        val effect = effect(design, style, reduced)
                        val first = render(effect, 800, dark, width, 390, transparent = true)
                        val later = render(effect, 1200, dark, width, 390, transparent = true)
                        val mask = checkPixels(first, dark)
                        assertTrue("Missing central check: $design $style reduced=$reduced dark=$dark", mask.size > 60)
                        assertEquals("Check moves with the crest: $design", mask, checkPixels(later, dark))
                        val bounds = pixelBounds(mask, width)
                        assertEquals(width / 2.0, bounds.centerX, 1.0)
                        assertEquals(centerY, bounds.centerY, 1.0)
                        assertTrue(bounds.width in 30..60 && bounds.height in 20..45)
                        val foreground = checkColor(dark)
                        val outline = if (dark) Color(26, 29, 37) else Color(251, 248, 239)
                        assertTrue("Check/outline contrast", contrast(foreground, outline) > 7.0)
                        if (reduced) {
                            assertTrue(pixels(first).contentEquals(pixels(render(effect, 150, dark, width, 390, transparent = true))))
                        }
                    }
                }
            }
        }
    }

    fun testCheckDrawsBrieflyAndReducedMotionStartsComplete() {
        withThemes { dark ->
            for (style in EffectStyle.entries) {
                val animated = effect(designs.first(), style, false)
                val width = if (style == EffectStyle.SUBTLE) 340 else 520
                val absent = checkPixels(render(animated, 240, dark, width, 390, transparent = true), dark)
                val drawing = checkPixels(render(animated, 380, dark, width, 390, transparent = true), dark)
                val complete = checkPixels(render(animated, 700, dark, width, 390, transparent = true), dark)
                assertTrue(absent.isEmpty())
                assertTrue(drawing.isNotEmpty() && drawing.size < complete.size)
                val reduced = animated.copy(options = animated.options.copy(reducedMotion = true))
                assertEquals(complete, checkPixels(render(reduced, 150, dark, width, 390, transparent = true), dark))
            }
        }
    }

    fun testSmallSurfacesKeepArtworkAndLabelsInsideWithClearGaps() {
        withThemes { dark ->
            for (style in EffectStyle.entries) {
                for (reduced in listOf(false, true)) {
                    for (design in designs) {
                        for ((width, height) in listOf(520 to 390, 336 to 158, 240 to 100, 160 to 64, 76 to 46)) {
                            val image = render(effect(design, style, reduced), 1200, dark, width, height, transparent = true, padding = 24)
                            val data = pixels(image)
                            for (i in data.indices) {
                                if (data[i] ushr 24 != 0) {
                                    val x = i % image.width
                                    val y = i / image.width
                                    if (x !in 24 until 24 + width || y !in 24 until 24 + height) {
                                        fail("Clipped at ($x,$y): $design $style ${width}x$height")
                                    }
                                }
                            }
                            val center = image.getSubimage(24, 24, width, height)
                            val rows = labelRows(center, dark)
                            val bands =
                                rows.fold(mutableListOf<MutableList<Int>>()) { result, y ->
                                    if (result.isEmpty() || result.last().last() != y - 1) result.add(mutableListOf())
                                    result.last().add(y)
                                    result
                                }
                            assertTrue("Missing labels: $design $width x $height", bands.size >= 2)
                            if (height == 390) assertEquals("Header, name, description and reward remain visible", 4, bands.size)
                            for (band in bands) {
                                assertTrue("Merged text rows: $design $width x $height", band.size <= if (height == 390) 27 else 18)
                                val gap = maxOf(0, band.first() - 3) until band.first()
                                assertTrue("Artwork or text overlaps label at row ${band.first()}: $design $width x $height", gap.any { y -> (0 until width).all { center.getRGB(it, y) ushr 24 == 0 } })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun effect(
        design: CrestDesign,
        style: EffectStyle,
        reduced: Boolean,
    ) = AchievementEffect(challenge, null, EffectOptions(style, reduced), design)

    private fun render(
        effect: AchievementEffect,
        keyframeMs: Long,
        dark: Boolean,
        width: Int,
        height: Int,
        transparent: Boolean = false,
        padding: Int = 0,
    ): BufferedImage {
        val image = BufferedImage(width + padding * 2, height + padding * 2, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.translate(padding, padding)
            if (!transparent) {
                g.color = background(dark)
                g.fillRect(0, 0, width, height)
            }
            // Match the celebration keyframe and sample the independent ambient crest at its reference phase.
            val frame = AchievementFrame(effect, keyframeMs * 3 / 2, motionSeconds = keyframeMs / 1000.0)
            AchievementPainter.paint(g, width, height, frame, dark)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun labelRows(
        image: BufferedImage,
        dark: Boolean,
    ): List<Int> {
        val base = if (dark) Color(26, 29, 37) else Color(251, 248, 239)
        return (0 until image.height).filter { y ->
            (0 until image.width).count { x ->
                val v = image.getRGB(x, y)
                v ushr 24 == 239 && (v ushr 16 and 255) in base.red - 2..base.red + 2 &&
                    (v ushr 8 and 255) in base.green - 2..base.green + 2 && (v and 255) in base.blue - 2..base.blue + 2
            } >= 6
        }
    }

    private fun checkPixels(
        image: BufferedImage,
        dark: Boolean,
    ): List<Int> {
        val color = checkColor(dark).rgb
        return (0 until image.width * (image.height * .45).toInt()).filter { index -> image.getRGB(index % image.width, index / image.width) == color }
    }

    private fun pixelBounds(
        indices: List<Int>,
        width: Int,
    ): Rectangle {
        val left = indices.minOf { it % width }
        val top = indices.minOf { it / width }
        return Rectangle(left, top, indices.maxOf { it % width } - left + 1, indices.maxOf { it / width } - top + 1)
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        fun luminance(color: Color): Double {
            fun linear(value: Int): Double = (value / 255.0).let { if (it <= .04045) it / 12.92 else Math.pow((it + .055) / 1.055, 2.4) }
            return .2126 * linear(color.red) + .7152 * linear(color.green) + .0722 * linear(color.blue)
        }
        val first = luminance(a)
        val second = luminance(b)
        return (maxOf(first, second) + .05) / (minOf(first, second) + .05)
    }

    private fun checkColor(dark: Boolean) = if (dark) Color(244, 235, 215) else Color(83, 57, 25)

    private fun background(dark: Boolean) = if (dark) Color(31, 32, 36) else Color(250, 250, 252)

    private fun pixels(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

    private fun withThemes(block: (Boolean) -> Unit) {
        val previous = !JBColor.isBright()
        try {
            for (dark in listOf(true, false)) {
                JBColor.setDark(dark)
                block(dark)
            }
        } finally {
            JBColor.setDark(previous)
        }
    }
}
