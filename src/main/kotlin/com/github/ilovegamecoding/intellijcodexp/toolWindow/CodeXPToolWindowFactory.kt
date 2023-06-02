package com.github.ilovegamecoding.intellijcodexp.toolWindow

import com.github.ilovegamecoding.intellijcodexp.form.CodeXPDashboard
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory


class CodeXPToolWindowFactory : ToolWindowFactory {
    private var codeXPDashboard = CodeXPDashboard()

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    private val contentFactory = ContentFactory.SERVICE.getInstance()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = contentFactory.createContent(codeXPDashboard.pMain, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    fun setXP(xp: Int) {
        codeXPDashboard.lblLevel.text = "$xp"
    }
}
