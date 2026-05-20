package com.github.ilovegamecoding.intellijcodexp.presentation.settings

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP
import com.github.ilovegamecoding.intellijcodexp.form.CodeXPConfigurationForm
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

/**
 * Creates and applies the CodeXP settings UI.
 */
class CodeXPConfigurable : Configurable {
    /**
     * CodeXP configuration form.
     */
    private lateinit var codeXPConfigurationForm: CodeXPConfigurationForm

    /**
     * CodeXP service.
     */
    private val codeXPService =
        ApplicationManager
            .getApplication()
            .getService(CodeXPService::class.java)

    override fun createComponent(): JComponent? {
        val config = codeXPService.state.codeXPConfiguration

        codeXPConfigurationForm =
            CodeXPConfigurationForm().apply {
                CodeXPNotificationUiOptions.values.forEach(cbNotificationType::addItem)
                cbNotificationType.selectedItem = config.notificationType
                cbShowLevelUpNotification.isSelected = config.showLevelUpNotification
                cbShowCompleteChallengeNotification.isSelected = config.showCompleteChallengeNotification
                cbShowGainedXP.isSelected = config.showGainedXP
                PositionToDisplayGainedXP.entries
                    .map { it.name }
                    .forEach { cbPositionToDisplayGainedXP.addItem(it) }
                cbPositionToDisplayGainedXP.isEnabled = config.showGainedXP
                cbPositionToDisplayGainedXP.selectedItem = config.positionToDisplayGainedXP.name
                cbNotificationType.addActionListener { updateDerivedUiState() }
                cbShowGainedXP.addActionListener { updateDerivedUiState() }
                updateDerivedUiState()
            }
        return codeXPConfigurationForm.pMain
    }

    override fun isModified(): Boolean =
        with(codeXPConfigurationForm) {
            val config = codeXPService.state.codeXPConfiguration

            updateDerivedUiState()
            cbNotificationType.selectedItem != config.notificationType ||
                cbShowLevelUpNotification.isSelected != config.showLevelUpNotification ||
                cbShowCompleteChallengeNotification.isSelected != config.showCompleteChallengeNotification ||
                cbShowGainedXP.isSelected != config.showGainedXP ||
                cbPositionToDisplayGainedXP.selectedItem != config.positionToDisplayGainedXP.name
        }

    override fun apply() {
        with(codeXPConfigurationForm) {
            codeXPService.updateConfiguration(
                CodeXPConfiguration(
                    notificationType = cbNotificationType.selectedItem as String,
                    showLevelUpNotification = cbShowLevelUpNotification.isSelected,
                    showCompleteChallengeNotification = cbShowCompleteChallengeNotification.isSelected,
                    showGainedXP = cbShowGainedXP.isSelected,
                    positionToDisplayGainedXP =
                        PositionToDisplayGainedXP.valueOf(cbPositionToDisplayGainedXP.selectedItem as String),
                ),
            )
        }
    }

    override fun getDisplayName(): String = CodeXPBundle.message("configurable.codexp.display.name")

    private fun updateDerivedUiState() {
        with(codeXPConfigurationForm) {
            lblTypeDescription.text =
                CodeXPNotificationUiOptions.descriptionFor(cbNotificationType.selectedItem as String)
            cbPositionToDisplayGainedXP.isEnabled = cbShowGainedXP.isSelected
        }
    }
}
