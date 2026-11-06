package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.EffectOptions
import com.intellij.util.ui.JBUI
import java.awt.AlphaComposite
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JPanel
import kotlin.math.roundToInt

/** Logical anchors survive window and DPI changes, including very small project windows. */
internal fun miniCrestBounds(
    client: Rectangle,
    config: CodeXPConfiguration,
    scale: Double,
): Rectangle {
    val factor = scale.coerceIn(.5, 4.0)
    // Keep existing anchors in their original padded coordinate space until the first drag.
    val area =
        if (config.miniCrestWindowAnchor) {
            client
        } else {
            Rectangle(
                client.x + (36 * factor).roundToInt(),
                client.y + (48 * factor).roundToInt(),
                (client.width - (72 * factor).roundToInt()).coerceAtLeast(0),
                (client.height - (84 * factor).roundToInt()).coerceAtLeast(0),
            )
        }
    val size = miniCrestSide(area, config, factor)
    val margin = if (config.miniCrestWindowAnchor) 0 else (12 * factor).roundToInt()

    fun position(
        value: Double,
        extent: Int,
    ): Int {
        val travel = (extent - size).coerceAtLeast(0)
        val first = minOf(margin, travel / 2)
        val last = maxOf(first, travel - margin)
        val fraction = if (value.isFinite()) value.coerceIn(0.0, 1.0) else 1.0
        return first + ((last - first) * fraction).roundToInt()
    }
    return Rectangle(area.x + position(config.miniCrestX, area.width), area.y + position(config.miniCrestY, area.height), size, size)
}

private fun miniCrestSide(
    client: Rectangle,
    config: CodeXPConfiguration,
    factor: Double,
): Int = minOf((config.miniCrestSize * factor).roundToInt(), client.width, client.height).coerceAtLeast(0)

internal fun miniCrestAnchor(
    point: Point,
    client: Rectangle,
    config: CodeXPConfiguration,
    scale: Double,
): Pair<Double, Double> {
    val size = miniCrestSide(client, config, scale.coerceIn(.5, 4.0))

    fun anchor(
        value: Int,
        travel: Int,
        previous: Double,
    ) = if (travel <= 0) previous else (value.toDouble() / travel).coerceIn(0.0, 1.0)
    return anchor(point.x - client.x, client.width - size, config.miniCrestX) to anchor(point.y - client.y, client.height - size, config.miniCrestY)
}

/** Artwork accepts dragging; transparent corners leave editor input untouched. */
internal class MiniCrestSurface : JPanel(null) {
    var visibilityAlpha = 1f
    var interactive = true
        set(value) {
            field = value
            control.isEnabled = value
        }
    var onControlRequested: () -> Unit = {}
    val control =
        object : JButton() {
            override fun contains(
                x: Int,
                y: Int,
            ): Boolean = false

            // Accessible options action without a visible button or a mouse hit region.
            override fun paintComponent(graphics: Graphics) = Unit
        }.apply {
            isFocusable = false
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            margin = JBUI.emptyInsets()
            getAccessibleContext().accessibleName = CodeXPBundle.message("mini.options")
            addActionListener { onControlRequested() }
        }
    var design: CrestDesign? = null
    var level = 1
        set(value) {
            if (field == value) return
            field = value
            getAccessibleContext().accessibleName = CodeXPBundle.message("mini.accessible", value)
        }
    var options = EffectOptions.from(CodeXPConfiguration())
    var frame = CollectionMotionFrame()
    var animationRunning = false
    private val metrics = CollectionFrameMetrics("MINI_CREST_FRAMES")

    init {
        isOpaque = false
        isFocusable = false
        add(control)
        getAccessibleContext().accessibleName = CodeXPBundle.message("mini.accessible", level)
    }

    override fun doLayout() {
        control.setBounds(0, 0, width, height)
    }

    override fun paint(graphics: Graphics) {
        if (visibilityAlpha <= 0f) return
        val g = graphics.create() as Graphics2D
        try {
            val inherited = (g.composite as? AlphaComposite)?.alpha ?: 1f
            g.composite = AlphaComposite.SrcOver.derive(inherited * visibilityAlpha.coerceIn(0f, 1f))
            super.paint(g)
        } finally {
            g.dispose()
        }
    }

    override fun contains(
        x: Int,
        y: Int,
    ): Boolean = interactive && onArtwork(Point(x, y))

    fun onArtwork(point: Point): Boolean {
        if (!super.contains(point.x, point.y)) return false
        val radius = minOf(width, height) / 2.0
        return point.distanceSq(width / 2.0, height / 2.0) <= radius * radius
    }

    override fun paintComponent(graphics: Graphics) {
        val crest = design ?: return
        val start = System.nanoTime()
        val g = graphics.create() as Graphics2D
        try {
            CollectionCrestPainter.paintEquipped(g, width, height, crest, level, frame, options.subtle)
        } finally {
            g.dispose()
        }
        if (animationRunning) {
            metrics.painted(start, System.nanoTime(), width, height, 1, graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0, graphicsConfiguration?.device?.displayMode?.refreshRate ?: 0)
        } else {
            metrics.pause()
        }
    }

    fun pauseMetrics() = metrics.pause()
}
