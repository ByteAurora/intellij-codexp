package com.github.ilovegamecoding.intellijcodexp.listeners

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.intellij.util.messages.Topic

/**
 * CodeXPListener interface
 *
 * This interface is used to manage xp and challenge update from the CodeXP plugin.
 */
interface CodeXPListener {
    companion object {
        /**
         * Topic for CodeXP events.
         */
        val CODEXP: Topic<CodeXPListener> = Topic.create("CodeXP", CodeXPListener::class.java)
    }

    /**
     * Function that is called when xp updated.
     *
     * @param levelInfo Level info.
     */
    fun xpUpdated(levelInfo: CodeXPLevel)

    /**
     * Function that is called when challenge updated.
     *
     * @param challenge Updated challenge.
     */
    fun challengeUpdated(event: Event, challenge: CodeXPChallenge, newChallenge: CodeXPChallenge? = null)
}