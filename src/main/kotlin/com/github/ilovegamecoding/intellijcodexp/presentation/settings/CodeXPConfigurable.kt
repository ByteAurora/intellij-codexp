package com.github.ilovegamecoding.intellijcodexp.presentation.settings

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPStateListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities

/**
 * Creates and applies the CodeXP settings UI.
 */
class CodeXPConfigurable : Configurable {
    private var uiDisposable: Disposable? = null
    private var notificationTypeComboBox: JComboBox<String>? = null
    private var typeDescriptionLabel: JLabel? = null
    private var showLevelUpNotificationCheckBox: JCheckBox? = null
    private var showCompleteChallengeNotificationCheckBox: JCheckBox? = null
    private var showGainedXPCheckBox: JCheckBox? = null
    private var gainedXPPositionComboBox: JComboBox<String>? = null
    private var effectStyleComboBox: JComboBox<String>? = null
    private var reducedMotionCheckBox: JCheckBox? = null
    private val effectStyles = EffectStyle.entries.map { CodeXPBundle.message("settings.effects.style.${it.storedValue}") }

    private val codeXPService =
        ApplicationManager
            .getApplication()
            .getService(CodeXPService::class.java)

    override fun createComponent(): JComponent =
        panel {
            group(CodeXPBundle.message("TEXT_NOTIFICATION")) {
                row(CodeXPBundle.message("TEXT_TYPE")) {
                    notificationTypeComboBox =
                        comboBox(CodeXPNotificationUiOptions.values).component.apply {
                            addActionListener { updateDerivedUiState() }
                        }
                }
                row {
                    typeDescriptionLabel = label("").component
                }
                row {
                    showLevelUpNotificationCheckBox =
                        checkBox(CodeXPBundle.message("TEXT_SHOW_LEVEL_UP_NOTIFICATION")).component
                }
                row {
                    showCompleteChallengeNotificationCheckBox =
                        checkBox(CodeXPBundle.message("TEXT_SHOW_COMPLETE_CHALLENGE_NOTIFICATION")).component
                }
            }

            group(CodeXPBundle.message("TEXT_EFFECT")) {
                row(CodeXPBundle.message("settings.effects.style")) {
                    effectStyleComboBox = comboBox(effectStyles).component
                }
                row {
                    reducedMotionCheckBox = checkBox(CodeXPBundle.message("settings.effects.reduced.motion")).component
                }
                row {
                    label(CodeXPBundle.message("settings.effects.reduced.motion.description"))
                }
                row {
                    showGainedXPCheckBox =
                        checkBox(CodeXPBundle.message("TEXT_SHOW_GAINED_XP")).component.apply {
                            addActionListener { updateDerivedUiState() }
                        }
                }
                row {
                    label(CodeXPBundle.message("TEXT_SHOW_GAINED_XP_DESCRIPTION"))
                }
                row(CodeXPBundle.message("TEXT_CARETS")) {
                    gainedXPPositionComboBox =
                        comboBox(PositionToDisplayGainedXP.entries.map { it.name }).component
                }
            }
            group(CodeXPBundle.message("settings.history.title")) {
                row {
                    button(CodeXPBundle.message("settings.history.reset")) {
                        if (Messages.showDialog(
                                null,
                                CodeXPBundle.message("settings.history.reset.message"),
                                CodeXPBundle.message("settings.history.reset.title"),
                                null,
                                arrayOf(CodeXPBundle.message("settings.history.cancel"), CodeXPBundle.message("settings.history.reset.confirm")),
                                0,
                                0,
                                Messages.getWarningIcon(),
                            ) == 1
                        ) {
                            codeXPService.resetAllExceptNickname()
                        }
                    }
                }
                row { label(CodeXPBundle.message("settings.history.reset.description")) }
            }
        }.also {
            uiDisposable?.let(Disposer::dispose)
            val lifetime = Disposer.newDisposable("CodeXP settings UI")
            uiDisposable = lifetime
            var observedState = codeXPService.state
            ApplicationManager.getApplication().messageBus.connect(lifetime).subscribe(
                CodeXPStateListener.STATE,
                CodeXPStateListener {
                    if (observedState !== codeXPService.state) {
                        observedState = codeXPService.state
                        val refresh = Runnable { if (uiDisposable === lifetime) reset() }
                        if (SwingUtilities.isEventDispatchThread()) refresh.run() else SwingUtilities.invokeLater(refresh)
                    }
                },
            )
            reset()
        }

