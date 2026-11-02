package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.utils.StringUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.math.cos
import kotlin.math.sin

internal object DashboardPalette {
    val background = JBColor(Color(0xFAFAF8), Color(0x1D2024))
    val surface = JBColor(Color(0xFFFFFF), Color(0x25292E))
    val foreground = JBColor(Color(0x30363B), Color(0xECEDEA))
    val muted = JBColor(Color(0x616970), Color(0xA1A8AE))
    val line = JBColor(Color(0xDADCD9), Color(0x383E44))
    val gold = JBColor(Color(0x89651E), Color(0xE0C18B))
    val teal = JBColor(Color(0x237A70), Color(0x83CBB9))
    val track = JBColor(Color(0xEEEFEB), Color(0x30353A))
}

internal fun dashboardFont(
    size: Float = 13f,
    style: Int = Font.PLAIN,
): Font = (UIManager.getFont("Label.font") ?: Font(Font.SANS_SERIF, Font.PLAIN, 13)).let { it.deriveFont(style, it.size2D * size / 13f) }

internal fun number(value: Long): String = StringUtil.numberToStringWithCommas(value)

internal fun dashboardLabel(
    text: String = "",
    size: Float = 12f,
    color: Color = DashboardPalette.foreground,
): JLabel =
    JLabel(text).apply {
        font = dashboardFont(size)
        foreground = color
    }

/** No interpolation or highlight: these pixels always represent the committed fraction. */
internal class DashboardProgressBar(
    private val fill: Color,
) : JPanel() {
    var fraction = 0.0
        set(value) {
            field = value.coerceIn(0.0, 1.0)
            repaint()
        }

    init {
        isOpaque = false
        preferredSize = JBUI.size(100, 5)
        minimumSize = JBUI.size(1, 5)
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(5))
    }

    override fun paintComponent(graphics: Graphics) {
        graphics.color = DashboardPalette.track
        graphics.fillRect(0, 0, width, height)
        graphics.color = fill
        graphics.fillRect(0, 0, (fraction * width).toInt(), height)
    }
}

internal data class CrestNumberLayout(
    val font: Font,
    val x: Float,
    val baseline: Float,
    val ink: Rectangle2D,
)

/** Fits actual glyph ink, including font bearings; neither advance width nor digit count sets the center. */
internal fun crestNumberLayout(
    text: String,
    context: FontRenderContext,
): CrestNumberLayout {
    val safe = Rectangle2D.Double(-62.0, -13.0, 124.0, 64.0)
    var font = Font(Font.SERIF, Font.PLAIN, 72)
    var ink = font.createGlyphVector(context, text).visualBounds
    repeat(3) {
        val fit = minOf(1.0, safe.width / ink.width.coerceAtLeast(1.0), safe.height / ink.height.coerceAtLeast(1.0))
        if (fit < 1.0) font = font.deriveFont((font.size2D * fit * .99).toFloat().coerceAtLeast(.1f))
        ink = font.createGlyphVector(context, text).visualBounds
    }
    val x = (safe.centerX - ink.centerX).toFloat()
    val baseline = (safe.centerY - ink.centerY).toFloat()
    return CrestNumberLayout(font, x, baseline, Rectangle2D.Double(ink.x + x, ink.y + baseline, ink.width, ink.height))
}

/** Geometry depends only on allocated bounds. Ambient paint touches decoration, never the level or XP. */
internal class DashboardCrest : JPanel() {
    var level = dashboardLevel(0)
    var ambientPhase = 0.0
    var subtle = false

    init {
        isOpaque = false
        preferredSize = JBUI.size(240, 210)
        minimumSize = JBUI.size(80, 70)
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.translate(width / 2.0, height / 2.0)
            val scale = minOf(width / 240.0, height / 210.0)
            if (scale <= 0) return
            g.scale(scale, scale)
            val radius = 86.0
            g.stroke = BasicStroke(.8f)
            g.color = DashboardPalette.gold
            g.composite = AlphaComposite.SrcOver.derive(.45f)
            for (r in listOf(radius, radius * .89)) g.draw(Ellipse2D.Double(-r, -r, 2 * r, 2 * r))
            for (r in listOf(radius * .86, radius * .74)) {
                g.draw(
                    Path2D.Double().apply {
                        moveTo(0.0, -r)
                        lineTo(r, 0.0)
                        lineTo(0.0, r)
                        lineTo(-r, 0.0)
                        closePath()
                    },
                )
            }
            repeat(12) { index ->
                val angle = index * Math.PI / 6
                val inner = radius * 1.02
                val outer = radius * if (index % 3 == 0) 1.16 else 1.07
                g.draw(Line2D.Double(cos(angle) * inner, sin(angle) * inner, cos(angle) * outer, sin(angle) * outer))
            }
            // Thin outer accents orbit without moving, scaling or pulsing the crest itself.
            val accents = if (subtle) 1 else 3
            repeat(accents) { index ->
                val angle = ambientPhase * 360 + index * 120
                val r = radius * 1.04
                g.color = DashboardPalette.gold
                g.composite = AlphaComposite.SrcOver.derive(if (subtle) .18f else .32f)
                g.stroke = BasicStroke(if (subtle) .8f else 1.1f)
                g.draw(Arc2D.Double(-r, -r, 2 * r, 2 * r, angle, if (subtle) 18.0 else 28.0, Arc2D.OPEN))
                if (!subtle) {
                    val a = -angle * Math.PI / 180
                    g.fill(Ellipse2D.Double(cos(a) * r - 1.2, sin(a) * r - 1.2, 2.4, 2.4))
                }
            }
            g.color = DashboardPalette.teal
            g.composite = AlphaComposite.SrcOver.derive(.85f)
            g.stroke = BasicStroke(1.5f)
            g.draw(Arc2D.Double(-radius, -radius, 2 * radius, 2 * radius, 90.0, -360 * level.fraction, Arc2D.OPEN))
            g.composite = AlphaComposite.SrcOver
            g.color = DashboardPalette.gold
            g.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            g.drawString("LEVEL", -g.fontMetrics.stringWidth("LEVEL") / 2, -32)
            val text = level.level.toString()
            val fit = crestNumberLayout(text, g.fontRenderContext)
            g.drawGlyphVector(fit.font.createGlyphVector(g.fontRenderContext, text), fit.x, fit.baseline)
        } finally {
            g.dispose()
        }
    }
}

