package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementFrame
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementPainter
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementTimeline
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.ChallengeEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.EffectOptions
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.LevelEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpCluster
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.achievementBounds
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.layoutXpText
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.levelNumbers
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.windowXpBounds
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.xpBounds
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

class AchievementReadabilityTest {
    private val out = File("build/reports/achievement-effects/refinement").apply { mkdirs() }

    @Test
    fun `XP pixels stay small translucent and confined to a single text line`() {
        for (style in EffectStyle.entries) {
            for (dark in listOf(false, true)) {
                val options = EffectOptions(style, false)
                val cluster = XpCluster(2, 0, options)
                repeat(7) { cluster.add(2, 0) }
                cluster.sample(600)
                val font = Font(Font.MONOSPACED, Font.PLAIN, 20).deriveFont(18f)
                val layout = layoutXpText(AchievementPainter.xpText(cluster.total), font, 500, 2)
                assertEquals("+16 XP", layout.text)
                assertEquals(18f, layout.font.size2D, 0f)
                assertTrue(layout.height < 24)
                assertTrue(layout.width < 100)
                val image = xpImage(cluster, 600, dark)
                val ink = pixelBounds(image)!!
                assertTrue(ink.height <= 18)
                assertTrue(ink.width <= layout.width)
                assertTrue(ink.x >= 1 && ink.y >= 1)
                assertTrue(ink.maxX < image.width - 1 && ink.maxY < image.height - 1)
                assertTrue(maxAlpha(image) in 175..194)
                assertTrue(maxAlpha(xpImage(cluster, 600, dark, .5f)) in 85..98)
                assertEquals(0, maxAlpha(xpImage(cluster, 0, dark)))
                assertTrue(maxAlpha(xpImage(cluster, options.timing.xp - 100, dark)) < 60)
                assertEquals(0, maxAlpha(xpImage(cluster, options.timing.xp, dark)))
                // Eight events and a single equal-total event must paint identically: no hit-count row.
                assertArrayEquals(pixels(image), pixels(xpImage(XpCluster(16, 0, options), 600, dark)))
                ImageIO.write(image, "png", File(out, "xp-${style.storedValue}-${if (dark) "dark" else "light"}.png"))
            }
        }
    }

    @Test
    fun `actual XP ink keeps the requested eight directions and caret gap at every scale`() {
        for (scale in listOf(1.0, 1.5, 2.0)) {
            val font = Font(Font.MONOSPACED, Font.ITALIC, 1).deriveFont((18 * scale).toFloat())
            val layout = layoutXpText("+2,048 XP", font, 700, (2 * scale).toInt())
            val viewport = Rectangle(100, 400, 1000, 500)
            val caret = Point(600, 650)
            val lineHeight = (28 * scale).toInt()
            val gap = (8 * scale).toInt()
            for (position in PositionToDisplayGainedXP.entries) {
                val bounds = xpBounds(caret, position, lineHeight, layout, viewport, gap)
                val left = bounds.x + layout.padding.toDouble()
                val top = bounds.y + layout.padding.toDouble()
                val right = left + layout.ink.width
                val bottom = top + layout.ink.height
                when (position.x) {
                    -1 -> assertEquals(gap.toDouble(), caret.x - right, 1.0)
                    1 -> assertEquals(gap.toDouble(), left - caret.x, 1.0)
                    else -> assertEquals(caret.x.toDouble(), (left + right) / 2, 1.0)
                }
                when (position.y) {
                    -1 -> assertEquals(gap.toDouble(), caret.y - bottom, 1.0)
                    1 -> assertEquals(gap.toDouble(), top - caret.y - lineHeight, 1.0)
                    else -> assertEquals(caret.y + lineHeight / 2.0, (top + bottom) / 2, 1.0)
                }
                val moved = xpBounds(caret, position, lineHeight, layout, viewport, gap, 5 * scale)
                assertTrue(abs(moved.y - bounds.y) <= 5 * scale + 1)
            }
        }
    }

