package com.github.ilovegamecoding.intellijcodexp.views

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

/**
 * CodeXPDialog class
 *
 * This class is used to create a dialog that is shown in the dialog area.
 */
class CodeXPDialog(
    /**
     * Dialog title.
     */
    private var title: String = "",

    /**
     * Dialog main description.
     */
    private var mainDescription: String = "",

    /**
     * Dialog sub description.
     */
    private var subDescription: String = "",
) {
    /**
     * Dialog frame.
     */
    var frame: JPanel = JPanel()

    /**
     * Dialog content.
     */
    private var content: JPanel

    /**
     * Show dialog duration.
     */
    private val showDuration = 2000

    /**
     * Fade animation duration.
     */
    private val fadeDuration = 1000

    /**
     * Animation step millis.
     */
    private val stepMillis = 30

    /**
     * Animation fade step.
     */
    private val fadeStep = 192.0 * stepMillis / fadeDuration

    init {
        val lblTitle = JLabel().apply {
            text = title
            foreground = JBColor(Color(255, 255, 255, 0), Color(255, 255, 255, 0))
            font = font.deriveFont(20f)
            horizontalAlignment = JLabel.CENTER
            verticalAlignment = JLabel.CENTER
            maximumSize = Dimension(400, Int.MAX_VALUE)
        }

        val lblMainDescription = JLabel().apply {
            text = mainDescription
            foreground = JBColor(Color(255, 255, 255, 0), Color(255, 255, 255, 0))
            font = font.deriveFont(14f)
            maximumSize = Dimension(400, Int.MAX_VALUE)
        }

        val lblSubDescription = JLabel().apply {
            text = subDescription
            foreground = JBColor(Color(255, 255, 255, 0), Color(255, 255, 255, 0))
            font = font.deriveFont(14f)
            maximumSize = Dimension(400, Int.MAX_VALUE)
        }

        val contentHeight = lblTitle.preferredSize.height + lblMainDescription.preferredSize.height +
                lblSubDescription.preferredSize.height + (2 * 20) + (2 * 10)

        content = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                val g2d = g.create() as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = background
                g2d.fillRoundRect(0, 0, width, height, 16, 16)
                g2d.dispose()
            }
        }.apply {
            preferredSize = JBUI.size(440, contentHeight)
            minimumSize = JBUI.size(440, contentHeight)
            maximumSize = JBUI.size(440, contentHeight)
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = JBColor(Color(0, 0, 0, 0), Color(0, 0, 0, 0))
            border = BorderFactory.createEmptyBorder(20, 20, 20, 20)

            add(lblTitle)
            add(lblMainDescription)
            add(lblSubDescription)
        }


        with(frame) {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(content)
            add(JPanel().apply {
                preferredSize = JBUI.size(440, 20)
                layout = BorderLayout()
                background = JBColor(Color(255, 255, 255, 0), Color(255, 255, 255, 0))
                isOpaque = false
            })
        }
    }

    companion object {
        /**
         * Create a new dialog with the given parameters.
         */
        fun createDialog(
            title: String = "",
            mainDescription: String = "",
            subDescription: String = "",
        ): CodeXPDialog {
            return CodeXPDialog(title, mainDescription, subDescription)
        }
    }

    /**
     * Show dialog.
     */
    fun show() {
        SwingUtilities.invokeLater {
            Timer(stepMillis) { event ->
                val alpha = ((content.background as JBColor).alpha + fadeStep).coerceIn(0.0, 192.0).toInt()

                with(content) {
                    background = JBColor(Color(0, 0, 0, alpha), Color(0, 0, 0, alpha))
                    components.forEach {
                        if (it is JLabel) {
                            it.foreground = JBColor(Color(255, 255, 255, alpha), Color(255, 255, 255, alpha))
                        }
                    }
                }

                frame.repaint()

                if (alpha >= 192) {
                    (event.source as Timer).stop()
                    Timer(showDuration) { hide() }.apply {
                        isRepeats = false
                        start()
                    }
                }
            }.start()

            frame.isVisible = true
        }
    }

    /**
     * Hide dialog.
     */
    private fun hide() {
        SwingUtilities.invokeLater {
            Timer(stepMillis) { event ->
                val alpha = ((content.background as JBColor).alpha - fadeStep).coerceIn(0.0, 192.0).toInt()

                with(content) {
                    background = JBColor(Color(0, 0, 0, alpha), Color(0, 0, 0, alpha))
                    components.forEach {
                        if (it is JLabel) {
                            it.foreground = JBColor(Color(255, 255, 255, alpha), Color(255, 255, 255, alpha))
                        }
                    }
                }

                frame.repaint()

                if (alpha <= 0) {
                    frame.isVisible = false
                    (event.source as Timer).stop()
                }
            }.start()
        }
    }
}