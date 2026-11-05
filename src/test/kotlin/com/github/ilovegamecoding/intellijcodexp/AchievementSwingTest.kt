package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementFrame
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementPainter
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.ChallengeEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.CodeXPOverlayDialog
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.EffectOptions
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.LevelEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XP_EMISSION_DELAY_MS
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XP_EMISSION_INTERVAL_MS
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XP_FLIGHT_MS
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XP_IMPACT_MS
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XP_MAX_LIGHTS
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpAbsorptionLight
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpAbsorptionTarget
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpGainEffectRenderer
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpTextLayoutCache
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollingModel
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorColorsScheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.lang.reflect.Proxy
import javax.imageio.ImageIO
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.SwingUtilities

class AchievementSwingTest {
    @Test
    fun `XP text geometry is reused until a layout input changes`() {
        val cache = XpTextLayoutCache()
        val font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
        val first = cache.layout("+123 XP", font, 800, 2)
        repeat(2000) { assertSame(first, cache.layout("+123 XP", font.deriveFont(14f), 800, 2)) }
        val changed = cache.layout("+124 XP", font, 800, 2)
        assertTrue(first !== changed)
        assertTrue(changed !== cache.layout("+124 XP", font, 30, 2))
        assertTrue(cache.layout("+124 XP", font, 800, 2) !== cache.layout("+124 XP", font.deriveFont(20f), 800, 2))
        val beforePadding = cache.layout("+124 XP", font, 800, 2)
        assertTrue(beforePadding !== cache.layout("+124 XP", font, 800, 4))
    }

    @Test
    fun `tight comet bounds reduce repaint area without clipping any direction or DPI`() {
        SwingUtilities.invokeAndWait {
            val oldScale =
                com.intellij.ui.scale.JBUIScale
                    .scale(1f)
            try {
                for (factor in listOf(1f, 2f)) {
                    com.intellij.ui.scale.JBUIScale
                        .setUserScaleFactor(factor)
                    for (subtle in listOf(true, false)) {
                        for (target in listOf(Point(1000, 0), Point(-1000, 0), Point(0, 1000), Point(0, -1000), Point(800, 800), Point(-800, -800))) {
                            val light = XpAbsorptionLight(Point(0, 0), subtle)
                            light.advance(target, XP_FLIGHT_MS * 4 / 5)
                            val oldSide = 2 * ((if (subtle) 78 else 120) + 5) * factor
                            assertTrue("$factor/$subtle/$target: ${light.size}", light.width * light.height < oldSide * oldSide * .65)
                            val bitmap = BufferedImage(light.width, light.height, BufferedImage.TYPE_INT_ARGB)
                            bitmap.createGraphics().let {
                                light.paint(it)
                                it.dispose()
                            }
                            assertTrue((0 until bitmap.width).all { bitmap.getRGB(it, 0) ushr 24 == 0 && bitmap.getRGB(it, bitmap.height - 1) ushr 24 == 0 })
                            assertTrue((0 until bitmap.height).all { bitmap.getRGB(0, it) ushr 24 == 0 && bitmap.getRGB(bitmap.width - 1, it) ushr 24 == 0 })
                            assertTrue((0 until bitmap.width).any { x -> (0 until bitmap.height).any { y -> bitmap.getRGB(x, y) ushr 24 > 20 } })
                        }
                    }
                }
            } finally {
                com.intellij.ui.scale.JBUIScale
                    .setUserScaleFactor(oldScale)
            }
        }
    }

    @Test
    fun `overlay hit testing leaves editor clickable and unfocused`() {
        SwingUtilities.invokeAndWait {
            val pane = JLayeredPane().apply { setSize(600, 400) }
            val editor = JPanel().apply { setBounds(0, 0, 600, 400) }
            val overlay = CodeXPOverlayDialog().apply { setBounds(20, 20, 520, 350) }
            pane.add(editor, JLayeredPane.DEFAULT_LAYER)
            pane.add(overlay, JLayeredPane.PALETTE_LAYER)
            assertSame(editor, pane.findComponentAt(250, 180))
            assertFalse(overlay.isFocusable)
        }
    }

