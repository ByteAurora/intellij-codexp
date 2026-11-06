package com.github.ilovegamecoding.intellijcodexp.presentation.guide

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPGuideService
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.border.Border

internal class CodeXPGuideDialog(
    project: Project,
    guide: CodeXPGuideService,
    progress: CodeXPService,
    onDestination: (GuideDestination) -> Unit,
) : DialogWrapper(project, false, IdeModalityType.MODELESS) {
    val guidePanel =
        CodeXPGuidePanel(
            guide,
            progress,
            onClose = { close(CANCEL_EXIT_CODE) },
            onDestination = {
                close(CANCEL_EXIT_CODE)
                onDestination(it)
            },
        )

    init {
        title = CodeXPBundle.message("action.CodeXP.Guide.text")
        isResizable = true
        guidePanel.close.isVisible = false
        guidePanel.preferredSize = JBUI.size(600, 620)
        guidePanel.showGuide()
        init()
        rootPane?.defaultButton = guidePanel.next
        window?.minimumSize = JBUI.size(420, 420)
    }

    override fun createCenterPanel(): JComponent = guidePanel

    override fun createSouthPanel(): JComponent? = null

    override fun createActions(): Array<Action> = emptyArray()

    override fun createContentPaneBorder(): Border = JBUI.Borders.empty()

    override fun getPreferredFocusedComponent(): JComponent = guidePanel.next

    override fun getDimensionServiceKey(): String = "CodeXP.Guide.Dialog"
}

/** One modeless guide per project, disposed with the project or plugin. */
@Service(Service.Level.PROJECT)
internal class CodeXPGuideWindow(
    private val project: Project,
) : Disposable {
    private var dialog: CodeXPGuideDialog? = null

    fun open() {
        if (project.isDisposed) return
        dialog?.takeUnless { it.isDisposed }?.let {
            it.toFront()
            it.window?.requestFocus()
            return
        }
        val guide = CodeXPGuideUi.service()
        val progress = ApplicationManager.getApplication().getService(CodeXPService::class.java)
        val created = CodeXPGuideDialog(project, guide, progress) { CodeXPGuideUi.openDestination(project, it) }
        dialog = created
        Disposer.register(created.disposable) { if (dialog === created) dialog = null }
        created.show()
    }

    override fun dispose() {
        dialog?.takeUnless { it.isDisposed }?.close(DialogWrapper.CANCEL_EXIT_CODE)
        dialog = null
    }
}
