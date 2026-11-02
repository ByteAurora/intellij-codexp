package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
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
    val nextLevel = dashboardLabel(size = 11f, color = DashboardPalette.muted)
    val crest = DashboardCrest()
    val xpBar = DashboardProgressBar(DashboardPalette.gold)
    val tabs = DashboardTabs()
    val challengeRows = linkedMapOf<Event, DashboardChallengeRow>()
    val activityRows = linkedMapOf<Event, DashboardActivityRow>()
    val historyModel = DefaultListModel<DashboardChallenge>()
    val history = DashboardHistoryList(historyModel)
    val statistics = linkedMapOf<Event, JLabel>()
    val settingsButton = JButton(AllIcons.General.Settings)
    var onNicknameChanged: (String) -> Unit = {}
    var onSettings: () -> Unit = {}
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
    private var activeAndVisible = false
    private var owner: Window? = null
    private val windowListener =
        object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent) = updateVisibility()

            override fun windowDeactivated(e: WindowEvent) = visibilityChanged(isShowing, false)

            override fun windowIconified(e: WindowEvent) = visibilityChanged(false, false)

            override fun windowDeiconified(e: WindowEvent) = updateVisibility()

            override fun windowClosed(e: WindowEvent) = visibilityChanged(false, false)
        }
    private val profile =
        object : JPanel(null) {
            init {
                isOpaque = false
            }

            override fun doLayout() {
                val inset = JBUI.scale(16)
                val contentWidth = (width - inset * 2).coerceAtLeast(0)
                settingsButton.setBounds((width - JBUI.scale(34)).coerceAtLeast(0), JBUI.scale(4), JBUI.scale(28), JBUI.scale(28))
                nickname.setBounds(inset, JBUI.scale(36), contentWidth, JBUI.scale(28))
                val crestHeight = (height - JBUI.scale(128)).coerceAtLeast(0)
                crest.setBounds(JBUI.scale(8), JBUI.scale(64), (width - JBUI.scale(16)).coerceAtLeast(0), crestHeight)
                val y = JBUI.scale(64) + crestHeight
                val nextWidth = minOf(contentWidth / 3, JBUI.scale(72))
                xpText.setBounds(inset, y, (contentWidth - nextWidth).coerceAtLeast(0), JBUI.scale(22))
                nextLevel.setBounds(width - inset - nextWidth, y, nextWidth, JBUI.scale(22))
                xpBar.setBounds(inset, y + JBUI.scale(26), contentWidth, JBUI.scale(5))
                remaining.setBounds(inset, y + JBUI.scale(39), contentWidth, JBUI.scale(22))
            }
        }
    internal val isAnimationRunning: Boolean get() = ambient.isRunning
    internal val targetXP: Long get() = latest?.xp ?: 0

    init {
        background = DashboardPalette.background
        minimumSize = JBUI.size(230, 300)
        settingsButton.apply {
            toolTipText = "CodeXP settings"
            accessibleContext.accessibleName = toolTipText
            isContentAreaFilled = false
            isBorderPainted = false
            addActionListener { onSettings() }
        }
        nickname.apply {
            font = dashboardFont(16f, java.awt.Font.BOLD)
            foreground = DashboardPalette.foreground
            caretColor = DashboardPalette.foreground
            horizontalAlignment = JLabel.CENTER
            isOpaque = false
            border = JBUI.Borders.empty(3)
            emptyText.text = "Your nickname"
            toolTipText = "Edit your nickname"
            accessibleContext.accessibleName = "Nickname"
        }
        nextLevel.horizontalAlignment = JLabel.RIGHT
        remaining.horizontalAlignment = JLabel.RIGHT
        listOf(settingsButton, nickname, crest, xpText, nextLevel, xpBar, remaining).forEach(profile::add)
        val summary =
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(profile, BorderLayout.CENTER)
                add(metrics(), BorderLayout.SOUTH)
            }
        add(summary, BorderLayout.NORTH)
        tabs.addTab("Challenges", scroll(challenges))
        val historyScroll = scroll(history)
        historyScroll.viewport.addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    history.fixedCellWidth = historyScroll.viewport.width.coerceAtLeast(1)
                }
            },
        )
        tabs.addTab("History", historyScroll)
        val stats = DashboardColumn()
        Event.entries.filter { it != Event.NONE }.forEach { event ->
            val row = DashboardActivityRow(event)
            activityRows[event] = row
            statistics[event] = row.count
            stats.add(row)
        }
        tabs.addTab("Activity", scroll(stats))
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
        val byHeight = (height - JBUI.scale(128 + 60 + 140)).coerceAtLeast(JBUI.scale(72))
        val desired = Dimension(width, JBUI.scale(128) + minOf(JBUI.scale(210), byWidth, byHeight))
        if (profile.preferredSize != desired) profile.preferredSize = desired
        super.doLayout()
    }

    private fun metrics(): JPanel =
        JPanel(GridLayout(1, 2, JBUI.scale(20), 0)).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, DashboardPalette.line), JBUI.Borders.empty(8, 16))
            add(metric("Total XP", totalXP))
            add(metric("Completed", completedCount))
            preferredSize = JBUI.size(230, 60)
        }

    private fun metric(
        title: String,
        value: JLabel,
    ): JPanel =
        JPanel(GridLayout(2, 1)).apply {
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
        val level = dashboardLevel(snapshot.xp)
        totalXP.text = number(snapshot.xp)
        xpText.text = "${number(level.into)} / ${number(level.required)} XP"
        remaining.text = "${number(level.remaining)} XP to level ${level.level + 1}"
        nextLevel.text = "Lv.${level.level + 1}"
        crest.level = level
        crest.accessibleContext.accessibleName = "Level ${level.level}"
        xpBar.accessibleContext.accessibleName = xpText.text
        xpBar.fraction = level.fraction
        completedCount.text = number(snapshot.completed.size.toLong())
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
    }

    private fun updateVisibility() {
        val window = owner
        visibilityChanged(isShowing && (window !is Frame || window.extendedState and Frame.ICONIFIED == 0), window?.isActive == true)
    }

    internal fun visibilityChanged(
        visible: Boolean,
        windowActive: Boolean = true,
    ) {
        if (disposed) return
        activeAndVisible = visible && windowActive
        ambient.visibilityChanged(visible, windowActive)
        updateListVisibility()
        if (visible) onBecameVisible()
    }

    internal fun tick(now: Long) = ambient.tick(now)

    private fun updateListVisibility() {
        challenges.motion.visibilityChanged(activeAndVisible && tabs.selectedIndex == 0)
        history.motion.visibilityChanged(activeAndVisible && tabs.selectedIndex == 1)
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
