package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardListMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Rectangle
import javax.swing.SwingUtilities

class DashboardListMotionTest {
    @Test fun `cadence accelerates only during movement then returns to highlights and stops`() =
        onEdt {
            var now = 0L
            val motion = DashboardListMotion(100, { now }) {}
            val view = Rectangle(0, 0, 300, 500)
            try {
                motion.update(listOf("a", "b"), emptySet(), view)
                motion.visibilityChanged(true)
                assertFalse(motion.isRunning)
                motion.update(listOf("b", "a"), setOf("b"), view)
                assertTrue(motion.isRunning)
                assertEquals(33, motion.frameDelayMs)
                assertEquals(33, motion.firstFrameDelayMs)
                now = 160
                motion.tick(now)
                assertEquals(16, motion.frameDelayMs)
                assertEquals(16, motion.firstFrameDelayMs)
                now = 430
                motion.tick(now)
                assertFalse(motion.hasMovement)
                assertTrue(motion.isRunning)
                assertEquals(33, motion.frameDelayMs)
                assertEquals(33, motion.firstFrameDelayMs)
                now = 621
                motion.tick(now)
                assertFalse(motion.isRunning)
                now = 700
                motion.update(listOf("b", "a"), setOf("b"), view)
                assertEquals(33, motion.firstFrameDelayMs)
                motion.update(listOf("a", "b"), setOf("a"), view)
                now = 860
                motion.tick(now)
                assertEquals(16, motion.frameDelayMs)
                motion.configure(false, true)
                assertFalse(motion.hasMovement)
                assertEquals(0.0, motion.y("a"), .0)
                assertEquals(33, motion.frameDelayMs)
                motion.visibilityChanged(false)
                assertFalse(motion.isRunning)
                motion.visibilityChanged(true)
                assertFalse(motion.isRunning)
            } finally {
                motion.dispose()
                assertFalse(motion.isRunning)
            }
        }

    @Test fun `movement duration and easing depend on elapsed time not callback cadence`() =
        onEdt {
            val halfway = mutableListOf<Double>()
            for (cadence in listOf(16, 33, 97)) {
                var now = 0L
                val motion = DashboardListMotion(100, { now }) {}
                val view = Rectangle(0, 0, 300, 500)
                try {
                    motion.update(listOf("a", "b"), emptySet(), view)
                    motion.visibilityChanged(true)
                    motion.update(listOf("b", "a"), setOf("b"), view)
                    now = 160
                    motion.tick(now)
                    for (elapsed in cadence until 135 step cadence) {
                        now = 160 + elapsed.toLong()
                        motion.tick(now)
                    }
                    now = 295
                    motion.tick(now)
                    halfway += motion.y("b")
                    assertEquals(12.5, motion.y("b"), .001)
                    now = 429
                    motion.tick(now)
                    assertTrue(motion.hasMovement)
                    now = 430
                    motion.tick(now)
                    assertFalse(motion.hasMovement)
                    assertEquals(0.0, motion.y("b"), .0)
                } finally {
                    motion.dispose()
                }
            }
            assertEquals(1, halfway.toSet().size)
        }

    @Test fun `burst uses a bounded deadline and retains only the latest target`() =
        onEdt {
            var now = 0L
            val motion = DashboardListMotion(100, { now }) {}
            val view = Rectangle(0, 0, 300, 500)
            try {
                motion.update(listOf("typing", "save", "build"), emptySet(), view)
                motion.visibilityChanged(true)
                repeat(140) { index ->
                    now = index * 5L
                    val order = if (index % 2 == 0) listOf("build", "typing", "save") else listOf("save", "build", "typing")
                    motion.update(order, setOf(order.first()), view)
                    motion.tick(now)
                    assertEquals(order, motion.order)
                    assertTrue(motion.highlight(order.first()) <= .46f)
                    if (now >= 200) assertTrue(motion.movementStarts > 0)
                }
                val target = motion.order
                now += 161
                motion.tick(now)
                now += 271
                motion.tick(now)
                target.forEachIndexed { index, id -> assertEquals(index * 100.0, motion.y(id), .01) }
                assertTrue(motion.movementStarts in 1..6)
                now += 700
                motion.tick(now)
                assertFalse(motion.isRunning)
                assertFalse(motion.hasMovement)
            } finally {
                motion.dispose()
            }
        }

    @Test fun `already first never moves and repeated typing does not restart its pulse`() =
        onEdt {
            var now = 0L
            val motion = DashboardListMotion(100, { now }) {}
            val order = listOf("typing", "save", "build")
            val view = Rectangle(0, 0, 300, 500)
            try {
                motion.update(order, emptySet(), view)
                motion.visibilityChanged(true)
                repeat(150) {
                    now = it * 5L
                    motion.update(order, setOf("typing"), view)
                    motion.tick(now)
                    if (now >= 120) assertEquals(.46f, motion.highlight("typing"), .001f)
                    assertEquals(0f, motion.highlight("save"), .0f)
                }
                assertEquals(0, motion.movementStarts)
                now += 621
                motion.tick(now)
                assertFalse(motion.isRunning)
            } finally {
                motion.dispose()
            }
        }

    @Test fun `hidden reopen reduced and dispose discard old presentation work`() =
        onEdt {
            var now = 0L
            val motion = DashboardListMotion(100, { now }) {}
            val view = Rectangle(0, 0, 300, 500)
            motion.update(listOf("a", "b"), emptySet(), view)
            motion.visibilityChanged(true)
            motion.update(listOf("b", "a"), setOf("b"), view)
            motion.visibilityChanged(false)
            assertFalse(motion.isRunning)
            assertEquals(0.0, motion.y("b"), .0)
            motion.visibilityChanged(true)
            assertFalse(motion.isRunning)
            assertEquals(0f, motion.highlight("b"), .0f)
            motion.configure(true, true)
            motion.update(listOf("a", "b"), setOf("a"), view)
            assertFalse(motion.hasMovement)
            val static = motion.highlight("a")
            now = 300
            motion.tick(now)
            assertEquals(static, motion.highlight("a"), .0f)
            now = 601
            motion.tick(now)
            assertFalse(motion.isRunning)
            motion.dispose()
            motion.update(listOf("b", "a"), setOf("b"), view)
            assertFalse(motion.isRunning)
        }

    private fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)
}
