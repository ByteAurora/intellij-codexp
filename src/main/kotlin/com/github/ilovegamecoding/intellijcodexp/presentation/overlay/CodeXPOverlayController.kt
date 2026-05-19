package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEventListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
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
import java.awt.Font
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseMotionAdapter
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.max

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

    private val fadingLabels: MutableMap<JComponent, FadingLabel> = mutableMapOf()
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
        if (!belongsToProject(dataContext)) {
            return
        }

        displayXPLabel(event, dataContext)
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
                "Level Up!",
                "Congratulations! You are now level ${StringUtil.numberToStringWithCommas(levelInfo.level.toLong())}!",
                "XP to next level: ${StringUtil.numberToStringWithCommas(levelInfo.totalXPForNextLevel)} xp",
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
                "Challenge Completed!",
                "Congratulations! You have completed ${challenge.name.lowercase()}!",
                "XP earned: ${StringUtil.numberToStringWithCommas(challenge.rewardXP)} xp",
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
        fadingLabels.forEach { (component, fadingLabel) ->
            fadingLabel.cancelFadeOut()
            component.remove(fadingLabel)
            component.revalidate()
            component.repaint()
        }
        fadingLabels.clear()
        dialogArea?.let { area ->
            area.removeAll()
            ide?.remove(area)
            ide?.revalidate()
            ide?.repaint()
        }
        dialogArea = null
        ide = null
    }

    private fun displayXPLabel(
        event: Event,
        dataContext: DataContext?,
    ) {
        dataContext ?: return

        val codeXPConfiguration = codeXPService.state.codeXPConfiguration
        if (!codeXPConfiguration.showGainedXP) {
            return
        }

        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return
        val component = editor.contentComponent
        val previousValue =
            fadingLabels.remove(component)?.let { fadingLabel ->
                fadingLabel.cancelFadeOut()
                component.remove(fadingLabel)
                component.revalidate()
                component.repaint()
                fadingLabel.value
            } ?: 0

        val caretModel = editor.caretModel
        val fadingLabelPosition = editor.visualPositionToXY(caretModel.visualPosition)
        val caretHeight = editor.lineHeight
        val newFadingLabel =
            FadingLabel(previousValue + event.xpValue.toInt()).apply {
                font = Font(font.fontName, Font.BOLD, editor.colorsScheme.editorFontSize)
                size = preferredSize
                location =
                    calculateLabelLocation(
                        config = codeXPConfiguration,
                        point = fadingLabelPosition,
                        caretHeight = caretHeight,
                        labelWidth = preferredSize.width,
                    )
                startFadeOut()
            }

        fadingLabels[component] = newFadingLabel

        with(component) {
            add(newFadingLabel)
            revalidate()
            repaint()
        }
    }

    private fun calculateLabelLocation(
        config: CodeXPConfiguration,
        point: Point,
        caretHeight: Int,
        labelWidth: Int,
    ): Point {
        val location = Point(point)
        with(config.positionToDisplayGainedXP) {
            val xOffset =
                when {
                    name.contains("LEFT") -> -labelWidth
                    name.contains("RIGHT") -> 0
                    else -> -labelWidth / 2
                }
            location.translate((x * 4) + xOffset, y * (caretHeight / 2))
        }
        return location
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

    /**
     * Label that fades out after displaying gained XP beside an editor caret.
     */
    internal class FadingLabel(
        initialValue: Int,
    ) : JLabel(if (initialValue == 0) "0 xp" else "+$initialValue XP") {
        private var timer: Timer? = null

        /**
         * Current XP value displayed by this label.
         */
        var value: Int = initialValue
            set(newValue) {
                field = newValue
                text = if (newValue == 0) "0 xp" else "+$newValue xp"
                if (newValue == 0) {
                    timer?.stop()
                }
            }

        /**
         * Starts the fade-out animation.
         */
        fun startFadeOut() {
            timer =
                Timer(100, null).apply {
                    addActionListener {
                        val newAlpha = max(foreground.alpha - 255 / 10, 0)
                        if (newAlpha <= 0) {
                            stop()
                            value = 0
                        } else {
                            foreground =
                                JBColor(
                                    Color(
                                        JBColor.foreground().red,
                                        JBColor.foreground().green,
                                        JBColor.foreground().blue,
                                        newAlpha,
                                    ),
                                    Color(
                                        JBColor.foreground().red,
                                        JBColor.foreground().green,
                                        JBColor.foreground().blue,
                                        newAlpha,
                                    ),
                                )
                        }
                    }
                    start()
                }
        }

        /**
         * Stops the fade-out animation.
         */
        fun cancelFadeOut() {
            timer?.stop()
            timer = null
        }
    }
}
