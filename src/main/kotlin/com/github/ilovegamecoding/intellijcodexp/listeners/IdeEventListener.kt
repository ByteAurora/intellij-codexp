package com.github.ilovegamecoding.intellijcodexp.listeners

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager

/**
 * CodeXPEventListener class
 *
 * This class listens to events from the IDE and fires them to the message bus.
 */
internal class IdeEventListener : AnActionListener {
    override fun afterEditorTyping(
        c: Char,
        dataContext: DataContext,
    ) {
        super.afterEditorTyping(c, dataContext)

        fireEvent(Event.TYPING, dataContext)
    }

    override fun afterActionPerformed(
        action: AnAction,
        event: AnActionEvent,
        result: AnActionResult,
    ) {
        super.afterActionPerformed(action, event, result)

        fireEvent(IdeActionEventMapper.eventFor(action), event.dataContext)
    }

    /**
     * Fire event to the message bus.
     *
     * @param event The event to fire.
     * @param dataContext The data context of the event.
     */
    private fun fireEvent(
        event: Event,
        dataContext: DataContext? = null,
    ) {
        ApplicationManager
            .getApplication()
            .messageBus
            .syncPublisher(CodeXPEventListener.CODEXP_EVENT)
            .eventOccurred(event, dataContext)
    }
}
