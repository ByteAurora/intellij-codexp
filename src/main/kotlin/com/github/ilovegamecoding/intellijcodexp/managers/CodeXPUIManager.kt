package com.github.ilovegamecoding.intellijcodexp.managers

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEventListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPDialog
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Point
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseMotionAdapter
import javax.swing.*
import kotlin.math.max


/**
 * CodeXPUIManager class
 *
 * This class manages the UI of the CodeXP plugin.
 */
object CodeXPUIManager : CodeXPEventListener, CodeXPListener {
    /**
     * Fading labels in each swing component.
     */
    private val fadingLabels: MutableMap<JComponent, FadingLabel> = mutableMapOf()

    /**
     * Current value of the fading label.
     */
    private var currentXPGainValue: Int = 0

    /**
     * The message bus for the plugin
     */
    private val messageBus = ApplicationManager.getApplication().messageBus

    /**
     * The connection to the message bus
     */
    private val connection = messageBus.connect()

    private lateinit var ide: JLayeredPane
    private lateinit var dialogArea: JPanel
    private val dialogTimers: MutableMap<CodeXPDialog, Timer> = mutableMapOf()

    init {
        connection.subscribe(CodeXPEventListener.CODEXP_EVENT, this)
        connection.subscribe(CodeXPListener.CODEXP, this)
    }

    override fun eventOccurred(event: Event, dataContext: DataContext?) {
        displayXPLabel(event, dataContext)
    }

    override fun xpUpdated(levelInfo: CodeXPLevel) {

    }

    override fun levelUp(levelInfo: CodeXPLevel) {
        showDialog(
            CodeXPDialog.createDialog(
                "Level Up!",
                "Congratulations! You have reached level ${levelInfo.level}!",
                "XP to next level: ${levelInfo.totalXPForNextLevel}xp"
            )
        )
    }

    override fun challengeUpdated(event: Event, challenge: CodeXPChallenge, newChallenge: CodeXPChallenge?) {

    }

    override fun challengeCompleted(event: Event, challenge: CodeXPChallenge) {
        showDialog(
            CodeXPDialog.createDialog(
                "Challenge Completed!",
                "Congratulations! You have completed '${challenge.name}'!",
                "XP gained: ${challenge.rewardXP}xp"
            )
        )
    }

    /**
     * Display XP label at the caret position.
     *
     * @param event The event to fire.
     * @param dataContext The data context of the event.
     */
    private fun displayXPLabel(event: Event, dataContext: DataContext?) {
        dataContext ?: return

        val codeXPConfiguration =
            ApplicationManager.getApplication().getService(CodeXPService::class.java).state.codeXPConfiguration

        if (!codeXPConfiguration.showGainedXP) {
            return
        }

        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return
        val caretModel = editor.caretModel
        val fadingLabelPosition = editor.visualPositionToXY(caretModel.visualPosition)

        val component = editor.contentComponent

        fadingLabels[component]?.let { fadingLabel ->
            fadingLabel.cancelFadeOut()
            currentXPGainValue = fadingLabel.value
            with(component) {
                remove(fadingLabel)
                revalidate()
                repaint()
            }
        }

        currentXPGainValue += event.xpValue.toInt()

        val newFadingLabel = FadingLabel(currentXPGainValue).apply {
            font = Font(font.fontName, Font.BOLD, editor.colorsScheme.editorFontSize)
            size = preferredSize
            val caretHeight = editor.lineHeight
            location =
                calculateLabelLocation(codeXPConfiguration, fadingLabelPosition, caretHeight, preferredSize.width)
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
        labelWidth: Int
    ): Point {
        with(config.positionToDisplayGainedXP) {
            val xOffset = when {
                name.contains("LEFT") -> -labelWidth
                name.contains("RIGHT") -> 0
                else -> -labelWidth / 2
            }
            point.translate((x * 4) + xOffset, y * (caretHeight / 2))
        }
        return point
    }

    /**
     * Fading label class for displaying XP gain.
     */
    internal class FadingLabel(initialValue: Int) : JLabel(if (initialValue == 0) "0 xp" else "+$initialValue XP") {
        private lateinit var timer: Timer

        /**
         * Start fade out animation.
         */
        fun startFadeOut() {
            timer = Timer(100, null)
            timer.addActionListener {
                val newAlpha = max(foreground.alpha - 255 / 10, 0)
                if (newAlpha <= 0) {
                    timer.stop()
                    value = 0
                } else {
                    foreground =
                        JBColor(
                            Color(
                                JBColor.foreground().red,
                                JBColor.foreground().green,
                                JBColor.foreground().blue,
                                newAlpha
                            ),
                            Color(
                                JBColor.foreground().red,
                                JBColor.foreground().green,
                                JBColor.foreground().blue,
                                newAlpha
                            )
                        )
                }
            }
            timer.start()
        }

        /**
         * Cancel fade out animation.
         */
        fun cancelFadeOut() {
            timer.stop()
        }

        /**
         * Current value of the fading label.
         */
        var value: Int = initialValue
            set(newValue) {
                field = newValue
                text = if (newValue == 0) "0 xp" else "+$newValue xp"
                if (newValue == 0) {
                    timer.stop()
                }
            }
    }

    /**
     * Create a dialog.
     */
    fun createDialogArea() {
        dialogArea = JPanel()
        with(dialogArea) {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isVisible = false
            background = JBColor(Color(255, 255, 255, 0), Color(255, 255, 255, 0))
            isOpaque = false

            addMouseListener(object : MouseAdapter() {})
            addMouseMotionListener(object : MouseMotionAdapter() {})
        }

        WindowManager.getInstance()
            .getIdeFrame(ProjectManager.getInstance().openProjects.firstOrNull())?.component?.rootPane?.layeredPane?.let {
                ide = it
                ide.add(dialogArea, JLayeredPane.POPUP_LAYER, 0)
            } ?: run {
            thisLogger().error("Could not find IDE frame.")
        }
    }

    /**
     * Show the progress window for 3 seconds.
     */
    private fun showDialog(dialog: CodeXPDialog) {
        dialogTimers[dialog] = Timer(3000, ActionListener { hideDialog(dialog) })
        dialogTimers[dialog]?.start()
        dialog.show()

        with(dialogArea) {
            add(dialog.frame, 0)
            size = Dimension(480, preferredSize.height)
            location = Point(ide.width / 2 - width / 2, (ide.height * 0.05).toInt())
            revalidate()
            repaint()
            isVisible = true
        }
    }

    /**
     * Hide the progress window immediately.
     */
    private fun hideDialog(dialog: CodeXPDialog) {
        SwingUtilities.invokeLater {
            dialogTimers[dialog]?.stop()
            dialogTimers.remove(dialog)

            with(dialogArea) {
                remove(dialog.frame)
                size = Dimension(480, preferredSize.height)
            }

            if (dialogArea.components.isEmpty()) {
                dialogArea.isVisible = false
            }
        }
    }
}
