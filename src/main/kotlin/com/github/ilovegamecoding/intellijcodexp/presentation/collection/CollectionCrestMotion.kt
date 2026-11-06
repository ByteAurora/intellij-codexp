package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestFamily
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.smoothArtwork
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.geom.Arc2D
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun crestColors(
    design: CrestDesign,
    dark: Boolean,
): Pair<Color, Color> {
    val palette =
        when (design.family) {
            CrestFamily.SEAL -> 0xDEC18A to 0x75BCAA
            CrestFamily.WINGS -> 0xE8C89B to 0xD8906F
            CrestFamily.WAVE -> 0x6FCBC9 to 0x93A9E0
            CrestFamily.CRYSTAL -> 0xB29AE7 to 0x69C9D5
            CrestFamily.ORBIT -> 0xAABFEC to 0xDABB79
            CrestFamily.SHIELD -> 0x7DBDD4 to 0x9FB8D6
            CrestFamily.PETALS -> 0xD69BAF to 0x9DBB87
            CrestFamily.FAN -> 0xDE9C67 to 0xD3C18E
            CrestFamily.ARCH -> 0x8EB99C to 0xC3B076
            CrestFamily.GEAR -> 0xAAB9C6 to 0xC38A59
        }

    fun ink(value: Int): Color = Color(value).let { if (dark) it else it.darker() }
    return ink(palette.first) to ink(palette.second)
}

/** Two independently moving motifs per family; form changes geometry, rhythm and direction. */
internal object CollectionCrestMotion {
    private val rim =
        Area(Rectangle2D.Double(-120.0, -105.0, 240.0, 210.0)).apply {
            subtract(Area(Ellipse2D.Double(-64.0, -49.0, 128.0, 132.0)))
        }
    private val teeth =
        (1..3).map { form ->
            Path2D.Double().apply {
                repeat(40 + form * 8) { i ->
                    val t = i * PI * 2 / (40 + form * 8)
                    val r = if (i % 4 < 2) 100.0 else 89.0
                    if (i == 0) moveTo(cos(t) * r, sin(t) * r) else lineTo(cos(t) * r, sin(t) * r)
                }
                closePath()
            }
        }

    fun paint(
        graphics: Graphics2D,
        design: CrestDesign?,
        title: String?,
        frame: CollectionMotionFrame,
        subtle: Boolean,
        compact: Boolean = false,
    ) {
        val g = graphics.create() as Graphics2D
        try {
            g.smoothArtwork()
            if (design == null) {
                if (title != null) title(g, title, frame, subtle)
                return
            }
            if (!compact) g.clip(rim)
            val brush = Brush(g, design, frame, subtle)
            when (design.family) {
                CrestFamily.SEAL -> brush.engraving()
                CrestFamily.WINGS -> brush.feathers()
                CrestFamily.WAVE -> brush.ripples()
                CrestFamily.CRYSTAL -> brush.facets()
                CrestFamily.ORBIT -> brush.satellites()
                CrestFamily.SHIELD -> brush.barrier()
                CrestFamily.PETALS -> brush.bloom()
                CrestFamily.FAN -> brush.unfold()
                CrestFamily.ARCH -> brush.gateway()
                CrestFamily.GEAR -> brush.gears()
            }
        } finally {
            g.dispose()
        }
    }

    private fun title(
        g: Graphics2D,
        name: String,
        frame: CollectionMotionFrame,
        subtle: Boolean,
    ) {
        val phase = frame.seconds * .7 + (name.hashCode() and 15) * .2
        val span = 62 + 8 * sin(phase)
        val opacity = if (subtle) .32 else .6
        g.color = color(DashboardPalette.gold, opacity + frame.emphasis * .2)
        g.stroke = BasicStroke(1.1f)
        g.draw(Line2D.Double(-span, 26.0, span, 26.0))
        val x = sin(phase * .67) * span
        g.color = color(DashboardPalette.teal, opacity)
        g.fill(Ellipse2D.Double(x - 2, 23.5, 5.0, 5.0))
        for (side in listOf(-1, 1)) {
            val offset = 4 * sin(phase + side)
            g.draw(Arc2D.Double(side * 82.0 - 12, -19 + offset, 24.0, 44.0, if (side < 0) 90.0 else -90.0, 180.0, Arc2D.OPEN))
        }
    }

