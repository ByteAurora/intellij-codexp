package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.smoothArtwork
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon

/** CodeXP currency: a ticket with the same diamond mark as the equipped crest. */
internal class ChoiceCreditIcon(
    private val size: Int = 24,
) : Icon {
    override fun getIconWidth(): Int = JBUI.scale(size)

    override fun getIconHeight(): Int = JBUI.scale(size)

    override fun paintIcon(
        c: Component?,
        graphics: Graphics,
        x: Int,
        y: Int,
    ) {
        val g = graphics.create() as Graphics2D
        try {
            g.translate(x.toDouble(), y.toDouble())
            g.scale(iconWidth / 24.0, iconHeight / 24.0)
            g.smoothArtwork()
            val ticket = RoundRectangle2D.Double(2.5, 4.5, 19.0, 15.0, 4.0, 4.0)
            g.color = DashboardPalette.surface
            g.fill(ticket)
            g.color = DashboardPalette.gold
            g.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(ticket)
            val mark =
                Path2D.Double().apply {
                    moveTo(10.0, 8.0)
                    lineTo(13.5, 12.0)
                    lineTo(10.0, 16.0)
                    lineTo(6.5, 12.0)
                    closePath()
                }
            g.fill(mark)
            g.color = DashboardPalette.teal
            g.stroke = BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (yy in listOf(8, 12, 16)) g.drawLine(17, yy, 18, yy)
        } finally {
            g.dispose()
        }
    }
}