    @Test
    fun `XP cluster reuses surface and releases timer and editor references`() {
        SwingUtilities.invokeAndWait {
            val component =
                object : JPanel(null) {
                    override fun isShowing(): Boolean = true
                }
            val caret = proxy(CaretModel::class.java) { name -> if (name == "getVisualPosition") VisualPosition(2, 5) else null }
            val scrolling = proxy(ScrollingModel::class.java) { name -> if (name == "getVisibleArea") Rectangle(0, 0, 800, 500) else null }
            val scheme =
                proxy(EditorColorsScheme::class.java) { name ->
                    when (name) {
                        "getFont" -> Font(Font.MONOSPACED, Font.PLAIN, 20)
                        "getEditorFontSize" -> 20
                        else -> null
                    }
                }
            var caretPoint = Point(200, 100)
            val editor =
                proxy(Editor::class.java) { name ->
                    when (name) {
                        "isDisposed" -> false
                        "getContentComponent" -> component
                        "getCaretModel" -> caret
                        "getScrollingModel" -> scrolling
                        "visualPositionToXY" -> caretPoint
                        "getLineHeight" -> 24
                        "getColorsScheme" -> scheme
                        else -> null
                    }
                }
            val caretPane = showingPane()
            JRootPane().layeredPane = caretPane
            caretPane.add(component)
            component.setBounds(37, 44, 800, 500)
            var now = 1000L
            val renderer = XpGainEffectRenderer(clock = { now }, crestTarget = { if (it === caretPane) XpAbsorptionTarget(Point(600, 300), 100.0) else null })
            val config = CodeXPConfiguration()
            renderer.render(2, editor, config)
            assertEquals(1, renderer.activeCount)
            assertTrue(renderer.isRunning)
            val surface = component.components.single()
            repeat(8) { renderer.render(2, editor, config) }
            assertSame(surface, component.components.single())
            assertFalse(surface.contains(10, 10))
            assertFalse(surface.isFocusable)
            assertTrue(surface.width < 120)
            assertTrue(surface.height < 24)
            assertEquals(206, surface.x)
            caretPoint = Point(20, 124) // The next typed character wraps to the next visual line.
            renderer.render(2, editor, config)
            assertSame(surface, component.components.single())
            assertEquals(26, surface.x)
            assertTrue(surface.y in 90..115)
            val pane =
                object : JLayeredPane() {
                    override fun isShowing(): Boolean = true
                }.apply { setSize(800, 500) }
            val otherProjectPane = JLayeredPane().apply { setSize(800, 500) }
            val caretText = surface.accessibleContext.accessibleName
            renderer.renderWindow(10, pane, config)
            val fixed = pane.components.single()
            renderer.renderWindow(10, pane, config)
            assertEquals(2, renderer.activeCount)
            assertEquals(1, renderer.caretCount)
            assertEquals(1, renderer.windowCount)
            assertEquals(caretText, surface.accessibleContext.accessibleName)
            assertEquals("+20 XP", fixed.accessibleContext.accessibleName)
            assertEquals(0, otherProjectPane.componentCount)
            assertFalse(fixed.contains(1, 1))
            assertFalse(fixed.isFocusable)
            val oldX = fixed.x
            pane.setSize(1000, 600)
            renderer.renderWindow(10, pane, config)
            assertEquals(oldX + 100, fixed.x)
            assertEquals(500.0, fixed.bounds.centerX, .5)
            assertEquals(552.0, fixed.bounds.maxY, .5)
            val start = SwingUtilities.convertPoint(surface, surface.width / 2, surface.height / 2, caretPane)
            now += XP_EMISSION_DELAY_MS
            renderer.tick()
            val light = caretPane.components.filterIsInstance<XpAbsorptionLight>().single()
            assertEquals(start.x.toDouble(), light.bounds.centerX, .01)
            assertEquals(start.y.toDouble(), light.bounds.centerY, .01)
            assertEquals(1, pane.componentCount) // The other window has no visible crest.
            renderer.release(editor)
            assertTrue(caretPane.components.none { it is XpAbsorptionLight })
            assertEquals(1, renderer.activeCount)
            assertTrue(renderer.isRunning)
            assertEquals(0, component.componentCount)
            renderer.renderWindow(10, pane, config.copy(showGainedXP = false))
            assertEquals(0, pane.componentCount)
            assertEquals(0, renderer.activeCount)
            assertFalse(renderer.isRunning)
            renderer.render(2, editor, config)
            renderer.renderWindow(10, pane, config)
            renderer.dispose()
            renderer.render(2, editor, config)
            assertEquals(0, component.componentCount)
            assertFalse(renderer.isRunning)
            assertEquals(0, pane.componentCount)
        }
    }

