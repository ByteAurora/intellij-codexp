package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.utils.StringUtil
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Native vector rendering. Logical scaling preserves Java2D's existing HiDPI device transform. */
internal object AchievementPainter {
    fun paint(
        g: Graphics2D,
        width: Int,
        height: Int,
        frame: AchievementFrame,
        dark: Boolean,
        uiScale: Double = 1.0,
    ) {
        val options = frame.effect.options
        val scale = min(uiScale, min(width / (if (options.subtle) 340.0 else 500.0), height / 390.0)).coerceAtLeast(.01)
        prepare(g)
        g.translate(width / 2.0, height / 2.0)
        g.scale(scale, scale)
        // Balance the complete motif + labels around the surface centre.
        val originY = if (frame.challengeAge != null) (if (options.subtle) -44.0 else -32.0) else (if (options.subtle) -10.0 else -4.0)
        g.translate(0.0, originY)
        val ink = Ink(g, dark, scale, width / scale)
        frame.challengeAge?.let { challenge(ink, frame.effect, it) }
        frame.levelAge?.let { level(ink, frame.effect, it) }
    }

    private fun level(
        p: Ink,
        effect: AchievementEffect,
        age: Long,
    ) {
        val level = effect.level ?: return
        val quiet = effect.options.subtle
        val reduced = effect.options.reducedMotion
        p.g.composite = AlphaComposite.SrcOver.derive(frameAlpha(age, effect.options.timing.level, reduced))
        p.seal(if (quiet) 76.0 else 108.0)
        val enter = if (reduced) 1.0 else ease((age - 370) / 600.0)
        if (quiet || reduced) {
            val radius = if (quiet) 54.0 else 72.0
            p.ring(0.0, 0.0, radius * .88 * if (reduced) 1.0 else ease(age / 440.0), p.gold, .55)
            p.diamond(0.0, 0.0, radius, p.gold, .85)
            if (!reduced) for (side in SIDES) p.line(side * 75.0, 0.0, side * 135.0, 0.0, p.gold, .6)
            p.text(CodeXPBundle.message("effects.level.up"), 0.0, -88.0, 11.0, p.gold, tracked = true)
            p.text(CodeXPBundle.message("effects.level.transition", level.from, level.to), 0.0, 90.0, 12.0, p.muted)
        } else {
            p.glow(0.0, 0.0, 180.0, p.gold, .17 * enter)
            p.glow(0.0, -8.0, 115.0, p.blue, .09 * enter)
            val wave = unit((age - 540) / 810.0)
            if (wave > 0 && wave < 1) p.ring(0.0, 0.0, 66 + 119 * ease(wave), p.gold, (1 - wave) * .7)
            val wing = ease((age - 420) / 600.0)
            for (side in SIDES) {
                for (i in 0..2) p.line(side * 66.0, -8.0 + i * 8, side * (80 + 165 * wing), -8.0 + i * 8 + (if (i == 0) -24 else 18) * wing, p.gold, .6 * enter)
                p.diamond(side * 146.0 * wing, -2.0, 5.0, p.gold, .65 * enter)
                p.trace(side, 58.0, ease((age - 240) / 720.0), .55 * enter)
                p.trace(side, -58.0, ease((age - 400) / 720.0), .4 * enter)
            }
            p.motif(0.0, 0.0, 107 * (.68 + .32 * ease((age - 240) / 680.0)), enter)
            p.diamond(0.0, 0.0, 85.0, p.gold, .85 * enter)
            p.diamond(0.0, 0.0, 76.0, p.gold, .3 * enter)
            p.diamond(0.0, 0.0, 64.0, p.blue, .35 * enter)
            p.burst(age - 530, 36, 220.0)
            p.burst(age - 950, 16, 165.0)
            p.text(CodeXPBundle.message("effects.level.up"), 0.0, -139.0, 12.0, p.gold, alpha = ease((age - 540) / 400.0), tracked = true)
            p.text(CodeXPBundle.message("effects.level.transition", level.from, level.to), 0.0, 137.0, 12.0, p.muted, alpha = ease((age - 900) / 400.0))
            TOKENS.forEachIndexed { i, token ->
                val u = unit((age - 620 - i * 65) / 1500.0)
                if (u > 0 && u < 1) p.text(token, (if (i % 2 == 0) -1 else 1) * (130 + 65 * ease(u)), (if (i < 2) -1 else 1) * (55.0 + i % 3 * 22) - 14 * u, 12.0, p.code, Font.MONOSPACED, sin(u * PI) * .65, backing = false)
            }
        }
        paintLevelNumbers(p.g, level, effect.options, age, p.text)
        val caption = if (quiet) "level.next()" else "developer.level(${level.from}) -> ${level.to}"
        p.text(caption, 0.0, if (quiet || reduced) 118.0 else 164.0, 11.0, p.code, Font.MONOSPACED, if (reduced) 1.0 else ease((age - 1050) / 420.0))
    }

