package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionOutcome
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionResult
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPButton
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPButtonStyle
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPTab
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardChoiceBalance
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.dashboardFont
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.dashboardLabel
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.number
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.systemReducedMotion
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Frame
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.AbstractAction
import javax.swing.ButtonGroup
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.Scrollable
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal enum class CollectionFilter(
    private val labelKey: String,
) {
    ALL("collection.filter.all"),
    OWNED("collection.filter.owned"),
    LOCKED("collection.filter.locked"),
    AVAILABLE("collection.filter.available"),
    ;

    override fun toString(): String = CodeXPBundle.message(labelKey)
}

/** The list never leaves the screen. Selection views/acknowledges; buttons acquire or equip. */
internal class CodeXPCollectionPanel(
    private val service: CodeXPService,
    osReducedMotion: () -> Boolean = ::systemReducedMotion,
) : JPanel(BorderLayout()),
    Disposable {
    val search = JBTextField()
    val filter = JComboBox(CollectionFilter.entries.toTypedArray())
    val model = DefaultListModel<CollectionEntry>()
    private val metrics = CollectionFrameMetrics()
    val motion = CollectionMotion(::repaintVisibleArtwork, osReducedMotion)
    val grid =
        object : JList<CollectionEntry>(model) {
            override fun paintComponent(g: Graphics) {
                val start = System.nanoTime()
                super.paintComponent(g)
                if (motion.isRunning) {
                    metrics.painted(start, System.nanoTime(), visibleRect.width, visibleRect.height, ownedVisibleCount(), (g as Graphics2D).transform.scaleX, graphicsConfiguration?.device?.displayMode?.refreshRate ?: 0)
                } else {
                    metrics.pause()
                }
            }
        }
    private val controlsReducedMotion = { latest?.reducedMotion == true || osReducedMotion() }
    val primary: JButton = CodeXPButton(CodeXPButtonStyle.PRIMARY, controlsReducedMotion)
    val back: JButton = CodeXPButton(CodeXPButtonStyle.QUIET, controlsReducedMotion, icon = AllIcons.Actions.Back)
    val crestTab: JToggleButton = CodeXPTab(CodeXPBundle.message("collection.tab.crests"), controlsReducedMotion)
    val titleTab: JToggleButton = CodeXPTab(CodeXPBundle.message("collection.tab.titles"), controlsReducedMotion)
    val detailName = wrappedText(18f, DashboardPalette.foreground)
    val closeDetail: JButton = CodeXPButton(CodeXPButtonStyle.QUIET, controlsReducedMotion, icon = AllIcons.Actions.Close)
    private val conditionLabel = dashboardLabel(CodeXPBundle.message("collection.condition.label"), 11f, DashboardPalette.muted)
    private val condition = wrappedText(12f, DashboardPalette.muted)
    private val progress = wrappedText(11f, DashboardPalette.teal)
    private val creditHelp = wrappedText(11f, DashboardPalette.muted)
    private val status = dashboardLabel(size = 11f, color = DashboardPalette.gold)
    private val heading = dashboardLabel(CodeXPBundle.message("collection.heading"), 20f)
    val balance = DashboardChoiceBalance()
    private val feedback = wrappedText(12f, DashboardPalette.foreground)
    private val notice = wrappedText(11f, DashboardPalette.muted)
    private val acknowledgeMigration = CodeXPButton(CodeXPButtonStyle.SECONDARY, controlsReducedMotion, CodeXPBundle.message("collection.migration.acknowledge"))
    private val renderer = CollectionCardRenderer { item -> motion.frame(item.id, item.owned) }
    val scroll = JBScrollPane(grid)
    private val empty = dashboardLabel(CodeXPBundle.message("collection.empty"), 12f, DashboardPalette.muted)
    private val listArea = JPanel(BorderLayout()).apply { isOpaque = false }
    val detail: JPanel =
        object : JPanel(null), Scrollable {
            init {
                background = DashboardPalette.surface
            }

            override fun getPreferredSize(): Dimension = Dimension(width, arrangeDetail(width))

            override fun getPreferredScrollableViewportSize(): Dimension = JBUI.size(260, 160)

            override fun getScrollableTracksViewportWidth(): Boolean = true

            override fun getScrollableTracksViewportHeight(): Boolean = false

            override fun getScrollableUnitIncrement(
                visibleRect: Rectangle,
                orientation: Int,
                direction: Int,
            ): Int = JBUI.scale(20)

            override fun getScrollableBlockIncrement(
                visibleRect: Rectangle,
                orientation: Int,
                direction: Int,
            ): Int = visibleRect.height.coerceAtLeast(1)

            override fun doLayout() {
                arrangeDetail(width, place = true)
            }
        }
    val detailScroll =
        JBScrollPane(detail).apply {
            border = JBUI.Borders.empty()
            background = DashboardPalette.background
            viewport.background = DashboardPalette.background
            verticalScrollBar.background = DashboardPalette.background
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
    private val body =
        object : JPanel(null) {
            init {
                isOpaque = false
            }

            override fun doLayout() {
                val gap = JBUI.scale(12)
                if (!detailScroll.isVisible) {
                    listArea.setBounds(0, 0, width, height)
                    detailScroll.setBounds(0, 0, 0, 0)
                } else if (width >= JBUI.scale(720)) {
                    val detailWidth = JBUI.scale(270)
                    listArea.setBounds(0, 0, width - detailWidth - gap, height)
                    detailScroll.setBounds(width - detailWidth, 0, detailWidth, height)
                } else {
                    val h = minOf(arrangeDetail(width), (height * .43).toInt())
                    listArea.setBounds(0, 0, width, (height - h - gap).coerceAtLeast(0))
                    detailScroll.setBounds(0, height - h, width, h)
                }
                listArea.doLayout()
                scroll.doLayout()
                detailScroll.doLayout()
                sizeCells()
                if (revealSelection && grid.selectedIndex >= 0 && scroll.viewport.width > 0) {
                    scroll.viewport.doLayout()
                    grid.ensureIndexIsVisible(grid.selectedIndex)
                    revealSelection = false
                }
            }
        }
    private val header =
        object : JPanel(null) {
            init {
                isOpaque = false
            }

            override fun getPreferredSize(): Dimension = Dimension(width, headerHeight(this@CodeXPCollectionPanel.width))

            override fun doLayout() = layoutHeader()
        }
    private val footer =
        object : JPanel(null) {
            init {
                isOpaque = false
            }

            override fun getPreferredSize(): Dimension = Dimension(width, arrangeFooter(this@CodeXPCollectionPanel.width))

            override fun doLayout() {
                arrangeFooter(width, place = true)
            }
        }
    private var latest: CollectionDisplaySnapshot? = null
    private var visibleEntries = emptyList<CollectionEntry>()
    private var selectedId: String? = null
    private var revealSelection = false
    private var kind = CollectionKind.CREST
    private var updating = false
    private var disposed = false
    private var owner: Window? = null
    private var cachedHeaderHeight = 0
    var onBack: () -> Unit = {}
    private val windowListener =
        object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent) = updateActivity()

            override fun windowDeactivated(e: WindowEvent) = updateActivity()

            override fun windowIconified(e: WindowEvent) = updateActivity()

            override fun windowDeiconified(e: WindowEvent) = updateActivity()

            override fun windowClosed(e: WindowEvent) = updateActivity()
        }

    init {
        background = DashboardPalette.background
        minimumSize = JBUI.size(230, 300)
        back.toolTipText = CodeXPBundle.message("collection.back")
        back.accessibleContext.accessibleName = back.toolTipText
        back.isContentAreaFilled = false
        back.isBorderPainted = false
        back.border = JBUI.Borders.empty(4)
        back.addActionListener { onBack() }
        ButtonGroup().apply {
            add(crestTab)
            add(titleTab)
        }
        crestTab.isSelected = true
        crestTab.addActionListener { setKind(CollectionKind.CREST) }
        titleTab.addActionListener { setKind(CollectionKind.TITLE) }
        for ((tab, other, otherKind) in listOf(Triple(crestTab, titleTab, CollectionKind.TITLE), Triple(titleTab, crestTab, CollectionKind.CREST))) {
            tab.border = JBUI.Borders.empty(6, 4)
            for (key in listOf("LEFT", "RIGHT")) {
                tab.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), "otherKind")
            }
            tab.actionMap.put(
                "otherKind",
                object : AbstractAction() {
                    override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                        setKind(otherKind)
                        other.requestFocusInWindow()
                    }
                },
            )
        }
        listOf(back, heading, balance, crestTab, titleTab, search, filter).forEach(header::add)
        search.emptyText.text = CodeXPBundle.message("collection.search.hint")
        search.accessibleContext.accessibleName = CodeXPBundle.message("collection.search")
        filter.accessibleContext.accessibleName = CodeXPBundle.message("collection.filter")
        filter.addActionListener { if (!updating) refreshList() }
        search.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = refreshList()

                override fun removeUpdate(e: DocumentEvent) = refreshList()

                override fun changedUpdate(e: DocumentEvent) = refreshList()
            },
        )
        grid.background = DashboardPalette.background
        grid.foreground = DashboardPalette.foreground
        grid.layoutOrientation = JList.HORIZONTAL_WRAP
        grid.visibleRowCount = -1
        grid.selectionMode = ListSelectionModel.SINGLE_SELECTION
        grid.cellRenderer = renderer
        grid.accessibleContext.accessibleName = CodeXPBundle.message("collection.grid.crests")
        grid.addListSelectionListener {
            if (!updating && !it.valueIsAdjusting) {
                selectedId = grid.selectedValue?.id
                detailScroll.verticalScrollBar.value = 0
                viewSelected()
            }
        }
        grid.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val index = grid.locationToIndex(e.point)
                    if (index >= 0 && grid.getCellBounds(index, index)?.contains(e.point) == true) viewSelected()
                }
            },
        )
        grid.inputMap.put(KeyStroke.getKeyStroke("ENTER"), "view")
        grid.inputMap.put(KeyStroke.getKeyStroke("SPACE"), "view")
        grid.actionMap.put(
            "view",
            object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) = viewSelected()
            },
        )
        scroll.border = JBUI.Borders.empty(0, 8, 0, 8)
        scroll.background = DashboardPalette.background
        scroll.horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        scroll.viewport.background = DashboardPalette.background
        scroll.verticalScrollBar.background = DashboardPalette.background
        scroll.verticalScrollBar.unitIncrement = JBUI.scale(32)
        scroll.viewport.addChangeListener { updateActivity() }
        scroll.viewport.addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    sizeCells()
                    updateActivity()
                }
            },
        )
        listArea.add(scroll, BorderLayout.CENTER)
        empty.border = JBUI.Borders.empty(12)
        empty.isVisible = false
        listArea.add(empty, BorderLayout.NORTH)
        listOf(detailName, status, closeDetail, conditionLabel, condition, progress, creditHelp, primary).forEach(detail::add)
        detail.border = JBUI.Borders.customLine(DashboardPalette.line, 1)
        val dismiss =
            object : AbstractAction() {
                override fun isEnabled(): Boolean = grid.selectedIndex >= 0

                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    grid.clearSelection()
                    grid.requestFocusInWindow()
                }
            }
        closeDetail.accessibleContext.accessibleName = CodeXPBundle.message("collection.detail.close")
        closeDetail.toolTipText = CodeXPBundle.message("collection.detail.close.hint")
        closeDetail.isContentAreaFilled = false
        closeDetail.isBorderPainted = false
        closeDetail.border = JBUI.Borders.empty(3)
        closeDetail.addActionListener(dismiss)
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "closeDetail")
        actionMap.put("closeDetail", dismiss)
        primary.addActionListener {
            val snapshot = latest ?: return@addActionListener
            val item = selected() ?: return@addActionListener
            val result: CollectionResult
            val updated: CollectionDisplaySnapshot
            // Keep the focused detail button on screen while the service updates every open window.
            updating = true
            try {
                result =
                    if (item.owned) {
                        service.equipCollection(snapshot.session, item.kind, if (item.equipped) null else item.id, DataManager.getInstance().getDataContext(this))
                    } else {
                        service.chooseCollection(snapshot.session, item.id)
                    }
                updated = service.collectionSnapshot()
                if (!item.owned && result.outcome == CollectionOutcome.APPLIED && updated.session === snapshot.session) {
                    val acquired = updated.entries.first { it.id == item.id }
                    if (filter.selectedItem != CollectionFilter.ALL) filter.selectedItem = CollectionFilter.OWNED
                    if (!acquired.matches(search.text)) search.text = ""
                    selectedId = acquired.id
                }
            } finally {
                updating = false
            }
            apply(updated)
            if (result.outcome == CollectionOutcome.APPLIED) viewSelected()
            report(result)
        }
        footer.add(feedback)
        footer.add(notice)
        footer.add(acknowledgeMigration)
        acknowledgeMigration.addActionListener {
            val result = service.acknowledgeCollectionMigration()
            apply(service.collectionSnapshot())
            report(result)
        }
        body.add(listArea)
        body.add(detailScroll)
        add(header, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)
        add(footer, BorderLayout.SOUTH)
        addHierarchyListener {
            if (it.changeFlags and (HierarchyEvent.SHOWING_CHANGED or HierarchyEvent.PARENT_CHANGED).toLong() != 0L) {
                val window = SwingUtilities.getWindowAncestor(this)
                if (owner !== window) {
                    owner?.removeWindowListener(windowListener)
                    owner = window
                    owner?.addWindowListener(windowListener)
                }
                updateActivity()
            }
        }
        refreshDetail()
    }

    fun apply(snapshot: CollectionDisplaySnapshot) {
        if (disposed) return
        val reset = latest?.session !== snapshot.session
        latest = snapshot
        if (reset) {
            selectedId = null
            updating = true
            grid.clearSelection()
            updating = false
            feedback.text = ""
        }
        for ((tab, tabKind) in listOf(crestTab to CollectionKind.CREST, titleTab to CollectionKind.TITLE)) {
            val items = snapshot.entries.filter { it.kind == tabKind }
            val label = if (tabKind == CollectionKind.CREST) CodeXPBundle.message("collection.tab.crests") else CodeXPBundle.message("collection.tab.titles")
            val owned = items.count { it.owned }
            tab.text = CodeXPBundle.message("collection.tab.count", label, owned, items.size)
            tab.accessibleContext.accessibleName = CodeXPBundle.message("collection.tab.count.accessible", label, owned, items.size)
        }
        balance.update(snapshot.credits, snapshot.choiceCreditHelp)
        notice.text = listOfNotNull(snapshot.storageProblem, snapshot.migrationNotice).joinToString("\n")
        notice.isVisible = notice.text.isNotEmpty()
        acknowledgeMigration.isVisible = snapshot.migrationNotice != null
        motion.configure(snapshot.reducedMotion)
        refreshList()
        revalidate()
    }

    internal fun setKind(value: CollectionKind) {
        if (kind == value) return
        kind = value
        crestTab.isSelected = value == CollectionKind.CREST
        titleTab.isSelected = value == CollectionKind.TITLE
        grid.accessibleContext.accessibleName = if (value == CollectionKind.CREST) CodeXPBundle.message("collection.grid.crests") else CodeXPBundle.message("collection.grid.titles")
        selectedId = null
        feedback.text = ""
        refreshList()
    }

    private fun refreshList() {
        if (updating) return
        val snapshot = latest ?: return
        val next =
            snapshot.entries.filter { item ->
                item.kind == kind && item.matches(search.text) &&
                    when (filter.selectedItem) {
                        CollectionFilter.OWNED -> item.owned
                        CollectionFilter.LOCKED -> !item.owned
                        CollectionFilter.AVAILABLE -> item.canAcquire
                        else -> true
                    }
            }
        if (next != visibleEntries) {
            updating = true
            try {
                val position = scroll.viewport.viewPosition
                visibleEntries = next
                model.clear()
                next.forEach(model::addElement)
                if (next.none { it.id == selectedId }) selectedId = null
                grid.selectedIndex = next.indexOfFirst { it.id == selectedId }
                scroll.viewport.viewPosition = position
            } finally {
                updating = false
            }
        }
        renderer.snapshot = snapshot
        empty.isVisible = next.isEmpty()
        empty.text = if (filter.selectedItem == CollectionFilter.AVAILABLE) CodeXPBundle.message("collection.empty.available") else CodeXPBundle.message("collection.empty")
        refreshDetail()
        sizeCells()
        updateActivity()
        grid.repaint()
    }

    private fun selected(): CollectionEntry? = latest?.entries?.firstOrNull { it.id == selectedId }

    /** Only explicit pointer/keyboard viewing acknowledges NEW; render and refresh never do. */
    internal fun viewSelected() {
        feedback.text = ""
        footer.revalidate()
        revealSelection = grid.selectedIndex >= 0
        refreshDetail()
        val item = selected() ?: return
        motion.emphasize(item.id, item.owned)
        if (item.owned && item.unseen) {
            val result = service.acknowledgeCollection(latest!!.session, item.id)
            apply(service.collectionSnapshot())
            if (result.outcome != CollectionOutcome.APPLIED && result.outcome != CollectionOutcome.UNCHANGED) report(result)
        }
    }

    private fun refreshDetail() {
        val item = selected()
        detailScroll.isVisible = item != null
        detailName.text = item?.name.orEmpty()
        detailName.accessibleContext.accessibleName = detailName.text
        detailName.toolTipText = item?.accessibleName
        status.text =
            item
                ?.let {
                    val kindLabel = if (it.kind == CollectionKind.CREST) CodeXPBundle.message("collection.kind.crest") else CodeXPBundle.message("collection.kind.title")
                    CodeXPBundle.message("collection.status.with.kind", kindLabel, it.status)
                }.orEmpty()
        condition.text = item?.condition.orEmpty()
        progress.text = item?.takeUnless { it.owned }?.progress.orEmpty()
        creditHelp.text = if (item?.choice == true && !item.owned) balance.toolTipText else ""
        primary.isVisible = item != null && (item.owned || item.choice)
        primary.text =
            when {
                item?.equipped == true -> CodeXPBundle.message("collection.unequip")
                item?.owned == true -> CodeXPBundle.message("collection.equip")
                else -> CodeXPBundle.message("collection.acquire", CodeXPGrowthPolicy.CHOICE_COST)
            }
        primary.isEnabled = item?.owned == true || item?.canAcquire == true
        detail.revalidate()
        body.revalidate()
        body.repaint()
    }

    private fun report(result: CollectionResult) {
        feedback.text =
            when (result.outcome) {
                CollectionOutcome.APPLIED, CollectionOutcome.UNCHANGED -> ""
                CollectionOutcome.STALE_SESSION -> CodeXPBundle.message("collection.error.stale")
                CollectionOutcome.INSUFFICIENT_CREDITS -> CodeXPBundle.message("collection.error.credits")
                CollectionOutcome.SAVE_FAILED -> if (latest?.storageProblem != null) "" else CodeXPBundle.message("collection.error.save")
                CollectionOutcome.READ_ONLY_STATE -> CodeXPBundle.message("collection.error.readonly")
                else -> CodeXPBundle.message("collection.error.conditions")
            }
        footer.revalidate()
        revalidate()
    }

    private fun headerHeight(width: Int): Int = JBUI.scale(138) + balanceOverflow(width)

    private fun balanceWidth(): Int = balance.preferredSize.width

    private fun balanceOverflow(width: Int): Int {
        val available = (width - JBUI.scale(24)).coerceAtLeast(1)
        return if (JBUI.scale(46) + heading.preferredSize.width + balanceWidth() > available) balance.preferredSize.height + JBUI.scale(4) else 0
    }

    private fun layoutHeader() {
        val pad = JBUI.scale(12)
        val gap = JBUI.scale(6)
        val w = (header.width - pad * 2).coerceAtLeast(0)
        back.setBounds(pad - JBUI.scale(4), JBUI.scale(6), JBUI.scale(36), JBUI.scale(36))
        heading.setBounds(pad + JBUI.scale(36), JBUI.scale(6), minOf(heading.preferredSize.width, (w - JBUI.scale(36)).coerceAtLeast(0)), JBUI.scale(36))
        val overflow = balanceOverflow(header.width)
        val bw = if (overflow > 0) w else minOf(w, balanceWidth())
        val bh = balance.preferredSize.height
        balance.setBounds(if (overflow > 0) pad else header.width - pad - bw, if (overflow > 0) JBUI.scale(44) else JBUI.scale(24) - bh / 2, bw, bh)
        var y = JBUI.scale(50) + overflow
        val tabWidth = minOf(JBUI.scale(160), ((w - gap) / 2).coerceAtLeast(0))
        crestTab.setBounds(pad, y, tabWidth, JBUI.scale(38))
        titleTab.setBounds(pad + tabWidth + gap, y, tabWidth, JBUI.scale(38))
        y += JBUI.scale(44)
        val filterWidth = minOf(JBUI.scale(100), w / 2)
        search.setBounds(pad, y, (w - filterWidth - gap).coerceAtLeast(0), JBUI.scale(34))
        filter.setBounds(header.width - pad - filterWidth, y, filterWidth, JBUI.scale(34))
    }

    private fun arrangeDetail(
        width: Int,
        place: Boolean = false,
    ): Int {
        if (selected() == null) return 0
        val pad = JBUI.scale(14)
        val w = (width - 2 * pad).coerceAtLeast(1)
        val closeWidth = minOf(w, JBUI.scale(32))
        var y = JBUI.scale(8)
        if (place) {
            status.setBounds(pad, y, (w - closeWidth - JBUI.scale(6)).coerceAtLeast(0), JBUI.scale(32))
            closeDetail.setBounds(pad + w - closeWidth, y, closeWidth, JBUI.scale(32))
        }
        y += JBUI.scale(34)

        fun row(
            text: JTextArea,
            after: Int,
        ) {
            val h = if (text.text.isEmpty()) 0 else textHeight(text, w)
            if (place) text.setBounds(pad, y, w, h)
            if (h > 0) y += h + JBUI.scale(after)
        }
        row(detailName, 8)
        val captionHeight = conditionLabel.preferredSize.height
        if (place) conditionLabel.setBounds(pad, y, w, captionHeight)
        y += captionHeight + JBUI.scale(2)
        row(condition, 3)
        row(progress, 3)
        row(creditHelp, 3)
        if (primary.isVisible) {
            y += JBUI.scale(5)
            if (place) primary.setBounds(pad, y, minOf(w, primary.preferredSize.width.coerceAtLeast(JBUI.scale(128))), JBUI.scale(42))
            y += JBUI.scale(42)
        }
        return y + pad
    }

    private fun arrangeFooter(
        width: Int,
        place: Boolean = false,
    ): Int {
        if (feedback.text.isEmpty() && notice.text.isEmpty()) return 0
        val pad = JBUI.scale(12)
        val w = (width - 2 * pad).coerceAtLeast(1)
        var y = JBUI.scale(6)
        for (text in listOf(feedback, notice)) {
            val h = if (text.text.isEmpty()) 0 else textHeight(text, w)
            if (place) text.setBounds(pad, y, w, h)
            if (h > 0) y += h + JBUI.scale(4)
        }
        if (acknowledgeMigration.isVisible) {
            if (place) acknowledgeMigration.setBounds(pad, y, minOf(w, acknowledgeMigration.preferredSize.width), JBUI.scale(38))
            y += JBUI.scale(38)
        }
        return y + JBUI.scale(6)
    }

    private fun sizeCells() {
        val available = scroll.viewport.width.coerceAtLeast(1)
        val columns = (available / JBUI.scale(158)).coerceIn(1, 4)
        val cellWidth = available / columns
        renderer.rowLayout = columns == 1 && kind == CollectionKind.CREST
        if (grid.fixedCellWidth != cellWidth) grid.fixedCellWidth = cellWidth
        val cellHeight = renderer.cellHeight(kind)
        if (grid.fixedCellHeight != cellHeight) grid.fixedCellHeight = cellHeight
    }

    private fun ownedVisibleCount(): Int {
        val first = grid.firstVisibleIndex
        val last = grid.lastVisibleIndex
        if (first < 0 || last < first) return 0
        return (first..last).count { it < model.size && model[it].owned }
    }

    private fun updateActivity() {
        if (disposed) return
        val window = owner
        val active = window?.isActive == true && (window !is Frame || window.extendedState and Frame.ICONIFIED == 0)
        motion.visibilityChanged(isShowing, active, ownedVisibleCount() > 0)
        if (!motion.isRunning) metrics.pause()
    }

    private fun repaintVisibleArtwork() {
        val first = grid.firstVisibleIndex
        val last = grid.lastVisibleIndex
        if (first < 0 || last < first) return
        for (index in first..last) {
            if (index >= model.size || !model[index].owned) continue
            val bounds = grid.getCellBounds(index, index) ?: continue
            if (!bounds.intersects(grid.visibleRect)) continue
            // Swing may coalesce rectangles, but the clip remains within the visible viewport.
            grid.repaint(bounds.intersection(grid.visibleRect))
        }
    }

    override fun doLayout() {
        val h = headerHeight(width)
        if (cachedHeaderHeight != h) {
            cachedHeaderHeight = h
            header.revalidate()
        }
        super.doLayout()
        body.doLayout()
        updateActivity()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        motion.dispose()
        renderer.dispose()
        owner?.removeWindowListener(windowListener)
        owner = null
        onBack = {}
    }

    companion object {
        internal fun wrappedText(
            size: Float,
            color: java.awt.Color,
        ): JTextArea =
            JTextArea().apply {
                font = dashboardFont(size)
                foreground = color
                isOpaque = false
                isEditable = false
                isFocusable = false
                lineWrap = true
                wrapStyleWord = true
                border = null
            }

        internal fun textHeight(
            text: JTextArea,
            width: Int,
        ): Int {
            val view = text.ui.getRootView(text)
            val insets = text.insets
            view.setSize((width - insets.left - insets.right).coerceAtLeast(1).toFloat(), Float.MAX_VALUE)
            return kotlin.math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS)).toInt() + insets.top + insets.bottom
        }
    }
}
