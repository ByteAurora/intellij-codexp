package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.XpDestination
import com.github.ilovegamecoding.intellijcodexp.listeners.actionDestination
import com.intellij.openapi.actionSystem.ActionPlaces
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel

class IdeXpOriginTest {
    private val editor = JPanel()
    private val key = KeyEvent(editor, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_V, 'v')
    private val mouse = MouseEvent(editor, MouseEvent.MOUSE_RELEASED, 0, 0, 5, 5, 1, false)

    @Test
    fun `direct editing keys use the caret but menus and toolbars always use the window`() {
        for (event in listOf(Event.CUT, Event.COPY, Event.PASTE, Event.BACKSPACE, Event.TAB, Event.ENTER)) {
            assertEquals(XpDestination.CARET, actionDestination(true, true, event, null, false, ActionPlaces.KEYBOARD_SHORTCUT, key, true))
            for (place in listOf(ActionPlaces.MAIN_MENU, ActionPlaces.EDITOR_POPUP, ActionPlaces.MAIN_TOOLBAR, ActionPlaces.ACTION_SEARCH)) {
                // A menu may carry both EDITOR data and a keyboard event whose source is the editor.
                assertEquals(XpDestination.WINDOW, actionDestination(true, true, event, null, false, place, key, true))
                assertEquals(XpDestination.WINDOW, actionDestination(true, true, event, null, false, place, mouse, true))
            }
        }
    }

    @Test
    fun `non editing shortcuts and missing editor data still use a project window`() {
        for (event in listOf(Event.SAVE, Event.BUILD, Event.RUN, Event.DEBUG, Event.ACTION)) {
            assertEquals(XpDestination.WINDOW, actionDestination(true, true, event, null, false, ActionPlaces.KEYBOARD_SHORTCUT, key, true))
            assertEquals(XpDestination.WINDOW, actionDestination(true, false, event, null, false, ActionPlaces.MAIN_TOOLBAR, mouse, false))
        }
        assertEquals(XpDestination.CARET, actionDestination(true, true, Event.ACTION, "EditorDelete", true, ActionPlaces.KEYBOARD_SHORTCUT, key, true))
        assertEquals(XpDestination.CARET, actionDestination(true, true, Event.ACTION, "CodeCompletion", false, ActionPlaces.KEYBOARD_SHORTCUT, key, true))
    }

    @Test
    fun `an editor or a mouse event alone cannot imply direct editing and unknown projects are skipped`() {
        assertEquals(XpDestination.WINDOW, actionDestination(true, true, Event.PASTE, null, false, ActionPlaces.UNKNOWN, mouse, false))
        assertEquals(XpDestination.WINDOW, actionDestination(true, true, Event.PASTE, null, false, ActionPlaces.UNKNOWN, null, true))
        assertEquals(XpDestination.WINDOW, actionDestination(true, false, Event.PASTE, null, false, ActionPlaces.KEYBOARD_SHORTCUT, key, true))
        assertEquals(XpDestination.NONE, actionDestination(false, true, Event.PASTE, null, false, ActionPlaces.KEYBOARD_SHORTCUT, key, true))
        assertEquals(XpDestination.NONE, actionDestination(false, false, Event.SAVE, null, false, ActionPlaces.MAIN_MENU, mouse, false))
    }
}
