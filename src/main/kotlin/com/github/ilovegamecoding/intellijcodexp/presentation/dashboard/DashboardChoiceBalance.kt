package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.ChoiceCreditIcon
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JPanel

/** A quiet currency row, separate from navigation and stable when the balance changes. */
internal class DashboardChoiceBalance : JPanel(null) {
    val symbol = JLabel(ChoiceCreditIcon())
    val amount = dashboardLabel(size = 16f, color = DashboardPalette.gold)

    init {
        isOpaque = false
        isFocusable = false
        amount.horizontalAlignment = JLabel.RIGHT
        add(symbol)
        add(amount)
        update(0, "")
    }

    fun update(
        credits: Long,
        help: String,
    ) {
        amount.text = number(credits)
        getAccessibleContext().accessibleName = CodeXPBundle.message("collection.credits.balance", amount.text)
        amount.accessibleContext.accessibleName = getAccessibleContext().accessibleName
        getAccessibleContext().accessibleDescription = help
        toolTipText = help
        symbol.toolTipText = "${getAccessibleContext().accessibleName}. $help"
        symbol.accessibleContext.accessibleName = CodeXPBundle.message("collection.credits.label")
        amount.toolTipText = help
        revalidate()
        repaint()
    }

    override fun getPreferredSize(): Dimension = Dimension(JBUI.scale(34) + amount.getFontMetrics(dashboardFont(16f, Font.BOLD)).stringWidth(amount.text), JBUI.scale(28))

    override fun doLayout() {
        val iconWidth = JBUI.scale(24)
        val available = (width - iconWidth - JBUI.scale(8)).coerceAtLeast(0)
        var size = 16f
        amount.font = dashboardFont(size, Font.BOLD)
        while (size > 10f && amount.getFontMetrics(amount.font).stringWidth(amount.text) > available) {
            size -= 1f
            amount.font = dashboardFont(size, Font.BOLD)
        }
        val valueWidth = minOf(available, amount.preferredSize.width + JBUI.scale(2)).coerceAtLeast(0)
        symbol.setBounds((width - valueWidth - JBUI.scale(8) - iconWidth).coerceAtLeast(0), 0, iconWidth, height)
        amount.setBounds(width - valueWidth, 0, valueWidth, height)
    }
}