    @Test
    fun `XP light accelerates all the way to the crest and stays there during its fade`() {
        SwingUtilities.invokeAndWait {
            val origin = Point(30, 95)
            for (target in listOf(Point(510, 295), Point(-110, 5))) {
                val light = XpAbsorptionLight(origin, false)

                fun centerAt(elapsed: Long): Point {
                    light.advance(target, elapsed)
                    return light.headLocation
                }
                val centers = (0..4).map { centerAt(XP_FLIGHT_MS * it / 4) }
                assertEquals(origin, centers.first())
                assertEquals(target, centers.last())
                val distances = centers.zipWithNext { a, b -> a.distance(b) }
                assertTrue("Expected increasing flight speed, got $distances", distances.zipWithNext().all { (a, b) -> a < b })
                assertEquals(target, centerAt(XP_FLIGHT_MS + XP_IMPACT_MS / 2))
            }
        }
    }

    @Test
    fun `comet leaves a long trail then blooms at the crest and fades completely`() {
        SwingUtilities.invokeAndWait {
            for (subtle in listOf(true, false)) {
                val light = XpAbsorptionLight(Point(0, 0), subtle)

                fun pixels(elapsed: Long): BufferedImage {
                    light.advance(Point(600, 0), elapsed)
                    val image = BufferedImage(light.width, light.height, BufferedImage.TYPE_INT_ARGB)
                    val g = image.createGraphics()
                    light.paint(g)
                    g.dispose()
                    return image
                }

                fun visibleBeyond(
                    image: BufferedImage,
                    distance: Int,
                ): Boolean =
                    (0 until image.width).any { x ->
                        (0 until image.height).any { y ->
                            Point.distance(x.toDouble(), y.toDouble(), (light.headLocation.x - light.x).toDouble(), (light.headLocation.y - light.y).toDouble()) > distance &&
                                image.getRGB(x, y) ushr 24 > 12
                        }
                    }
                assertTrue(visibleBeyond(pixels(XP_FLIGHT_MS * 4 / 5), if (subtle) 42 else 70))
                val burst = pixels(XP_FLIGHT_MS + XP_IMPACT_MS / 2)
                val center = burst.width / 2
                assertTrue((center + 15 until burst.width).any { x -> burst.getRGB(x, center) ushr 24 > 12 })
                assertFalse(visibleBeyond(pixels(XP_FLIGHT_MS + XP_IMPACT_MS), -1))
            }
        }
    }

    @Test
    fun `impact expands across each crest size without multiplying DPI or event batch scale`() {
        SwingUtilities.invokeAndWait {
            val originalScale =
                com.intellij.ui.scale.JBUIScale
                    .scale(1f)
            try {
                for (factor in listOf(1f, 2f)) {
                    com.intellij.ui.scale.JBUIScale
                        .setUserScaleFactor(factor)
                    for (subtle in listOf(true, false)) {
                        for (side in listOf(96, 128, 164, 256)) {
                            val targetRadius = side * factor * if (subtle) .42 else .48
                            for (count in listOf(1, 4)) {
                                val light = XpAbsorptionLight(Point(0, 0), subtle, count)
                                val center = Point(600, 300)
                                light.advance(center, XP_FLIGHT_MS + XP_IMPACT_MS / 2, targetRadius)
                                assertEquals(center.x.toDouble(), light.bounds.centerX, .01)
                                assertEquals(center.y.toDouble(), light.bounds.centerY, .01)
                                assertTrue(light.width <= (targetRadius + 7 * factor) * 2 + 2)
                                val image = BufferedImage(light.width, light.height, BufferedImage.TYPE_INT_ARGB)
                                val g = image.createGraphics()
                                light.paint(g)
                                g.dispose()
                                val mid = image.width / 2
                                assertTrue("$side/$factor/$subtle: bloom should reach the crest artwork", (mid + (side * factor * .32).toInt() until image.width).any { x -> image.getRGB(x, mid) ushr 24 > 8 })
                                light.advance(center, XP_FLIGHT_MS + XP_IMPACT_MS, targetRadius)
                                val empty = BufferedImage(light.width, light.height, BufferedImage.TYPE_INT_ARGB)
                                empty.createGraphics().let {
                                    light.paint(it)
                                    it.dispose()
                                }
                                assertTrue((0 until empty.width).all { x -> empty.getRGB(x, empty.height / 2) ushr 24 == 0 })
                            }
                        }
                    }
                }
            } finally {
                com.intellij.ui.scale.JBUIScale
                    .setUserScaleFactor(originalScale)
            }
        }
    }

