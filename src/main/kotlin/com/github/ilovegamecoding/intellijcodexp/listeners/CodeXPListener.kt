package com.github.ilovegamecoding.intellijcodexp.listeners

import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.util.messages.Topic

/**
 * CodeXPListener interface
 *
 * This interface is used to listen to events from the CodeXP plugin.
 */
interface CodeXPListener {
    companion object {
        /**
         * Topic for CodeXP events.
         */
        val CODEXP_EVENT: Topic<CodeXPListener> = Topic.create("CodeXP Event", CodeXPListener::class.java)
    }

    /**
     * Function that is called when an event occurs.
     *
     * @param event The event that occurred.
     */
    fun eventOccurred(event: CodeXPService.Event)
}