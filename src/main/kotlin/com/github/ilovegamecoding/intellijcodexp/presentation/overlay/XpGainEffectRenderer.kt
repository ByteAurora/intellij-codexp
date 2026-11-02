package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.font.FontRenderContext
import java.awt.geom.Rectangle2D
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.Timer
import javax.swing.UIManager
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** One passive component per visible editor; one timer for the project's active XP clusters. */
internal class XpGainEffectRenderer {
    private val active = mutableMapOf<Editor, XpSurface>()
    private val fixed = mutableMapOf<JLayeredPane, XpSurface>()
    private val timer = Timer(16) { tick() }.apply { isCoalesce = true }
    private var disposed = false
    internal val activeCount: Int get() = active.size + fixed.size
    internal val caretCount: Int get() = active.size
    internal val windowCount: Int get() = fixed.size
    internal val isRunning: Boolean get() = timer.isRunning

    fun render(
        value: Long,
        editor: Editor,
        config: CodeXPConfiguration,
    ) {
        if (disposed || editor.isDisposed) return
        if (!config.showGainedXP) {
            clear()
            return
        }
        if (value <= 0 || !editor.contentComponent.isShowing) return
        val now = motionTimeMillis()
        val options = EffectOptions.from(config)
        val previous = active[editor]
        val surface =
            if (previous != null && previous.cluster.alive(now) && previous.cluster.options == options) {
                previous.apply { cluster.add(value, now) }
            } else {
                release(editor)
                XpSurface(XpCluster(value, now, options)).also {
                    active[editor] = it
                    editor.contentComponent.add(it)
                }
            }
        surface.position = config.positionToDisplayGainedXP
        place(editor, surface)
        surface.accessibleContext.accessibleName = AchievementPainter.xpText(surface.cluster.total)
        timer.start()
    }

    fun renderWindow(
        value: Long,
        pane: JLayeredPane,
        config: CodeXPConfiguration,
    ) {
        if (disposed) return
        if (!config.showGainedXP) {
            clear()
            return
        }
        if (value <= 0 || !pane.isShowing) return
        val now = motionTimeMillis()
        val options = EffectOptions.from(config)
        val previous = fixed[pane]
        val surface =
            if (previous != null && previous.cluster.alive(now) && previous.cluster.options == options) {
                previous.apply { cluster.add(value, now) }
            } else {
                release(pane)
                XpSurface(XpCluster(value, now, options)).also {
                    fixed[pane] = it
                    pane.add(it, JLayeredPane.PALETTE_LAYER, 0)
                }
            }
        placeWindow(pane, surface)
        surface.accessibleContext.accessibleName = AchievementPainter.xpText(surface.cluster.total)
        timer.start()
    }

    private fun tick() {
        val now = motionTimeMillis()
        active.keys.toList().forEach { editor ->
            val surface = active[editor] ?: return@forEach
            if (editor.isDisposed || !editor.contentComponent.isShowing || !surface.cluster.alive(now)) {
                release(editor)
            } else {
                surface.cluster.sample(now)
                surface.now = now
                place(editor, surface)
                surface.repaint()
            }
        }
        fixed.keys.toList().forEach { pane ->
            val surface = fixed[pane] ?: return@forEach
            if (!pane.isShowing || !surface.cluster.alive(now)) {
                release(pane)
            } else {
                surface.cluster.sample(now)
                surface.now = now
                placeWindow(pane, surface)
                surface.repaint()
            }
        }
        stopIfIdle()
    }

    private fun place(
        editor: Editor,
        surface: XpSurface,
    ) {
        val visible = editor.scrollingModel.visibleArea
        // Recompute after the typed action: its caret may have wrapped, moved or scrolled.
        val anchor = editor.visualPositionToXY(editor.caretModel.visualPosition)
        val padding = JBUI.scale(2)
        if (!visible.contains(anchor) || visible.width <= padding * 2 || visible.height < editor.lineHeight) {
            surface.isVisible = false
            return
        }
        val scheme = editor.colorsScheme
        val font = scheme.getFont(EditorFontType.PLAIN).deriveFont(scheme.editorFontSize * .9f)
        val textLayout = layoutXpText(AchievementPainter.xpText(surface.cluster.displayed.roundToLong()), font, visible.width, padding)
        surface.textLayout = textLayout
        val drift = if (surface.cluster.options.reducedMotion) 0.0 else JBUI.scale(5) * ease((surface.now - surface.cluster.startedAt) / 700.0)
        surface.bounds = xpBounds(anchor, surface.position, editor.lineHeight, textLayout, visible, JBUI.scale(8), drift)
        surface.isVisible = true
    }