    @Test
    fun `each spaced XP event launches before its number fades without cancelling existing flights`() {
        SwingUtilities.invokeAndWait {
            val pane = showingPane()
            var now = 1000L
            var target: Point? = Point(740, 300)
            val renderer = XpGainEffectRenderer(clock = { now }, crestTarget = { target?.let { XpAbsorptionTarget(it, 100.0) } })
            val config = CodeXPConfiguration()

            fun lights() = pane.components.filterIsInstance<XpAbsorptionLight>()
            try {
                renderer.renderWindow(2, pane, config)
                val label = pane.components.single()
                now += XP_EMISSION_DELAY_MS - 1
                renderer.tick()
                assertTrue(lights().isEmpty())
                now++
                renderer.tick()
                val startedAt = now
                val first = lights().single()
                assertEquals(1, first.eventCount)
                assertEquals(label.bounds.centerX, first.bounds.centerX, 1.0)
                assertFalse(first.contains(first.width / 2, first.height / 2))
                assertFalse(first.isFocusable)
                now += 200
                renderer.tick()
                assertTrue(first.bounds.centerX > label.bounds.centerX && first.bounds.centerX < 740)
                target = Point(700, 280)
                renderer.renderWindow(4, pane, config)
                assertSame(first, lights().single())
                assertEquals("+6 XP", label.accessibleContext.accessibleName)
                now += XP_EMISSION_DELAY_MS
                renderer.tick()
                assertEquals(2, lights().size)
                assertTrue(lights().all { it.eventCount == 1 })
                now = startedAt + XP_FLIGHT_MS
                renderer.tick()
                assertEquals(Point(700, 280), first.headLocation)
                assertTrue(label.isVisible)
                now += XP_IMPACT_MS
                renderer.tick()
                assertFalse(lights().contains(first))
                now += EffectOptions.from(config).timing.xp + XP_FLIGHT_MS + XP_IMPACT_MS
                renderer.tick()
                renderer.tick()
                assertEquals(0, pane.componentCount)
                assertFalse(renderer.isRunning)
            } finally {
                renderer.dispose()
            }
        }
    }

    @Test
    fun `continuous XP is coalesced into bounded flights without starving or changing the total`() {
        SwingUtilities.invokeAndWait {
            val pane = showingPane()
            var now = 1000L
            val renderer = XpGainEffectRenderer(clock = { now }, crestTarget = { XpAbsorptionTarget(Point(740, 300), 100.0) })
            val config = CodeXPConfiguration()
            val emitted = mutableSetOf<XpAbsorptionLight>()
            var maximum = 0
            try {
                renderer.renderWindow(2, pane, config)
                val label = pane.components.single()
                repeat(2000) {
                    now += 4
                    renderer.renderWindow(2, pane, config)
                    renderer.tick()
                    val lights = pane.components.filterIsInstance<XpAbsorptionLight>()
                    maximum = maxOf(maximum, lights.size)
                    emitted.addAll(lights)
                    assertTrue(lights.size <= XP_MAX_LIGHTS)
                }
                assertTrue(maximum > 1)
                assertTrue(emitted.size > 20)
                assertTrue(emitted.any { it.eventCount > 1 })
                now += XP_EMISSION_INTERVAL_MS
                renderer.tick()
                emitted.addAll(pane.components.filterIsInstance<XpAbsorptionLight>())
                assertEquals(2001, emitted.sumOf { it.eventCount })
                assertEquals(AchievementPainter.xpText(4002), label.accessibleContext.accessibleName)
                now += EffectOptions.from(config).timing.xp + XP_FLIGHT_MS + XP_IMPACT_MS
                renderer.tick()
                renderer.tick()
                assertEquals(0, pane.componentCount)
                assertFalse(renderer.isRunning)
            } finally {
                renderer.dispose()
            }
        }
    }

    @Test
    fun `hidden targets reduced motion and stalled frames do not queue a delayed XP burst`() {
        SwingUtilities.invokeAndWait {
            var showing = true
            val pane =
                object : JLayeredPane() {
                    override fun isShowing() = showing
                }.apply { setSize(800, 500) }
            var now = 1000L
            var target: Point? = null
            val renderer = XpGainEffectRenderer(clock = { now }, crestTarget = { target?.let { XpAbsorptionTarget(it, 100.0) } })
            val config = CodeXPConfiguration()

            fun lights() = pane.components.filterIsInstance<XpAbsorptionLight>()
            try {
                renderer.renderWindow(2, pane, config)
                target = Point(700, 280)
                now += XP_EMISSION_DELAY_MS
                renderer.tick()
                assertTrue(lights().isEmpty())
                renderer.renderWindow(2, pane, config)
                now += XP_EMISSION_DELAY_MS
                renderer.tick()
                assertEquals(1, lights().size)
                target = null
                now += 16
                renderer.tick()
                assertTrue(lights().isEmpty())
                renderer.renderWindow(2, pane, config)
                target = Point(10, 10)
                now += XP_EMISSION_INTERVAL_MS
                renderer.tick()
                assertTrue(lights().isEmpty())
                renderer.renderWindow(5, pane, config.copy(reducedMotion = true))
                now += XP_EMISSION_DELAY_MS
                renderer.tick()
                assertTrue(lights().isEmpty())
                renderer.clear()
                renderer.renderWindow(5, pane, config)
                now += 1000 // Drop stale visuals after an EDT stall instead of replaying them.
                renderer.tick()
                assertTrue(lights().isEmpty())
                renderer.renderWindow(5, pane, config)
                now += XP_EMISSION_DELAY_MS
                renderer.tick()
                assertEquals(1, lights().size)
                renderer.renderWindow(5, pane, config)
                showing = false
                renderer.tick()
                assertEquals(0, pane.componentCount)
                assertFalse(renderer.isRunning)
                showing = true
                renderer.renderWindow(5, pane, config)
                now += XP_EMISSION_INTERVAL_MS
                renderer.tick()
                assertEquals(1, lights().size)
            } finally {
                renderer.dispose()
            }
            assertEquals(0, pane.componentCount)
            assertFalse(renderer.isRunning)
        }
    }

