package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEventListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.github.ilovegamecoding.intellijcodexp.utils.StringUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
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
 * Displays CodeXP overlay effects for a single project window.
 */
@Service(Service.Level.PROJECT)
class CodeXPOverlayController(
    private val project: Project,
) : CodeXPEventListener,
    CodeXPListener,
    Disposable {
    private val codeXPService: CodeXPService =
        ApplicationManager
            .getApplication()
            .getService(CodeXPService::class.java)

    private val xpGainEffectRenderer = XpGainEffectRenderer()
    private val dialogTimers: MutableMap<CodeXPOverlayDialog, Timer> = mutableMapOf()
    private val dialogDuration: Int = 4000
    private var ide: JLayeredPane? = null
    private var dialogArea: JPanel? = null

    init {
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(CodeXPEventListener.CODEXP_EVENT, this)
        connection.subscribe(CodeXPListener.CODEXP, this)

        SwingUtilities.invokeLater {
            if (!project.isDisposed) {
                createDialogArea()
            }
        }
    }

    override fun eventOccurred(
        event: Event,
        dataContext: DataContext?,
    ) {
        val context = dataContext ?: return
        if (!belongsToProject(context)) {
            return
        }

        xpGainEffectRenderer.render(event, context, codeXPService.state.codeXPConfiguration)
    }

    override fun xpUpdated(levelInfo: CodeXPLevel) {
    }

    override fun levelUp(
        levelInfo: CodeXPLevel,
        dataContext: DataContext?,
    ) {
        if (!belongsToProject(dataContext)) {
            return
        }

        showDialog(
            CodeXPOverlayDialog.createDialog(
                CodeXPBundle.message("overlay.level.up.title"),
                CodeXPBundle.message(
                    "overlay.level.up.main",
                    StringUtil.numberToStringWithCommas(levelInfo.level.toLong()),
                ),
                CodeXPBundle.message(
                    "overlay.level.up.sub",
                    StringUtil.numberToStringWithCommas(levelInfo.totalXPForNextLevel),
                ),
            ),
        )
    }

    override fun challengeUpdated(
        event: Event,
        challenge: CodeXPChallenge,
        newChallenge: CodeXPChallenge?,
    ) {
    }

    override fun challengeCompleted(
        event: Event,
        challenge: CodeXPChallenge,
        dataContext: DataContext?,
    ) {
        if (!belongsToProject(dataContext)) {
            return
        }

        showDialog(
            CodeXPOverlayDialog.createDialog(
                CodeXPBundle.message("overlay.challenge.completed.title"),
                CodeXPBundle.message("overlay.challenge.completed.main", challenge.name.lowercase()),
                CodeXPBundle.message(
                    "overlay.challenge.completed.sub",
                    StringUtil.numberToStringWithCommas(challenge.rewardXP),
                ),
            ),
        )
    }

    private fun belongsToProject(dataContext: DataContext?): Boolean {
        dataContext ?: return false
        return CommonDataKeys.PROJECT.getData(dataContext) == project
    }

    override fun dispose() {
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

    private fun disposeUi() {
        xpGainEffectRenderer.dispose()
        dialogArea?.let { area ->
            area.removeAll()
            ide?.remove(area)
            ide?.revalidate()
            ide?.repaint()
        }
        dialogArea = null
        ide = null
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

    private fun showDialog(dialog: CodeXPOverlayDialog) {
        val area = dialogArea ?: return
        val layeredPane = ide ?: return

        dialogTimers[dialog] =
            Timer(dialogDuration) {
                hideDialog(dialog)
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

    private fun hideDialog(dialog: CodeXPOverlayDialog) {
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
}
