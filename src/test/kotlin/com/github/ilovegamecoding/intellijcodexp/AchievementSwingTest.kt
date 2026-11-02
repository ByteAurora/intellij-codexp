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
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpGainEffectRenderer
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
import javax.swing.SwingUtilities

class AchievementSwingTest {
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
            val renderer = XpGainEffectRenderer()
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
            renderer.release(editor)
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
