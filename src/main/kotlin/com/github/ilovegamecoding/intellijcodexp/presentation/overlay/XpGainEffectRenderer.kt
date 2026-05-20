package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.Timer
import kotlin.math.max

/**
 * Renders gained XP labels beside an editor caret.
 */
internal class XpGainEffectRenderer {
    private val fadingLabels: MutableMap<JComponent, FadingLabel> = mutableMapOf()

    /**
     * Shows or accumulates the gained XP effect for an editor event.
     */
    fun render(
        event: Event,
        dataContext: DataContext,
        config: CodeXPConfiguration,
    ) {
        if (!config.showGainedXP) {
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

        val fadingLabelPosition = editor.visualPositionToXY(editor.caretModel.visualPosition)
        val caretHeight = editor.lineHeight
        val newFadingLabel =
            FadingLabel(previousValue + event.xpValue.toInt()).apply {
                font = Font(font.fontName, Font.BOLD, editor.colorsScheme.editorFontSize)
                size = preferredSize
                location =
                    calculateLabelLocation(
                        config = config,
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

    /**
     * Stops active effects and removes their UI components.
     */
    fun dispose() {
        fadingLabels.forEach { (component, fadingLabel) ->
            fadingLabel.cancelFadeOut()
            component.remove(fadingLabel)
            component.revalidate()
            component.repaint()
        }
        fadingLabels.clear()
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
