package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestFamily
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionAnimationClock
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardPanel
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardAmbient
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardCrest
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardSnapshot
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.CardLayout
import java.awt.Container
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.event.WindowEvent
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.RepaintManager

class DashboardAmbientRegressionTest : BasePlatformTestCase() {
    fun testVisibleDashboardKeepsMovingAcrossFocusAndPausesWhenHidden() {
        val panel = CodeXPDashboardPanel { false }
        val cards = CardLayout()
        val host = JPanel(cards)
        val baseline = CollectionAnimationClock.subscriberCount
        // A headful run can also dispatch real AWT window events without showing a native window.
        val owner = if (GraphicsEnvironment.isHeadless()) null else UnshownFrame()
        try {
            panel.apply(DashboardSnapshot.from(CodeXPState()))
            host.add(JPanel(), "collection")
            owner?.add(host)
            if (owner == null) host.addNotify() else owner.addNotify()
            host.add(panel, "dashboard")
            cards.show(host, "dashboard")
            assertTrue(panel.isShowing)
            assertTrue("A visible dashboard must run without an active window", panel.isAnimationRunning)
            assertEquals(baseline + 1, CollectionAnimationClock.subscriberCount)
            panel.crest.setSize(240, 210)
            var time = System.nanoTime() / 1_000_000 + 100
            for (design in listOf(null, CrestDesign(CrestFamily.SEAL, 1))) {
                panel.crest.collectionDesign = design
                panel.tick(time)
                val before = panel.crest.motionFrame
                val image = render(panel.crest)
                owner?.signal(WindowEvent.WINDOW_DEACTIVATED)
                time += 1000
                panel.tick(time)
                assertEquals(before.seconds + 1.0, panel.crest.motionFrame.seconds, .000001)
                assertTrue("Inactive dashboard pixels should advance for $design", difference(image, render(panel.crest)) > 50)
                val after = panel.crest.motionFrame
                owner?.signal(WindowEvent.WINDOW_ACTIVATED)
                assertEquals(after, panel.crest.motionFrame)
                assertEquals(baseline + 1, CollectionAnimationClock.subscriberCount)
            }
            val last = panel.crest.motionFrame
            cards.show(host, "collection")
            panel.tick(time + 60_000)
            assertFalse(panel.isAnimationRunning)
            assertEquals(last, panel.crest.motionFrame)
            assertEquals(baseline, CollectionAnimationClock.subscriberCount)
            cards.show(host, "dashboard")
            assertTrue(panel.isAnimationRunning)
            assertEquals(last, panel.crest.motionFrame)
            if (owner != null) {
                assertFalse(owner.isVisible)
                assertFalse(owner.isActive)
                owner.reportedState = Frame.ICONIFIED
                owner.signal(WindowEvent.WINDOW_ICONIFIED)
                assertFalse(panel.isAnimationRunning)
                assertEquals(baseline, CollectionAnimationClock.subscriberCount)
                cards.show(host, "collection")
                cards.show(host, "dashboard")
                assertFalse("An iconified frame still reports its Swing components as showing", panel.isAnimationRunning)
                owner.reportedState = Frame.NORMAL
                owner.signal(WindowEvent.WINDOW_DEICONIFIED)
                assertTrue(panel.isAnimationRunning)
                assertEquals(last, panel.crest.motionFrame)
                owner.signal(WindowEvent.WINDOW_CLOSED)
                assertFalse(panel.isAnimationRunning)
                assertEquals(baseline, CollectionAnimationClock.subscriberCount)
                println("Verified focus, iconify, restore and close callbacks on an unshown AWT frame.")
            }
        } finally {
            panel.dispose()
            if (owner == null) host.removeNotify() else owner.dispose()
        }
        assertEquals(baseline, CollectionAnimationClock.subscriberCount)
    }

    private class UnshownFrame : Frame() {
        var reportedState = NORMAL

        init {
            focusableWindowState = false
            isAutoRequestFocus = false
        }

        override fun isShowing(): Boolean = isDisplayable

        override fun getExtendedState(): Int = reportedState

        fun signal(id: Int) = processWindowEvent(WindowEvent(this, id))
    }

    fun testEquippedCrestDoesNotJumpAcrossTheOldAmbientPeriodBoundary() {
        for (subtle in listOf(false, true)) {
            val crest =
                DashboardCrest().apply {
                    setSize(240, 210)
                    collectionDesign = CrestDesign(CrestFamily.SEAL, 1)
                }
            val ambient = DashboardAmbient(crest, { false })
            try {
                ambient.configure(subtle, false)
                ambient.visibilityChanged(true)
                val period = if (subtle) 42_000L else 28_000L
                val boundary = (System.nanoTime() / 1_000_000 / period + 1) * period
                ambient.tick(boundary - 24)
                val previous = render(crest)
                ambient.tick(boundary - 12)
                val before = render(crest)
                ambient.tick(boundary)
                val after = render(crest)
                val ordinary = difference(previous, before)
                val crossing = difference(before, after)
                assertTrue("period boundary jumps: subtle=$subtle ordinary=$ordinary crossing=$crossing", crossing <= maxOf(350, ordinary * 4))
            } finally {
                ambient.dispose()
            }
        }
    }

