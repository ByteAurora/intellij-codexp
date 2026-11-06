package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.intellij.openapi.Disposable
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.ListCellRenderer
import javax.swing.Scrollable
import javax.swing.plaf.basic.BasicListUI
import kotlin.math.roundToInt

/** Retained components move at their real hit-test bounds; focus never changes to a replacement card. */
internal class DashboardChallengeColumn(
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : JPanel(null),
    Scrollable,
    Disposable {
    val motion =
        DashboardListMotion(JBUI.scale(115), clock) {
            doLayout()
            repaint(visibleRect)
        }
    private var rows = linkedMapOf<String, DashboardChallengeRow>()

    init {
        background = DashboardPalette.background
    }

    fun update(
        items: List<Pair<String, DashboardChallengeRow>>,
        changedIds: Set<String>,
    ) {
        val viewport = parent as? JViewport
        val visible = viewport?.viewRect ?: Rectangle(0, 0, width, height)
        val anchor = scrollAnchor(motion, visible, items.map { it.first }.toSet())
        val next = items.toMap(LinkedHashMap())
        rows.values.filter { it !in next.values }.forEach(::remove)
        items.forEachIndexed { index, (_, row) ->
            if (row.parent !== this) add(row)
            setComponentZOrder(row, index)
        }
        rows = next
        motion.update(items.map { it.first }, changedIds, visible)
        revalidate()
        doLayout()
        restoreAnchor(viewport, motion, anchor, preferredSize)
    }

    override fun doLayout() {
        rows.forEach { (id, row) ->
            row.setBounds(0, motion.y(id).roundToInt(), width, motion.rowHeight - JBUI.scale(9))
            row.highlightAlpha = motion.highlight(id)
        }
    }

    override fun getPreferredSize(): Dimension = Dimension(JBUI.scale(300), rows.size * motion.rowHeight)

    override fun getPreferredScrollableViewportSize(): Dimension = JBUI.size(300, 400)

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = JBUI.scale(20)

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = (visibleRect.height - JBUI.scale(20)).coerceAtLeast(1)

    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = false

    override fun dispose() = motion.dispose()
}

/** Virtualized history with ID-based selection, visual geometry and hit testing using the same positions. */
internal class DashboardHistoryList(
    private val records: DefaultListModel<DashboardChallenge>,
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : JList<DashboardChallenge>(records),
    Disposable {
    val motion = DashboardListMotion(JBUI.scale(116), clock) { repaint(visibleRect) }
    private var indices = emptyMap<String, Int>()
    private var motionReady = false

    init {
        background = DashboardPalette.background
        fixedCellHeight = motion.rowHeight
        cellRenderer =
            object : ListCellRenderer<DashboardChallenge> {
                private val row = DashboardChallengeRow()

                override fun getListCellRendererComponent(
                    list: JList<out DashboardChallenge>,
                    value: DashboardChallenge,
                    index: Int,
                    selected: Boolean,
                    focused: Boolean,
                ): Component {
                    row.setFinal(value)
                    row.highlightAlpha = motion.highlight(value.id)
                    row.highlightGold = true
                    row.isOpaque = false
                    row.cardInsetY = JBUI.scale(5)
                    row.border =
                        BorderFactory.createCompoundBorder(
                            JBUI.Borders.empty(5, 0),
                            BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(if (selected) DashboardPalette.gold else DashboardPalette.line), JBUI.Borders.empty(12)),
                        )
                    return row
                }
            }
        getAccessibleContext().accessibleName = CodeXPBundle.message("dashboard.history.accessible")
        motionReady = true
        installMotionUI()
    }

    override fun updateUI() {
        super.updateUI()
        // JList calls this from its constructor, before motion is initialized.
        if (motionReady) installMotionUI()
    }

    private fun installMotionUI() {
        setUI(
            object : BasicListUI() {
                override fun paint(
                    g: Graphics,
                    c: JComponent,
                ) {
                    val candidates = (motion.visibleIds(visibleRect) + motion.movingIds).mapNotNull(indices::get).distinct().sortedDescending()
                    for (index in candidates) {
                        val bounds = bounds(index) ?: continue
                        if (!bounds.intersects(g.clipBounds ?: visibleRect)) continue
                        val renderer = cellRenderer.getListCellRendererComponent(this@DashboardHistoryList, records[index], index, isSelectedIndex(index), hasFocus() && leadSelectionIndex == index)
                        rendererPane.paintComponent(g, renderer, this@DashboardHistoryList, bounds.x, bounds.y, bounds.width, bounds.height, true)
                    }
                }

                override fun indexToLocation(
                    list: JList<*>,
                    index: Int,
                ): Point? = bounds(index)?.location

                override fun getCellBounds(
                    list: JList<*>,
                    index1: Int,
                    index2: Int,
                ): Rectangle? {
                    val first = bounds(index1) ?: return null
                    return bounds(index2)?.let(first::union) ?: first
                }

                override fun locationToIndex(
                    list: JList<*>,
                    location: Point,
                ): Int {
                    if (records.isEmpty) return -1
                    val candidates = (motion.visibleIds(Rectangle(0, location.y, width, motion.rowHeight)) + motion.movingIds).mapNotNull(indices::get).distinct()
                    // Lower indices paint last; overlapping entering rows hit the card actually on top.
                    return candidates.sorted().firstOrNull { bounds(it)?.contains(location) == true }
                        ?: (location.y / motion.rowHeight).coerceIn(0, records.size - 1)
                }
            },
        )
    }

    private fun bounds(index: Int): Rectangle? = if (index in 0 until records.size) Rectangle(0, motion.y(records[index].id).roundToInt(), width, motion.rowHeight) else null

    fun update(
        items: List<DashboardChallenge>,
        changedIds: Set<String>,
    ) {
        val viewport = parent as? JViewport
        val anchor = scrollAnchor(motion, viewport?.viewRect ?: visibleRect, items.map { it.id }.toSet())
        val selectedIds = selectedValuesList.map { it.id }.toSet()
        val prefix = items.size - records.size
        val isPrepend = prefix >= 0 && (0 until records.size).all { records[it].id == items[it + prefix].id }
        selectionModel.valueIsAdjusting = true
        try {
            if (isPrepend) {
                for (index in prefix - 1 downTo 0) records.add(0, items[index])
            } else {
                while (records.size > items.size) records.remove(records.size - 1)
                items.forEachIndexed { index, item ->
                    if (index >= records.size) {
                        records.addElement(item)
                    } else if (records[index] != item) {
                        records[index] = item
                    }
                }
            }
            indices = items.withIndex().associate { it.value.id to it.index }
            selectedIndices = selectedIds.mapNotNull(indices::get).toIntArray()
            motion.update(items.map { it.id }, changedIds, viewport?.viewRect ?: visibleRect)
            revalidate()
            restoreAnchor(viewport, motion, anchor, preferredSize)
        } finally {
            selectionModel.valueIsAdjusting = false
        }
    }

    override fun dispose() = motion.dispose()
}

private fun scrollAnchor(
    motion: DashboardListMotion,
    visible: Rectangle,
    remainingIds: Set<String>,
): Pair<String, Double>? {
    if (visible.y <= 2) return null
    val id = motion.visibleIds(visible).firstOrNull { it in remainingIds && motion.y(it) + motion.rowHeight > visible.y } ?: return null
    return id to visible.y - motion.y(id)
}

private fun restoreAnchor(
    viewport: JViewport?,
    motion: DashboardListMotion,
    anchor: Pair<String, Double>?,
    preferred: Dimension,
) {
    if (viewport == null || anchor == null) return
    viewport.viewSize = Dimension(viewport.width, preferred.height)
    viewport.viewPosition = Point(0, (motion.y(anchor.first) + anchor.second).roundToInt().coerceIn(0, (preferred.height - viewport.height).coerceAtLeast(0)))
}
