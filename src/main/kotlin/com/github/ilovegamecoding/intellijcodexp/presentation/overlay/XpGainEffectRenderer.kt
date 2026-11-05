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
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** One passive component per visible editor; one timer for the project's active XP clusters. */
internal class XpGainEffectRenderer(
    private val clock: () -> Long = ::motionTimeMillis,
    private val crestTarget: (JLayeredPane) -> XpAbsorptionTarget? = { null },
    private val activityChanged: () -> Unit = {},
) {
    private val active = mutableMapOf<Editor, XpSurface>()
    private val fixed = mutableMapOf<JLayeredPane, XpSurface>()
    private val flights = mutableListOf<Flight>()
    private var nextEmissionAt = Long.MIN_VALUE
    private val timer = Timer(16) { tick() }.apply { isCoalesce = true }
    private var disposed = false
    private var reportedActive = false
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
        val now = clock()
        val options = EffectOptions.from(config)
        val previous = active[editor]
        val surface =
            if (previous != null && previous.cluster.options == options) {
                previous.apply { accumulate(value, now) }
            } else {
                release(editor)
                XpSurface(XpCluster(value, now, options)).also {
                    active[editor] = it
                    editor.contentComponent.add(it)
                }
            }
        surface.position = config.positionToDisplayGainedXP
        place(editor, surface)
        requestAbsorption(surface, SwingUtilities.getRootPane(surface)?.layeredPane, now)
        surface.accessibleContext.accessibleName = AchievementPainter.xpText(surface.cluster.total)
        timer.start()
        reportActivity()
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
        val now = clock()
        val options = EffectOptions.from(config)
        val previous = fixed[pane]
        val surface =
            if (previous != null && previous.cluster.options == options) {
                previous.apply { accumulate(value, now) }
            } else {
                release(pane)
                XpSurface(XpCluster(value, now, options)).also {
                    fixed[pane] = it
                    pane.add(it, JLayeredPane.PALETTE_LAYER, 0)
                }
            }
        placeWindow(pane, surface)
        requestAbsorption(surface, pane, now)
        surface.accessibleContext.accessibleName = AchievementPainter.xpText(surface.cluster.total)
        timer.start()
        reportActivity()
    }

    /** Screen-local occlusion only: distant typing XP must not dismiss the mini crest. */
    fun overlaps(
        pane: JLayeredPane,
        bounds: Rectangle,
    ): Boolean {
        fun intersects(surface: XpSurface): Boolean {
            if (!surface.isVisible || !surface.cluster.alive(clock()) || !SwingUtilities.isDescendingFrom(surface, pane)) return false
            val rect = SwingUtilities.convertRectangle(surface.parent, surface.bounds, pane)
            rect.grow(JBUI.scale(8), JBUI.scale(8)) // Includes the small XP drift between layout events.
            return rect.intersects(bounds)
        }
        return active.values.any(::intersects) || fixed.values.any(::intersects)
    }

    internal fun tick() {
        val now = clock()
        active.keys.toList().forEach { editor ->
            val surface = active[editor] ?: return@forEach
            if (editor.isDisposed || !editor.contentComponent.isShowing ||
                !advanceSurface(surface, now) { place(editor, surface) }
            ) {
                release(editor)
            }
        }
        fixed.keys.toList().forEach { pane ->
            val surface = fixed[pane] ?: return@forEach
            if (!pane.isShowing || !advanceSurface(surface, now) { placeWindow(pane, surface) }) {
                release(pane)
            }
        }
        advanceAbsorption(now)
        stopIfIdle()
    }

    private inline fun advanceSurface(
        surface: XpSurface,
        now: Long,
        place: () -> Unit,
    ): Boolean {
        surface.now = now
        val textAlive = surface.cluster.alive(now)
        if (textAlive) {
            surface.cluster.sample(now)
            place()
            surface.repaint()
        } else {
            surface.isVisible = false
        }
        return textAlive || surface.pending != null || flights.any { it.source === surface }
    }

    private fun requestAbsorption(
        surface: XpSurface,
        pane: JLayeredPane?,
        now: Long,
    ) {
        if (surface.cluster.options.reducedMotion || pane == null || !pane.isShowing || !surface.isVisible ||
            surface.textLayout?.text.isNullOrEmpty() || crestTarget(pane) == null
        ) {
            surface.pending = null
            return
        }
        val origin = SwingUtilities.convertPoint(surface, surface.width / 2, surface.height / 2, pane)
        val pending = surface.pending
        // Keep one batch per source, without postponing it on every repeated keystroke.
        surface.pending =
            if (pending != null && pending.pane === pane) {
                pending.copy(origin = origin, latestAt = now, events = (pending.events + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            } else {
                Emission(pane, origin, now + XP_EMISSION_DELAY_MS, now, 1)
            }
    }

    private fun advanceAbsorption(now: Long) {
        val targets = mutableMapOf<JLayeredPane, XpAbsorptionTarget?>()

        fun target(pane: JLayeredPane): XpAbsorptionTarget? = if (targets.containsKey(pane)) targets[pane] else pane.takeIf { it.isShowing }?.let(crestTarget).also { targets[pane] = it }

        flights.toList().forEach { flight ->
            val destination = target(flight.pane)
            if (destination == null || flight.light.parent !== flight.pane || now - flight.startedAt >= XP_FLIGHT_MS + XP_IMPACT_MS) {
                removeFlight(flight)
            } else {
                flight.light.advance(destination.center, now - flight.startedAt, destination.radius, destination.design, destination.artworkScale)
            }
        }
        (active.values + fixed.values).filter { it.pending != null }.sortedBy { it.pending!!.readyAt }.forEach { surface ->
            val pending = surface.pending ?: return@forEach
            val destination = target(pending.pane)
            if (destination == null || !surface.isVisible || now - pending.latestAt > 350) {
                surface.pending = null
            } else if (now >= pending.readyAt && now >= nextEmissionAt && flights.size < XP_MAX_LIGHTS) {
                surface.pending = null
                nextEmissionAt = now + XP_EMISSION_INTERVAL_MS
                val light = XpAbsorptionLight(pending.origin, surface.cluster.options.subtle, pending.events)
                pending.pane.add(light, JLayeredPane.PALETTE_LAYER, 0)
                flights.add(Flight(surface, pending.pane, light, now))
                light.advance(destination.center, 0, destination.radius, destination.design, destination.artworkScale)
            }
        }
    }

    private fun removeFlight(flight: Flight) {
        flights.remove(flight)
        val layer = flight.light.parent ?: return
        val area = flight.light.bounds
        layer.remove(flight.light)
        layer.repaint(area.x, area.y, area.width, area.height)
    }

    private fun releaseAbsorption(surface: XpSurface) {
        surface.pending = null
        flights.filter { it.source === surface }.forEach(::removeFlight)
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
        val textLayout = surface.layoutCache.layout(AchievementPainter.xpText(surface.cluster.displayed.roundToLong()), font, visible.width, padding)
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
        val layout = surface.layoutCache.layout(AchievementPainter.xpText(surface.cluster.displayed.roundToLong()), font, maxOf(1, pane.width - JBUI.scale(32)), JBUI.scale(2))
        surface.textLayout = layout
        surface.bounds = windowXpBounds(Rectangle(0, 0, pane.width, pane.height), layout, JBUI.scale(48))
        surface.isVisible = true
    }

    fun release(editor: Editor) {
        val surface = active.remove(editor) ?: return
        releaseAbsorption(surface)
        val component = editor.contentComponent
        val bounds = surface.bounds
        component.remove(surface)
        component.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
        stopIfIdle()
    }

    private fun release(pane: JLayeredPane) {
        val surface = fixed.remove(pane) ?: return
        releaseAbsorption(surface)
        val bounds = surface.bounds
        pane.remove(surface)
        pane.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
        stopIfIdle()
    }

    fun clear() {
        active.keys.toList().forEach(::release)
        fixed.keys.toList().forEach(::release)
        nextEmissionAt = Long.MIN_VALUE
        timer.stop()
    }

    private fun stopIfIdle() {
        if (active.isEmpty() && fixed.isEmpty()) timer.stop()
        reportActivity()
    }

    private fun reportActivity() {
        val busy = activeCount > 0
        if (busy == reportedActive) return
        reportedActive = busy
        activityChanged()
    }

    fun dispose() {
        disposed = true
        clear()
    }

    private data class Emission(
        val pane: JLayeredPane,
        val origin: Point,
        val readyAt: Long,
        val latestAt: Long,
        val events: Int,
    )

    private data class Flight(
        val source: XpSurface,
        val pane: JLayeredPane,
        val light: XpAbsorptionLight,
        val startedAt: Long,
    )

    private class XpSurface(
        var cluster: XpCluster,
    ) : JPanel(null) {
        var position = PositionToDisplayGainedXP.TOP_RIGHT
        var now = cluster.startedAt
        var textLayout: XpTextLayout? = null
        val layoutCache = XpTextLayoutCache()
        var pending: Emission? = null

        fun accumulate(
            value: Long,
            time: Long,
        ) {
            if (cluster.alive(time)) cluster.add(value, time) else cluster = XpCluster(value, time, cluster.options)
            now = time
        }

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

/** A single entry per source; caret movement and fading do not change glyph geometry. */
internal class XpTextLayoutCache {
    private var text: String? = null
    private var maxWidth = 0
    private var layout: XpTextLayout? = null

    fun layout(
        text: String,
        font: Font,
        maxWidth: Int,
        padding: Int,
    ): XpTextLayout {
        val previous = layout
        if (this.text == text && this.maxWidth == maxWidth && previous?.font == font && previous.padding == padding) return previous
        return layoutXpText(text, font, maxWidth, padding).also {
            this.text = text
            this.maxWidth = maxWidth
            layout = it
        }
    }
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
