package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.dashboardFont
import com.intellij.openapi.Disposable
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.plaf.basic.BasicGraphicsUtils
import kotlin.math.ceil

/** One retained renderer with cached static card faces. Live frames paint only lightweight art. */
internal class CollectionCardRenderer(
    private val frame: (CollectionEntry) -> CollectionMotionFrame,
) : JPanel(null),
    ListCellRenderer<CollectionEntry>,
    Disposable {
    private data class Key(
        val entry: CollectionEntry,
        val width: Int,
        val height: Int,
        val scale: Double,
        val dark: Boolean,
        val selected: Boolean,
        val row: Boolean,
    )

    private val faces = LinkedHashMap<Key, BufferedImage>(32, .75f, true)
    private var facePixels = 0
    var snapshot: CollectionDisplaySnapshot? = null
    var rowLayout = false
    private var entry: CollectionEntry? = null
    private var selected = false

    init {
        isOpaque = false
    }

    override fun getListCellRendererComponent(
        list: JList<out CollectionEntry>,
        value: CollectionEntry,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        entry = value
        selected = isSelected
        toolTipText = value.accessibleName
        getAccessibleContext().accessibleName = if (isSelected) CodeXPBundle.message("collection.selected.accessible", value.accessibleName) else value.accessibleName
        return this
    }

    override fun paintComponent(graphics: Graphics) {
        val item = entry ?: return
        val state = snapshot ?: return
        if (width <= 0 || height <= 0) return
        val g = graphics.create() as Graphics2D
        try {
            val scale = (ceil(g.transform.scaleX * 4) / 4).coerceIn(.5, 4.0)
            val key = Key(item, width, height, scale, DashboardPalette.background.red < 128, selected, rowLayout)
            val face =
                faces.getOrPut(key) {
                    val image = BufferedImage((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
                    val copy = image.createGraphics()
                    try {
                        copy.scale(scale, scale)
                        paintFace(copy, item)
                    } finally {
                        copy.dispose()
                    }
                    facePixels += image.width * image.height
                    image
                }
            while (facePixels > 6_000_000 && faces.size > 1) {
                val iterator = faces.entries.iterator()
                val old = iterator.next().value
                facePixels -= old.width * old.height
                iterator.remove()
            }
            g.drawImage(face, 0, 0, width, height, null)
            val inset = JBUI.scale(12)
            val artHeight = if (rowLayout) (height - inset * 2).coerceAtLeast(1) else artHeight(item.kind)
            val artWidth = if (rowLayout) rowArtWidth() else (width - inset * 2).coerceAtLeast(1)
            val artGraphics = g.create(inset, inset, artWidth, artHeight) as Graphics2D
            try {
                paintCollectionArtwork(artGraphics, artWidth, artHeight, item, state, frame(item))
            } finally {
                artGraphics.dispose()
            }
            if (selected) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = DashboardPalette.gold
                g.stroke = BasicStroke(JBUI.scale(1.4f))
                g.draw(RoundRectangle2D.Double(JBUI.scale(5).toDouble(), JBUI.scale(5).toDouble(), (width - JBUI.scale(10)).toDouble(), (height - JBUI.scale(10)).toDouble(), JBUI.scale(14).toDouble(), JBUI.scale(14).toDouble()))
            }
        } finally {
            g.dispose()
        }
    }

    private fun artHeight(kind: CollectionKind): Int = JBUI.scale(if (kind == CollectionKind.CREST) 108 else 64)

    private fun rowArtWidth(): Int = minOf(JBUI.scale(88), width / 3).coerceAtLeast(1)

    fun cellHeight(kind: CollectionKind): Int {
        val nameHeight = getFontMetrics(dashboardFont(14f, Font.BOLD)).height
        val labels = nameHeight + 2 * getFontMetrics(dashboardFont(11f)).height
        return if (rowLayout) {
            maxOf(JBUI.scale(if (kind == CollectionKind.CREST) 102 else 84), labels + JBUI.scale(34))
        } else {
            artHeight(kind) + labels + JBUI.scale(39)
        }
    }

    private fun paintFace(
        g: Graphics2D,
        item: CollectionEntry,
    ) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = DashboardPalette.surface
        val card = RoundRectangle2D.Double(JBUI.scale(5).toDouble(), JBUI.scale(5).toDouble(), (width - JBUI.scale(10)).toDouble(), (height - JBUI.scale(10)).toDouble(), JBUI.scale(14).toDouble(), JBUI.scale(14).toDouble())
        g.fill(card)
        g.color = DashboardPalette.line
        g.stroke = BasicStroke(1f)
        g.draw(card)
        val x = if (rowLayout) JBUI.scale(20) + rowArtWidth() else JBUI.scale(16)
        val available = (width - x - JBUI.scale(13)).coerceAtLeast(1)
        val nameFont = dashboardFont(14f, Font.BOLD)
        val captionFont = dashboardFont(11f)
        val gap = JBUI.scale(3)
        // Owned titles show their name in the artwork. Choice titles keep the mask and add a clue below it.
        val title = item.kind == CollectionKind.TITLE
        val showStatus = title || item.owned || item.canAcquire
        val captions = (if (showStatus) 1 else 0) + (if (item.owned) 0 else 1)
        val labelHeight = g.getFontMetrics(nameFont).height + captions * (gap + g.getFontMetrics(captionFont).height)
        var y = if (rowLayout) (height - labelHeight) / 2 else JBUI.scale(15) + artHeight(item.kind)

        fun line(
            text: String,
            font: Font,
            color: Color,
        ) {
            g.font = font
            g.color = color
            val fm = g.fontMetrics
            val fitted = BasicGraphicsUtils.getClippedString(this, fm, text, available)
            g.drawString(fitted, if (title) (width - fm.stringWidth(fitted)) / 2 else x, y + fm.ascent)
            y += fm.height + gap
        }
        if (!title || (item.choice && !item.owned)) line(item.name, nameFont, DashboardPalette.foreground)
        if (showStatus) {
            line(item.status, captionFont, if (item.equipped || item.unseen || item.canAcquire) DashboardPalette.teal else DashboardPalette.muted)
        }
        if (!item.owned) line(item.conditionHint, captionFont, DashboardPalette.muted)
    }

    override fun dispose() {
        faces.clear()
        facePixels = 0
        snapshot = null
        entry = null
    }
}
