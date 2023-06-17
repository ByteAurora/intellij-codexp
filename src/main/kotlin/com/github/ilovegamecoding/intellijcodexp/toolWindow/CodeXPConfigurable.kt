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
    private var codeXPConfigurationForm: CodeXPConfigurationForm? = null

    /**
     * CodeXP service.
     */
    private val codeXPService = ApplicationManager.getApplication().getService(CodeXPService::class.java)

    override fun createComponent(): JComponent? {
        // Create the configuration form and set the values to the current configuration.
        codeXPConfigurationForm = CodeXPConfigurationForm().also { form ->
            form.cbShowLevelUpNotification.isSelected = codeXPService.codeXPConfiguration.showLevelUpNotification
            form.cbShowCompleteChallengeNotification.isSelected =
                codeXPService.codeXPConfiguration.showCompleteChallengeNotification
        }
        return codeXPConfigurationForm!!.pMain
    }

    override fun isModified(): Boolean {
        return codeXPConfigurationForm!!.cbShowLevelUpNotification.isSelected != codeXPService.codeXPConfiguration.showLevelUpNotification ||
                codeXPConfigurationForm!!.cbShowCompleteChallengeNotification.isSelected != codeXPService.codeXPConfiguration.showCompleteChallengeNotification
    }

    override fun apply() {
        codeXPService.codeXPConfiguration.showLevelUpNotification =
            codeXPConfigurationForm!!.cbShowLevelUpNotification.isSelected
        codeXPService.codeXPConfiguration.showCompleteChallengeNotification =
            codeXPConfigurationForm!!.cbShowCompleteChallengeNotification.isSelected
    }

    override fun getDisplayName(): String {
        return "CodeXP Plugin Configuration"
    }
}