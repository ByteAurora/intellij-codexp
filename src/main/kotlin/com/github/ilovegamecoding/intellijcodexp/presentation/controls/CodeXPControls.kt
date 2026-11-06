package com.github.ilovegamecoding.intellijcodexp.presentation.controls

import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.dashboardFont
import com.github.ilovegamecoding.intellijcodexp.presentation.smoothArtwork
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.geom.RoundRectangle2D
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JProgressBar
import javax.swing.JToggleButton
import javax.swing.Timer
import javax.swing.event.ChangeListener
import javax.swing.plaf.basic.BasicButtonUI
import javax.swing.plaf.basic.BasicGraphicsUtils

internal enum class CodeXPButtonStyle { PRIMARY, SECONDARY, LINK, QUIET, SEGMENT }

internal interface CodeXPControl {
    val controlStyle: CodeXPButtonStyle
    val reducedMotion: () -> Boolean
}

internal class CodeXPButton(
    override val controlStyle: CodeXPButtonStyle,
    override val reducedMotion: () -> Boolean,
    text: String = "",
    icon: Icon? = null,
) : JButton(text, icon),
    CodeXPControl {
    init {
        configureCodeXPControl()
    }

    override fun updateUI() = setUI(CodeXPButtonUI())
}

internal class CodeXPTab(
    text: String,
    override val reducedMotion: () -> Boolean,
) : JToggleButton(text),
    CodeXPControl {
    override val controlStyle = CodeXPButtonStyle.SEGMENT

    init {
        configureCodeXPControl()
    }

    override fun updateUI() = setUI(CodeXPButtonUI())
}

private fun AbstractButton.configureCodeXPControl() {
    isOpaque = false
    isContentAreaFilled = false
    isBorderPainted = false
    isRolloverEnabled = true
    font = dashboardFont(13f, Font.BOLD)
    border = JBUI.Borders.empty(8, 14)
    iconTextGap = JBUI.scale(8)
}

/** Keep Swing's button models, keyboard actions and accessibility; replace only their painting. */
internal class CodeXPButtonUI : BasicButtonUI() {
    private var button: AbstractButton? = null
    private var from = 0f
    private var target = 0f
    private var emphasis = 0f
    private var started = 0L
    private val timer = Timer(12) { advance(System.nanoTime()) }.apply { isCoalesce = true }
    private val changes = ChangeListener { updateEmphasis() }
    private val visibility =
        HierarchyListener {
            if (it.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && button?.isShowing != true) finish()
        }
    internal val isAnimating: Boolean get() = timer.isRunning

    override fun installListeners(b: AbstractButton) {
        super.installListeners(b)
        button = b
        b.addChangeListener(changes)
        b.addHierarchyListener(visibility)
    }

    override fun uninstallListeners(b: AbstractButton) {
        timer.stop()
        b.removeChangeListener(changes)
        b.removeHierarchyListener(visibility)
        button = null
        super.uninstallListeners(b)
    }

    private fun updateEmphasis() {
        val b = button ?: return
        val next =
            when {
                !b.isEnabled -> 0f
                b.model.isPressed && b.model.isArmed -> 1f
                b.model.isRollover -> .55f
                else -> 0f
            }
        if (next == target) return
        from = emphasis
        target = next
        started = System.nanoTime()
        if (!b.isShowing || (b as? CodeXPControl)?.reducedMotion?.invoke() != false) finish() else timer.start()
    }

    private fun advance(now: Long) {
        val b = button ?: return finish()
        val t = ((now - started) / 160_000_000.0).coerceIn(0.0, 1.0).toFloat()
        if (t >= 1f || !b.isShowing || (b as? CodeXPControl)?.reducedMotion?.invoke() != false) {
            finish()
        } else {
            val eased = 1 - (1 - t) * (1 - t)
            emphasis = from + (target - from) * eased
            b.repaint()
        }
    }

    private fun finish() {
        timer.stop()
        emphasis = target
        button?.repaint()
    }

