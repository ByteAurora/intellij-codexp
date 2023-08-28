package com.github.ilovegamecoding.intellijcodexp.listeners

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.intellij.util.messages.Topic

interface CodeXPListener {
    companion object {
        /**
         * Topic for CodeXP events.
         */
        val CODEXP: Topic<CodeXPListener> = Topic.create("CodeXP", CodeXPListener::class.java)
    }

    /**
     * Function that is called when level up.
     *
     * @param levelInfo The new level.
     */
    fun xpUpdated(levelInfo: CodeXPLevel)

    /**
     * Function that is called when challenge completed.
     *
     * @param challenge The completed challenge.
     */
    fun challengeUpdated(event: Event, challenge: CodeXPChallenge, newChallenge: CodeXPChallenge? = null)
}