package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEffectListener
import com.github.ilovegamecoding.intellijcodexp.listeners.IdeEventListener
import com.github.ilovegamecoding.intellijcodexp.listeners.IdeXpOrigin
import com.github.ilovegamecoding.intellijcodexp.listeners.XpDestination
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JButton

class IdeEventPresentationTest : BasePlatformTestCase() {
    fun testEachIdeCallbackAwardsOnceAndPublishesExactlyOnePresentationDestination() {
        myFixture.configureByText("Routing.kt", "class Routing")
        val app = ApplicationManager.getApplication()
        val service = app.getService(CodeXPService::class.java)
        val recorded = mutableListOf<Pair<Event, IdeXpOrigin>>()
        app.messageBus.connect(testRootDisposable).subscribe(
            CodeXPEffectListener.EFFECTS,
            CodeXPEffectListener { result, context ->
                IdeXpOrigin.KEY.getData(context!!)?.let { recorded += result.event to it }
            },
        )
        val listener = IdeEventListener()
        val context =
            SimpleDataContext
                .builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.EDITOR, myFixture.editor)
                .build()
        val key = KeyEvent(myFixture.editor.contentComponent, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_V, 'v')
        val button = JButton("Paste")
        val click = MouseEvent(button, MouseEvent.MOUSE_RELEASED, 0, 0, 2, 2, 1, false)

        fun check(
            expectedEvent: Event,
            expectedDestination: XpDestination,
            operation: () -> Unit,
        ) {
            val before = service.state.getEventCount(expectedEvent)
            recorded.clear()
            operation()
            assertEquals(before + 1, service.state.getEventCount(expectedEvent))
            assertEquals(1, recorded.size)
            val (event, origin) = recorded.single()
            assertEquals(expectedEvent, event)
            assertEquals(expectedDestination, origin.destination)
            assertSame(project, origin.project)
            if (expectedDestination == XpDestination.CARET) assertSame(myFixture.editor, origin.editor) else assertNull(origin.editor)
        }

        fun action(
            name: String,
            place: String,
            input: InputEvent?,
            data: DataContext = context,
        ) {
            val action =
                object : AnAction(name) {
                    override fun actionPerformed(e: AnActionEvent) {}
                }
            listener.afterActionPerformed(action, AnActionEvent.createFromAnAction(action, input, place, data), AnActionResult.PERFORMED)
        }

        check(Event.TYPING, XpDestination.CARET) { listener.afterEditorTyping('x', context) }
        check(Event.PASTE, XpDestination.CARET) { action("Paste", ActionPlaces.KEYBOARD_SHORTCUT, key) }
        check(Event.PASTE, XpDestination.WINDOW) { action("Paste", ActionPlaces.MAIN_MENU, key) }
        check(Event.PASTE, XpDestination.WINDOW) { action("Paste", ActionPlaces.MAIN_TOOLBAR, click) }
        check(Event.SAVE, XpDestination.WINDOW) { action("Save All", ActionPlaces.KEYBOARD_SHORTCUT, key) }
        check(Event.BUILD, XpDestination.WINDOW) { action("Build Project", ActionPlaces.MAIN_TOOLBAR, click) }
        check(Event.SAVE, XpDestination.WINDOW) { action("Save All", ActionPlaces.MAIN_MENU, null, SimpleDataContext.getProjectContext(project)) }

        val global = SimpleDataContext.builder().add(CommonDataKeys.EDITOR, myFixture.editor).build()
        recorded.clear()
        action("Save All", ActionPlaces.MAIN_MENU, null, global)
        assertEquals(XpDestination.NONE, recorded.single().second.destination)
        assertNull(recorded.single().second.project)
    }
}
