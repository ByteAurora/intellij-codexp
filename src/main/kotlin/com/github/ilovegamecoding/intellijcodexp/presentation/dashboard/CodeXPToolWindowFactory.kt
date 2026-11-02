package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

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
        val dashboard = CodeXPDashboardPanel()
        dashboard.onSettings = { ShowSettingsUtil.getInstance().showSettingsDialog(project, "CodeXP") }

        CodeXPDashboardController(
            codeXPService = codeXPService,
            dashboard = dashboard,
            dashboardDisposable = dashboardDisposable,
        ).initialize()

        val content = ContentFactory.getInstance().createContent(dashboard, null, false)
        content.setDisposer(dashboardDisposable)
        toolWindow.contentManager.addContent(content)
    }
}
