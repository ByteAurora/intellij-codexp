package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallengeFactory
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardChallenge
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardChallengeRow
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardSnapshot
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.number
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import com.intellij.util.xmlb.XmlSerializer
import java.awt.Container
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JLabel

class CodeXPChallengeSequenceTest : BasePlatformTestCase() {
    fun testEveryKindAdvancesIndependentlyAndHistoryKeepsItsOwnNumber() {
        val engine = CodeXPProgressEngine()
        val state = CodeXPState()
        engine.initialize(state)
        for (event in Event.entries.filter { it != Event.NONE }) {
            assertEquals(1L, CodeXPChallengeFactory.sequenceNumber(state.challenges.getValue(event)))
            val otherNumbers = state.challenges.filterKeys { it != event }.mapValues { CodeXPChallengeFactory.sequenceNumber(it.value) }
            for (stage in 1L..3L) {
                val challenge = state.challenges.getValue(event)
                assertEquals(stage, CodeXPChallengeFactory.sequenceNumber(challenge))
                repeat(challenge.goal.toInt()) { engine.recordEvent(state, event) }
                assertEquals(stage, CodeXPChallengeFactory.sequenceNumber(state.completedChallenges.last()))
                assertEquals(stage + 1, CodeXPChallengeFactory.sequenceNumber(state.challenges.getValue(event)))
                assertEquals(otherNumbers, state.challenges.filterKeys { it != event }.mapValues { CodeXPChallengeFactory.sequenceNumber(it.value) })
            }
        }
        val before = DashboardSnapshot.from(state).active.associate { it.id to it.sequenceNumber }
        engine.recordEvent(state, Event.TYPING)
        val reordered = DashboardSnapshot.from(state)
        assertEquals(Event.TYPING, reordered.active.first().event)
        assertEquals(before, reordered.active.associate { it.id to it.sequenceNumber })
        for (event in Event.entries.filter { it != Event.NONE }) {
            assertEquals(listOf(3L, 2L, 1L), reordered.completed.filter { it.event == event }.map { it.sequenceNumber })
        }
    }

    fun testLegacyRoundTripAndMissingHistoryDoNotInventOrChangeSequenceNumbers() {
        val engine = CodeXPProgressEngine()
        val state = CodeXPState()
        engine.initialize(state)
        repeat(3) {
            state.challenges.getValue(Event.SAVE).apply { progress = goal - 1 }
            engine.recordEvent(state, Event.SAVE)
        }
        val saved = XmlSerializer.serialize(state)
        assertFalse(JDOMUtil.writeElement(saved).contains("sequenceNumber"))
        val restored = XmlSerializer.deserialize(saved, CodeXPState::class.java)
        val restarted = CodeXPProgressEngine()
        restarted.initialize(restored)
        assertEquals(listOf(1L, 2L, 3L), restored.completedChallenges.map(CodeXPChallengeFactory::sequenceNumber))
        restored.completedChallenges.clear()
        restored.eventCounts.clear()
        assertEquals(4L, CodeXPChallengeFactory.sequenceNumber(restored.challenges.getValue(Event.SAVE)))
        restored.challenges.getValue(Event.SAVE).apply { progress = goal - 1 }
        restarted.recordEvent(restored, Event.SAVE)
        assertEquals(4L, CodeXPChallengeFactory.sequenceNumber(restored.completedChallenges.single()))
        assertEquals(5L, CodeXPChallengeFactory.sequenceNumber(restored.challenges.getValue(Event.SAVE)))
        assertEquals(1L, CodeXPChallengeFactory.sequenceNumber(restored.challenges.getValue(Event.TYPING)))

        fun unknown(change: CodeXPChallenge.() -> Unit) {
            val item = CodeXPChallengeFactory.createEventDefaultChallenges().first().apply(change)
            assertNull(CodeXPChallengeFactory.sequenceNumber(item))
            assertNull(DashboardChallenge.from(item).sequenceNumber)
        }
        unknown { goal += 1 }
        unknown { rewardXP += rewardXPIncrement }
        unknown { goalIncrement = 0 }
        unknown { rewardXPIncrement = 0 }
        unknown { goal = Long.MAX_VALUE }
        unknown { rewardXP = Long.MIN_VALUE }
        unknown { event = Event.NONE }
    }

    fun testSequenceTextFitsFixedCardsWithLongTitlesNarrowWidthsAndBothThemes() {
        val state = CodeXPState()
        val engine = CodeXPProgressEngine()
        engine.initialize(state)
        val stages = listOf(1L, 2L, 3L, 12L, 123L, 1234L)
        val records = mutableListOf<DashboardChallenge>()
        for (stage in 1L..stages.last()) {
            val item = state.challenges.getValue(Event.SAVE)
            if (stage in stages) records += DashboardChallenge.from(item).copy(name = "Very long challenge title / 아주 긴 챌린지 이름")
            item.progress = item.goal - 1
            engine.recordEvent(state, Event.SAVE)
        }
        val originalDark = !JBColor.isBright()
        try {
            for (dark in listOf(false, true)) {
                JBColor.setDark(dark)
                for (width in listOf(198, 228, 388)) {
                    val image = BufferedImage(width * 2, 106 * records.size * 2, BufferedImage.TYPE_INT_ARGB)
                    val graphics = image.createGraphics()
                    graphics.scale(2.0, 2.0)
                    for ((index, item) in records.withIndex()) {
                        val row = DashboardChallengeRow()
                        row.setFinal(item)
                        row.setSize(width, 106)
                        layout(row)
                        assertEquals(CodeXPBundle.message("challenge.sequence", number(stages[index])), row.sequence.text)
                        assertTrue(row.sequence.width >= row.sequence.preferredSize.width)
                        assertEquals(106, row.height)
                        assertTrue(row.accessibleContext.accessibleName.contains(row.sequence.text))
                        val labels = labels(row).map { javax.swing.SwingUtilities.convertRectangle(it.parent, it.bounds, row) }
                        for ((i, bounds) in labels.withIndex()) {
                            assertTrue(bounds.height > 0)
                            assertTrue(bounds.y + bounds.height <= row.bar.y)
                            assertTrue(labels.drop(i + 1).none(bounds::intersects))
                        }
                        val cellGraphics = graphics.create(0, index * 106, width, 106)
                        row.paint(cellGraphics)
                        cellGraphics.dispose()
                    }
                    graphics.dispose()
                    val file = File("build/reports/achievement-effects/activity-sequence/sequence-${if (dark) "dark" else "light"}-$width.png")
                    file.parentFile.mkdirs()
                    ImageIO.write(image, "png", file)
                }
            }
        } finally {
            JBColor.setDark(originalDark)
        }
    }

    private fun layout(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layout)
    }

    private fun labels(container: Container): List<JLabel> =
        container.components.flatMap {
            when (it) {
                is JLabel -> listOf(it)
                is Container -> labels(it)
                else -> emptyList()
            }
        }
}
