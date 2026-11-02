package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.GridLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/** A small retained card switcher, avoiding recursive Darcula scroll-tab layout during IDE restoration. */
internal class DashboardTabs : JPanel(BorderLayout()) {
    private val strip =
        JPanel(GridLayout(1, 0, JBUI.scale(4), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 12)
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
                button.foreground = if (index == value) DashboardPalette.gold else DashboardPalette.muted
                button.border =
                    BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, JBUI.scale(2), 0, if (index == value) DashboardPalette.gold else DashboardPalette.line),
                        JBUI.Borders.empty(12, 0, 10, 0),
                    )
            }
            if (changed) listeners.toList().forEach { it.stateChanged(ChangeEvent(this)) }
        }

    init {
        isOpaque = false
        add(strip, BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
    }

    fun addTab(
        title: String,
        component: Component,
    ) {
        val index = buttons.size
        val button =
            JToggleButton(title).apply {
                font = dashboardFont(11f)
                isOpaque = false
                isContentAreaFilled = false
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
