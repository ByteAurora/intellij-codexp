package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JPanel

/** Passive Swing surface with no focus, hit testing, or animation resources. */
internal class CodeXPOverlayDialog : JPanel(null) {
    var frame: AchievementFrame? = null

    init {
        isOpaque = false
        isFocusable = false
    }

    override fun contains(
        x: Int,
        y: Int,
    ): Boolean = false

    override fun paintComponent(graphics: Graphics) {
        val value = frame ?: return
        val g = graphics.create() as Graphics2D
        try {
            AchievementPainter.paint(g, width, height, value, !JBColor.isBright(), JBUIScale.scale(1f).toDouble())
        } finally {
            g.dispose()
        }
    }
}