    private fun challenge(
        p: Ink,
        effect: AchievementEffect,
        age: Long,
    ) {
        val challenge = effect.challenge ?: return
        val quiet = effect.options.subtle
        val reduced = effect.options.reducedMotion
        p.g.composite = AlphaComposite.SrcOver.derive(frameAlpha(age, effect.options.timing.challenge, reduced))
        p.seal(if (quiet) 72.0 else 100.0)
        val grow = if (reduced) 1.0 else ease(age / 480.0)
        val radius = if (quiet) 34.0 else 49.0
        if (!quiet && !reduced) {
            p.glow(0.0, -13.0, 130.0, p.gold, .12)
            p.motif(0.0, -13.0, radius * 1.44 * grow, .8)
            p.burst(age - 430, 16, 130.0)
            for (side in SIDES) {
                p.line(side * 67.0, -13.0, side * (70 + 85 * ease((age - 300) / 500.0)), -13.0, p.gold, .8)
                p.trace(side, 24.0, ease((age - 180) / 650.0), .45)
            }
        }
        p.diamond(0.0, -13.0, radius * grow, p.gold, .9)
        p.diamond(0.0, -13.0, radius * .8, p.gold, .3)
        val check = if (reduced) 1.0 else ease((age - 280) / 340.0)
        p.line(-12.0, -12.0, -12 + 9 * min(1.0, check / .38), -12 + 9 * min(1.0, check / .38), p.gold, 1.0, 2.4)
        if (check > .38) p.line(-3.0, -3.0, -3 + 20 * (check - .38) / .62, -3 - 23 * (check - .38) / .62, p.gold, 1.0, 2.4)
        p.text(CodeXPBundle.message("effects.challenge.complete"), 0.0, if (quiet) 43.0 else 59.0, 11.0, p.gold, tracked = true)
        val name = if (challenge.count == 1) challenge.name else CodeXPBundle.message("effects.challenge.multiple", challenge.count)
        p.text(name, 0.0, if (quiet) 67.0 else 85.0, if (quiet) 16.0 else 21.0, p.text)
        if (challenge.count == 1) p.text(challenge.description, 0.0, if (quiet) 89.0 else 110.0, 11.0, p.muted)
        p.text(xpText(challenge.reward), 0.0, if (quiet) 114.0 else 139.0, if (quiet) 14.0 else 17.0, p.gold, alpha = if (reduced) 1.0 else ease((age - 700) / 350.0))
        p.text("achievement.complete()", 0.0, if (quiet) 140.0 else 166.0, 11.0, p.code, Font.MONOSPACED, if (reduced) 1.0 else ease((age - 760) / 350.0))
    }

