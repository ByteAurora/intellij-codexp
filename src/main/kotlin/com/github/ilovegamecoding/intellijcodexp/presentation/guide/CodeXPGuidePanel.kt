package com.github.ilovegamecoding.intellijcodexp.presentation.guide

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.ChoiceCreditIcon
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CodeXPCollectionPanel.Companion.wrappedText
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPButton
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPButtonStyle
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPStepIndicator
import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPTab
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardCrest
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.dashboardFont
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.dashboardLabel
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.dashboardLevel
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.number
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.systemReducedMotion
import com.github.ilovegamecoding.intellijcodexp.presentation.smoothArtwork
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPGuideService
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.ButtonGroup
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.Scrollable
import javax.swing.SwingUtilities

/** Non-modal, scrollable guide. Preview art reads real state and never awards or spends anything. */
internal class CodeXPGuidePanel(
    private val guide: CodeXPGuideService,
    private val progress: CodeXPService,
    private val onClose: () -> Unit,
    private val onDestination: (GuideDestination) -> Unit,
) : JPanel(BorderLayout()) {
    private val reducedMotion = { progress.state.codeXPConfiguration.reducedMotion || systemReducedMotion() }
    val introTab = CodeXPTab(CodeXPBundle.message("guide.tab.intro"), reducedMotion)
    val updatesTab = CodeXPTab(CodeXPBundle.message("guide.tab.updates"), reducedMotion)
    val close = CodeXPButton(CodeXPButtonStyle.QUIET, reducedMotion, icon = AllIcons.Actions.Close)
    val previous = CodeXPButton(CodeXPButtonStyle.SECONDARY, reducedMotion, icon = AllIcons.Actions.Back)
    val next = CodeXPButton(CodeXPButtonStyle.PRIMARY, reducedMotion)
    val skip = CodeXPButton(CodeXPButtonStyle.QUIET, reducedMotion, CodeXPBundle.message("guide.skip"))
    val destination = CodeXPButton(CodeXPButtonStyle.LINK, reducedMotion, icon = AllIcons.Actions.Forward)
    val steps = CodeXPStepIndicator()
    val page = dashboardLabel(size = 11f, color = DashboardPalette.muted)
    val heading = wrappedText(22f, DashboardPalette.foreground)
    val description = wrappedText(13f, DashboardPalette.foreground)
    val preview = JPanel(BorderLayout()).apply { isOpaque = false }
    private var run = guide.open()
    private var index = 0
    val body: JPanel =
        object : JPanel(null), Scrollable {
            init {
                isOpaque = false
            }

            private fun arrange(place: Boolean): Int {
                val available = minOf(JBUI.scale(500), (width - JBUI.scale(40)).coerceAtLeast(1))
                val inset = (width - available) / 2
                var y = JBUI.scale(12)

                fun row(
                    component: javax.swing.JComponent,
                    h: Int,
                    gap: Int,
                ) {
                    if (place) component.setBounds(inset, y, available, h)
                    y += h + JBUI.scale(gap)
                }
                row(preview, JBUI.scale(148), 20)
                for (text in listOf(heading, description)) {
                    text.setSize(available, 10000)
                    row(text, text.preferredSize.height, if (text === heading) 12 else 20)
                }
                if (place) destination.setBounds(inset, y, minOf(available, destination.preferredSize.width), JBUI.scale(40))
                y += JBUI.scale(56)
                return y
            }

            override fun doLayout() {
                arrange(true)
            }

            override fun getPreferredSize(): Dimension = Dimension(width, arrange(false))

            override fun getPreferredScrollableViewportSize(): Dimension = JBUI.size(280, 420)

            override fun getScrollableTracksViewportWidth(): Boolean = true

            override fun getScrollableTracksViewportHeight(): Boolean = false

            override fun getScrollableUnitIncrement(
                visibleRect: Rectangle,
                orientation: Int,
                direction: Int,
            ): Int = JBUI.scale(24)

            override fun getScrollableBlockIncrement(
                visibleRect: Rectangle,
                orientation: Int,
                direction: Int,
            ): Int = visibleRect.height.coerceAtLeast(1)
        }
    val scroll =
        JBScrollPane(body).apply {
            border = JBUI.Borders.empty()
            viewport.background = DashboardPalette.background
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

    init {
        background = DashboardPalette.background
        minimumSize = JBUI.size(230, 300)
        heading.font = dashboardFont(22f, Font.BOLD)
        for (tab in listOf(introTab, updatesTab)) {
            tab.font = dashboardFont(12f)
            tab.border = JBUI.Borders.empty(6, 8)
        }
        ButtonGroup().apply {
            add(introTab)
            add(updatesTab)
        }
        val tabs =
            object : JPanel(GridLayout(1, 2, 0, 0)) {
                override fun paintComponent(graphics: Graphics) {
                    val g = graphics.create() as Graphics2D
                    try {
                        g.smoothArtwork()
                        g.color = DashboardPalette.track
                        g.fillRoundRect(0, 0, width, height, JBUI.scale(16), JBUI.scale(16))
                    } finally {
                        g.dispose()
                    }
                }
            }.apply {
                isOpaque = false
                border = JBUI.Borders.empty(3)
                add(introTab)
                add(updatesTab)
            }
        val header =
            object : JPanel(null) {
                override fun getPreferredSize(): Dimension = JBUI.size(280, 72)

                override fun doLayout() {
                    val space = (width - JBUI.scale(if (close.isVisible) 80 else 32)).coerceAtLeast(0)
                    val tabWidth = minOf(JBUI.scale(280), space)
                    tabs.setBounds((width - tabWidth) / 2 - JBUI.scale(if (close.isVisible) 20 else 0), JBUI.scale(16), tabWidth, JBUI.scale(40))
                    close.setBounds(width - JBUI.scale(52), JBUI.scale(16), JBUI.scale(40), JBUI.scale(40))
                }
            }.apply {
                isOpaque = false
                add(tabs)
                add(close)
            }
        add(header, BorderLayout.NORTH)
        listOf(preview, heading, description, destination).forEach(body::add)
        add(scroll, BorderLayout.CENTER)
        val footer =
            object : JPanel(null) {
                override fun getPreferredSize(): Dimension = JBUI.size(280, 136)

                override fun doLayout() {
                    val available = minOf(JBUI.scale(500), (width - JBUI.scale(40)).coerceAtLeast(1))
                    val x = (width - available) / 2
                    page.setBounds(x, JBUI.scale(4), available, JBUI.scale(18))
                    val indicatorWidth = minOf(JBUI.scale(120), available)
                    steps.setBounds((width - indicatorWidth) / 2, JBUI.scale(26), indicatorWidth, JBUI.scale(8))
                    previous.setBounds(x, JBUI.scale(44), JBUI.scale(44), JBUI.scale(44))
                    val nextWidth = minOf(JBUI.scale(160), (available - JBUI.scale(56)).coerceAtLeast(0))
                    next.setBounds(x + available - nextWidth, JBUI.scale(44), nextWidth, JBUI.scale(44))
                    skip.setBounds(x, JBUI.scale(96), available, JBUI.scale(32))
                }
            }.apply {
                isOpaque = false
                listOf(page, steps, previous, next, skip).forEach(::add)
            }
        add(footer, BorderLayout.SOUTH)
        page.horizontalAlignment = JLabel.CENTER
        destination.horizontalTextPosition = JLabel.LEFT
        destination.horizontalAlignment = JLabel.LEFT
        destination.border = JBUI.Borders.empty(8, 4)
        skip.font = dashboardFont(12f)
        close.toolTipText = CodeXPBundle.message("guide.later")
        previous.toolTipText = CodeXPBundle.message("guide.previous")
        for (button in listOf(close, previous)) button.accessibleContext.accessibleName = button.toolTipText
        for (tab in listOf(introTab, updatesTab)) {
            for ((key, target) in listOf("LEFT" to introTab, "RIGHT" to updatesTab)) {
                tab.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key)
                tab.actionMap.put(
                    key,
                    object : AbstractAction() {
                        override fun actionPerformed(e: ActionEvent) {
                            if (!target.isSelected) target.doClick(0)
                            target.requestFocusInWindow()
                        }
                    },
                )
            }
        }
        introTab.addActionListener { showGuide(true) }
        updatesTab.addActionListener { showGuide(false) }
        close.addActionListener {
            guide.remember(run, index)
            onClose()
        }
        skip.addActionListener {
            guide.complete(run)
            onClose()
        }
        previous.addActionListener {
            if (index > 0) {
                index--
                render()
            }
        }
        next.addActionListener {
            if (index == run.steps.lastIndex) {
                guide.complete(run)
                onClose()
            } else {
                index++
                render()
            }
        }
        destination.addActionListener {
            guide.remember(run, index)
            onDestination(run.steps[index].destination)
        }
    }

    fun showGuide(intro: Boolean? = null) {
        run = guide.open(intro)
        index = guide.page(run)
        render()
    }

    private fun render() {
        guide.remember(run, index)
        val step = run.steps[index]
        introTab.isSelected = run.intro
        updatesTab.isSelected = !run.intro
        val edition = if (run.intro) CodeXPBundle.message("guide.tab.intro") else run.releases.joinToString(", ") { it.version }
        page.text = CodeXPBundle.message("guide.page", index + 1, run.steps.size, edition)
        steps.maximum = run.steps.size
        steps.value = index + 1
        steps.accessibleContext.accessibleName = page.text
        heading.text = CodeXPBundle.message("guide.${step.id}.title")
        description.text = CodeXPBundle.message("guide.${step.id}.body", CodeXPGrowthPolicy.MAX_LEVEL, number(CodeXPGrowthPolicy.CHOICE_INTERVAL_XP), number(CodeXPGrowthPolicy.CHOICE_INTERVAL_INCREMENT_XP), number(CodeXPGrowthPolicy.CHOICE_MAX_INTERVAL_XP))
        destination.text = CodeXPBundle.message("guide.open.${step.destination.name.lowercase()}")
        val focusWasPrevious = previous.isFocusOwner
        previous.isVisible = index > 0
        previous.isEnabled = index > 0
        if (!previous.isVisible && focusWasPrevious) next.requestFocusInWindow()
        next.text = CodeXPBundle.message(if (index == run.steps.lastIndex) "guide.done" else "guide.next")
        skip.text = CodeXPBundle.message(if (run.intro) "guide.skip" else "guide.dismiss.update")
        preview.removeAll()
        val snapshot = progress.collectionSnapshot()

        fun crest(design: com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign?) =
            DashboardCrest().apply {
                level = dashboardLevel(progress.state.xp, progress.state.levelInfo())
                collectionDesign = design
                accessibleContext.accessibleName = CodeXPBundle.message("dashboard.level.accessible", snapshot.displayedLevel)
            }
        when (step.id) {
            "credits" -> {
                preview.add(
                    JLabel(number(snapshot.credits), ChoiceCreditIcon(64), JLabel.CENTER).apply {
                        font = dashboardFont(28f, Font.BOLD)
                        foreground = DashboardPalette.gold
                        iconTextGap = JBUI.scale(16)
                        accessibleContext.accessibleName = CodeXPBundle.message("collection.credits.balance", text)
                    },
                    BorderLayout.CENTER,
                )
                preview.add(dashboardLabel(CodeXPBundle.message("guide.your.credits"), 12f, DashboardPalette.muted).apply { horizontalAlignment = JLabel.CENTER }, BorderLayout.SOUTH)
            }

            "collection" -> {
                val examples = snapshot.entries.filter { it.owned && it.kind == CollectionKind.CREST }.take(2)
                val art = JPanel(GridLayout(1, examples.size.coerceAtLeast(1))).apply { isOpaque = false }
                examples.forEach { art.add(crest(it.design)) }
                if (examples.isEmpty()) art.add(crest(null))
                preview.add(art)
            }

            "challenges" -> {
                preview.add(JLabel(AllIcons.General.InspectionsOK), BorderLayout.NORTH)
                preview.add(
                    dashboardLabel(
                        number(
                            progress.state.completedChallenges.size
                                .toLong(),
                        ),
                        40f,
                        DashboardPalette.gold,
                    ).apply { horizontalAlignment = JLabel.CENTER },
                )
                preview.add(dashboardLabel(CodeXPBundle.message("dashboard.completed"), 12f, DashboardPalette.muted).apply { horizontalAlignment = JLabel.CENTER }, BorderLayout.SOUTH)
            }

            else -> {
                preview.add(crest(snapshot.equippedCrest))
            }
        }
        body.revalidate()
        revalidate()
        repaint()
        SwingUtilities.invokeLater { scroll.verticalScrollBar.value = 0 }
    }
}