    override fun isModified(): Boolean {
        if (notificationTypeComboBox == null) {
            return false
        }
        val config = codeXPService.state.codeXPConfiguration
        return notificationTypeComboBox.selectedItemOrNull() != config.notificationType ||
            selectedEffectStyle() != EffectStyle.fromStoredValue(config.effectStyle) ||
            reducedMotionCheckBox.isSelectedOrFalse() != config.reducedMotion ||
            showLevelUpNotificationCheckBox.isSelectedOrFalse() != config.showLevelUpNotification ||
            showCompleteChallengeNotificationCheckBox.isSelectedOrFalse() != config.showCompleteChallengeNotification ||
            showGainedXPCheckBox.isSelectedOrFalse() != config.showGainedXP ||
            gainedXPPositionComboBox.selectedItemOrNull() != config.positionToDisplayGainedXP.name
    }

    override fun apply() {
        codeXPService.updateConfiguration(
            CodeXPConfiguration(
                notificationType = notificationTypeComboBox.selectedItemOrNull() ?: return,
                showLevelUpNotification = showLevelUpNotificationCheckBox.isSelectedOrFalse(),
                showCompleteChallengeNotification = showCompleteChallengeNotificationCheckBox.isSelectedOrFalse(),
                showGainedXP = showGainedXPCheckBox.isSelectedOrFalse(),
                positionToDisplayGainedXP =
                    PositionToDisplayGainedXP.valueOf(gainedXPPositionComboBox.selectedItemOrNull() ?: return),
                effectStyle = selectedEffectStyle().storedValue,
                reducedMotion = reducedMotionCheckBox.isSelectedOrFalse(),
            ),
        )
    }

    override fun reset() {
        val config = codeXPService.state.codeXPConfiguration
        notificationTypeComboBox?.selectedItem = config.notificationType
        showLevelUpNotificationCheckBox?.isSelected = config.showLevelUpNotification
        showCompleteChallengeNotificationCheckBox?.isSelected = config.showCompleteChallengeNotification
        showGainedXPCheckBox?.isSelected = config.showGainedXP
        gainedXPPositionComboBox?.selectedItem = config.positionToDisplayGainedXP.name
        effectStyleComboBox?.selectedIndex = EffectStyle.fromStoredValue(config.effectStyle).ordinal
        reducedMotionCheckBox?.isSelected = config.reducedMotion
        updateDerivedUiState()
    }

    override fun getDisplayName(): String = CodeXPBundle.message("configurable.codexp.display.name")

    override fun disposeUIResources() {
        uiDisposable?.let(Disposer::dispose)
        uiDisposable = null
        notificationTypeComboBox = null
        typeDescriptionLabel = null
        showLevelUpNotificationCheckBox = null
        showCompleteChallengeNotificationCheckBox = null
        showGainedXPCheckBox = null
        gainedXPPositionComboBox = null
        effectStyleComboBox = null
        reducedMotionCheckBox = null
    }

    private fun updateDerivedUiState() {
        val notificationType = notificationTypeComboBox.selectedItemOrNull() ?: return
        typeDescriptionLabel?.text = CodeXPNotificationUiOptions.descriptionFor(notificationType)
        gainedXPPositionComboBox?.isEnabled = showGainedXPCheckBox.isSelectedOrFalse()
    }

    private fun JCheckBox?.isSelectedOrFalse(): Boolean = this?.isSelected == true

    private fun selectedEffectStyle(): EffectStyle = EffectStyle.entries.getOrNull(effectStyleComboBox?.selectedIndex ?: -1) ?: EffectStyle.CELEBRATORY

    private fun JComboBox<String>?.selectedItemOrNull(): String? = this?.selectedItem as? String
}
