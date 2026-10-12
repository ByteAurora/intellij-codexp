package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.IdeActionEventMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class IdeActionEventMapperTest {
    @Test
    fun `action ids map to specific code xp events`() {
        assertEquals(Event.SAVE, IdeActionEventMapper.eventForAction("SaveAll", null))
        assertEquals(Event.BUILD, IdeActionEventMapper.eventForAction("MakeProject", null))
        assertEquals(Event.PASTE, IdeActionEventMapper.eventForAction("\$Paste", null))
        assertEquals(Event.BACKSPACE, IdeActionEventMapper.eventForAction("EditorBackSpace", null))
    }

    @Test
    fun `template text is used as fallback for existing action names`() {
        assertEquals(Event.RUN, IdeActionEventMapper.eventForAction(null, "Run"))
        assertEquals(Event.BUILD, IdeActionEventMapper.eventForAction(null, "Build Project"))
        assertEquals(Event.ENTER, IdeActionEventMapper.eventForAction(null, "Enter"))
    }

    @Test
    fun `unknown actions map to generic action event`() {
        assertEquals(Event.ACTION, IdeActionEventMapper.eventForAction("UnknownAction", "Unknown Action"))
    }
}
