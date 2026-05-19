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
 * CodeXPConfigurable class
 *
 * CodeXPConfigurable is the class that is used to create the configuration window for the plugin.
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

        // Create the configuration form and set the values to the current configuration.
        codeXPConfigurationForm =
            CodeXPConfigurationForm().apply {
                cbNotificationType.addItem("IntelliJ Notification")
                cbNotificationType.addItem("CodeXP Notification")
                cbNotificationType.selectedItem = config.notificationType
                if (cbNotificationType.selectedItem == "IntelliJ Notification") {
                    lblTypeDescription.text =
                        "Default notification will appear in the bottom-right of the IDE and IDE notification tool window."
                } else {
                    lblTypeDescription.text = "Customized notification will appear in the top-center of the IDE."
                }
                cbShowLevelUpNotification.isSelected = config.showLevelUpNotification
                cbShowCompleteChallengeNotification.isSelected = config.showCompleteChallengeNotification
                cbShowGainedXP.isSelected = config.showGainedXP
                PositionToDisplayGainedXP.entries
                    .map { it.name }
                    .forEach { cbPositionToDisplayGainedXP.addItem(it) }
                cbPositionToDisplayGainedXP.isEnabled = config.showGainedXP
                cbPositionToDisplayGainedXP.selectedItem = config.positionToDisplayGainedXP.name
            }
        return codeXPConfigurationForm.pMain
    }

    override fun isModified(): Boolean =
        with(codeXPConfigurationForm) {
            val config = codeXPService.state.codeXPConfiguration

            if (cbNotificationType.selectedItem == "IntelliJ Notification") {
                lblTypeDescription.text =
                    "Default notification will appear in the bottom-right of the IDE and IDE notification tool window."
            } else {
                lblTypeDescription.text = "Customized notification will appear in the top-center of the IDE."
            }
            cbPositionToDisplayGainedXP.isEnabled = cbShowGainedXP.isSelected
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
}
