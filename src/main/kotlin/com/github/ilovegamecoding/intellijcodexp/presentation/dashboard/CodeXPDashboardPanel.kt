package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionDisplaySnapshot
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPButton
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPButtonStyle
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Frame
import java.awt.GridLayout
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Values render immediately; decoration and retained list positions animate independently. */
internal class CodeXPDashboardPanel(
    osReducedMotion: () -> Boolean = ::systemReducedMotion,
) : JPanel(BorderLayout()),
    Disposable {
    val nickname = JBTextField()
    val totalXP = dashboardLabel(size = 18f)
    val completedCount = dashboardLabel(size = 18f)
    val xpText = dashboardLabel(size = 12f, color = DashboardPalette.gold)
    val remaining = dashboardLabel(size = 11f, color = DashboardPalette.muted)
    val crest = DashboardCrest()
    val xpBar = DashboardProgressBar(DashboardPalette.gold)
    val xpMarker = DashboardProgressMarker()
    private val controlsReducedMotion = { latest?.reducedMotion == true || osReducedMotion() }
    val tabs = DashboardTabs(controlsReducedMotion)
    val challengeRows = linkedMapOf<Event, DashboardChallengeRow>()
    val activityRows = linkedMapOf<Event, DashboardActivityRow>()
    val historyModel = DefaultListModel<DashboardChallenge>()
    val history = DashboardHistoryList(historyModel)
    val statistics = linkedMapOf<Event, JLabel>()
    val settingsButton: JButton = CodeXPButton(CodeXPButtonStyle.QUIET, controlsReducedMotion, icon = AllIcons.General.Settings)
    val guideButton: JButton = CodeXPButton(CodeXPButtonStyle.QUIET, controlsReducedMotion, icon = AllIcons.General.ContextHelp)

    // The bundled artwork exists in 2023.3, before its AllIcons.General.Groups field.
    val collectionButton: JButton =
        CodeXPButton(
            CodeXPButtonStyle.SECONDARY,
            controlsReducedMotion,
            icon = IconLoader.findIcon("/expui/general/groups.svg", AllIcons::class.java) ?: AllIcons.Nodes.Artifact,
        )
    val choiceBalance = DashboardChoiceBalance()
    val equippedTitle = dashboardLabel(size = 12f, color = DashboardPalette.gold)
    var onCollectionRequested: () -> Unit = {}
    var onNicknameChanged: (String) -> Unit = {}
    var onSettings: () -> Unit = {}
    var onGuide: () -> Unit = {}
    var onBecameVisible: () -> Unit = {}
    val challenges = DashboardChallengeColumn()
    private var historySnapshot: List<DashboardChallenge>? = null
    private val ambient =
        DashboardAmbient(crest, osReducedMotion) { reduced ->
            challenges.motion.configure(latest?.subtle ?: false, reduced)
            history.motion.configure(latest?.subtle ?: false, reduced)
        }
    private var latest: DashboardSnapshot? = null
    private var updatingNickname = false
    private var disposed = false
    private var dashboardVisible = false
    internal val isDashboardVisible: Boolean get() = dashboardVisible
    private var owner: Window? = null
    private val windowListener =
        object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent) = updateVisibility()

            override fun windowIconified(e: WindowEvent) = visibilityChanged(false)

            override fun windowDeiconified(e: WindowEvent) = updateVisibility()

            override fun windowClosed(e: WindowEvent) = visibilityChanged(false)
        }
    private val profile =
        object : JPanel(null) {
            init {
                isOpaque = false
            }

            override fun doLayout() {
                val inset = JBUI.scale(16)
                val contentWidth = (width - inset * 2).coerceAtLeast(0)
                settingsButton.setBounds((width - JBUI.scale(42)).coerceAtLeast(0), JBUI.scale(4), JBUI.scale(36), JBUI.scale(36))
                guideButton.setBounds((width - JBUI.scale(80)).coerceAtLeast(0), JBUI.scale(4), JBUI.scale(36), JBUI.scale(36))
                collectionButton.setBounds(inset - JBUI.scale(3), JBUI.scale(4), JBUI.scale(36), JBUI.scale(36))
                val creditHeight = if (choiceBalance.isVisible) JBUI.scale(28) else 0
                choiceBalance.setBounds(inset, JBUI.scale(46), contentWidth, creditHeight)
                nickname.setBounds(inset, JBUI.scale(48) + creditHeight, contentWidth, JBUI.scale(28))
                val titleHeight = if (equippedTitle.isVisible) JBUI.scale(22) else 0
                equippedTitle.setBounds(inset, JBUI.scale(76) + creditHeight, contentWidth, titleHeight)
                val gap = JBUI.scale(12)
                val summaryWidth = remaining.preferredSize.width.coerceAtMost(contentWidth)
                // Numeric changes must not resize the crest; reflow only when the panel is resized.
                val stacked = contentWidth < JBUI.scale(360)
                val headerHeight = JBUI.scale(if (stacked) 44 else 22)
                val crestHeight = (height - JBUI.scale(76 + 44) - headerHeight - titleHeight - creditHeight).coerceAtLeast(0)
                crest.setBounds(JBUI.scale(8), JBUI.scale(76) + titleHeight + creditHeight, (width - JBUI.scale(16)).coerceAtLeast(0), crestHeight)
                val y = JBUI.scale(76) + titleHeight + creditHeight + crestHeight
                xpText.setBounds(inset, y, if (stacked) contentWidth else (contentWidth - summaryWidth - gap).coerceAtLeast(0), JBUI.scale(22))
                remaining.setBounds(width - inset - summaryWidth, y + if (stacked) JBUI.scale(22) else 0, summaryWidth, JBUI.scale(22))
                xpBar.setBounds(inset, y + headerHeight + JBUI.scale(4), contentWidth, JBUI.scale(5))
                xpMarker.setBounds(0, xpBar.y + xpBar.height + JBUI.scale(3), width, JBUI.scale(28))
            }
        }
    private val summaryScroll: JBScrollPane
    internal val isAnimationRunning: Boolean get() = ambient.isRunning
    internal val targetXP: Long get() = latest?.xp ?: 0

    init {
        background = DashboardPalette.background
        minimumSize = JBUI.size(230, 300)
        settingsButton.apply {
            toolTipText = CodeXPBundle.message("dashboard.settings")
            accessibleContext.accessibleName = toolTipText
            isContentAreaFilled = false
            isBorderPainted = false
            addActionListener { onSettings() }
        }
        guideButton.apply {
            toolTipText = CodeXPBundle.message("guide.open")
            accessibleContext.accessibleName = toolTipText
            isContentAreaFilled = false
            isBorderPainted = false
            addActionListener { onGuide() }
        }
        nickname.apply {
            font = dashboardFont(16f, java.awt.Font.BOLD)
            foreground = DashboardPalette.foreground
            caretColor = DashboardPalette.foreground
            horizontalAlignment = JLabel.CENTER
            isOpaque = false
            border = JBUI.Borders.empty(3)
            emptyText.text = CodeXPBundle.message("dashboard.nickname.hint")
            toolTipText = CodeXPBundle.message("dashboard.nickname.edit")
            accessibleContext.accessibleName = CodeXPBundle.message("dashboard.nickname")
        }
        remaining.horizontalAlignment = JLabel.RIGHT
        collectionButton.isVisible = false
        collectionButton.toolTipText = CodeXPBundle.message("collection.heading")
        collectionButton.accessibleContext.accessibleName = collectionButton.toolTipText
        listOf(settingsButton, guideButton, collectionButton).forEach { it.border = JBUI.Borders.empty(4) }
        collectionButton.addActionListener { onCollectionRequested() }
        choiceBalance.isVisible = false
        equippedTitle.horizontalAlignment = JLabel.CENTER
        equippedTitle.isVisible = false
        listOf(settingsButton, guideButton, collectionButton, choiceBalance, nickname, equippedTitle, crest, xpText, xpBar, remaining, xpMarker).forEach(profile::add)
        val summary =
            DashboardColumn().apply {
                layout = BorderLayout()
                add(profile, BorderLayout.CENTER)
                add(metrics(), BorderLayout.SOUTH)
            }
        summaryScroll = scroll(summary).apply { border = JBUI.Borders.empty() }
        add(summaryScroll, BorderLayout.NORTH)
        tabs.addTab(CodeXPBundle.message("TEXT_CHALLENGES"), scroll(challenges))
        val historyScroll = scroll(history)
        historyScroll.viewport.addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    history.fixedCellWidth = historyScroll.viewport.width.coerceAtLeast(1)
                }
            },
        )
        tabs.addTab(CodeXPBundle.message("dashboard.history"), historyScroll)
        val stats = DashboardColumn()
        Event.entries.filter { it != Event.NONE }.forEach { event ->
            val row = DashboardActivityRow(event)
            activityRows[event] = row
            statistics[event] = row.count
            stats.add(row)
        }
        tabs.addTab(CodeXPBundle.message("dashboard.activity"), scroll(stats))
        tabs.addChangeListener { updateListVisibility() }
        add(tabs, BorderLayout.CENTER)
        nickname.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = changed()

                override fun removeUpdate(e: DocumentEvent) = changed()

                override fun changedUpdate(e: DocumentEvent) = changed()

                private fun changed() {
                    if (!disposed && !updatingNickname) onNicknameChanged(nickname.text)
                }
            },
        )
        addHierarchyListener { event ->
            if (disposed) return@addHierarchyListener
            if (event.changeFlags and (HierarchyEvent.SHOWING_CHANGED or HierarchyEvent.PARENT_CHANGED).toLong() != 0L) {
                val newOwner = SwingUtilities.getWindowAncestor(this)
                if (newOwner !== owner) {
                    owner?.removeWindowListener(windowListener)
                    owner = newOwner
                    owner?.addWindowListener(windowListener)
                }
                updateVisibility()
            }
        }
    }

    override fun doLayout() {
        // Text preferred sizes cannot move the profile center or change the crest's allocated bounds.
        val byWidth = ((width - JBUI.scale(16)).coerceAtLeast(0) * 210 / 240)
        val creditHeight = if (choiceBalance.isVisible) JBUI.scale(28) else 0
        val byHeight = (height - JBUI.scale(140 + 60 + 140) - creditHeight).coerceAtLeast(JBUI.scale(72))
        val desired = Dimension(width, JBUI.scale(140) + creditHeight + minOf(JBUI.scale(210), byWidth, byHeight))
        if (profile.preferredSize != desired) profile.preferredSize = desired
        // Let the profile scroll before the tab strip, its insets and the first row lose their space.
        val listHeight = tabs.minimumSize.height + JBUI.scale(16) + challenges.motion.rowHeight
        val summaryHeight = minOf(desired.height + JBUI.scale(60), (height - listHeight).coerceAtLeast(0))
        val extent = Dimension(width, summaryHeight)
        if (summaryScroll.preferredSize != extent) summaryScroll.preferredSize = extent
        super.doLayout()
    }

    private fun metrics(): JPanel =
        JPanel(GridLayout(1, 2, JBUI.scale(20), 0)).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, DashboardPalette.line), JBUI.Borders.empty(8, 16))
            add(metric(CodeXPBundle.message("TEXT_TOTAL_XP"), totalXP))
            add(metric(CodeXPBundle.message("dashboard.completed"), completedCount))
            preferredSize = JBUI.size(230, 60)
        }

    private fun metric(
        title: String,
        value: JLabel,
    ): JPanel =
        object : JPanel(GridLayout(2, 1)) {
            override fun doLayout() {
                super.doLayout()
                value.font = dashboardFont(18f)
                while (value.font.size2D > dashboardFont(11f).size2D && value.getFontMetrics(value.font).stringWidth(value.text) > value.width) {
                    value.font = value.font.deriveFont((value.font.size2D - 1f).coerceAtLeast(dashboardFont(11f).size2D))
                }
            }
        }.apply {
            isOpaque = false
            add(dashboardLabel(title, 11f, DashboardPalette.muted))
            add(value)
        }

    fun apply(
        snapshot: DashboardSnapshot,
        changedIds: Set<String> = emptySet(),
    ) {
        if (disposed) return
        latest = snapshot
        ambient.configure(snapshot.subtle, snapshot.reducedMotion)
        if (nickname.text != snapshot.nickname) {
            updatingNickname = true
            try {
                nickname.text = snapshot.nickname
            } finally {
                updatingNickname = false
            }
        }
        val level = dashboardLevel(snapshot.xp, CodeXPGrowthPolicy.levelInfo(snapshot.xp, snapshot.levelOffset))
        totalXP.text = number(snapshot.xp)
        totalXP.toolTipText = totalXP.text
        val capped = level.level >= CodeXPGrowthPolicy.MAX_LEVEL
        xpText.text = if (capped) "MAX" else "${number(level.into)} / ${number(level.required)} XP"
        xpText.toolTipText = null
        remaining.text = if (capped) CodeXPBundle.message("dashboard.level.max", CodeXPGrowthPolicy.MAX_LEVEL) else CodeXPBundle.message("dashboard.next.xp", number(level.remaining), level.level + 1)
        remaining.toolTipText = if (capped) CodeXPBundle.message("dashboard.max.description") else remaining.text
        crest.level = level
        crest.accessibleContext.accessibleName = CodeXPBundle.message("dashboard.level.accessible", level.level)
        xpBar.accessibleContext.accessibleName = xpText.text
        xpBar.fraction = level.fraction
        xpMarker.fraction = xpBar.fraction
        completedCount.text = number(snapshot.completed.size.toLong())
        completedCount.toolTipText = completedCount.text
        val events = snapshot.active.map { it.event }.toSet()
        challengeRows.keys.filter { it !in events }.forEach { event ->
            challengeRows.remove(event)
        }
        snapshot.active.forEach { challenge ->
            val row = challengeRows.getOrPut(challenge.event) { DashboardChallengeRow() }
            row.setFinal(challenge)
        }
        challenges.update(snapshot.active.map { it.id to challengeRows.getValue(it.event) }, changedIds)
        if (historySnapshot !== snapshot.completed) {
            historySnapshot = snapshot.completed
            history.update(snapshot.completed, changedIds)
        }
        snapshot.counts.forEach { (event, count) -> statistics[event]?.text = number(count) }
        crest.repaint()
        profile.revalidate()
    }

    fun applyCollection(snapshot: CollectionDisplaySnapshot) {
        if (disposed) return
        collectionButton.isVisible = snapshot.entries.isNotEmpty()
        choiceBalance.isVisible = snapshot.entries.isNotEmpty()
        equippedTitle.text = snapshot.equippedTitle.orEmpty()
        equippedTitle.isVisible = snapshot.equippedTitle != null
        crest.collectionDesign = snapshot.equippedCrest
        crest.accessibleContext.accessibleName = CodeXPBundle.message("dashboard.level.accessible", snapshot.displayedLevel)
        choiceBalance.update(snapshot.credits, snapshot.choiceCreditHelp)
        if (snapshot.displayedLevel >= CodeXPGrowthPolicy.MAX_LEVEL) {
            remaining.text = CodeXPBundle.message("dashboard.level.max", CodeXPGrowthPolicy.MAX_LEVEL)
            remaining.toolTipText = if (snapshot.previousLevel > CodeXPGrowthPolicy.MAX_LEVEL) CodeXPBundle.message("dashboard.level.legacy", snapshot.previousLevel) else CodeXPBundle.message("dashboard.max.description")
            xpText.text = "${number(snapshot.choiceProgressXP)} / ${number(snapshot.choiceIntervalXP)} XP"
            xpText.toolTipText = snapshot.choiceCreditHelp
            xpBar.fraction = snapshot.choiceProgressXP.toDouble() / snapshot.choiceIntervalXP
            xpMarker.fraction = xpBar.fraction
            xpBar.accessibleContext.accessibleName = CodeXPBundle.message("collection.credits.progress", xpText.text)
        }
        profile.revalidate()
        crest.repaint()
    }

    private fun updateVisibility() {
        val window = owner
        visibilityChanged(isShowing && (window !is Frame || window.extendedState and Frame.ICONIFIED == 0))
    }

    internal fun visibilityChanged(visible: Boolean) {
        if (disposed) return
        dashboardVisible = visible
        ambient.visibilityChanged(visible)
        updateListVisibility()
        if (visible) onBecameVisible()
    }

    internal fun tick(now: Long) = ambient.tick(now)

    private fun updateListVisibility() {
        challenges.motion.visibilityChanged(dashboardVisible && tabs.selectedIndex == 0)
        history.motion.visibilityChanged(dashboardVisible && tabs.selectedIndex == 1)
    }

    fun clearTransientEffects() {
        challenges.motion.visibilityChanged(false)
        history.motion.visibilityChanged(false)
        updateListVisibility()
    }

    override fun dispose() {
        disposed = true
        ambient.dispose()
        challenges.dispose()
        history.dispose()
        owner?.removeWindowListener(windowListener)
        owner = null
        onNicknameChanged = {}
        onBecameVisible = {}
        onSettings = {}
    }
}

private fun scroll(component: Component): JBScrollPane =
    JBScrollPane(component).apply {
        // The empty border, scrollbar gutter and viewport must share the dashboard's canvas.
        // Otherwise the LAF paints a different rectangle around and below the Activity rows.
        background = DashboardPalette.background
        border = JBUI.Borders.empty(10, 16, 6, 16)
        viewportBorder = JBUI.Borders.empty()
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        viewport.background = DashboardPalette.background
        verticalScrollBar.background = DashboardPalette.background
        verticalScrollBar.unitIncrement = JBUI.scale(20)
        minimumSize = JBUI.size(0, 0)
    }

private class DashboardColumn :
    JPanel(),
    Scrollable {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = DashboardPalette.background
    }

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
}
