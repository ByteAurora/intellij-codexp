package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CodeXPCollectionPanel
import com.github.ilovegamecoding.intellijcodexp.presentation.guide.CodeXPGuideUi
import com.github.ilovegamecoding.intellijcodexp.presentation.guide.GuideDestination
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.CardLayout
import java.awt.event.HierarchyEvent
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
        val root = CodeXPDashboardRoot(project, dashboardDisposable)
        val content = ContentFactory.getInstance().createContent(root, null, false)
        content.setDisposer(dashboardDisposable)
        toolWindow.contentManager.addContent(content)
    }
}

internal class CodeXPDashboardRoot(
    private val project: Project,
    dashboardDisposable: Disposable,
) : JPanel(CardLayout()) {
    private val cards get() = layout as CardLayout

    init {
        val codeXPService = ApplicationManager.getApplication().getService(CodeXPService::class.java)
        val dashboard = CodeXPDashboardPanel()
        val collection = CodeXPCollectionPanel(codeXPService)
        Disposer.register(dashboardDisposable, collection)
        add(dashboard, "dashboard")
        add(collection, "collection")
        dashboard.onCollectionRequested = { cards.show(this, "collection") }
        collection.onBack = { cards.show(this, "dashboard") }
        dashboard.onSettings = { ShowSettingsUtil.getInstance().showSettingsDialog(project, "CodeXP") }
        dashboard.onGuide = { CodeXPGuideUi.open(project) }

        val controller =
            CodeXPDashboardController(
                codeXPService = codeXPService,
                dashboard = dashboard,
                dashboardDisposable = dashboardDisposable,
                onCollectionSnapshot = collection::apply,
            )
        controller.isCollectionVisible = { collection.isShowing }
        collection.addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && collection.isShowing) controller.refreshVisible()
        }
        controller.initialize()
    }

    fun showDestination(destination: GuideDestination) {
        when (destination) {
            GuideDestination.DASHBOARD -> cards.show(this, "dashboard")
            GuideDestination.COLLECTION -> cards.show(this, "collection")
            GuideDestination.SETTINGS -> ShowSettingsUtil.getInstance().showSettingsDialog(project, "CodeXP")
        }
    }
}
