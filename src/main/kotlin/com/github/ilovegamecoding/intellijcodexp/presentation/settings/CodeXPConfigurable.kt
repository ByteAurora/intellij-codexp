package com.github.ilovegamecoding.intellijcodexp.presentation.settings

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.enums.CodeXPNotificationType
import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPStateListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration.Companion.DEFAULT_MINI_CREST_SIZE
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration.Companion.MINI_CREST_SIZES
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.SwingUtilities

/**
 * Creates and applies the CodeXP settings UI.
 */
class CodeXPConfigurable : Configurable {
    private var uiDisposable: Disposable? = null
    private var notificationTypeComboBox: JComboBox<String>? = null
    private var typeDescriptionLabel: JEditorPane? = null
    private var showLevelUpNotificationCheckBox: JCheckBox? = null
    private var showCompleteChallengeNotificationCheckBox: JCheckBox? = null
    private var showGainedXPCheckBox: JCheckBox? = null
    private var gainedXPPositionComboBox: JComboBox<String>? = null
    private var effectStyleComboBox: JComboBox<String>? = null
    private var reducedMotionCheckBox: JCheckBox? = null
    private var miniCrestCheckBox: JCheckBox? = null
    private var miniCrestSizeComboBox: JComboBox<String>? = null
    private var restoreMiniPosition = false
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
                            renderer =
                                SimpleListCellRenderer.create("") { value: String ->
                                    val mode = CodeXPNotificationType.fromStoredValue(value)
                                    CodeXPBundle.message("settings.notification.type.${mode.name.lowercase()}.label")
                                }
                            addActionListener { updateDerivedUiState() }
                        }
                }
                row {
                    typeDescriptionLabel = comment("", maxLineLength = -1).align(AlignX.FILL).component
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
                    comment(CodeXPBundle.message("settings.effects.reduced.motion.description"), maxLineLength = -1).align(AlignX.FILL)
                }
                row {
                    showGainedXPCheckBox =
                        checkBox(CodeXPBundle.message("TEXT_SHOW_GAINED_XP")).component.apply {
                            addActionListener { updateDerivedUiState() }
                        }
                }
                row {
                    comment(CodeXPBundle.message("TEXT_SHOW_GAINED_XP_DESCRIPTION"), maxLineLength = -1).align(AlignX.FILL)
                }
                row(CodeXPBundle.message("TEXT_CARETS")) {
                    gainedXPPositionComboBox =
                        comboBox(PositionToDisplayGainedXP.entries.map { it.name }).component.apply {
                            renderer =
                                SimpleListCellRenderer.create("") { value: String ->
                                    CodeXPBundle.message("settings.caret.position.${value.lowercase()}")
                                }
                        }
                }
            }
            group(CodeXPBundle.message("settings.mini.title")) {
                row { miniCrestCheckBox = checkBox(CodeXPBundle.message("settings.mini.enabled")).component }
                row(CodeXPBundle.message("settings.mini.size")) { miniCrestSizeComboBox = comboBox(MINI_CREST_SIZES.map { CodeXPBundle.message("mini.size", it) }).component }
                row { comment(CodeXPBundle.message("settings.mini.description"), maxLineLength = -1).align(AlignX.FILL) }
                row {
                    button(CodeXPBundle.message("settings.mini.restore")) {
                        restoreMiniPosition = true
                        miniCrestCheckBox?.isSelected = true
                    }
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
                            if (!codeXPService.resetAllExceptNickname()) {
                                Messages.showErrorDialog(CodeXPBundle.message("collection.error.reset"), "CodeXP")
                            }
                        }
                    }
                }
                row { comment(CodeXPBundle.message("settings.history.reset.description"), maxLineLength = -1).align(AlignX.FILL) }
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
        return restoreMiniPosition || notificationTypeComboBox.selectedItemOrNull() != config.notificationType ||
            selectedEffectStyle() != EffectStyle.fromStoredValue(config.effectStyle) ||
            reducedMotionCheckBox.isSelectedOrFalse() != config.reducedMotion ||
            miniCrestCheckBox.isSelectedOrFalse() != config.miniCrestEnabled ||
            selectedMiniSize() != config.miniCrestSize ||
            showLevelUpNotificationCheckBox.isSelectedOrFalse() != config.showLevelUpNotification ||
            showCompleteChallengeNotificationCheckBox.isSelectedOrFalse() != config.showCompleteChallengeNotification ||
            showGainedXPCheckBox.isSelectedOrFalse() != config.showGainedXP ||
            gainedXPPositionComboBox.selectedItemOrNull() != config.positionToDisplayGainedXP.name
    }

    override fun apply() {
        codeXPService.updateConfiguration(
            codeXPService.state.codeXPConfiguration
                .copy(
                    notificationType = notificationTypeComboBox.selectedItemOrNull() ?: return,
                    showLevelUpNotification = showLevelUpNotificationCheckBox.isSelectedOrFalse(),
                    showCompleteChallengeNotification = showCompleteChallengeNotificationCheckBox.isSelectedOrFalse(),
                    showGainedXP = showGainedXPCheckBox.isSelectedOrFalse(),
                    positionToDisplayGainedXP =
                        PositionToDisplayGainedXP.valueOf(gainedXPPositionComboBox.selectedItemOrNull() ?: return),
                    effectStyle = selectedEffectStyle().storedValue,
                    reducedMotion = reducedMotionCheckBox.isSelectedOrFalse(),
                    miniCrestEnabled = miniCrestCheckBox.isSelectedOrFalse(),
                    miniCrestSize = selectedMiniSize(),
                ).apply {
                    if (restoreMiniPosition) {
                        miniCrestX = 1.0
                        miniCrestY = 1.0
                        miniCrestWindowAnchor = false
                    }
                },
        )
        restoreMiniPosition = false
    }

    override fun reset() {
        restoreMiniPosition = false
        val config = codeXPService.state.codeXPConfiguration
        notificationTypeComboBox?.selectedItem = config.notificationType
        showLevelUpNotificationCheckBox?.isSelected = config.showLevelUpNotification
        showCompleteChallengeNotificationCheckBox?.isSelected = config.showCompleteChallengeNotification
        showGainedXPCheckBox?.isSelected = config.showGainedXP
        gainedXPPositionComboBox?.selectedItem = config.positionToDisplayGainedXP.name
        effectStyleComboBox?.selectedIndex = EffectStyle.fromStoredValue(config.effectStyle).ordinal
        reducedMotionCheckBox?.isSelected = config.reducedMotion
        miniCrestCheckBox?.isSelected = config.miniCrestEnabled
        miniCrestSizeComboBox?.selectedItem = CodeXPBundle.message("mini.size", config.miniCrestSize)
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
        miniCrestCheckBox = null
        miniCrestSizeComboBox = null
        restoreMiniPosition = false
    }

    private fun updateDerivedUiState() {
        val notificationType = notificationTypeComboBox.selectedItemOrNull() ?: return
        typeDescriptionLabel?.text = CodeXPNotificationUiOptions.descriptionFor(notificationType)
        gainedXPPositionComboBox?.isEnabled = showGainedXPCheckBox.isSelectedOrFalse()
    }

    private fun JCheckBox?.isSelectedOrFalse(): Boolean = this?.isSelected == true

    private fun selectedMiniSize(): Int = MINI_CREST_SIZES.getOrNull(miniCrestSizeComboBox?.selectedIndex ?: -1) ?: DEFAULT_MINI_CREST_SIZE

    private fun selectedEffectStyle(): EffectStyle = EffectStyle.entries.getOrNull(effectStyleComboBox?.selectedIndex ?: -1) ?: EffectStyle.CELEBRATORY

    private fun JComboBox<String>?.selectedItemOrNull(): String? = this?.selectedItem as? String
}