    fun paintLevelNumbers(
        g: Graphics2D,
        level: LevelEffect,
        options: EffectOptions,
        age: Long,
        color: Color,
    ) {
        prepare(g)
        val width = if (options.subtle) 92.0 else 132.0
        val height = if (options.subtle) 64.0 else 88.0
        val font = Font(Font.SERIF, Font.PLAIN, 1).deriveFont(if (options.subtle) 49f else 68f)
        val inkWidth =
            maxOf(
                font.createGlyphVector(g.fontRenderContext, level.from.toString()).visualBounds.width,
                font.createGlyphVector(g.fontRenderContext, level.to.toString()).visualBounds.width,
            )
        val fitted = font.deriveFont((font.size2D * min(1.0, (width - 4) / inkWidth.coerceAtLeast(1.0))).toFloat())
        for (number in levelNumbers(level, options, age)) {
            val digit = g.create() as Graphics2D
            try {
                prepare(digit)
                digit.clip(Rectangle2D.Double(-width / 2, -height / 2, width, height))
                digit.translate(0.0, number.offset * height * .72)
                digit.scale(number.scale, number.scale)
                multiplyAlpha(digit, number.alpha.toFloat())
                digit.color = color
                digit.font = fitted
                val label = number.value.toString()
                val ink = fitted.createGlyphVector(digit.fontRenderContext, label).visualBounds
                digit.drawString(label, (-ink.centerX).toFloat(), (-ink.centerY).toFloat())
            } finally {
                digit.dispose()
            }
        }
    }

    fun paintXp(
        g: Graphics2D,
        layout: XpTextLayout,
        cluster: XpCluster,
        now: Long,
        dark: Boolean,
    ) {
        prepare(g)
        multiplyAlpha(g, cluster.alpha(now) * .76f)
        g.font = layout.font
        g.color =
            if (cluster.options.subtle) {
                if (dark) Color(136, 198, 255) else Color(47, 111, 165)
            } else {
                if (dark) Color(241, 208, 141) else Color(155, 93, 16)
            }
        // Only glyphs: no backing, glow, token, underline, or action count over code.
        g.drawString(layout.text, layout.baselineX, layout.baselineY)
    }

    fun xpText(value: Long): String = CodeXPBundle.message("effects.xp", StringUtil.numberToStringWithCommas(value))

    private fun prepare(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }

    private fun multiplyAlpha(
        g: Graphics2D,
        alpha: Float,
    ) {
        val inherited = (g.composite as? AlphaComposite)?.alpha ?: 1f
        g.composite = AlphaComposite.SrcOver.derive((inherited * alpha).coerceIn(0f, 1f))
    }

    private val SIDES = intArrayOf(-1, 1)
    private val TOKENS = arrayOf("</>", "++", "=>", "::", "[]", "&&")

