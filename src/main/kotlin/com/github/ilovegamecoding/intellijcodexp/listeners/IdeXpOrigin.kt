package com.github.ilovegamecoding.intellijcodexp.listeners

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities

internal enum class XpDestination { CARET, WINDOW, NONE }

/** Captured synchronously; never retains an action, InputEvent or ephemeral DataContext. */
internal data class IdeXpOrigin(
    val project: Project?,
    val editor: Editor?,
    val pane: JLayeredPane?,
    val destination: XpDestination,
) {
    companion object {
        val KEY: DataKey<IdeXpOrigin> = IdeXpOriginKey.KEY
        private val log = Logger.getInstance(IdeXpOrigin::class.java)

        fun typing(context: DataContext): IdeXpOrigin {
            val editor = CommonDataKeys.EDITOR.getData(context)
            val project = CommonDataKeys.PROJECT.getData(context) ?: editor?.project
            val target = editor?.takeIf { project != null && it.project === project }
            return IdeXpOrigin(project, target, target?.contentComponent?.rootPane?.layeredPane, if (target != null) XpDestination.CARET else XpDestination.NONE)
        }

        fun action(
            event: AnActionEvent,
            mappedEvent: Event,
            actionId: String?,
            editorAction: Boolean,
        ): IdeXpOrigin {
            val context = event.dataContext
            val component = event.inputEvent?.component ?: PlatformDataKeys.CONTEXT_COMPONENT.getData(context)
            val sourceFrame = frameOf(component)
            val contextFrame = IdeFrame.KEY.getData(context)
            val project = CommonDataKeys.PROJECT.getData(context) ?: sourceFrame?.project ?: contextFrame?.project
            // A mismatched source frame must never send an effect to a different project's window.
            if (sourceFrame?.project != null && sourceFrame.project !== project) return IdeXpOrigin(null, null, null, XpDestination.NONE)
            val frame = (sourceFrame ?: contextFrame)?.takeIf { it.project === project }
            val editor = CommonDataKeys.EDITOR.getData(context)?.takeIf { project != null && it.project === project }
            val content = editor?.contentComponent
            val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            val insideEditor = content != null && component != null && SwingUtilities.isDescendingFrom(component, content)
            // A key may originate on the IDE frame rather than the editor that has keyboard focus.
            val focusedEditorSource =
                event.inputEvent is KeyEvent && content != null && component != null && SwingUtilities.isDescendingFrom(content, component) &&
                    focus != null && SwingUtilities.isDescendingFrom(focus, content)
            val destination =
                actionDestination(
                    project != null,
                    editor != null,
                    mappedEvent,
                    actionId,
                    editorAction,
                    event.place,
                    event.inputEvent,
                    insideEditor,
                    focusedEditorSource,
                )
            if (log.isDebugEnabled) {
                log.debug("XP route id=$actionId event=$mappedEvent place=${event.place} input=${event.inputEvent?.javaClass?.simpleName} source=${component?.javaClass?.simpleName} editor=${editor != null} inside=$insideEditor focused=$focusedEditorSource destination=$destination")
            }
            return IdeXpOrigin(project, editor.takeIf { destination == XpDestination.CARET }, frame?.component?.rootPane?.layeredPane, destination)
        }

        /** Legacy callers with a known project get a fixed effect, never a guessed caret. */
        fun unspecified(context: DataContext): IdeXpOrigin {
            val project = CommonDataKeys.PROJECT.getData(context)
            return IdeXpOrigin(project, null, null, if (project != null) XpDestination.WINDOW else XpDestination.NONE)
        }

        private fun frameOf(component: Component?): IdeFrame? {
            var window = component as? Window ?: component?.let(SwingUtilities::getWindowAncestor)
            while (window != null) {
                if (window is IdeFrame) return window
                window = window.owner
            }
            return null
        }
    }
}

internal fun actionDestination(
    projectKnown: Boolean,
    editorKnown: Boolean,
    event: Event,
    actionId: String?,
    editorAction: Boolean,
    place: String,
    input: InputEvent?,
    sourceInsideEditor: Boolean,
    focusedEditorSource: Boolean = false,
): XpDestination {
    if (!projectKnown) return XpDestination.NONE
    val directPlace = place == ActionPlaces.KEYBOARD_SHORTCUT || place == ActionPlaces.MOUSE_SHORTCUT || place == ActionPlaces.UNKNOWN
    val edit = event in EDIT_EVENTS || (event == Event.ACTION && (editorAction || actionId in EDIT_ACTION_IDS))
    val directInput = (input is KeyEvent && (sourceInsideEditor || focusedEditorSource)) || (input is MouseEvent && sourceInsideEditor)
    return if (editorKnown && edit && directPlace && directInput) XpDestination.CARET else XpDestination.WINDOW
}

private val EDIT_EVENTS = setOf(Event.CUT, Event.COPY, Event.PASTE, Event.BACKSPACE, Event.TAB, Event.ENTER)
private val EDIT_ACTION_IDS = setOf("\$Undo", "\$Redo", "CodeCompletion", "SmartTypeCompletion", "ClassNameCompletion", "HippieCompletion", "HippieBackwardCompletion")
