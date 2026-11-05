package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionMotionFrame
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardCrest
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardLevel
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.crestNumberLayout
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.dashboardLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.ceil

class DashboardGeometryTest {
    @Test fun `remaining XP reaches the engine boundary at variable thresholds`() {
        for (level in listOf(1, 2, 3, 9, 12, 58, 59, 99, 100, 149)) {
            var amount = 300.0
            var total = 0.0
            repeat(level) {
                total += amount
                amount = (amount * 1.05).coerceAtMost(5000.0)
            }
            val boundary = ceil(total).toLong() * 3
            val before = dashboardLevel(boundary - 1)
            assertEquals(level, before.level)
            assertEquals(1L, before.remaining)
            assertTrue(before.fraction < 1)
            assertEquals(level + 1, CodeXPLevel.createLevelInfo(boundary).level)
            assertEquals(before.required, before.into + before.remaining)
            if (level >= 59) assertEquals(15000L, before.required)
        }
    }

    @Test fun `glyph ink fits and stays centered through three four and maximal digits`() {
        for (scale in listOf(1.0, 1.5, 2.0, 3.0)) {
            val context = FontRenderContext(AffineTransform.getScaleInstance(scale, scale), true, true)
            for (level in listOf(9, 99, 100, 999, 1000, 9999, Int.MAX_VALUE)) {
                val fit = crestNumberLayout(level.toString(), context)
                assertTrue(Rectangle2D.Double(-62.01, -13.01, 124.02, 64.02).contains(fit.ink))
                assertEquals(0.0, fit.ink.centerX, .001)
                assertEquals(19.0, fit.ink.centerY, .001)
                assertTrue(fit.font.size2D > 0)
            }
        }
        assertEquals(150, CodeXPLevel.createLevelInfo(Long.MAX_VALUE).level)
    }

    @Test fun `ambient changes decoration without moving the level number`() {
        val crest = DashboardCrest()
        crest.setSize(240, 210)
        crest.level = DashboardLevel(9999, 1, 10, 9)

        fun render(phase: Double): BufferedImage {
            crest.motionFrame = CollectionMotionFrame(phase * 28, 0.0, true)
            return BufferedImage(240, 210, BufferedImage.TYPE_INT_ARGB).also {
                val g = it.createGraphics()
                crest.paint(g)
                g.dispose()
            }
        }
        val first = render(0.0)
        val second = render(.25)
        var changed = 0
        for (y in 0 until 210) {
            for (x in 0 until 240) {
                if (x in 58..182 && y in 92..156) assertEquals(first.getRGB(x, y), second.getRGB(x, y))
                if (first.getRGB(x, y) != second.getRGB(x, y)) changed++
            }
        }
        assertTrue(changed > 10)
        assertEquals(DashboardLevel(9999, 1, 10, 9), crest.level)
    }
}
