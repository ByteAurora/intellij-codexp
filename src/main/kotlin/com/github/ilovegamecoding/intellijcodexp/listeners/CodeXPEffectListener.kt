package com.github.ilovegamecoding.intellijcodexp.listeners

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressResult
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.util.messages.Topic

/** One complete domain result, allowing presentation to sequence rewards without changing event order. */
internal fun interface CodeXPEffectListener {
    fun progressRecorded(
        result: CodeXPProgressResult,
        dataContext: DataContext?,
    )

    fun crestEquipped(
        eventId: String,
        design: com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign,
        dataContext: DataContext?,
    ) {}

    companion object {
        val EFFECTS: Topic<CodeXPEffectListener> = Topic.create("CodeXP effects", CodeXPEffectListener::class.java)
    }
}
