package com.github.ilovegamecoding.intellijcodexp.listeners

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.math.max

/**
 * CodeXPEventListener class
 *
 * This class listens to events from the IDE and fires them to the message bus.
 */
internal class CodeXPEventListener : AnActionListener {
    /**
     * Fading labels in each swing component.
     */
    private val fadingLabels: MutableMap<JComponent, FadingLabel> = mutableMapOf()

    /**
     * Current value of the fading label.
     */
    private var currentXPGainValue: Int = 0

    override fun afterEditorTyping(c: Char, dataContext: DataContext) {
        super.afterEditorTyping(c, dataContext)

        fireEventAndDisplayXPLabel(Event.TYPING, dataContext)
    }

    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        super.afterActionPerformed(action, event, result)
        thisLogger().warn("Action performed: ${action.templateText}")

        when (action.templateText) { // Fire event based on action.
            "Run" -> fireEvent(Event.RUN)
            "Save All" -> fireEvent(Event.SAVE)
            "Debug" -> fireEvent(Event.DEBUG)
            "Build Project" -> fireEvent(Event.BUILD)
            "Rebuild Project" -> fireEvent(Event.BUILD)
            "Cut" -> fireEventAndDisplayXPLabel(Event.CUT, event.dataContext)
            "Copy" -> fireEventAndDisplayXPLabel(Event.COPY, event.dataContext)
            "Paste" -> fireEventAndDisplayXPLabel(Event.PASTE, event.dataContext)
            "Backspace" -> fireEventAndDisplayXPLabel(Event.BACKSPACE, event.dataContext)
            "Tab" -> fireEventAndDisplayXPLabel(Event.TAB, event.dataContext)
            "Enter" -> fireEventAndDisplayXPLabel(Event.ENTER, event.dataContext)
            else -> fireEvent(Event.ACTION)
        }
    }

    /**
     * Fire event to the message bus and display XP label.
     *
     * @param event The event to fire.
     * @param dataContext The data context of the event.
     */
    private fun fireEventAndDisplayXPLabel(event: Event, dataContext: DataContext) {
        fireEvent(event)
        displayXPLabel(event, dataContext)
    }

    /**
     * Fire event to the message bus.
     *
     * @param event The event to fire.
     */
    private fun fireEvent(event: Event) {
        ApplicationManager.getApplication().messageBus.syncPublisher(CodeXPListener.CODEXP_EVENT)
            .eventOccurred(event)
    }

    /**
     * Display XP label at the caret position.
     *
     * @param event The event to fire.
     * @param dataContext The data context of the event.
     */
    private fun displayXPLabel(event: Event, dataContext: DataContext) {
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
            component.remove(fadingLabel)
            component.revalidate()
            component.repaint()
        }

        currentXPGainValue += event.xpValue.toInt()

        val newFadingLabel = FadingLabel(currentXPGainValue).apply {
            font = Font(font.fontName, Font.BOLD, editor.colorsScheme.editorFontSize)
            size = preferredSize
            val caretHeight = editor.lineHeight
            fadingLabelPosition.let { point ->
                val position = codeXPConfiguration.positionToDisplayGainedXP
                val xOffset = when {
                    position.name.contains("LEFT") -> -width
                    position.name.contains("RIGHT") -> 0
                    else -> -width / 2
                }
                point.translate(
                    (position.x * 4) + xOffset,
                    position.y * (caretHeight / 2)
                )
                location = point
            }
            startFadeOut()
        }

        fadingLabels[component] = newFadingLabel

        component.add(newFadingLabel)
        component.revalidate()
        component.repaint()
    }

    internal class FadingLabel(initialValue: Int) : JLabel(if (initialValue == 0) "0 xp" else "+$initialValue XP") {
        private lateinit var timer: javax.swing.Timer

        /**
         * Start fade out animation.
         */
        fun startFadeOut() {
            timer = javax.swing.Timer(100, null)
            timer.addActionListener {
                val newAlpha = max(foreground.alpha - 255 / 10, 0)
                if (newAlpha <= 0) {
                    timer.stop()
                    value = 0
                } else {
                    foreground =
                        Color(JBColor.foreground().red, JBColor.foreground().green, JBColor.foreground().blue, newAlpha)
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
}