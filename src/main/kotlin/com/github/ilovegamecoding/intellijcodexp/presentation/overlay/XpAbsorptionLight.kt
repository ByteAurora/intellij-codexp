package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CrestImpactPainter
import com.github.ilovegamecoding.intellijcodexp.presentation.smoothArtwork
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RadialGradientPaint
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.QuadCurve2D
import javax.swing.JComponent
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

internal const val XP_EMISSION_DELAY_MS = 80L
internal const val XP_EMISSION_INTERVAL_MS = 200L
internal const val XP_MAX_LIGHTS = 6
internal const val XP_FLIGHT_MS = 820L
internal const val XP_IMPACT_MS = 340L

internal data class XpAbsorptionTarget(
    val center: Point,
    val radius: Double,
    val design: CrestDesign? = null,
    val artworkScale: Double = radius / 100,
)

/** A small, click-through light on the window layer; it never dirties the whole flight path. */
internal class XpAbsorptionLight(
    private val origin: Point,
    private val subtle: Boolean,
    internal val eventCount: Int = 1,
) : JComponent() {
    private var travel = 0.0
    private var impact = 0.0
    private var atCrest = false
    private var tipX = 0.0
    private var tipY = 0.0
    private var controlX = 0.0
    private var controlY = 0.0
    private var wingX = 0.0
    private var wingY = 0.0
    private var headX = 0.0
    private var headY = 0.0
    internal val headLocation: Point get() = Point((x + headX).roundToInt(), (y + headY).roundToInt())
    private val scale = JBUIScale.scale(1f).toDouble() * (1 + (eventCount.coerceIn(1, 4) - 1) * .06)
    private val tint = if (subtle) JBColor(Color(47, 111, 165), Color(136, 198, 255)) else JBColor(Color(155, 93, 16), Color(241, 208, 141))
    private val glint = if (subtle) JBColor(Color(52, 115, 162), Color(216, 239, 255)) else JBColor(Color(163, 96, 25), Color(255, 245, 212))
    private val transparent = Color(tint.red, tint.green, tint.blue, 0)
    private var impactRadius = if (subtle) 25.0 else 37.0
    private var crestDesign: CrestDesign? = null
    private var crestScale = 1.0

    init {
        isOpaque = false
        isFocusable = false
    }

    fun advance(
        target: Point,
        elapsedMs: Long,
        targetRadius: Double = (if (subtle) 25.0 else 37.0) * scale,
        design: CrestDesign? = null,
        artworkScale: Double = targetRadius / 100,
    ) {
        crestDesign = design
        crestScale = if (artworkScale.isFinite()) artworkScale.coerceAtLeast(.01) / scale else 1.0
        // Target geometry is already in window coordinates; do not scale it again for DPI or batching.
        impactRadius = if (targetRadius.isFinite()) (targetRadius / scale).coerceAtLeast(8.0) else 25.0
        travel = unit(elapsedMs.toDouble() / XP_FLIGHT_MS)
        impact = unit((elapsedMs - XP_FLIGHT_MS).toDouble() / XP_IMPACT_MS)
        atCrest = elapsedMs >= XP_FLIGHT_MS
        val t = travel * travel
        val dx = (target.x - origin.x).toDouble()
        val dy = (target.y - origin.y).toDouble()
        val distance = hypot(dx, dy)
        val bend = minOf(52 * scale, distance * .14) * (if (dx < 0) -1 else 1)
        val nx = dy / distance.coerceAtLeast(1.0) * bend
        val ny = -dx / distance.coerceAtLeast(1.0) * bend
        val arch = 4 * t * (1 - t)
        val x = origin.x + dx * t + nx * arch
        val y = origin.y + dy * t + ny * arch
        // A trailing segment of the same quadratic arc, in UI units relative to the head.
        val vx = (dx + nx * (4 - 8 * t)) / scale
        val vy = (dy + ny * (4 - 8 * t)) / scale
        val tangent = hypot(vx, vy).coerceAtLeast(1.0)
        val span = minOf(t, .44 * travel, (if (subtle) 78.0 else 120.0) / tangent) * (1 - unit(impact * 2))
        controlX = -vx * span / 2
        controlY = -vy * span / 2
        tipX = 2 * controlX - 4 * nx / scale * span * span
        tipY = 2 * controlY - 4 * ny / scale * span * span
        val wing = (if (subtle) 2.8 else 4.2) / tangent
        wingX = -vy * wing
        wingY = vx * wing
        // Enclose the actual ribbon instead of a large square of transparent pixels around its head.
        val haloX =
            if (!atCrest) {
                15.0
            } else if (design == null) {
                impactRadius
            } else {
                maxOf(15.0, CrestImpactPainter.HALF_WIDTH * crestScale * 1.035)
            }
        val haloY =
            if (!atCrest) {
                15.0
            } else if (design == null) {
                impactRadius
            } else {
                maxOf(15.0, CrestImpactPainter.HALF_HEIGHT * crestScale * 1.035)
            }
        val left = floor((minOf(-haloX, tipX, controlX - kotlin.math.abs(wingX), -kotlin.math.abs(wingX)) - 5) * scale).toInt()
        val top = floor((minOf(-haloY, tipY, controlY - kotlin.math.abs(wingY), -kotlin.math.abs(wingY)) - 5) * scale).toInt()
        val right = ceil((maxOf(haloX, tipX, controlX + kotlin.math.abs(wingX), kotlin.math.abs(wingX)) + 5) * scale).toInt()
        val bottom = ceil((maxOf(haloY, tipY, controlY + kotlin.math.abs(wingY), kotlin.math.abs(wingY)) + 5) * scale).toInt()
        headX = -left.toDouble()
        headY = -top.toDouble()
        setBounds(x.roundToInt() + left, y.roundToInt() + top, right - left, bottom - top)
        repaint()
    }

    override fun contains(
        x: Int,
        y: Int,
    ) = false

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.smoothArtwork()
            g.translate(headX, headY)
            g.scale(scale, scale)
            val light = unit(travel / .12) * (1 - impact)
            if (hypot(tipX, tipY) > .5) paintTail(g, light)
            val headHalo = if (subtle) 10.0 else 15.0
            val outlined = atCrest && crestDesign != null
            val halo = if (outlined) headHalo else headHalo + (impactRadius - headHalo) * ease(impact)
            val bloomAlpha = if (subtle) .32 else .45
            val bloom =
                if (outlined) {
                    unit(1 - impact * 4) * bloomAlpha
                } else if (atCrest) {
                    bloomAlpha
                } else {
                    .62
                }
            g.composite = AlphaComposite.SrcOver.derive((light * bloom).toFloat())
            g.paint = RadialGradientPaint(0f, 0f, halo.toFloat(), floatArrayOf(0f, .34f, 1f), arrayOf(glint, tint, transparent))
            g.fill(Ellipse2D.Double(-halo, -halo, halo * 2, halo * 2))
            if (outlined) {
                val edge = g.create() as Graphics2D
                try {
                    edge.scale(crestScale, crestScale)
                    CrestImpactPainter.paint(edge, requireNotNull(crestDesign), impact, subtle)
                } finally {
                    edge.dispose()
                }
            } else if (atCrest) {
                val ring = 5 + (impactRadius - 5) * ease(impact)
                g.color = tint
                g.composite = AlphaComposite.SrcOver.derive(((1 - impact) * (1 - impact) * if (subtle) .6 else .8).toFloat())
                g.stroke = BasicStroke((1 + (1 - impact) * 1.6).toFloat())
                g.draw(Ellipse2D.Double(-ring, -ring, ring * 2, ring * 2))
            }
            val core = (if (subtle) 3.1 else 4.5) * (1 - impact)
            g.color = glint
            g.composite = AlphaComposite.SrcOver.derive(light.toFloat())
            g.fill(Ellipse2D.Double(-core, -core, core * 2, core * 2))
        } finally {
            g.dispose()
        }
    }

    private fun paintTail(
        g: Graphics2D,
        light: Double,
    ) {
        val ribbon = Path2D.Double()
        ribbon.moveTo(tipX, tipY)
        ribbon.quadTo(controlX + wingX * .45, controlY + wingY * .45, wingX, wingY)
        ribbon.lineTo(-wingX, -wingY)
        ribbon.quadTo(controlX - wingX * .45, controlY - wingY * .45, tipX, tipY)
        ribbon.closePath()
        g.paint = GradientPaint(tipX.toFloat(), tipY.toFloat(), transparent, 0f, 0f, tint)
        g.composite = AlphaComposite.SrcOver.derive((light * .9).toFloat())
        g.fill(ribbon)
        g.paint = GradientPaint(tipX.toFloat(), tipY.toFloat(), transparent, 0f, 0f, glint)
        g.stroke = BasicStroke(if (subtle) 1.0f else 1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(QuadCurve2D.Double(tipX, tipY, controlX, controlY, 0.0, 0.0))
    }
}
