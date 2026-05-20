package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.utils.StringUtil
import java.awt.Font
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Renders event statistic rows in the dashboard.
 */
internal object CodeXPEventStatisticRenderer {
    private const val EVENT_COUNT_COMPONENT_INDEX = 2

    /**
     * Creates an event statistic row.
     */
    fun create(
        event: Event,
        eventCount: Long,
    ): JPanel {
        val panel = JPanel(GridLayout(1, 3))
        panel.border = BorderFactory.createEmptyBorder(0, 16, 8, 0)
        panel.add(createLabel(event.name, "Noto Sans SemiBold", leadingInset = 32))
        panel.add(createLabel("+${StringUtil.numberToStringWithCommas(event.xpValue)} XP", "Noto Sans SemiBold", leadingInset = 32))
        panel.add(
            createLabel(StringUtil.numberToStringWithCommas(eventCount), "Noto Sans Medium").apply {
                horizontalAlignment = JLabel.CENTER
            },
        )
        return panel
    }

    /**
     * Updates the event count label in an existing event statistic row.
     */
    fun updateCount(
        panel: JPanel,
        eventCount: Long,
    ) {
        val countLabel = panel.getComponent(EVENT_COUNT_COMPONENT_INDEX) as JLabel
        countLabel.text = StringUtil.numberToStringWithCommas(eventCount)
    }

    private fun createLabel(
        text: String,
        fontName: String,
        leadingInset: Int = 0,
    ): JLabel =
        JLabel(text).apply {
            font = Font(fontName, Font.PLAIN, 14)
            border = BorderFactory.createEmptyBorder(0, leadingInset, 0, 0)
        }
}