    @Test
    fun `XP keeps transparent device scaling and clamps long values at viewport edges`() {
        val cluster = XpCluster(Long.MAX_VALUE, 0, EffectOptions(EffectStyle.CELEBRATORY, true))
        for (width in listOf(45, 120, 300)) {
            val font = Font(Font.MONOSPACED, Font.PLAIN, 18)
            val layout = layoutXpText(AchievementPainter.xpText(cluster.total), font, width, 2)
            assertTrue(layout.width <= width)
            for (position in PositionToDisplayGainedXP.entries) {
                for (caret in listOf(Point(300, 900), Point(300 + width - 1, 1099))) {
                    val viewport = Rectangle(300, 900, width, 200)
                    assertTrue(viewport.contains(xpBounds(caret, position, 24, layout, viewport, 8, 5.0)))
                }
            }
        }
        val one = xpImage(cluster, 500, true)
        val two = xpImage(cluster, 500, true, deviceScale = 2)
        assertEquals(one.width * 2, two.width)
        assertEquals(pixelBounds(one)!!.height * 2.0, pixelBounds(two)!!.height.toDouble(), 3.0)
        assertTrue(maxAlpha(two) <= 194)
    }

    @Test
    fun `fixed window XP stays at client bottom center on move resize and HiDPI`() {
        for (scale in listOf(1.0, 1.5, 2.0)) {
            val font = Font(Font.SANS_SERIF, Font.PLAIN, (13 * scale).toInt())
            for (width in listOf(160, 800, 1400)) {
                val client = Rectangle(17, 31, width, 400)
                val layout = layoutXpText("+200 XP", font, width, (2 * scale).toInt())
                val bottomInset = (48 * scale).toInt()
                val bounds = windowXpBounds(client, layout, bottomInset)
                assertTrue(client.contains(bounds))
                if (width >= 800) {
                    assertEquals(client.centerX, bounds.centerX, .5)
                    assertEquals(bottomInset, client.y + client.height - bounds.y - bounds.height)
                }
            }
        }
    }

    @Test
    fun `challenge ends without a second reward label and preserves subsequent input and queued challenges`() {
        for (style in EffectStyle.entries) {
            for (reduced in listOf(false, true)) {
                val options = EffectOptions(style, reduced)
                val challenge = AchievementEffect(ChallengeEffect("one", "Save", "Save 10 times", 300), null, options)
                val timeline = AchievementTimeline()
                timeline.enqueue(challenge, 0)
                assertEquals(options.timing.challenge, challenge.duration)
                assertNotNull(timeline.frame(challenge.duration - 1))
                assertNull(timeline.frame(challenge.duration))
                val withLevel = challenge.copy(level = LevelEffect(1, 2))
                for (age in options.timing.challenge until options.timing.levelAt step 40) {
                    assertEquals(0, maxAlpha(achievementImage(withLevel, age)))
                }
                for (age in challenge.duration..challenge.duration + options.timing.xp step 40) {
                    assertEquals(0, maxAlpha(achievementImage(challenge, age)))
                }
                val input = XpCluster(2, challenge.duration, options)
                input.add(2, challenge.duration + 100)
                input.sample(challenge.duration + 700)
                assertEquals(4, input.total)
                assertTrue(maxAlpha(xpImage(input, challenge.duration + 700, true)) > 0)
                timeline.enqueue(challenge.copy(challenge = challenge.challenge!!.copy(id = "two", reward = 700)), challenge.duration + 1)
                assertEquals(
                    700,
                    timeline
                        .frame(challenge.duration + 1)!!
                        .effect.challenge!!
                        .reward,
                )
                assertNull(timeline.frame(challenge.duration * 2 + 1))
            }
        }
    }

    @Test
    fun `both achievement styles follow IDE client centre at thirty five percent height`() {
        for (scale in listOf(1.0, 1.5, 2.0)) {
            for (client in listOf(Rectangle(0, 0, 1400, 1000), Rectangle(25, 45, 1920, 1200), Rectangle(0, 0, 160, 120))) {
                for (subtle in listOf(false, true)) {
                    val bounds = achievementBounds(client, subtle, scale)
                    assertTrue(client.contains(bounds))
                    assertEquals(client.centerX, bounds.centerX, .5)
                    if (client.height >= 1000) assertEquals(client.y + client.height * .35, bounds.centerY, .5)
                    // No editor/sidebar/split/terminal dimensions participate in this client-based anchor.
                    val resized = Rectangle(client.x, client.y, client.width + 200, client.height + 100)
                    assertEquals(resized.centerX, achievementBounds(resized, subtle, scale).centerX, .5)
                }
            }
        }
    }

