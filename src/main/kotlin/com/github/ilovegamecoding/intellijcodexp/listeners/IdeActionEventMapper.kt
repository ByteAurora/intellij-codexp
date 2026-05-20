package com.github.ilovegamecoding.intellijcodexp.listeners

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction

/**
 * Maps IntelliJ action metadata to CodeXP events.
 */
internal object IdeActionEventMapper {
    private val actionIdEvents: Map<String, Event> =
        mapOf(
            "Run" to Event.RUN,
            "Debug" to Event.DEBUG,
            "SaveAll" to Event.SAVE,
            "CompileProject" to Event.BUILD,
            "CompileDirty" to Event.BUILD,
            "MakeProject" to Event.BUILD,
            "RebuildProject" to Event.BUILD,
            "\$Cut" to Event.CUT,
            "EditorCut" to Event.CUT,
            "\$Copy" to Event.COPY,
            "EditorCopy" to Event.COPY,
            "\$Paste" to Event.PASTE,
            "EditorPaste" to Event.PASTE,
            "EditorBackSpace" to Event.BACKSPACE,
            "EditorBackSpaceWithSelection" to Event.BACKSPACE,
            "EditorTab" to Event.TAB,
            "EditorEnter" to Event.ENTER,
        )

    private val templateTextEvents: Map<String, Event> =
        mapOf(
            "Run" to Event.RUN,
            "Save All" to Event.SAVE,
            "Debug" to Event.DEBUG,
            "Build Project" to Event.BUILD,
            "Rebuild Project" to Event.BUILD,
            "Cut" to Event.CUT,
            "Copy" to Event.COPY,
            "Paste" to Event.PASTE,
            "Backspace" to Event.BACKSPACE,
            "Tab" to Event.TAB,
            "Enter" to Event.ENTER,
        )

    /**
     * Maps an IntelliJ action instance to a CodeXP event.
     */
    fun eventFor(action: AnAction): Event =
        eventForAction(
            actionId = ActionManager.getInstance().getId(action),
            templateText = action.templateText,
        )

    /**
     * Maps raw action metadata to a CodeXP event.
     */
    fun eventForAction(
        actionId: String?,
        templateText: String?,
    ): Event = actionIdEvents[actionId] ?: templateTextEvents[templateText] ?: Event.ACTION
}