    private fun placeWindow(
        pane: JLayeredPane,
        surface: XpSurface,
    ) {
        val font = (UIManager.getFont("Label.font") ?: Font(Font.SANS_SERIF, Font.PLAIN, 13)).let { it.deriveFont(it.size2D * 1.15f) }
        val layout = layoutXpText(AchievementPainter.xpText(surface.cluster.displayed.roundToLong()), font, maxOf(1, pane.width - JBUI.scale(32)), JBUI.scale(2))
        surface.textLayout = layout
        surface.bounds = windowXpBounds(Rectangle(0, 0, pane.width, pane.height), layout, JBUI.scale(48))
    }

    fun release(editor: Editor) {
        val surface = active.remove(editor) ?: return
        val component = editor.contentComponent
        val bounds = surface.bounds
        component.remove(surface)
        component.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
        stopIfIdle()
    }

    private fun release(pane: JLayeredPane) {
        val surface = fixed.remove(pane) ?: return
        val bounds = surface.bounds
        pane.remove(surface)
        pane.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
        stopIfIdle()
    }

    fun clear() {
        active.keys.toList().forEach(::release)
        fixed.keys.toList().forEach(::release)
        timer.stop()
    }

    private fun stopIfIdle() {
        if (active.isEmpty() && fixed.isEmpty()) timer.stop()
    }

    fun dispose() {
        disposed = true
        clear()
    }

    private class XpSurface(
        val cluster: XpCluster,
    ) : JPanel(null) {
        var position = PositionToDisplayGainedXP.TOP_RIGHT
        var now = cluster.startedAt
        var textLayout: XpTextLayout? = null

        init {
            isOpaque = false
            isFocusable = false
        }

        override fun contains(
            x: Int,
            y: Int,
        ): Boolean = false

        override fun paintComponent(graphics: Graphics) {
            val text = textLayout ?: return
            val g = graphics.create() as Graphics2D
            try {
                AchievementPainter.paintXp(g, text, cluster, now, !JBColor.isBright())
            } finally {
                g.dispose()
            }
        }
    }
}

/** Center of the source IDE client, above its status bar; never an editor or screen coordinate. */
internal fun windowXpBounds(
    client: Rectangle,
    layout: XpTextLayout,
    bottomInset: Int,
): Rectangle =
    Rectangle(
        (client.x + (client.width - layout.width) / 2).coerceAtLeast(client.x),
        (client.y + client.height - bottomInset - layout.height).coerceIn(client.y, maxOf(client.y, client.y + client.height - layout.height)),
        minOf(layout.width, client.width.coerceAtLeast(0)),
        minOf(layout.height, client.height.coerceAtLeast(0)),
    )

internal data class XpTextLayout(
    val text: String,
    val font: Font,
    val ink: Rectangle2D,
    val padding: Int,
) {
    val width: Int get() = ceil(ink.width).toInt() + padding * 2
    val height: Int get() = ceil(ink.height).toInt() + padding * 2
    val baselineX: Float get() = (padding - ink.x).toFloat()
    val baselineY: Float get() = (padding - ink.y).toFloat()
}

internal fun layoutXpText(
    text: String,
    font: Font,
    maxWidth: Int,
    padding: Int,
): XpTextLayout {
    val context = FontRenderContext(null, true, true)

    fun ink(value: String): Rectangle2D = font.createGlyphVector(context, value).visualBounds
    var label = text
    val limit = (maxWidth - padding * 2).coerceAtLeast(0)
    while (ink(label).width > limit && label.isNotEmpty()) {
        label = if (label.length <= 2) "" else label.dropLast(if (label.endsWith("…")) 2 else 1) + "…"
    }
    return XpTextLayout(label, font, ink(label), padding)
}

internal fun xpBounds(
    anchor: Point,
    position: PositionToDisplayGainedXP,
    lineHeight: Int,
    layout: XpTextLayout,
    viewport: Rectangle,
    gap: Int,
    drift: Double = 0.0,
): Rectangle {
    // Place the visible glyphs, not the centre of an oversized transparent panel.
    val x =
        anchor.x +
            when {
                position.x < 0 -> -layout.ink.width - gap
                position.x > 0 -> gap.toDouble()
                else -> -layout.ink.width / 2
            }
    val y =
        anchor.y +
            when {
                position.y < 0 -> -layout.ink.height - gap
                position.y > 0 -> lineHeight + gap.toDouble()
                else -> (lineHeight - layout.ink.height) / 2
            } + if (position.y > 0) drift else -drift
    return Rectangle(
        floor(x - layout.padding).toInt().coerceIn(viewport.x, maxOf(viewport.x, viewport.x + viewport.width - layout.width)),
        floor(y - layout.padding).toInt().coerceIn(viewport.y, maxOf(viewport.y, viewport.y + viewport.height - layout.height)),
        layout.width,
        layout.height,
    )
}
