package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionAcquisition
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionFacts
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionRequirement
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionCatalogTest {
    @Test
    fun `catalog rejects ambiguous IDs and invalid reward definitions`() {
        val item = CollectionFixtures.catalog().items().first()
        assertThrows(IllegalArgumentException::class.java) { CollectionCatalog(listOf(item, item.copy(displayName = "Renamed"))) }
        for (id in listOf("", "Localized name", "crest..one", ".crest", "crest/one")) {
            assertThrows(IllegalArgumentException::class.java) { item.copy(id = id) }
        }
        assertThrows(IllegalArgumentException::class.java) { item.copy(displayName = " ") }
        assertThrows(IllegalArgumentException::class.java) { CollectionAcquisition.Choice(0, CollectionRequirement.Always) }
        assertThrows(IllegalArgumentException::class.java) { CollectionAcquisition.Choice(-1, CollectionRequirement.Always) }
        assertThrows(IllegalArgumentException::class.java) { CollectionRequirement.EventCount(Event.NONE, 1) }
        assertThrows(IllegalArgumentException::class.java) { CollectionRequirement.CompletedChallenges(1, Event.NONE) }
        assertThrows(IllegalArgumentException::class.java) { CollectionRequirement.TotalXP(0) }
        assertThrows(IllegalArgumentException::class.java) { CollectionRequirement.AllOf(emptyList()) }
    }

    @Test
    fun `catalog size order and labels do not determine identity`() {
        val originals = CollectionFixtures.catalog().items().toMutableList()
        val firstCatalog = CollectionCatalog(originals)
        val extra = CollectionFixtures.item("title.future.extra", CollectionKind.TITLE, CollectionAcquisition.Automatic(CollectionRequirement.Always))
        val changedCatalog = CollectionCatalog(originals.reversed().map { it.copy(displayName = "New label") } + extra)
        originals.clear()
        assertEquals(6, firstCatalog.items().size)
        assertEquals(firstCatalog.items().map { it.id }.toSet() + extra.id, changedCatalog.items().map { it.id }.toSet())
        assertEquals("New label", changedCatalog.find(CollectionFixtures.STARTER)?.displayName)
        assertTrue(CollectionCatalog(emptyList()).items().isEmpty())
    }

    @Test
    fun `requirements use inclusive thresholds and preserve event specificity`() {
        val xp = CollectionRequirement.TotalXP(11)
        val saves = CollectionRequirement.EventCount(Event.SAVE, 3)
        val completedSaves = CollectionRequirement.CompletedChallenges(2, Event.SAVE)
        val completedAny = CollectionRequirement.CompletedChallenges(2)
        assertFalse(xp.isMet(CollectionFixtures.facts(xp = 10)))
        assertTrue(xp.isMet(CollectionFixtures.facts(xp = 11)))
        assertFalse(saves.isMet(CollectionFixtures.facts(saves = 2)))
        assertTrue(saves.isMet(CollectionFixtures.facts(saves = 3)))
        assertFalse(completedSaves.isMet(CollectionFixtures.facts(completedSaves = 1, completedRuns = 2)))
        assertTrue(completedSaves.isMet(CollectionFixtures.facts(completedSaves = 2)))
        assertTrue(completedAny.isMet(CollectionFixtures.facts(completedSaves = 1, completedRuns = 1)))
        assertFalse(completedAny.isMet(CollectionFixtures.facts()))
    }

    @Test
    fun `combined requirements require every condition and detach the definition list`() {
        val requirements = mutableListOf<CollectionRequirement>(CollectionRequirement.TotalXP(11), CollectionRequirement.EventCount(Event.SAVE, 3))
        val combined = CollectionRequirement.AllOf(requirements)
        requirements.clear()
        assertFalse(combined.isMet(CollectionFixtures.facts(xp = 11, saves = 2)))
        assertFalse(combined.isMet(CollectionFixtures.facts(xp = 10, saves = 3)))
        assertTrue(combined.isMet(CollectionFixtures.facts(xp = 11, saves = 3)))
    }

    @Test
    fun `historical level requirements use explicit inclusive evidence without inferring a curve`() {
        val requirement = CollectionRequirement.AchievedLevel(5)
        assertFalse(requirement.isMet(CollectionFixtures.facts(xp = Long.MAX_VALUE)))
        assertFalse(requirement.isMet(CollectionFixtures.facts(highestAchievedLevel = 4)))
        assertTrue(requirement.isMet(CollectionFixtures.facts(xp = 0, highestAchievedLevel = 5)))
        assertTrue(requirement.isMet(CollectionFixtures.facts(highestAchievedLevel = Int.MAX_VALUE)))
        assertThrows(IllegalArgumentException::class.java) { CollectionRequirement.AchievedLevel(0) }
        assertThrows(IllegalArgumentException::class.java) { CollectionFacts(0, highestAchievedLevel = 0) }
    }

    @Test
    fun `facts detach mutable source counters and reject negative progress`() {
        val events = mutableMapOf(Event.SAVE to 3L)
        val history = mutableMapOf(Event.SAVE to 2L)
        val facts = CollectionFacts(11, events, history)
        events.clear()
        history.clear()
        assertEquals(3L, facts.eventCounts[Event.SAVE])
        assertEquals(2L, facts.completedChallenges[Event.SAVE])
        assertThrows(IllegalArgumentException::class.java) { CollectionFacts(-1) }
        assertThrows(IllegalArgumentException::class.java) { CollectionFacts(0, mapOf(Event.SAVE to -1)) }
        assertThrows(IllegalArgumentException::class.java) { CollectionFacts(0, completedChallenges = mapOf(Event.SAVE to -1)) }
    }

    @Test
    fun `completed totals cannot overflow and incorrectly relock a reward`() {
        val requirement = CollectionRequirement.CompletedChallenges(Long.MAX_VALUE)
        assertFalse(requirement.isMet(CollectionFixtures.facts(completedSaves = Long.MAX_VALUE - 2, completedRuns = 1)))
        assertTrue(requirement.isMet(CollectionFixtures.facts(completedSaves = Long.MAX_VALUE - 1, completedRuns = 1)))
        assertTrue(requirement.isMet(CollectionFixtures.facts(completedSaves = Long.MAX_VALUE, completedRuns = Long.MAX_VALUE)))
    }
}