    private fun showingPane(): JLayeredPane =
        object : JLayeredPane() {
            override fun isShowing() = true
        }.apply { setSize(800, 500) }

    @Test
    fun `fixed XP in an editorless window releases its timer when idle`() {
        val renderer = XpGainEffectRenderer()
        val pane =
            object : JLayeredPane() {
                override fun isShowing(): Boolean = true
            }.apply { setSize(800, 500) }
        SwingUtilities.invokeAndWait {
            renderer.renderWindow(10, pane, CodeXPConfiguration(reducedMotion = true))
            assertEquals(1, pane.componentCount)
            assertTrue(renderer.isRunning)
        }
        Thread.sleep(1600)
        SwingUtilities.invokeAndWait {
            assertEquals(0, pane.componentCount)
            assertFalse(renderer.isRunning)
            renderer.dispose()
        }
    }

    @Test
    fun `native painter produces theme and scale previews with static reduced motion`() {
        val out = File("build/reports/achievement-effects").apply { mkdirs() }
        for (dark in listOf(true, false)) {
            for (style in EffectStyle.entries) {
                val effect = AchievementEffect(null, LevelEffect(7, 8), EffectOptions(style, false))
                ImageIO.write(render(effect, 1400, dark, 520, 390), "png", File(out, "level-${style.storedValue}-${if (dark) "dark" else "light"}.png"))
                val reduced = effect.copy(options = effect.options.copy(reducedMotion = true))
                val first = render(reduced, 500, dark, 320, 300)
                val second = render(reduced, 1300, dark, 320, 300)
                assertArrayEquals(pixels(first), pixels(second))
                ImageIO.write(first, "png", File(out, "reduced-${style.storedValue}-${if (dark) "dark" else "light"}.png"))
                assertTrue(pixels(first).distinct().size > 100)
            }
        }
        val challenge = AchievementEffect(ChallengeEffect("visual", "Build Challenge", "Build 10 times.", 100), null, EffectOptions(EffectStyle.CELEBRATORY, false))
        ImageIO.write(render(challenge, 1300, true, 520, 390), "png", File(out, "challenge-dark.png"))
        val level = challenge.copy(challenge = null, level = LevelEffect(7, 8))
        val hidpi = render(level, 1400, false, 520, 390, 2)
        ImageIO.write(hidpi, "png", File(out, "level-hidpi.png"))
        assertEquals(1040, hidpi.width)
        val samples =
            (1..120)
                .map {
                    val start = System.nanoTime()
                    render(level, it * 16L, true, 520, 390)
                    (System.nanoTime() - start) / 1_000_000.0
                }.sorted()
        println("Native Java2D offscreen paint (includes image allocation), p50=${samples[60]}ms, p95=${samples[114]}ms")
    }

    private fun render(
        effect: AchievementEffect,
        age: Long,
        dark: Boolean,
        width: Int,
        height: Int,
        deviceScale: Int = 1,
    ): BufferedImage {
        val image = BufferedImage(width * deviceScale, height * deviceScale, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.scale(deviceScale.toDouble(), deviceScale.toDouble())
            g.color = if (dark) Color(31, 32, 36) else Color(250, 250, 252)
            g.fillRect(0, 0, width, height)
            AchievementPainter.paint(g, width, height, AchievementFrame(effect, age), dark)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun pixels(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

    private fun <T> proxy(
        type: Class<T>,
        call: (String) -> Any?,
    ): T =
        type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, args ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(instance)
                    "equals" -> instance === args?.get(0)
                    "toString" -> type.simpleName
                    else -> call(method.name)
                }
            },
        )
}