    private class Ink(
        val g: Graphics2D,
        dark: Boolean,
        val scale: Double,
        val availableWidth: Double,
    ) {
        val gold = if (dark) Color(241, 208, 141) else Color(155, 93, 16)
        val blue = if (dark) Color(136, 198, 255) else Color(47, 111, 165)
        val text = if (dark) Color(244, 235, 215) else Color(83, 57, 25)
        val muted = if (dark) Color(190, 192, 204) else Color(102, 100, 100)
        val base = if (dark) Color(26, 29, 37) else Color(251, 248, 239)
        val code = if (dark) Color(128, 220, 197) else Color(25, 113, 99)

        fun color(
            c: Color,
            alpha: Double,
        ): Color = Color(c.red, c.green, c.blue, (unit(alpha) * 255).toInt())

        fun line(
            x1: Double,
            y1: Double,
            x2: Double,
            y2: Double,
            c: Color,
            alpha: Double,
            width: Double = 1.0,
        ) {
            g.color = color(c, alpha)
            g.stroke = BasicStroke(width.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(Line2D.Double(x1, y1, x2, y2))
        }

        fun ring(
            x: Double,
            y: Double,
            r: Double,
            c: Color,
            alpha: Double,
        ) {
            g.color = color(c, alpha)
            g.stroke = BasicStroke(.8f)
            g.draw(Ellipse2D.Double(x - r, y - r, r * 2, r * 2))
        }

        fun diamond(
            x: Double,
            y: Double,
            r: Double,
            c: Color,
            alpha: Double,
        ) {
            g.color = color(c, alpha)
            g.stroke = BasicStroke(1f)
            val path = Path2D.Double()
            path.moveTo(x, y - r)
            path.lineTo(x + r, y)
            path.lineTo(x, y + r)
            path.lineTo(x - r, y)
            path.closePath()
            g.draw(path)
        }

        fun glow(
            x: Double,
            y: Double,
            r: Double,
            c: Color,
            alpha: Double,
        ) {
            g.paint = RadialGradientPaint(Point2D.Double(x, y), r.toFloat(), floatArrayOf(0f, .4f, 1f), arrayOf(color(c, alpha), color(c, alpha * .35), color(c, 0.0)))
            g.fill(Rectangle2D.Double(x - r, y - r, r * 2, r * 2))
        }

        fun seal(r: Double) {
            g.paint = RadialGradientPaint(Point2D.Double(0.0, 0.0), (r * 1.3).toFloat(), floatArrayOf(0f, .7f, 1f), arrayOf(color(base, .96), color(base, .9), color(base, 0.0)))
            g.fill(Rectangle2D.Double(-r * 1.3, -r * 1.3, r * 2.6, r * 2.6))
        }

        fun motif(
            x: Double,
            y: Double,
            r: Double,
            alpha: Double,
        ) {
            ring(x, y, r, gold, .5 * alpha)
            ring(x, y, r * .91, gold, .6 * alpha)
            repeat(12) { i ->
                val theta = i * PI / 6
                val inner = r * if (i % 3 == 0) .82 else .93
                val outer = r * if (i % 3 == 0) 1.1 else 1.02
                line(x + cos(theta) * inner, y + sin(theta) * inner, x + cos(theta) * outer, y + sin(theta) * outer, gold, alpha * .7)
                if (i % 3 == 0) diamond(x + cos(theta) * r * 1.16, y + sin(theta) * r * 1.16, 3.5, gold, alpha * .7)
            }
        }

        fun burst(
            age: Long,
            count: Int,
            range: Double,
        ) {
            if (age < 0 || age > 1900) return
            repeat(count) { i ->
                val life = age / (900.0 + i % 7 * 120)
                if (life < 1) {
                    val angle = i * 2.39996 + .7
                    val radius = (16 + range * (.4 + (i % 9) / 14.0)) * ease(life)
                    val x = cos(angle) * radius
                    val y = sin(angle) * radius * .73 - 14 * life
                    val length = (2 + i % 5) * (1 - life)
                    line(x, y, x + cos(angle) * length, y + sin(angle) * length, if (i % 4 == 0) blue else gold, sin(life * PI) * .65)
                }
            }
        }

        fun trace(
            side: Int,
            y: Double,
            progress: Double,
            alpha: Double,
        ) {
            val points = listOf(Point2D.Double(side * 225.0, y), Point2D.Double(side * 192.0, y), Point2D.Double(side * 169.0, y * .6), Point2D.Double(side * 143.0, y * .6))
            var remaining = points.zipWithNext().sumOf { (a, b) -> a.distance(b) } * progress
            for (i in 0..2) {
                val a = points[i]
                val b = points[i + 1]
                val fraction = min(1.0, remaining / a.distance(b))
                if (fraction <= 0) break
                line(a.x, a.y, a.x + (b.x - a.x) * fraction, a.y + (b.y - a.y) * fraction, code, alpha)
                remaining -= a.distance(b)
            }
        }

        fun text(
            value: String,
            x: Double,
            y: Double,
            size: Double,
            c: Color,
            family: String = Font.SANS_SERIF,
            alpha: Double = 1.0,
            tracked: Boolean = false,
            backing: Boolean = true,
        ) {
            g.font = Font(family, if (size > 15 && family != Font.SERIF) Font.BOLD else Font.PLAIN, 1).deriveFont(maxOf(size, 11 / scale).toFloat())
            val metrics = g.fontMetrics
            val limit = (availableWidth - 24 / scale).coerceAtLeast(1.0)
            var label = if (tracked) value.toCharArray().joinToString(" ") else value
            while (metrics.stringWidth(label) > limit && label.length > 1) label = label.dropLast(if (label.endsWith("…")) 2 else 1) + "…"
            val width = metrics.stringWidth(label).toDouble()
            if (backing && size <= 24) {
                g.color = color(base, .94 * alpha)
                g.fill(Rectangle2D.Double(x - width / 2 - 4, y - metrics.height / 2.0, width + 8, metrics.height.toDouble()))
            }
            g.color = color(c, alpha)
            g.drawString(label, (x - width / 2).toFloat(), (y + (metrics.ascent - metrics.descent) / 2.0).toFloat())
        }
    }
}
