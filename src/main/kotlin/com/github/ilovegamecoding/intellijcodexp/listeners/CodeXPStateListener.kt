package com.github.ilovegamecoding.intellijcodexp.listeners

import com.intellij.util.messages.Topic

/** Preference/restoration updates are separate from earned progress, so reopening never celebrates. */
internal fun interface CodeXPStateListener {
    fun stateChanged()

    companion object {
        val STATE: Topic<CodeXPStateListener> = Topic.create("CodeXP state", CodeXPStateListener::class.java)
    }
}