    @Test
    fun `level roll starts at current crosses once and settles at actual target including digit changes and jumps`() {
        for (style in EffectStyle.entries) {
            val options = EffectOptions(style, false)
            val start = if (options.subtle) 280L else 620L
            val duration = if (options.subtle) 520L else 740L
            for (level in listOf(LevelEffect(12, 13), LevelEffect(9, 10), LevelEffect(99, 100), LevelEffect(7, 15))) {
                assertEquals(level.from, levelNumbers(level, options, 0).single().value)
                val middle = levelNumbers(level, options, start + duration / 2)
                assertEquals(listOf(level.from, level.to), middle.map { it.value })
                assertTrue(middle.first().offset < 0 && middle.last().offset > 0)
                assertTrue(middle.all { it.scale in .88..1.0 && it.alpha in 0.4..0.6 })
                assertEquals(1.0, middle.sumOf { it.alpha }, .00001)
                assertEquals(level.to, levelNumbers(level, options, start + duration).single().value)
                for ((name, age) in listOf("start" to start, "middle" to start + duration / 2, "end" to start + duration)) {
                    val image = numberImage(level, options, age)
                    val ink = pixelBounds(image)!!
                    val mask = if (options.subtle) Rectangle(54, 68, 92, 64) else Rectangle(34, 56, 132, 88)
                    assertTrue(mask.contains(ink))
                    ImageIO.write(image, "png", File(out, "roll-${style.storedValue}-${level.from}-${level.to}-$name.png"))
                }
                assertArrayEquals(pixels(numberImage(level, options, 2000)), pixels(numberImage(LevelEffect(level.to, level.to), options, 2000)))
                val reduced = options.copy(reducedMotion = true)
                assertEquals(level.to, levelNumbers(level, reduced, 0).single().value)
                assertEquals(0.0, levelNumbers(level, reduced, 0).single().offset, 0.0)
                assertArrayEquals(pixels(numberImage(level, reduced, 0)), pixels(numberImage(level, reduced, 1000)))
                assertFalse(pixels(numberImage(level, options, start)).contentEquals(pixels(numberImage(level, options, start + duration / 2))))
            }
        }
    }

    private fun xpImage(
        cluster: XpCluster,
        now: Long,
        dark: Boolean,
        inheritedAlpha: Float = 1f,
        deviceScale: Int = 1,
    ): BufferedImage {
        val layout = layoutXpText(AchievementPainter.xpText(cluster.displayed.toLong()), Font(Font.MONOSPACED, Font.PLAIN, 18), 500, 2)
        val image = BufferedImage(layout.width * deviceScale, layout.height * deviceScale, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.scale(deviceScale.toDouble(), deviceScale.toDouble())
            g.composite = AlphaComposite.SrcOver.derive(inheritedAlpha)
            AchievementPainter.paintXp(g, layout, cluster, now, dark)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun achievementImage(
        effect: AchievementEffect,
        age: Long,
    ): BufferedImage {
        val image = BufferedImage(520, 390, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            AchievementPainter.paint(g, 520, 390, AchievementFrame(effect, age), true)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun numberImage(
        level: LevelEffect,
        options: EffectOptions,
        age: Long,
    ): BufferedImage {
        val image = BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.translate(100, 100)
            AchievementPainter.paintLevelNumbers(g, level, options, age, Color.WHITE)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun pixels(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

    private fun maxAlpha(image: BufferedImage): Int = pixels(image).maxOf { it ushr 24 }

    private fun pixelBounds(image: BufferedImage): Rectangle? {
        var bounds: Rectangle? = null
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr 24 != 0) {
                    val pixel = Rectangle(x, y, 1, 1)
                    bounds = bounds?.union(pixel) ?: pixel
                }
            }
        }
        return bounds
    }
}
