package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestFamily
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.crestNumberLayout
import com.github.ilovegamecoding.intellijcodexp.presentation.smoothArtwork
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Arc2D
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Vector geometry ported from the approved acquisition study; no per-card timers or raster assets. */
internal object CollectionCrestPainter {
    private data class StaticKey(
        val design: CrestDesign?,
        val level: Int,
        val locked: Boolean,
        val title: String?,
        val dark: Boolean,
        val scale: Double,
        val compact: Boolean,
    )

    private val staticArt = LinkedHashMap<StaticKey, BufferedImage>(64, .75f, true)
    private var cachedPixels = 0

    private val impactContours =
        object : LinkedHashMap<CrestDesign, Shape>(32, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CrestDesign, Shape>?): Boolean = size > 32
        }

    /** Reuse the metalwork geometry, joining overlapping plates only once per design. */
    fun impactOutline(design: CrestDesign): Shape =
        synchronized(impactContours) {
            impactContours.getOrPut(design) {
                val contours = ImpactContours()
                val g = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
                try {
                    Art(g, design, contours).draw()
                } finally {
                    g.dispose()
                }
                Path2D.Double(contours.solid).apply { append(contours.lines, false) }
            }
        }

    private class ImpactContours {
        val solid = Area()
        val lines = Path2D.Double()

        fun add(
            shape: Shape,
            filled: Boolean = true,
        ) {
            if (filled) solid.add(Area(shape)) else lines.append(shape, false)
        }
    }

    /** Fit the equipped crest, including its level typography, to a component-local graphics copy. */
    fun paintEquipped(
        g: Graphics2D,
        width: Int,
        height: Int,
        design: CrestDesign,
        level: Int,
        frame: CollectionMotionFrame,
        subtle: Boolean,
    ) {
        val scale = minOf(width / 240.0, height / 210.0)
        if (scale <= 0) return
        g.translate(width / 2.0, height / 2.0)
        g.scale(scale, scale)
        paintAnimated(g, design, level, locked = false, frame = frame, subtle = subtle, title = null)
    }

    /** Rasterize detailed metalwork at the device scale; only light motion is live. */
    fun paintAnimated(
        g: Graphics2D,
        design: CrestDesign?,
        level: Int,
        locked: Boolean,
        frame: CollectionMotionFrame,
        subtle: Boolean,
        title: String?,
        compact: Boolean = false,
    ) {
        val scale = (ceil(hypot(g.transform.scaleX, g.transform.shearX) * 4) / 4).coerceIn(.5, 4.0)
        val key = StaticKey(design, if (compact) 0 else level, locked, title, DashboardPalette.background.red < 128, scale, compact)
        val image =
            synchronized(staticArt) {
                staticArt
                    .getOrPut(key) {
                        val bitmap = BufferedImage((240 * scale).toInt(), ceil(210 * scale).toInt(), BufferedImage.TYPE_INT_ARGB_PRE)
                        val copy = bitmap.createGraphics()
                        try {
                            copy.scale(bitmap.width / 240.0, bitmap.height / 210.0)
                            copy.translate(120.0, 105.0)
                            paint(copy, design, level, locked, title = title, compact = compact)
                        } finally {
                            copy.dispose()
                        }
                        cachedPixels += bitmap.width * bitmap.height
                        bitmap
                    }.also {
                        while (cachedPixels > 8_000_000 && staticArt.size > 1) {
                            val first = staticArt.entries.iterator()
                            val old = first.next().value
                            cachedPixels -= old.width * old.height
                            first.remove()
                        }
                    }
            }
        val layer = g.create() as Graphics2D
        try {
            layer.smoothArtwork()
            layer.drawImage(image, -120, -105, 240, 210, null)
            if (!locked && frame.animated) CollectionCrestMotion.paint(layer, design, title, frame, subtle, compact)
        } finally {
            layer.dispose()
        }
    }

    private data class Key(
        val design: CrestDesign,
        val dark: Boolean,
    )

    private val silhouettes =
        object : LinkedHashMap<Key, BufferedImage>(64, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, BufferedImage>?): Boolean = size > 64
        }

    fun paint(
        g: Graphics2D,
        design: CrestDesign?,
        level: Int,
        locked: Boolean,
        phase: Double = 0.0,
        subtle: Boolean = false,
        title: String? = null,
        compact: Boolean = false,
    ) {
        g.smoothArtwork()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        if (locked) {
            if (design != null) {
                val key = Key(design, DashboardPalette.background.red < 128)
                val image = synchronized(silhouettes) { silhouettes.getOrPut(key) { silhouette(design) } }
                g.composite = AlphaComposite.SrcOver.derive(.65f)
                g.drawImage(image, -120, -105, null)
            } else {
                g.composite = AlphaComposite.SrcOver.derive(.18f)
                g.color = DashboardPalette.gold
                g.fillRoundRect(-70, -20, 140, 14, 12, 12)
                g.fillRoundRect(-48, 7, 96, 14, 12, 12)
            }
            g.composite = AlphaComposite.SrcOver
            drawNumber(g, "?", false)
            return
        }
        if (design != null) {
            val painter = Art(g, design)
            painter.draw()
            if (phase > 0.0) CollectionCrestMotion.paint(g, design, title, CollectionMotionFrame(phase * 5, .5, true), subtle, compact)
            if (!compact) {
                val base = DashboardPalette.background
                g.paint = RadialGradientPaint(Point2D.Float(0f, 12f), 77f, floatArrayOf(0f, .7f, 1f), arrayOf(alpha(base, .98), alpha(base, .95), alpha(base, 0.0)))
                g.fill(Rectangle2D.Double(-78.0, -66.0, 156.0, 156.0))
                drawNumber(g, level.toString(), true)
            }
        } else if (title != null) {
            g.color = DashboardPalette.gold
            var font = Font(Font.SANS_SERIF, Font.PLAIN, 25)
            while (font.size > 10 && g.getFontMetrics(font).stringWidth(title) > 210) font = font.deriveFont(font.size2D - 1)
            g.font = font
            g.drawString(title, -g.fontMetrics.stringWidth(title) / 2, 8)
        }
    }

    private fun drawNumber(
        g: Graphics2D,
        text: String,
        label: Boolean,
    ) {
        g.composite = AlphaComposite.SrcOver
        g.color = if (label) DashboardPalette.gold else DashboardPalette.muted
        if (label) {
            g.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            val caption = CodeXPBundle.message("dashboard.crest.tracked.caption")
            g.drawString(caption, -g.fontMetrics.stringWidth(caption) / 2, -32)
        }
        val fit = crestNumberLayout(text, g.fontRenderContext)
        // Only level numbers sit below a caption; center the unlabelled lock glyph in the art.
        val baseline = if (label) fit.baseline else fit.baseline - fit.ink.centerY.toFloat()
        g.drawGlyphVector(fit.font.createGlyphVector(g.fontRenderContext, text), fit.x, baseline)
    }

    private fun silhouette(design: CrestDesign): BufferedImage {
        val image = BufferedImage(240, 210, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.smoothArtwork()
            g.translate(120.0, 105.0)
            Art(g, design).draw()
        } finally {
            g.dispose()
        }
        val weights = floatArrayOf(1f, 4f, 7f, 10f, 13f, 10f, 7f, 4f, 1f).map { it / 57f }.toFloatArray()
        val horizontal = ConvolveOp(Kernel(9, 1, weights), ConvolveOp.EDGE_NO_OP, null).filter(image, null)
        return ConvolveOp(Kernel(1, 9, weights), ConvolveOp.EDGE_NO_OP, null).filter(horizontal, null)
    }

    private fun alpha(
        color: Color,
        opacity: Double,
    ): Color = Color(color.red, color.green, color.blue, (opacity.coerceIn(0.0, 1.0) * 255).toInt())

    private class Art(
        val g: Graphics2D,
        val design: CrestDesign,
        val contours: ImpactContours? = null,
    ) {
        private val stage = design.form.coerceIn(1, 3)
        private val dark = DashboardPalette.background.red < 128
        private val colors =
            when (design.family) {
                CrestFamily.SEAL -> if (dark) 0xE0C18B to 0x83CBB9 else 0x89651E to 0x237A70
                CrestFamily.WINGS -> if (dark) 0xF0D19D to 0xDE9A76 else 0x9C6D27 to 0x9E5737
                CrestFamily.WAVE -> if (dark) 0x85D3CA to 0x9DB3E2 else 0x1E7575 to 0x4A68A1
                CrestFamily.CRYSTAL -> if (dark) 0xB8ACED to 0x82D4E1 else 0x6F4FAA to 0x1F7B91
                CrestFamily.ORBIT -> if (dark) 0xBFCCE7 to 0xDDBE84 else 0x4F6591 to 0x916B27
                CrestFamily.SHIELD -> if (dark) 0x8BC5D6 to 0xB1C5D5 else 0x30708B to 0x4E6F8F
                CrestFamily.PETALS -> if (dark) 0xDFA4B7 to 0xACBE93 else 0x9E4D71 to 0x5D7736
                CrestFamily.FAN -> if (dark) 0xE5AD77 to 0xDFCEA3 else 0xA3652D to 0x93742F
                CrestFamily.ARCH -> if (dark) 0xACCAB4 to 0xCEC193 else 0x40704F to 0x907138
                CrestFamily.GEAR -> if (dark) 0xBEBCB6 to 0xC9976A else 0x666560 to 0x925B30
            }
        val a = Color(colors.first)
        val b = Color(colors.second)

        fun draw() {
            when (design.family) {
                CrestFamily.SEAL -> seal()
                CrestFamily.WINGS -> wings()
                CrestFamily.WAVE -> wave()
                CrestFamily.CRYSTAL -> crystal()
                CrestFamily.ORBIT -> orbit()
                CrestFamily.SHIELD -> shield()
                CrestFamily.PETALS -> petals()
                CrestFamily.FAN -> fan()
                CrestFamily.ARCH -> arch()
                CrestFamily.GEAR -> gear()
            }
        }

        private fun stroke(
            shape: Shape,
            color: Color = a,
            opacity: Double = .65,
            width: Double = .8,
            impactEdge: Boolean = false,
            filledEdge: Boolean = true,
        ) {
            if (contours != null) {
                if (impactEdge) contours.add(g.transform.createTransformedShape(shape), filledEdge)
                return
            }
            g.composite = AlphaComposite.SrcOver
            g.color = alpha(color, opacity)
            g.stroke = BasicStroke(width.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(shape)
        }

        private fun line(
            x: Double,
            y: Double,
            xx: Double,
            yy: Double,
            color: Color = a,
            opacity: Double = .65,
            width: Double = .8,
        ) = stroke(Line2D.Double(x, y, xx, yy), color, opacity, width)

        private fun ring(
            radius: Double,
            color: Color = a,
            opacity: Double = .6,
            width: Double = .8,
            impactEdge: Boolean = false,
            filledEdge: Boolean = true,
        ) = stroke(Ellipse2D.Double(-radius, -radius, radius * 2, radius * 2), color, opacity, width, impactEdge, filledEdge)

        private fun poly(
            points: List<Pair<Double, Double>>,
            color: Color = a,
            fill: Double = .12,
            opacity: Double = .7,
            width: Double = .8,
        ) {
            val p = Path2D.Double()
            points.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            p.closePath()
            if (contours != null) {
                contours.add(g.transform.createTransformedShape(p))
                return
            }
            g.color = alpha(color, fill)
            g.fill(p)
            stroke(p, color, opacity, width)
        }

        private fun diamond(
            x: Double,
            y: Double,
            radius: Double,
            color: Color = a,
            opacity: Double = .8,
        ) = poly(listOf(x to y - radius, x + radius to y, x to y + radius, x - radius to y), color, .2, opacity)

        private fun metal(
            path: Path2D.Double,
            strength: Double = .4,
        ) {
            if (contours != null) {
                contours.add(g.transform.createTransformedShape(path))
                return
            }
            g.paint = LinearGradientPaint(-90f, -90f, 90f, 90f, floatArrayOf(0f, .35f, .57f, .8f, 1f), arrayOf(alpha(a, strength * .3), alpha(a, strength), alpha(b, strength * .45), alpha(a, strength * .8), alpha(a, strength * .2)))
            g.fill(path)
            stroke(path)
        }

        private fun seal() {
            ring(86.0, impactEdge = true)
            ring(76.5, a, .38)
            diamond(0.0, 0.0, 74.0, a, .65)
            diamond(0.0, 0.0, 63.5, a, .33)
            repeat(12) { i ->
                val t = i * PI / 6
                val r = if (i % 3 == 0) 100.0 else 92.0
                line(cos(t) * 87, sin(t) * 87, cos(t) * r, sin(t) * r)
            }
            if (stage >= 2) {
                repeat(4) { i ->
                    val t = i * PI / 2
                    diamond(cos(t) * 96, sin(t) * 96, 4.0)
                }
                ring(92.0, a, .2, impactEdge = true)
                repeat(8) { i ->
                    val t = (i + .5) * PI / 4
                    diamond(cos(t) * 81, sin(t) * 81, 2.5, b)
                }
            }
            if (stage >= 3) {
                for (s in listOf(-1, 1)) {
                    repeat(5) { i ->
                        val y = 38.0 - i * 17
                        val x = s * (94 - abs(y) * .08)
                        metal(
                            Path2D.Double().apply {
                                moveTo(x, y + 9)
                                quadTo(x + s * 17, y + 2, x + s * 8, y - 11)
                                quadTo(x - s * 2, y - 4, x, y + 9)
                                closePath()
                            },
                        )
                    }
                }
                diamond(0.0, -94.0, 7.0)
                diamond(0.0, 94.0, 6.0, b)
            }
        }

        private fun wings() {
            for (s in listOf(-1.0, 1.0)) {
                for (i in stage + 1 downTo 0) {
                    val sx = s * (48 + i * 3)
                    val sy = 43.0 - i * 8
                    val tx = s * (84 + i * 7)
                    val ty = -43.0 - i * 11
                    metal(
                        Path2D.Double().apply {
                            moveTo(sx, sy)
                            curveTo(s * (65 + i * 6), sy - 29, tx, ty + 21, tx, ty)
                            curveTo(s * (102 + i * 3), ty + 47, s * (77 + i * 3), sy + 9, sx, sy)
                            closePath()
                        },
                    )
                    line(sx + s * 4, sy - 3, tx - s * 3, ty + 13, a, .4)
                }
            }
            poly(listOf(-54.0 to -48.0, 0.0 to -69.0, 54.0 to -48.0, 65.0 to 23.0, 0.0 to 80.0, -65.0 to 23.0), fill = .05)
            if (stage >= 2) {
                poly(listOf(-28.0 to -72.0, -33.0 to -88.0, -13.0 to -80.0, 0.0 to -99.0, 13.0 to -80.0, 33.0 to -88.0, 28.0 to -72.0))
                diamond(0.0, -83.0, 5.0, b)
            }
            if (stage >= 3) {
                for (s in listOf(-1.0, 1.0)) poly(listOf(s * 40 to 55.0, s * 28 to 80.0, s * 12 to 88.0, s * 21 to 59.0))
                diamond(0.0, 88.0, 5.0)
            }
        }

        private fun wave() {
            repeat(stage + 1) { i ->
                val transform = g.transform
                g.scale(if (i % 2 == 0) 1.0 else -1.0, 1.0)
                val o = i / 2 * 10.0
                metal(
                    Path2D.Double().apply {
                        moveTo(-54.0, -67 + o)
                        curveTo(-105.0, -48 - o, -101.0, 25 + o, -46.0, 66.0)
                        curveTo(-7.0, 97 + o, 67.0, 72 + o, 88.0, 24.0)
                        curveTo(68.0, 58.0, 9.0, 66.0, -30.0, 39.0)
                        curveTo(-70.0, 12.0, -80.0, -30.0, -54.0, -67 + o)
                        closePath()
                    },
                    .32 - i * .025,
                )
                g.transform = transform
            }
            stroke(Ellipse2D.Double(-72.0, -66.0, 144.0, 132.0), impactEdge = true)
            drop(0.0, -81.0, if (stage == 3) 10.0 else 7.0)
            if (stage >= 2) {
                drop(-82.0, 29.0, 5.0)
                drop(78.0, -28.0, 5.0)
            }
            if (stage >= 3) {
                for (s in listOf(-1.0, 1.0)) {
                    stroke(
                        Path2D.Double().apply {
                            moveTo(s * 12, -78.0)
                            curveTo(s * 34, -101.0, s * 66, -94.0, s * 68, -70.0)
                        },
                        b,
                    )
                }
                drop(0.0, 89.0, 5.0)
            }
        }

        private fun drop(
            x: Double,
            y: Double,
            r: Double,
        ) = metal(
            Path2D.Double().apply {
                moveTo(x, y - r * 1.4)
                curveTo(x + r * 1.4, y, x + r, y + r, x, y + r)
                curveTo(x - r, y + r, x - r * 1.4, y, x, y - r * 1.4)
                closePath()
            },
            .65,
        )

        private fun crystal() {
            val outer = if (stage == 1) 86.0 else 92.0
            repeat(6) { i ->
                val t = -PI / 2 + i * PI / 3
                val u = t + PI / 3
                poly(listOf(cos(t) * 62 to sin(t) * 62, cos(t) * outer to sin(t) * outer, cos(u) * outer to sin(u) * outer, cos(u) * 62 to sin(u) * 62), if (i % 2 == 0) a else b, .24 + i % 3 * .11)
                if (stage >= 2) {
                    val x = cos(t) * 97
                    val y = sin(t) * 97
                    poly(listOf(x - sin(t) * 7 to y + cos(t) * 7, cos(t) * 106 to sin(t) * 106, x + sin(t) * 7 to y - cos(t) * 7, cos(t) * 83 to sin(t) * 83))
                }
            }
            if (stage >= 3) {
                for (s in listOf(-1.0, 1.0)) {
                    repeat(2) { i ->
                        val x = s * (103 + i * 7)
                        val y = -24.0 + i * 47
                        poly(listOf(x to y - 12, x + s * 6 to y, x to y + 12, x - s * 7 to y), b)
                    }
                }
            }
        }

        private fun orbit() {
            repeat(stage) { i ->
                val transform = g.transform
                g.rotate(listOf(-.48, .64, 1.45)[i])
                val rx = listOf(109.0, 98.0, 90.0)[i]
                val ry = listOf(55.0, 57.0, 47.0)[i]
                stroke(Ellipse2D.Double(-rx, -ry, rx * 2, ry * 2), if (i % 2 == 0) a else b, impactEdge = true, filledEdge = false)
                if (stage == 3) stroke(Arc2D.Double(-rx + 4, -ry + 3, (rx - 4) * 2, (ry - 3) * 2, 36.0, 162.0, Arc2D.OPEN), a, .3)
                g.transform = transform
            }
            ring(68.0, a, .35, impactEdge = true, filledEdge = false)
            val points = listOf(-83.0 to -43.0, 87.0 to 42.0, 44.0 to -80.0, -43.0 to 82.0, 91.0 to -21.0, -96.0 to 26.0)
            points.take(stage * 2).forEachIndexed { i, (x, y) ->
                diamond(x, y, if (i == 0) 7.0 else 4.0)
                if (i > 0 && stage == 3) line(x, y, points[i - 1].first, points[i - 1].second, a, .16)
            }
            if (stage >= 2) diamond(0.0, -84.0, 7.0, b)
        }

        private fun shield() {
            poly(listOf(-83.0 to -83.0, 0.0 to -95.0, 83.0 to -83.0, 88.0 to 23.0, 55.0 to 72.0, 0.0 to 103.0, -55.0 to 72.0, -88.0 to 23.0), fill = .16, width = 1.1)
            poly(listOf(-68.0 to -68.0, 0.0 to -79.0, 68.0 to -68.0, 70.0 to 19.0, 42.0 to 58.0, 0.0 to 84.0, -42.0 to 58.0, -70.0 to 19.0), b, .04)
            if (stage >= 2) {
                for (s in listOf(-1.0, 1.0)) {
                    poly(listOf(s * 75 to -65.0, s * 95 to -50.0, s * 97 to 17.0, s * 63 to 62.0, s * 71 to 14.0), fill = .3)
                    for (y in -45 until 30 step 27) diamond(s * 84, y.toDouble(), 3.0, b)
                }
            }
            if (stage >= 3) {
                for (i in -2..2) diamond(i * 24.0, -83.0 + abs(i) * 3, 3.0, b)
                line(-48.0, 75.0, 0.0, 104.0, width = 2.0)
                line(48.0, 75.0, 0.0, 104.0, width = 2.0)
            }
        }

        private fun petals() {
            for (layer in (if (stage >= 2) 1 else 0) downTo 0) {
                repeat(8) { i ->
                    val transform = g.transform
                    g.rotate(i * PI / 4 + layer * PI / 8)
                    metal(
                        Path2D.Double().apply {
                            moveTo(-16.0, -62.0)
                            curveTo(-43.0, -90.0, -20.0, -104.0, 0.0, -103.0)
                            curveTo(25.0, -105.0, 42.0, -88.0, 16.0, -62.0)
                            closePath()
                        },
                        if (layer == 1) .2 else .45,
                    )
                    g.transform = transform
                }
            }
            ring(72.0, impactEdge = true)
            if (stage >= 3) for (s in listOf(-1.0, 1.0)) poly(listOf(s * 60 to 62.0, s * 91 to 72.0, s * 62 to 94.0, s * 48 to 72.0), b, .25)
        }

        private fun fan() {
            fun point(t: Double) = sin(t) * 163 to 65 - cos(t) * 163
            val n = 5 + stage * 2
            repeat(n) { i -> poly(listOf(0.0 to 68.0, point(-.72 + i * 1.44 / n), point(-.72 + (i + 1) * 1.44 / n)), if (i % 2 == 0) a else b, .13 + i % 2 * .12) }
            diamond(0.0, 77.0, 7.0)
            if (stage >= 2) {
                stroke(
                    Path2D.Double().apply {
                        repeat(20) { i ->
                            val (x, y) = point(-.72 + i * 1.44 / 19)
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    },
                    a,
                    .8,
                    2.0,
                )
            }
            if (stage >= 3) {
                repeat(7) { i ->
                    val (x, y) = point(-.7 + i * 1.4 / 6)
                    diamond(x, y, 3.0)
                }
            }
        }

        private fun arch() {
            metal(
                Path2D.Double().apply {
                    moveTo(-96.0, 84.0)
                    lineTo(-96.0, -20.0)
                    curveTo(-96.0, -119.0, 96.0, -119.0, 96.0, -20.0)
                    lineTo(96.0, 84.0)
                    lineTo(74.0, 84.0)
                    lineTo(74.0, -20.0)
                    curveTo(74.0, -89.0, -74.0, -89.0, -74.0, -20.0)
                    lineTo(-74.0, 84.0)
                    closePath()
                },
                .35,
            )
            for (s in listOf(-1.0, 1.0)) {
                line(s * 85, -29.0, s * 85, 75.0, b)
                if (stage >= 2) {
                    poly(listOf(s * 68 to -34.0, s * 103 to -34.0, s * 103 to -23.0, s * 68 to -23.0))
                    for (y in -5 until 70 step 22) line(s * 75, y.toDouble(), s * 95, y.toDouble())
                }
                if (stage >= 3) poly(listOf(s * 64 to 83.0, s * 108 to 83.0, s * 108 to 96.0, s * 64 to 96.0))
            }
            if (stage >= 3) diamond(0.0, -93.0, 8.0, b)
        }

        private fun gear() {
            poly(
                List(48) { i ->
                    val t = i * PI / 24
                    val r = if (i % 4 < 2) 101 else 86
                    cos(t) * r to sin(t) * r
                },
                fill = .22,
                width = 1.0,
            )
            ring(75.0, b, .8, 1.3)
            if (stage >= 2) {
                repeat(6) { i ->
                    val t = i * PI / 3
                    line(cos(t) * 77, sin(t) * 77, cos(t) * 92, sin(t) * 92, b, .8, 3.0)
                }
            }
            if (stage >= 3) {
                repeat(6) { i ->
                    val t = (i + .5) * PI / 3
                    diamond(cos(t) * 84, sin(t) * 84, 5.0, b)
                }
            }
        }
    }
}