internal class DashboardChallengeRow : JPanel(BorderLayout(0, JBUI.scale(5))) {
    var highlightAlpha = 0f
    var highlightGold = false
    var highlightInsetY = 0
    private val title = dashboardLabel(size = 13f)
    val sequence = dashboardLabel(size = 10f, color = DashboardPalette.muted)
    private val reward = dashboardLabel(size = 11f, color = DashboardPalette.gold)
    private val description = dashboardLabel(size = 11f, color = DashboardPalette.muted)
    val count = dashboardLabel(size = 11f)
    val bar = DashboardProgressBar(DashboardPalette.teal)

    init {
        background = DashboardPalette.surface
        border = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DashboardPalette.line), JBUI.Borders.empty(12))
        val top =
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(
                    JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                        isOpaque = false
                        add(title, BorderLayout.CENTER)
                        add(reward, BorderLayout.EAST)
                    },
                    BorderLayout.NORTH,
                )
                add(sequence, BorderLayout.SOUTH)
            }
        val details =
            JPanel(GridLayout(2, 1, 0, JBUI.scale(2))).apply {
                isOpaque = false
                add(description)
                add(count)
            }
        count.horizontalAlignment = JLabel.RIGHT
        reward.verticalAlignment = JLabel.TOP
        add(top, BorderLayout.NORTH)
        add(details, BorderLayout.CENTER)
        add(bar, BorderLayout.SOUTH)
        preferredSize = JBUI.size(300, 106)
        minimumSize = JBUI.size(150, 106)
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(106))
    }

    fun setFinal(challenge: DashboardChallenge) {
        title.text = challenge.name
        title.toolTipText = challenge.name
        sequence.text = challenge.sequenceNumber?.let { CodeXPBundle.message("challenge.sequence", number(it)) } ?: ""
        sequence.toolTipText = sequence.text.takeIf { it.isNotEmpty() }
        reward.text = "+${number(challenge.reward)} XP"
        description.text = challenge.description
        description.toolTipText = challenge.description
        count.text = "${number(challenge.progress)} / ${number(challenge.goal)}"
        bar.fraction = challenge.fraction
        getAccessibleContext().accessibleName = listOf(challenge.name, sequence.text, challenge.description, count.text, reward.text).filter { it.isNotEmpty() }.joinToString(", ")
        bar.accessibleContext.accessibleName = count.text
    }

    override fun paintBorder(graphics: Graphics) {
        super.paintBorder(graphics)
        if (highlightAlpha <= 0f) return
        val g = graphics.create() as Graphics2D
        try {
            g.composite = AlphaComposite.SrcOver.derive(highlightAlpha.coerceIn(0f, 1f))
            g.color = if (highlightGold) DashboardPalette.gold else DashboardPalette.teal
            g.drawRect(0, highlightInsetY, (width - 1).coerceAtLeast(0), (height - 1 - 2 * highlightInsetY).coerceAtLeast(0))
        } finally {
            g.dispose()
        }
    }
}

/** Fixed XP and count columns keep every XP value centered on the same axis. Long labels elide. */
internal class DashboardActivityRow(
    event: Event,
) : JPanel(null) {
    val nameLabel = dashboardLabel(event.name.lowercase().replaceFirstChar { it.uppercase() })
    val xpLabel = dashboardLabel("+${number(event.xpValue)} XP", 11f, DashboardPalette.gold)
    val count = dashboardLabel("0", 13f)

    init {
        isOpaque = false
        border = BorderFactory.createMatteBorder(0, 0, 1, 0, DashboardPalette.line)
        xpLabel.horizontalAlignment = JLabel.CENTER
        count.horizontalAlignment = JLabel.RIGHT
        add(nameLabel)
        add(xpLabel)
        add(count)
        preferredSize = JBUI.size(220, 44)
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(44))
        minimumSize = JBUI.size(0, 44)
    }

    override fun doLayout() {
        val gap = JBUI.scale(6)
        val countWidth = minOf(JBUI.scale(72), width / 3)
        val xpWidth = minOf(JBUI.scale(72), width / 3)
        val xpX = (width - countWidth - gap - xpWidth).coerceAtLeast(0)
        nameLabel.setBounds(0, 0, (xpX - gap).coerceAtLeast(0), height)
        xpLabel.setBounds(xpX, 0, xpWidth, height)
        count.setBounds(width - countWidth, 0, countWidth, height)
        nameLabel.toolTipText = nameLabel.text
        count.toolTipText = count.text
    }
}