    fun testElapsedTimeCrossesBothOldPeriodsAndExcludesPausedTime() {
        var time = 10_000_000_000L
        val crest = DashboardCrest()
        val ambient = DashboardAmbient(crest, { false }, { time }, false)
        try {
            ambient.configure(false, false)
            ambient.visibilityChanged(true)
            for (milliseconds in listOf(27_988L, 28_000L, 28_012L, 41_988L, 42_000L, 42_012L)) {
                time = 10_000_000_000L + milliseconds * 1_000_000
                ambient.tick(time / 1_000_000)
                assertEquals(milliseconds / 1000.0, crest.motionFrame.seconds, .000001)
            }
            val pose = crest.motionFrame
            ambient.visibilityChanged(false)
            assertFalse(ambient.isRunning)
            time += 3_600_000_000_000
            ambient.tick(time / 1_000_000)
            assertEquals(pose, crest.motionFrame)
            ambient.visibilityChanged(true)
            assertEquals(pose, crest.motionFrame)
            time += 12_000_000
            ambient.tick(time / 1_000_000)
            assertEquals(pose.seconds + .012, crest.motionFrame.seconds, .000001)
        } finally {
            ambient.dispose()
        }
    }

    fun testSharedClockStopsForVisibilityReductionAndDisposal() {
        var time = 10_000_000_000L
        var osReduced = false
        var effectiveReduced = false
        val baseline = CollectionAnimationClock.subscriberCount
        val crest = DashboardCrest()
        val ambient = DashboardAmbient(crest, { osReduced }, { time }) { effectiveReduced = it }
        try {
            ambient.configure(false, false)
            ambient.visibilityChanged(true)
            assertEquals(baseline + 1, CollectionAnimationClock.subscriberCount)
            ambient.visibilityChanged(false)
            assertEquals(baseline, CollectionAnimationClock.subscriberCount)
            ambient.visibilityChanged(true)
            ambient.configure(true, true)
            assertTrue(crest.subtle)
            assertTrue(effectiveReduced)
            assertFalse(crest.motionFrame.animated)
            assertEquals(baseline, CollectionAnimationClock.subscriberCount)
            ambient.configure(true, false)
            assertFalse(effectiveReduced)
            assertTrue(ambient.isRunning)
            osReduced = true
            time += 2_100_000_000
            ambient.tick(time / 1_000_000)
            assertTrue(effectiveReduced)
            assertFalse(ambient.isRunning)
            assertFalse(crest.animationRunning)
            assertFalse(crest.motionFrame.animated)
            assertEquals(baseline, CollectionAnimationClock.subscriberCount)
            osReduced = false
            ambient.visibilityChanged(false)
            ambient.visibilityChanged(true)
            assertTrue(ambient.isRunning)
        } finally {
            ambient.dispose()
        }
        assertEquals(baseline, CollectionAnimationClock.subscriberCount)
        ambient.visibilityChanged(true)
        ambient.configure(false, false)
        assertFalse(ambient.isRunning)
        assertFalse(crest.animationRunning)
    }

    fun testMotionOnlyRepaintsCrestAndPreservesNumbersAndImmediateXp() {
        var time = 10_000_000_000L
        val panel = CodeXPDashboardPanel { false }
        val ambient = DashboardAmbient(panel.crest, { false }, { time }, false)
        val manager = RepaintManager.currentManager(panel)
        val dirty = mutableListOf<JComponent>()
        try {
            val snapshot = DashboardSnapshot.from(CodeXPState()).copy(xp = 925)
            panel.apply(snapshot)
            panel.setSize(280, 900)

            fun layout(container: Container) {
                container.doLayout()
                container.components.filterIsInstance<Container>().forEach(::layout)
            }
            repeat(4) { layout(panel) }
            val bounds = panel.crest.bounds
            panel.crest.collectionDesign = CrestDesign(CrestFamily.SEAL, 1)
            ambient.configure(false, false)
            ambient.visibilityChanged(true)
            val initial = render(panel.crest)
            RepaintManager.setCurrentManager(
                object : RepaintManager() {
                    override fun addDirtyRegion(
                        component: JComponent,
                        x: Int,
                        y: Int,
                        width: Int,
                        height: Int,
                    ) {
                        dirty.add(component)
                    }
                },
            )
            time += 1_000_000_000
            ambient.tick(time / 1_000_000)
            assertTrue(dirty.isNotEmpty())
            assertTrue(dirty.all { it === panel.crest })
            assertEquals(bounds, panel.crest.bounds)
            assertEquals("925", panel.totalXP.text)
            assertEquals(2, panel.crest.level.level)
            assertEquals(25.0 / 945, panel.xpBar.fraction, .000001)
            val moved = render(panel.crest)
            val scale = minOf(bounds.width / 240.0, bounds.height / 210.0)
            val safe = Ellipse2D.Double(bounds.width / 2.0 - 62 * scale, bounds.height / 2.0 - 47 * scale, 124 * scale, 128 * scale)
            for (y in 0 until moved.height) {
                for (x in 0 until moved.width) {
                    if (safe.contains(x.toDouble(), y.toDouble())) assertEquals(initial.getRGB(x, y), moved.getRGB(x, y))
                }
            }
            assertTrue(difference(initial, moved) > 50)
            panel.apply(snapshot.copy(xp = 1950))
            assertEquals("1,950", panel.totalXP.text)
            assertEquals(3, panel.crest.level.level)
            assertEquals(bounds, panel.crest.bounds)
        } finally {
            RepaintManager.setCurrentManager(manager)
            ambient.dispose()
            panel.dispose()
        }
    }

    private fun render(crest: DashboardCrest): BufferedImage =
        BufferedImage(crest.width, crest.height, BufferedImage.TYPE_INT_ARGB).also { image ->
            image.createGraphics().let { graphics ->
                crest.paint(graphics)
                graphics.dispose()
            }
        }

    private fun difference(
        first: BufferedImage,
        second: BufferedImage,
    ): Int = (0 until first.height).sumOf { y -> (0 until first.width).count { x -> first.getRGB(x, y) != second.getRGB(x, y) } }
}
