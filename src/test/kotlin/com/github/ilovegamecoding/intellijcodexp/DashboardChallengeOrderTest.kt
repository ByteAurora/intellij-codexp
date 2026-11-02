package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardChallenge
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardChallengeColumn
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardChallengeRow
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardHistoryList
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardSnapshot
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import java.awt.Point
import java.awt.Rectangle
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JViewport
import javax.swing.SwingUtilities

class DashboardChallengeOrderTest : BasePlatformTestCase() {
    fun testRealEventRecencyCompletionIdentityStableTiesAndPersistence() {
        val state = CodeXPState()
        val engine = CodeXPProgressEngine()
        engine.initialize(state)
        assertTrue(state.challenges.values.all { it.lastUpdatedOrder == 0L })
        engine.recordEvent(state, Event.BUILD)
        engine.recordEvent(state, Event.SAVE)
        val previous = DashboardSnapshot.from(state).active.map { it.id }
        engine.recordEvent(state, Event.COPY)
        val next = DashboardSnapshot.from(state).active.map { it.id }
        val copy = state.challenges.getValue(Event.COPY).id
        assertEquals(copy, next.first())
        assertEquals(previous.filter { it != copy }, next.drop(1))
        for (event in listOf(Event.CUT, Event.COPY)) {
            state.challenges.getValue(event).apply { progress = goal - 1 }
            val result = engine.recordEvent(state, event)
            assertNotSame(result.completedChallenge, result.newChallenge)
            assertEquals(result.completedChallenge!!.lastUpdatedOrder, result.newChallenge!!.lastUpdatedOrder)
            assertEquals(
                result.newChallenge!!.id,
                DashboardSnapshot
                    .from(state)
                    .active
                    .first()
                    .id,
            )
        }
        repeat(150) { engine.recordEvent(state, Event.TYPING) }
        val history = DashboardSnapshot.from(state).completed.map { it.id }
        assertEquals(state.completedChallenges.asReversed().map { it.id }, history)
        repeat(20) { engine.recordEvent(state, Event.COPY) }
        // COPY's new completion is a new record; older COPY/CUT/TYPING records keep their relative order.
        assertEquals(
            history,
            DashboardSnapshot
                .from(state)
                .completed
                .map { it.id }
                .filter { it in history },
        )
        val order = DashboardSnapshot.from(state).active
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(state), CodeXPState::class.java)
        engine.initialize(restored)
        assertEquals(order, DashboardSnapshot.from(restored).active)
        val tied = listOf(Event.DEBUG, Event.RUN)
        tied.forEach { state.challenges.getValue(it).lastUpdatedOrder = 9999 }
        val stable =
            DashboardSnapshot
                .from(state)
                .active
                .take(2)
                .map { it.event }
        assertEquals(tied.sortedBy { it.ordinal }, stable)
        repeat(20) {
            assertEquals(
                stable,
                DashboardSnapshot
                    .from(state)
                    .active
                    .take(2)
                    .map { it.event },
            )
        }
        state.challenges.getValue(Event.BUILD).lastUpdatedOrder = Long.MAX_VALUE
        engine.recordEvent(state, Event.SAVE)
        assertEquals(
            Event.SAVE,
            DashboardSnapshot
                .from(state)
                .active
                .first()
                .event,
        )
    }

    fun testRetainedRowsMoveAtTheirRealClickBoundsAndPreserveScrollAnchor() {
        var now = 0L
        val column = DashboardChallengeColumn { now }
        val viewport =
            JViewport().apply {
                setSize(300, 230)
                view = column
            }
        val rows = (0..11).map { it.toString() to DashboardChallengeRow() }
        val button = JButton("Focus target")
        rows[1].second.add(button)
        try {
            column.update(rows, emptySet())
            viewport.doLayout()
            column.doLayout()
            column.motion.visibilityChanged(true)
            val reordered = listOf(rows[2], rows[0], rows[1]) + rows.drop(3)
            column.update(reordered, setOf("2"))
            assertEquals("2", column.motion.order.first())
            now = 160
            column.motion.tick(now)
            now = 290
            column.motion.tick(now)
            val moving = rows[2].second
            assertTrue(moving.y in 1 until 230)
            val hit = column.findComponentAt(2, moving.y + 2)
            assertTrue(hit === moving || SwingUtilities.isDescendingFrom(hit, moving))
            assertSame(rows[1].second, button.parent)
            assertEquals(0, column.getComponentZOrder(moving))
            now = 431
            column.motion.tick(now)
            assertEquals(0, moving.y)
            viewport.viewPosition = Point(0, 460)
            val anchor = column.motion.order[4]
            val anchoredOffset = viewport.viewPosition.y - column.motion.y(anchor)
            column.update(listOf(rows.last()) + reordered.filter { it != rows.last() }, setOf(rows.last().first))
            assertEquals(anchoredOffset, viewport.viewPosition.y - column.motion.y(anchor), .01)
            assertTrue(viewport.viewPosition.y > 0)
            assertFalse(column.motion.hasMovement)
        } finally {
            column.dispose()
        }
    }

    fun testHistoryPrependKeepsSelectionReadingPositionAndHitTestingDuringMotion() {
        var now = 0L
        val history = DashboardHistoryList(DefaultListModel()) { now }
        val viewport =
            JViewport().apply {
                setSize(300, 232)
                view = history
            }
        val records = (0..29).map { record(it.toString()) }
        try {
            history.update(records, emptySet())
            history.fixedCellWidth = 300
            viewport.doLayout()
            history.motion.visibilityChanged(true)
            history.selectedIndex = 4
            viewport.viewPosition = Point(0, 464)
            history.update(listOf(record("new")) + records, setOf("new"))
            assertEquals("4", history.selectedValue.id)
            assertEquals(580, viewport.viewPosition.y)
            assertFalse(history.motion.hasMovement)
            viewport.viewPosition = Point(0, 0)
            history.update(listOf(record("latest"), record("new")) + records, setOf("latest"))
            now = 160
            history.motion.tick(now)
            now = 295
            history.motion.tick(now)
            for (index in 0..2) {
                val rect = history.getCellBounds(index, index)
                val actual = history.locationToIndex(Point(10, rect.centerY.toInt()))
                assertEquals(index, actual)
            }
            assertEquals("4", history.selectedValue.id)
            history.motion.visibilityChanged(false)
            history.motion.visibilityChanged(true)
            assertFalse(history.motion.isRunning)
            assertEquals(Rectangle(0, 0, history.width, 116), history.getCellBounds(0, 0))
        } finally {
            history.dispose()
        }
    }

    private fun record(id: String): DashboardChallenge = DashboardChallenge(id, Event.TYPING, "Typing", "Type", 100, 100, 100)
}
