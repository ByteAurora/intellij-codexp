package com.github.ilovegamecoding.intellijcodexp.presentation.settings

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * Creates and applies the CodeXP settings UI.
 */
class CodeXPConfigurable : Configurable {
    private var notificationTypeComboBox: JComboBox<String>? = null
    private var typeDescriptionLabel: JLabel? = null
    private var showLevelUpNotificationCheckBox: JCheckBox? = null
    private var showCompleteChallengeNotificationCheckBox: JCheckBox? = null
    private var showGainedXPCheckBox: JCheckBox? = null
    private var gainedXPPositionComboBox: JComboBox<String>? = null

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
        }.also {
            reset()
        }

    override fun isModified(): Boolean {
        val config = codeXPService.state.codeXPConfiguration
        return notificationTypeComboBox.selectedItemOrNull() != config.notificationType ||
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
        updateDerivedUiState()
    }

    override fun getDisplayName(): String = CodeXPBundle.message("configurable.codexp.display.name")

    override fun disposeUIResources() {
        notificationTypeComboBox = null
        typeDescriptionLabel = null
        showLevelUpNotificationCheckBox = null
        showCompleteChallengeNotificationCheckBox = null
        showGainedXPCheckBox = null
        gainedXPPositionComboBox = null
    }

    private fun updateDerivedUiState() {
        val notificationType = notificationTypeComboBox.selectedItemOrNull() ?: return
        typeDescriptionLabel?.text = CodeXPNotificationUiOptions.descriptionFor(notificationType)
        gainedXPPositionComboBox?.isEnabled = showGainedXPCheckBox.isSelectedOrFalse()
    }

    private fun JCheckBox?.isSelectedOrFalse(): Boolean = this?.isSelected == true

    private fun JComboBox<String>?.selectedItemOrNull(): String? = this?.selectedItem as? String
}
