package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.presentation.controls.CodeXPTab
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/** A small retained card switcher, avoiding recursive Darcula scroll-tab layout during IDE restoration. */
internal class DashboardTabs(
    private val reducedMotion: () -> Boolean = ::systemReducedMotion,
) : JPanel(BorderLayout()) {
    private val strip =
        object : JPanel(null) {
            init {
                isOpaque = false
                border = JBUI.Borders.empty(6, 12)
                preferredSize = JBUI.size(230, 50)
                minimumSize = JBUI.size(0, 50)
            }

            override fun doLayout() {
                if (buttons.isEmpty()) return
                val gap = JBUI.scale(4)
                val available = (width - insets.left - insets.right).coerceAtLeast(0)
                val stacked = stackedTabs(width)
                val spare = ((available - buttons.sumOf { it.preferredSize.width } - gap * (buttons.size - 1)) / buttons.size).coerceAtLeast(0)
                var x = insets.left
                var y = insets.top
                buttons.forEach { button ->
                    val w = if (stacked) available else button.preferredSize.width + spare
                    button.setBounds(x, y, w, tabHeight())
                    if (stacked) y += tabHeight() + gap else x += w + gap
                }
            }
        }
    private val cards = JPanel(CardLayout()).apply { isOpaque = false }
    private val group = ButtonGroup()
    private val buttons = mutableListOf<JToggleButton>()
    private val listeners = mutableListOf<ChangeListener>()
    val tabCount: Int get() = buttons.size
    var selectedIndex = 0
        set(value) {
            if (value !in buttons.indices) return
            val changed = field != value
            field = value
            (cards.layout as CardLayout).show(cards, value.toString())
            buttons.forEachIndexed { index, button ->
                button.isSelected = index == value
            }
            if (changed) listeners.toList().forEach { it.stateChanged(ChangeEvent(this)) }
        }

    init {
        isOpaque = false
        add(strip, BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
    }

    private fun tabHeight(): Int = maxOf(JBUI.scale(38), buttons.maxOfOrNull { it.preferredSize.height } ?: 0)

    private fun stackedTabs(width: Int): Boolean = buttons.sumOf { it.preferredSize.width } + JBUI.scale(4) * (buttons.size - 1).coerceAtLeast(0) > width - strip.insets.left - strip.insets.right

    override fun doLayout() {
        val rows = if (stackedTabs(width)) buttons.size.coerceAtLeast(1) else 1
        strip.preferredSize = Dimension(width, strip.insets.top + strip.insets.bottom + rows * tabHeight() + (rows - 1) * JBUI.scale(4))
        super.doLayout()
    }

    fun addTab(
        title: String,
        component: Component,
    ) {
        val index = buttons.size
        val button =
            CodeXPTab(title, reducedMotion).apply {
                font = dashboardFont(11f, java.awt.Font.BOLD)
                border = JBUI.Borders.empty(6, 2)
                addActionListener { selectedIndex = index }
                for ((key, direction) in listOf("RIGHT" to 1, "LEFT" to -1)) {
                    getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key)
                    actionMap.put(
                        key,
                        object : AbstractAction() {
                            override fun actionPerformed(event: ActionEvent) {
                                selectedIndex = (index + direction + tabCount) % tabCount
                                buttons[selectedIndex].requestFocusInWindow()
                            }
                        },
                    )
                }
            }
        buttons.add(button)
        group.add(button)
        strip.add(button)
        cards.add(component, index.toString())
        selectedIndex = selectedIndex
    }

    fun addChangeListener(listener: ChangeListener) {
        listeners.add(listener)
    }
}
