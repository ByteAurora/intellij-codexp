package com.github.ilovegamecoding.intellijcodexp.listeners

import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger

/**
 * CodeXPEventListener class
 *
 * This class listens to events from the IDE and fires them to the message bus.
 */
internal class CodeXPEventListener : AnActionListener {
    override fun afterEditorTyping(c: Char, dataContext: DataContext) {
        super.afterEditorTyping(c, dataContext)
        fireEvent(CodeXPService.Event.TYPING)
    }

    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        super.afterActionPerformed(action, event, result)
        thisLogger().warn("Action performed: ${action.templateText}")

        when (action.templateText) { // Fire event based on action.
            "Run" -> fireEvent(CodeXPService.Event.RUN)
            "Save All" -> fireEvent(CodeXPService.Event.SAVE)
            "Debug" -> fireEvent(CodeXPService.Event.DEBUG)
            "Build Project" -> fireEvent(CodeXPService.Event.BUILD)
            "Rebuild Project" -> fireEvent(CodeXPService.Event.BUILD)
            "Paste" -> fireEvent(CodeXPService.Event.PASTE)
            "Backspace" -> fireEvent(CodeXPService.Event.BACKSPACE)
            "Tab" -> fireEvent(CodeXPService.Event.TAB)
            else -> fireEvent(CodeXPService.Event.ACTION)
        }
    }

    /**
     * Fire event to the message bus.
     *
     * @param event The event to fire.
     */
    private fun fireEvent(event: CodeXPService.Event) {
        ApplicationManager.getApplication().messageBus.syncPublisher(CodeXPListener.CODEXP_EVENT)
            .eventOccurred(event)
    }
}