    override fun paint(
        g: Graphics,
        c: JComponent,
    ) {
        val b = c as AbstractButton
        val style = (b as? CodeXPControl)?.controlStyle ?: CodeXPButtonStyle.SECONDARY
        val copy = g.create() as Graphics2D
        try {
            copy.smoothArtwork()
            val inset = JBUI.scale(3).toDouble()
            val shape = RoundRectangle2D.Double(inset, inset, (c.width - inset * 2).coerceAtLeast(0.0), (c.height - inset * 2).coerceAtLeast(0.0), JBUI.scale(16).toDouble(), JBUI.scale(16).toDouble())
            val base =
                when (style) {
                    CodeXPButtonStyle.PRIMARY -> if (b.isEnabled) DashboardPalette.teal else DashboardPalette.track
                    CodeXPButtonStyle.SECONDARY -> DashboardPalette.surface
                    CodeXPButtonStyle.SEGMENT -> if (b.isSelected) DashboardPalette.surface else DashboardPalette.track
                    else -> if (b.parent?.isOpaque == true) b.parent.background else DashboardPalette.background
                }
            val hover = if (style == CodeXPButtonStyle.PRIMARY) DashboardPalette.foreground else DashboardPalette.teal
            copy.color = mixControlColor(base, hover, emphasis * if (style == CodeXPButtonStyle.PRIMARY) .16f else .12f)
            if ((style != CodeXPButtonStyle.QUIET && style != CodeXPButtonStyle.LINK) || emphasis > 0f) copy.fill(shape)
            if (b.hasFocus() && b.isFocusPainted) {
                copy.color = DashboardPalette.teal
                copy.stroke = BasicStroke(JBUI.scale(2).toFloat())
                val focusInset = JBUI.scale(1).toDouble()
                copy.draw(RoundRectangle2D.Double(focusInset, focusInset, (c.width - focusInset * 2).coerceAtLeast(0.0), (c.height - focusInset * 2).coerceAtLeast(0.0), JBUI.scale(16).toDouble(), JBUI.scale(16).toDouble()))
            }
        } finally {
            copy.dispose()
        }
        super.paint(g, c)
    }

    override fun paintButtonPressed(
        g: Graphics,
        b: AbstractButton,
    ) = Unit

    override fun paintFocus(
        g: Graphics,
        b: AbstractButton,
        viewRect: Rectangle,
        textRect: Rectangle,
        iconRect: Rectangle,
    ) = Unit

    override fun paintText(
        g: Graphics,
        b: AbstractButton,
        textRect: Rectangle,
        text: String,
    ) {
        val style = (b as? CodeXPControl)?.controlStyle
        g.color =
            when {
                !b.isEnabled -> DashboardPalette.muted
                style == CodeXPButtonStyle.PRIMARY -> JBColor(Color.WHITE, Color(0x142B26))
                style == CodeXPButtonStyle.LINK -> DashboardPalette.teal
                style == CodeXPButtonStyle.QUIET || (style == CodeXPButtonStyle.SEGMENT && !b.isSelected) -> DashboardPalette.muted
                else -> DashboardPalette.foreground
            }
        val copy = g.create() as Graphics2D
        try {
            copy.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            BasicGraphicsUtils.drawStringUnderlineCharAt(copy, text, b.displayedMnemonicIndex, textRect.x, textRect.y + copy.fontMetrics.ascent)
        } finally {
            copy.dispose()
        }
    }
}

private fun mixControlColor(
    a: Color,
    b: Color,
    amount: Float,
): Color {
    fun channel(
        x: Int,
        y: Int,
    ) = (x + (y - x) * amount.coerceIn(0f, 1f)).toInt()
    return Color(channel(a.red, b.red), channel(a.green, b.green), channel(a.blue, b.blue))
}

internal class CodeXPStepIndicator : JProgressBar() {
    init {
        isOpaque = false
        isFocusable = false
        preferredSize = JBUI.size(120, 8)
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.smoothArtwork()
            val count = maximum.coerceAtLeast(1)
            val gap = minOf(JBUI.scale(4).toDouble(), width.toDouble() / count / 3)
            val segment = ((width - gap * (count - 1)) / count).coerceAtLeast(0.0)
            for (i in 0 until count) {
                g.color = if (i < value) DashboardPalette.teal else DashboardPalette.track
                g.fill(RoundRectangle2D.Double(i * (segment + gap), height / 2.0 - JBUI.scale(2), segment, JBUI.scale(4).toDouble(), JBUI.scale(4).toDouble(), JBUI.scale(4).toDouble()))
            }
        } finally {
            g.dispose()
        }
    }
}
