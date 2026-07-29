package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseMotionAdapter
import javax.swing.BoxLayout
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Presents CodeXP overlay dialogs inside a project IDE frame.
 */
internal class CodeXPOverlayDialogPresenter(
    private val project: Project,
) {
    private val dialogTimers: MutableMap<CodeXPOverlayDialog, Timer> = mutableMapOf()
    private val dialogDuration: Int = 4000
    private var ide: JLayeredPane? = null
    private var dialogArea: JPanel? = null

    /**
     * Creates the shared dialog area when the project frame is available.
     */
    fun initialize() {
        SwingUtilities.invokeLater {
            if (!project.isDisposed) {
                createDialogArea()
            }
        }
    }

    /**
     * Shows a dialog in the project overlay area.
     */
    fun show(dialog: CodeXPOverlayDialog) {
        val area = dialogArea ?: return
        val layeredPane = ide ?: return

        dialogTimers[dialog] =
            Timer(dialogDuration) {
                hide(dialog)
            }.apply {
                isRepeats = false
                start()
            }
        dialog.show()

        with(area) {
            add(dialog.frame, 0)
            size = Dimension(480, preferredSize.height)
            location = Point(layeredPane.width / 2 - width / 2, (layeredPane.height * 0.05).toInt())
            revalidate()
            repaint()
            isVisible = true
        }
    }

    /**
     * Stops timers and removes overlay dialog UI.
     */
    fun dispose() {
        dialogTimers.forEach { (dialog, timer) ->
            timer.stop()
            dialog.dispose()
        }
        dialogTimers.clear()

        if (SwingUtilities.isEventDispatchThread()) {
            disposeUi()
        } else {
            SwingUtilities.invokeLater {
                disposeUi()
            }
        }
    }

    private fun createDialogArea() {
        val layeredPane =
            WindowManager
                .getInstance()
                .getIdeFrame(project)
                ?.component
                ?.rootPane
                ?.layeredPane

        if (layeredPane == null) {
            thisLogger().warn("Could not find IDE frame for project ${project.name}.")
            return
        }

        val area =
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isVisible = false
                background = JBColor(Color(255, 255, 255, 0), Color(255, 255, 255, 0))
                isOpaque = false

                addMouseListener(object : MouseAdapter() {})
                addMouseMotionListener(object : MouseMotionAdapter() {})
            }

        ide = layeredPane
        dialogArea = area
        layeredPane.add(area, JLayeredPane.POPUP_LAYER, 0)
    }

    private fun hide(dialog: CodeXPOverlayDialog) {
        SwingUtilities.invokeLater {
            val area = dialogArea ?: return@invokeLater
            dialogTimers.remove(dialog)?.stop()
            dialog.dispose()

            with(area) {
                remove(dialog.frame)
                size = Dimension(480, preferredSize.height)
            }

            if (area.components.isEmpty()) {
                area.isVisible = false
            }
        }
    }

    private fun disposeUi() {
        dialogArea?.let { area ->
            area.removeAll()
            ide?.remove(area)
            ide?.revalidate()
            ide?.repaint()
        }
        dialogArea = null
        ide = null
    }
}
