package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.form.CodeXPDashboardForm
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Creates the CodeXP dashboard tool window content.
 */
class CodeXPToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val dashboardDisposable = Disposer.newDisposable("CodeXP dashboard")
        val codeXPService = ApplicationManager.getApplication().getService(CodeXPService::class.java)
        val dashboardForm = CodeXPDashboardForm()

        CodeXPDashboardController(
            codeXPService = codeXPService,
            dashboardForm = dashboardForm,
            dashboardDisposable = dashboardDisposable,
        ).initialize()

        val rootPanel =
            JPanel(BorderLayout()).apply {
                add(BorderLayout.CENTER, JBScrollPane(dashboardForm.pMain))
            }
        val content = ContentFactory.getInstance().createContent(rootPanel, null, false)
        content.setDisposer(dashboardDisposable)
        toolWindow.contentManager.addContent(content)
    }
}