    private fun color(
        c: Color,
        alpha: Double,
    ) = Color(c.red, c.green, c.blue, (alpha.coerceIn(0.0, 1.0) * 255).toInt())

    private class Brush(
        val g: Graphics2D,
        design: CrestDesign,
        frame: CollectionMotionFrame,
        subtle: Boolean,
    ) {
        val form = design.form.coerceIn(1, 3)
        val t = frame.seconds * (if (subtle) .78 else 1.0) * (1.0 + form * .13) + form * .71
        val intensity = (if (subtle) .60 else .88) + frame.emphasis * .16
        private val strokeGain = if (subtle) 1.35 else 1.65
        val accent = frame.emphasis
        private val dark = DashboardPalette.background.red < 128
        private val colors = crestColors(design, dark)
        private val a = colors.first
        private val b = colors.second

        private fun stroke(
            shape: Shape,
            ink: Color = a,
            alpha: Double = 1.0,
            width: Double = 1.2,
        ) {
            g.color = color(ink, intensity * alpha)
            g.stroke = BasicStroke((width * strokeGain).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(shape)
        }

        private fun line(
            x: Double,
            y: Double,
            xx: Double,
            yy: Double,
            ink: Color = a,
            alpha: Double = 1.0,
        ) = stroke(Line2D.Double(x, y, xx, yy), ink, alpha)

        private fun dot(
            x: Double,
            y: Double,
            r: Double = 2.0,
            ink: Color = b,
            alpha: Double = 1.0,
        ) {
            g.color = color(ink, intensity * alpha * .24)
            g.fill(Ellipse2D.Double(x - r * 2.5, y - r * 2.5, r * 5, r * 5))
            g.color = color(ink, intensity * alpha)
            g.fill(Ellipse2D.Double(x - r, y - r, r * 2, r * 2))
        }

        private fun polygon(
            points: List<Pair<Double, Double>>,
            ink: Color = a,
            alpha: Double = .6,
        ) {
            val path = Path2D.Double()
            points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
            path.closePath()
            g.color = color(ink, intensity * alpha * .3)
            g.fill(path)
            stroke(path, ink, alpha)
        }

        // Travelling engraving strokes + independent expanding afterglow rings.
        fun engraving() {
            repeat(form) { layer ->
                val r = 73.0 + layer * 9
                val phase = t * (if (layer % 2 == 0) .55 else -.39) + layer * PI
                stroke(Arc2D.Double(-r, -r, r * 2, r * 2, phase * 180 / PI, 48.0 + form * 20, Arc2D.OPEN))
                dot(cos(-phase) * r, sin(-phase) * r, 3.5)
                stroke(Arc2D.Double(-r, -r, r * 2, r * 2, phase * 180 / PI + 18, 32.0 + form * 8, Arc2D.OPEN), b, .35, 2.4)
            }
            val pulse = .5 + .5 * sin(t * .8)
            val r = 85 + pulse * (10 + form * 3) + accent * 3
            stroke(Ellipse2D.Double(-r, -r, r * 2, r * 2), b, .35 + .4 * (1 - pulse), 1.9)
            repeat(4) { i ->
                val angle = PI / 4 + i * PI / 2
                val x = cos(angle) * (77 + pulse * 15)
                val y = sin(angle) * (77 + pulse * 15)
                line(x - 6, y, x + 6, y, b, .8)
            }
        }

        // Articulated feather opening + delayed feather trails, independently phased per form.
        fun feathers() {
            for (side in listOf(-1.0, 1.0)) {
                repeat(3 + form) { i ->
                    val open = .5 + .5 * sin(t * .6 - i * .3 * form)
                    val x = side * (84 + i * 3 + open * (8 + form * 2)).coerceAtMost(112.0)
                    val y = -38.0 - i * 9 - open * 13
                    stroke(
                        Path2D.Double().apply {
                            moveTo(side * 64, 43.0 - i * 8)
                            quadTo(side * (92 + open * 13), 7.0 - i * 7, x, y)
                        },
                        a,
                        .45 + open * .4,
                        1.4,
                    )
                    val drift = .5 + .5 * sin(t * .9 - i * .45 - 1.4)
                    line(x + side * (3 + drift * 5), y + 8, x + side * (1 + drift * 5), y + 24 + drift * 15, b, .35 + .4 * (1 - drift))
                }
            }
        }

        // Expanding elliptical ripples + counter-flowing refraction curves.
        fun ripples() {
            repeat(2 + form) { i ->
                val phase = (t * .19 + i.toDouble() / (2 + form)) % 1
                val r = 64 + phase * 44
                stroke(Ellipse2D.Double(-r, -r * .82, r * 2, r * 1.64), if (i % 2 == 0) a else b, sin(phase * PI) * .95)
            }
            for (side in listOf(-1.0, 1.0)) {
                stroke(
                    Path2D.Double().apply {
                        val offset = sin(t + side * form) * 20
                        moveTo(-88.0, side * 58 + offset)
                        curveTo(-42.0, side * 95 - offset, 30.0, side * 41 + offset, 87.0, side * 64 - offset)
                    },
                    b,
                    .9,
                    2.3,
                )
            }
        }

        // Facet reflections chase around the crystal + detached shards float on different paths.
        fun facets() {
            repeat(6) { i ->
                val angle = -PI / 2 + i * PI / 3
                val shine = .3 + .7 * (.5 + .5 * sin(t * .85 - i * .9))
                polygon(listOf(cos(angle) * 65 to sin(angle) * 65, cos(angle) * 91 to sin(angle) * 91, cos(angle + .6) * 87 to sin(angle + .6) * 87), if (i % 2 == 0) a else b, shine)
            }
            repeat(2 + form * 2) { i ->
                val angle = i * PI * 2 / (2 + form * 2) + sin(t * .35 + i) * .16
                val r = 96 + sin(t * .7 + i * form) * 9
                val x = cos(angle) * r
                val y = sin(angle) * r * .92
                polygon(listOf(x - 4 to y, x to y - 8, x + 4 to y, x to y + 8), b, .9)
            }
        }

        // Inclined orbital bodies + long meteor trails, with counter-orbits in advanced forms.
        fun satellites() {
            repeat(form + 1) { i ->
                val before = g.transform
                g.rotate(-.5 + i * .83)
                val phase = t * (if (i % 2 == 0) .65 else -.48) + i * 2
                val rx = 108.0 - i * 6
                val ry = 56.0 + i * 3
                stroke(Arc2D.Double(-rx, -ry, rx * 2, ry * 2, -phase * 180 / PI, 52.0 + form * 16, Arc2D.OPEN), if (i % 2 == 0) a else b, .65, 1.8)
                dot(cos(phase) * rx, sin(phase) * ry, 3.8 + accent, a)
                repeat(3) { trail ->
                    val delayed = phase - (trail + 1) * .09 * if (i % 2 == 0) 1 else -1
                    dot(cos(delayed) * rx, sin(delayed) * ry, 2.8 - trail * .5, b, .55 - trail * .15)
                }
                g.transform = before
            }
        }

        // Shield barrier scans in a different axis per form + a broad, slow impact wave.
        fun barrier() {
            val sweep = sin(t * .55) * 86
            val before = g.transform
            if (form == 2) g.rotate(.25)
            if (form == 3) g.rotate(-.25)
            for (i in 0..2) line(-77.0, sweep + i * 6, 77.0, sweep + i * 6, a, .9 - i * .2)
            g.transform = before
            val pulse = .5 + .5 * sin(t * .7 + form)
            val r = 78 + pulse * 24
            stroke(Ellipse2D.Double(-r, -r * .95, r * 2, r * 1.9), b, .25 + .55 * (1 - pulse), 2.4)
            for (side in listOf(-1.0, 1.0)) dot(side * 84, sin(t * .6 + side) * 67, 3.8)
        }

        // Petal opening changes the actual outline + small petals circulate between blossoms.
        fun bloom() {
            repeat(6 + form * 2) { i ->
                val before = g.transform
                g.rotate(i * PI * 2 / (6 + form * 2))
                val opening = .5 + .5 * sin(t * .6 - i * .2 * form)
                stroke(
                    Path2D.Double().apply {
                        moveTo(-12.0, -70.0)
                        curveTo(-22 - opening * 19, -91.0, -8.0, -101.0, 0.0, -97 - opening * 7)
                        curveTo(14.0, -99.0, 24 + opening * 13, -86.0, 12.0, -70.0)
                    },
                    a,
                    .7,
                )
                g.transform = before
            }
            repeat(form + 2) { i ->
                val angle = t * .28 * (if (form == 2) -1 else 1) + i * PI * 2 / (form + 2)
                val x = cos(angle) * 104
                val y = sin(angle) * 96
                polygon(listOf(x - 6 to y, x to y - 4, x + 6 to y, x to y + 4), b, .95)
            }
        }

        // Staggered fan ribs unfold + a separate scanning glint follows the scalloped edge.
        fun unfold() {
            val ribs = 5 + form * 2
            repeat(ribs + 1) { i ->
                val spread = .78 + .20 * sin(t * .55 - i * .18 * form)
                val angle = (-.72 + i * 1.44 / ribs) * spread
                line(0.0, 68.0, sin(angle) * 163, 65 - cos(angle) * 163, if (i % 2 == 0) a else b, .65)
            }
            val angle = sin(t * .65) * .7
            val x = sin(angle) * 163
            val y = 65 - cos(angle) * 163
            dot(x, y, 4.4 + accent)
            stroke(Arc2D.Double(-163.0, -98.0, 326.0, 326.0, 90 - angle * 180 / PI - 9, 25.0 + form * 6, Arc2D.OPEN), b, .85, 2.2)
        }

        // Staggered contour lights climb the arch + light streams travel through the inner pillars.
        fun gateway() {
            repeat(10 + form * 2) { i ->
                val angle = PI + i * PI / (9 + form * 2)
                val glow = .25 + .6 * (.5 + .5 * sin(t * .8 - i * .55))
                dot(cos(angle) * 85, -18 + sin(angle) * 78, 2.8 + form * .4, a, glow)
            }
            for (side in listOf(-1.0, 1.0)) {
                repeat(form + 1) { i ->
                    val phase = (t * .18 * (if (form == 2) 1.3 else 1.0) + i.toDouble() / (form + 1) + (side + 1) * .13) % 1
                    val y = 78 - phase * 120
                    line(side * 79, y, side * 79, y - 29, b, sin(phase * PI))
                    line(side * 88, y + 8, side * 88, y - 11, a, sin(phase * PI) * .7)
                }
            }
        }

        // Counter-rotating toothed rings + tangential sparks; form changes tooth count and gearing.
        fun gears() {
            val before = g.transform
            g.rotate(t * (if (form == 2) -.16 else .12))
            stroke(teeth[form - 1], a, .95, 2.0)
            g.transform = before
            repeat(6 + form) { i ->
                val angle = i * PI * 2 / (6 + form) - t * .24
                line(cos(angle) * 74, sin(angle) * 74, cos(angle + .06) * 87, sin(angle + .06) * 87, b, .8)
            }
            repeat(form + 2) { i ->
                val phase = (t * .3 + i.toDouble() / (form + 2)) % 1
                val angle = -.5 + i * PI * .7 + phase * .15
                val r = 87 + phase * 16
                line(cos(angle) * r, sin(angle) * r, cos(angle + .12) * (r + 3), sin(angle + .12) * (r + 3), b, sin(phase * PI) * .8)
            }
        }
    }
}
