package com.github.ilovegamecoding.intellijcodexp.toolWindow

import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory


class CodeXPToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    private val contentFactory = ContentFactory.SERVICE.getInstance()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val codeXPDashboard = CodeXPDashboard()
        val content = contentFactory.createContent(codeXPDashboard.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class CodeXPDashboard() {
        private val service = service<CodeXPService>()

        init {

        }

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            // TODO: Implement the UI for this feature
        }
    }
}
