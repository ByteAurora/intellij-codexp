package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestFamily
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionMotionFrame
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.MiniCrestSurface
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.MiniCrestVisibility
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.image.BufferedImage

class MiniCrestVisibilityTest : BasePlatformTestCase() {
    fun testTypingNearCrestDimsWithoutDisablingDraggingOrRestartingTheFade() {
        var now = 0L
        var showing = false
        var interactive = false
        val surface = MiniCrestSurface().apply { setSize(164, 164) }
        val fade =
            MiniCrestVisibility({ opacity, visible, acceptsInput ->
                showing = visible
                interactive = acceptsInput
                surface.visibilityAlpha = opacity
                surface.interactive = acceptsInput
            }, { now })
        try {
            fade.setVisible(true, true)
            fade.setVisible(true, false, dimmed = true)
            assertTrue(showing)
            assertTrue(interactive)
            for (step in 1..100) {
                now = step * 10_000_000L
                fade.setVisible(true, false, dimmed = true)
                fade.advance(now)
                assertTrue(fade.opacity >= .32f)
                assertTrue(showing)
                assertTrue(surface.contains(82, 82))
            }
            assertEquals(.32f, fade.opacity)
            assertFalse(fade.isRunning)
            fade.setVisible(true, false)
            assertTrue(interactive)
            now += 240_000_000
            fade.advance(now)
            assertEquals(1f, fade.opacity)
            assertFalse(fade.dimmed)
            assertTrue(showing)
        } finally {
            fade.dispose()
        }
    }

    fun testCaretDimmingStillHonorsExplicitHideAndReducedMotion() {
        var showing = false
        val fade = MiniCrestVisibility({ _, visible, _ -> showing = visible })
        try {
            fade.setVisible(true, true, dimmed = true)
            assertEquals(.32f, fade.opacity)
            assertTrue(showing)
            assertFalse(fade.isRunning)
            fade.setVisible(false, true, dimmed = true)
            assertEquals(0f, fade.opacity)
            assertFalse(showing)
            assertFalse(fade.dimmed)
            fade.setVisible(true, true)
            assertEquals(1f, fade.opacity)
        } finally {
            fade.dispose()
        }
    }

    fun testFadeEndpointsAndRepeatedVisibilityDoNotRestartAnimation() {
        var now = 0L
        var showing = false
        var interactive = false
        val fade =
            MiniCrestVisibility({ _, visible, acceptsInput ->
                showing = visible
                interactive = acceptsInput
            }, { now })
        try {
            fade.setVisible(true, false)
            assertTrue(showing)
            assertTrue(interactive)
            assertEquals(0f, fade.opacity)
            assertTrue(fade.isRunning)
            for (step in 1..24) {
                now = step * 10_000_000L
                fade.setVisible(true, false)
                fade.advance(now)
            }
            assertEquals(1f, fade.opacity)
            assertFalse(fade.isRunning)
            fade.setVisible(false, false)
            assertTrue(showing)
            assertFalse(interactive)
            now += 100_000_000
            fade.advance(now)
            assertEquals(.5f, fade.opacity, .001f)
            assertTrue(showing)
            now += 100_000_000
            fade.advance(now)
            assertFalse(showing)
            assertEquals(0f, fade.opacity)
            assertFalse(fade.isRunning)
        } finally {
            fade.dispose()
        }
    }

    fun testReversalContinuesAtCurrentOpacityAndReducedMotionSettlesImmediately() {
        var now = 0L
        val fade = MiniCrestVisibility({ _, _, _ -> }, { now })
        try {
            fade.setVisible(true, false)
            now = 120_000_000
            fade.advance(now)
            val half = fade.opacity
            assertEquals(.5f, half, .001f)
            fade.setVisible(false, false)
            assertEquals(half, fade.opacity)
            now += 50_000_000
            fade.advance(now)
            assertTrue(fade.opacity < half && fade.opacity > 0f)
            val interrupted = fade.opacity
            fade.setVisible(true, false)
            assertEquals(interrupted, fade.opacity)
            fade.setVisible(true, true)
            assertEquals(1f, fade.opacity)
            assertFalse(fade.isRunning)
            fade.setVisible(false, true)
            assertEquals(0f, fade.opacity)
            assertFalse(fade.isRunning)
        } finally {
            fade.dispose()
        }
    }

    fun testDetachAndDisposalStopTheTransitionAndReleaseInput() {
        var showing = false
        val fade = MiniCrestVisibility({ _, visible, _ -> showing = visible }, { 0L })
        fade.setVisible(true, false)
        fade.reset()
        fade.advance(1L)
        assertFalse(showing)
        assertFalse(fade.isRunning)
        fade.setVisible(true, false)
        fade.dispose()
        fade.setVisible(true, false)
        fade.advance(Long.MAX_VALUE)
        assertFalse(showing)
        assertFalse(fade.isRunning)
    }

    fun testOpacityIncludesLevelAndAnimatedArtworkAtRetinaScaleWithoutBlockingInput() {
        val surface =
            MiniCrestSurface().apply {
                setSize(164, 164)
                design = CrestDesign(CrestFamily.entries.first(), 1)
                level = 150
                frame = CollectionMotionFrame(1.5, .3, true)
            }

        fun alphaSum(opacity: Float): Long {
            surface.visibilityAlpha = opacity
            val image = BufferedImage(328, 328, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            try {
                g.scale(2.0, 2.0)
                surface.paint(g)
            } finally {
                g.dispose()
            }
            return (0 until 328).sumOf { y -> (0 until 328).sumOf { x -> (image.getRGB(x, y) ushr 24).toLong() } }
        }
        val full = alphaSum(1f)
        val half = alphaSum(.5f)
        assertTrue(full > 0)
        assertTrue(half.toDouble() / full in 0.45..0.65)
        assertEquals(0L, alphaSum(0f))
        assertTrue(surface.contains(82, 82))
        surface.interactive = false
        assertFalse(surface.contains(82, 82))
        assertFalse(surface.control.isEnabled)
        surface.doLayout()
        assertFalse(surface.control.contains(82, 82))
    }
}
