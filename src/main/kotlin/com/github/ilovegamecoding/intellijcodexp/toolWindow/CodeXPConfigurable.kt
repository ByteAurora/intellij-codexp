package com.github.ilovegamecoding.intellijcodexp.toolWindow

import com.github.ilovegamecoding.intellijcodexp.form.CodeXPConfigurationForm
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
    private val config =
        ApplicationManager.getApplication().getService(CodeXPService::class.java).state.codeXPConfiguration

    override fun createComponent(): JComponent? {
        // Create the configuration form and set the values to the current configuration.
        codeXPConfigurationForm = CodeXPConfigurationForm().apply {
            cbShowLevelUpNotification.isSelected = config.showLevelUpNotification
            cbShowCompleteChallengeNotification.isSelected = config.showCompleteChallengeNotification
            cbShowGainedXP.isSelected = config.showGainedXP
            CodeXPService.PositionToDisplayGainedXP.values().map { it.name }
                .forEach { cbPositionToDisplayGainedXP.addItem(it) }
            cbPositionToDisplayGainedXP.isEnabled = config.showGainedXP
            cbPositionToDisplayGainedXP.selectedItem = config.positionToDisplayGainedXP.name
        }
        return codeXPConfigurationForm.pMain
    }

    override fun isModified(): Boolean {
        return with(codeXPConfigurationForm) {
            cbPositionToDisplayGainedXP.isEnabled = cbShowGainedXP.isSelected
            cbShowLevelUpNotification.isSelected != config.showLevelUpNotification ||
                    cbShowCompleteChallengeNotification.isSelected != config.showCompleteChallengeNotification ||
                    cbShowGainedXP.isSelected != config.showGainedXP ||
                    cbPositionToDisplayGainedXP.selectedItem != config.positionToDisplayGainedXP.name
        }
    }

    override fun apply() {
        with(codeXPConfigurationForm) {
            config.showLevelUpNotification = cbShowLevelUpNotification.isSelected
            config.showCompleteChallengeNotification = cbShowCompleteChallengeNotification.isSelected
            config.showGainedXP = cbShowGainedXP.isSelected
            config.positionToDisplayGainedXP =
                CodeXPService.PositionToDisplayGainedXP.valueOf(cbPositionToDisplayGainedXP.selectedItem as String)
        }
    }

    override fun getDisplayName(): String {
        return "CodeXP"
    }
}